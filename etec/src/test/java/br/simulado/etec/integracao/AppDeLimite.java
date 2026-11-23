package br.simulado.etec.integracao;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.crypto.password.PasswordEncoder;

import br.simulado.etec.EtecApplication;
import br.simulado.etec.shared.security.limite.RelogioDeTeste;
import br.simulado.etec.usuarios.service.UsuarioService;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

/**
 * A aplicação INTEIRA (Flyway, Hibernate em {@code validate}, segurança, filtros) contra um schema próprio do MySQL real, com
 * o relógio de teste no lugar do relógio da aplicação e um contador de comparações de BCrypt. Vários contextos podem
 * apontar para o MESMO schema (é assim que se prova que o contador é compartilhado entre instâncias).
 *
 * <p>Os limites do application.yml de teste são altíssimos; cada teste passa por {@code extras} os valores que quer provar
 * (por exemplo {@code --app.rate-limit.signin-email-origem.limite=5}). Nada de senha em linha de comando de PROCESSO: os
 * argumentos são em memória.
 */
final class AppDeLimite implements AutoCloseable {

	/** Senha usada pelos usuários de teste (falsa, só do teste). */
	static final String SENHA_DO_ALUNO = "senha-de-teste-123";

	/** As comparações de BCrypt feitas por este contexto (só {@code matches}). */
	final AtomicInteger comparacoes;

	final ConfigurableApplicationContext contexto;

	final RelogioDeTeste relogio;

	final String schema;

	private final int porta;

	private AppDeLimite(ConfigurableApplicationContext contexto, RelogioDeTeste relogio, String schema, int porta,
			AtomicInteger comparacoes) {
		this.comparacoes = comparacoes;
		this.contexto = contexto;
		this.relogio = relogio;
		this.schema = schema;
		this.porta = porta;
	}

	/** Cria um schema novo e vazio e devolve o nome. */
	static String novoSchema() throws SQLException {
		String nome = "etec_limite_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
		try (Connection c = DriverManager.getConnection(AbstractIntegrationTest.MYSQL.getJdbcUrl(), "root",
				AbstractIntegrationTest.MYSQL.getPassword()); Statement s = c.createStatement()) {
			s.execute("CREATE DATABASE " + nome + " CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
		}
		return nome;
	}

	static JdbcTemplate jdbcDo(String schema) {
		return new JdbcTemplate(new DriverManagerDataSource(urlDo(schema), "root",
				AbstractIntegrationTest.MYSQL.getPassword()));
	}

	private static String urlDo(String schema) {
		return AbstractIntegrationTest.MYSQL.getJdbcUrl().replaceFirst("/etec(\\?|$)", "/" + schema + "$1");
	}

	/**
	 * Sobe a aplicação no schema dado. {@code web} liga o servidor (porta aleatória); sem ele o contexto serve só para
	 * chamar beans. {@code extras} são argumentos {@code --chave=valor} em memória.
	 */
	static AppDeLimite iniciar(String schema, RelogioDeTeste relogio, boolean web, String... extras) {
		AtomicInteger comparacoes = new AtomicInteger();
		List<String> args = new ArrayList<>(List.of("--spring.datasource.url=" + urlDo(schema),
				"--spring.datasource.username=root", "--spring.datasource.password=" + AbstractIntegrationTest.MYSQL.getPassword(),
				"--app.termos.url=https://exemplo.invalid/termos", "--server.port=0", "--ADMIN_EMAIL=", "--ADMIN_SENHA="));
		args.addAll(List.of(extras));
		ConfigurableApplicationContext contexto = new SpringApplicationBuilder(EtecApplication.class)
				.web(web ? WebApplicationType.SERVLET : WebApplicationType.NONE)
				.profiles("testcontainers")
				.initializers(ctx -> {
					// O relógio de teste vence o da aplicação (primário) e o encoder passa a contar as comparações.
					((GenericApplicationContext) ctx).registerBean("relogioDeTeste", Clock.class, () -> relogio,
							definicao -> definicao.setPrimary(true));
					ctx.getBeanFactory().addBeanPostProcessor(new BeanPostProcessor() {
						@Override
						public Object postProcessAfterInitialization(Object bean, String nome) {
							return (bean instanceof PasswordEncoder encoder) ? new Contador(encoder, comparacoes) : bean;
						}
					});
				})
				.run(args.toArray(String[]::new));
		int porta = web ? ((WebServerApplicationContext) contexto).getWebServer().getPort() : -1;
		comparacoes.set(0);
		return new AppDeLimite(contexto, relogio, schema, porta, comparacoes);
	}

	JdbcTemplate jdbc() {
		return jdbcDo(schema);
	}

	/** Requisição HTTP real (JSON) para este contexto, pelo IPv4 de loopback. */
	RequestSpecification http() {
		return RestAssured.given().baseUri("http://127.0.0.1").port(porta).contentType(ContentType.JSON).accept(ContentType.JSON);
	}

	/** Cria um aluno com e-mail único e a senha de teste; devolve o e-mail. */
	String criarAluno() {
		String email = "aluno." + UUID.randomUUID().toString().substring(0, 8) + "@exemplo.invalid";
		contexto.getBean(UsuarioService.class).criarAluno("Aluno de Teste", email, SENHA_DO_ALUNO, LocalDate.of(2008, 5, 10),
				contexto.getEnvironment().getProperty("app.termos.versao"));
		return email;
	}

	Response signin(String email, String senha) {
		return signin(email, senha, new String[0]);
	}

	/** Signin com cabeçalhos extras, em pares nome, valor. */
	Response signin(String email, String senha, String... cabecalhos) {
		RequestSpecification pedido = http();
		for (int i = 0; i + 1 < cabecalhos.length; i += 2) {
			pedido = pedido.header(cabecalhos[i], cabecalhos[i + 1]);
		}
		return pedido.body("{\"email\":\"" + email + "\",\"senha\":\"" + senha + "\"}").post("/api/v1/auth/signin");
	}

	@Override
	public void close() {
		contexto.close();
	}

	/** Encoder que só conta as chamadas de {@code matches}. */
	private record Contador(PasswordEncoder delegado, AtomicInteger comparacoes) implements PasswordEncoder {

		@Override
		public String encode(CharSequence senha) {
			return delegado.encode(senha);
		}

		@Override
		public boolean matches(CharSequence senha, String hash) {
			comparacoes.incrementAndGet();
			return delegado.matches(senha, hash);
		}

	}

}
