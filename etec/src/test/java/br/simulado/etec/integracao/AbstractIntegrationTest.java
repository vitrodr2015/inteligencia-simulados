package br.simulado.etec.integracao;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;

import br.simulado.etec.shared.security.JwtTokenProvider;
import br.simulado.etec.shared.security.JwtTokenProvider.TipoToken;
import br.simulado.etec.usuarios.model.Papel;
import br.simulado.etec.usuarios.model.Usuario;
import br.simulado.etec.usuarios.repository.UsuarioRepository;

/**
 * Base dos testes de integração: aplicação inteira (porta aleatória, HTTP real) contra um MySQL de
 * verdade, com as migrations de verdade (Flyway) e {@code ddl-auto: validate}.
 *
 * <p>UM contêiner estático, iniciado uma única vez para todas as classes (o início custa segundos;
 * um por classe deixaria o build lento). Todas as subclasses compartilham a mesma configuração do
 * Spring, então o contexto é reaproveitado (nada de {@code @DirtiesContext}).
 *
 * <p>Como o banco é compartilhado, cada teste cria os próprios dados com nomes únicos
 * ({@link #nomeUnico(String)}) e não depende de ordem nem de limpeza.
 *
 * <p>A segurança está LIGADA (filtros reais). {@link #requisicao()} já vai autenticada, com um token de acesso de
 * verdade de um usuário ADMIN de teste (gravado no banco uma vez); {@link #requisicaoSemToken()} e
 * {@link #requisicaoCom(String)} servem aos testes de 401 e 403. O segredo do JWT é aleatório a cada execução (ver
 * o SegredoJwtAleatorioInitializer).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("testcontainers")
public abstract class AbstractIntegrationTest {

	/** Mesmos parâmetros do banco local (README, "Banco local"): MySQL 8.4, utf8mb4 e utf8mb4_unicode_ci. */
	@SuppressWarnings("resource") // singleton: nunca fechado de propósito; o Ryuk o remove ao fim da JVM
	@ServiceConnection
	protected static final MySQLContainer MYSQL = new MySQLContainer(DockerImageName.parse("mysql:8.4"))
			.withDatabaseName("etec")
			.withCommand("--character-set-server=utf8mb4", "--collation-server=utf8mb4_unicode_ci");

	static {
		// Padrão "contêiner singleton": iniciado uma vez; o Ryuk do Testcontainers o remove ao fim da JVM.
		MYSQL.start();
	}

	@LocalServerPort
	protected int porta;

	@Autowired
	protected JwtTokenProvider provedorDeTokens;

	@Autowired
	protected UsuarioRepository usuariosDeTeste;

	@Autowired
	protected JdbcTemplate jdbc;

	/**
	 * Os contadores do limite de tentativas (D128) vivem no banco COMPARTILHADO, e todos os testes chegam do mesmo endereço de
	 * loopback. Os limites do application.yml de teste são altíssimos e a limpeza agendada fica desligada; mesmo assim cada teste
	 * começa sem nenhum contador, para nenhum depender do que outro deixou. Os testes DEDICADOS do limite usam contextos e
	 * schemas próprios.
	 */
	@org.junit.jupiter.api.BeforeEach
	void limparContadoresDoLimite() {
		jdbc.update("DELETE FROM limite_tentativa");
	}

	/** Um usuário de teste por papel (um só para a JVM; o banco é compartilhado), criado na primeira necessidade. */
	private static final java.util.Map<Papel, Usuario> USUARIOS_POR_PAPEL = new java.util.concurrent.ConcurrentHashMap<>();

	/**
	 * Requisição HTTP real para a aplicação, em JSON UTF-8 (o RestAssured usaria ISO-8859-1 por padrão), AUTENTICADA com
	 * um token de acesso do ADMIN de teste.
	 */
	protected RequestSpecification requisicao() {
		return requisicaoComo(Papel.ADMIN);
	}

	/** Requisição autenticada como o usuário de teste do papel dado (ADMIN, STUDENT ou GUARDIAN). */
	protected RequestSpecification requisicaoComo(Papel papel) {
		return requisicaoCom(tokenDeAcesso(usuarioDoPapel(papel)));
	}

	protected RequestSpecification requisicaoComoStudent() {
		return requisicaoComo(Papel.STUDENT);
	}

	protected RequestSpecification requisicaoComoGuardian() {
		return requisicaoComo(Papel.GUARDIAN);
	}

	/** O usuário de teste compartilhado do papel (criado uma vez). NÃO altere o papel dele: use {@link #criarUsuarioNoBanco(Papel)}. */
	protected Usuario usuarioDoPapel(Papel papel) {
		return USUARIOS_POR_PAPEL.computeIfAbsent(papel, this::criarUsuarioNoBanco);
	}

	/** Troca o papel direto no banco (JDBC), sem passar pela API: simula revogar ou conceder uma permissão. */
	protected void mudarPapelNoBanco(Usuario usuario, Papel novoPapel) {
		org.assertj.core.api.Assertions.assertThat(jdbc.update("UPDATE usuario SET papel = ? WHERE email = ?", novoPapel.name(), usuario.getEmail())).isEqualTo(1);
	}

	/** Como {@link #requisicao()}, mas sem o cabeçalho Authorization. */
	protected RequestSpecification requisicaoSemToken() {
		return RestAssured.given()
				.baseUri("http://localhost")
				.port(porta)
				.contentType(ContentType.JSON.withCharset(StandardCharsets.UTF_8))
				.accept(ContentType.JSON);
	}

	/** Como {@link #requisicao()}, mas com o token dado no cabeçalho {@code Authorization: Bearer}. */
	protected RequestSpecification requisicaoCom(String token) {
		return requisicaoSemToken().header("Authorization", "Bearer " + token);
	}

	/** Token de acesso de verdade (assinado com o segredo do contexto) para o usuário dado. */
	protected String tokenDeAcesso(Usuario usuario) {
		return provedorDeTokens.emitir(TipoToken.ACCESS, usuario.getPublicId(), usuario.getPapel().name()).valor();
	}

	/** Grava um usuário de teste no banco, com e-mail único e hash falso (o login real é provado em outro teste). */
	protected Usuario criarUsuarioNoBanco(Papel papel) {
		return usuariosDeTeste.saveAndFlush(new Usuario("Usuário de Teste", "teste." + sufixo() + "@exemplo.invalid",
				"hash-falso-nao-e-real", LocalDate.of(2000, 1, 1), "v1", papel));
	}

	/** Sufixo aleatório curto, para os dados de cada teste não colidirem com os de outros. */
	protected static String sufixo() {
		return UUID.randomUUID().toString().substring(0, 8);
	}

	/** Nome único a partir de uma base (ex.: {@code "Matemática 3f2a9c1b"}). */
	protected static String nomeUnico(String base) {
		return base + " " + sufixo();
	}

}
