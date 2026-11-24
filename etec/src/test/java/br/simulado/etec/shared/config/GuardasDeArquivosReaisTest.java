package br.simulado.etec.shared.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.core.io.FileSystemResource;

import br.simulado.etec.shared.security.limite.ProxiesConfiaveis;

/**
 * Guardas que leem os arquivos REAIS do projeto (yml, pom, migrations): o que pode abrir um caminho sensível ou deixar um
 * login conhecido no banco quebra o build. Sem depender do diretório de trabalho ({@link RaizDoProjeto}). As guardas de
 * AWS ficam para o Módulo 17.
 */
class GuardasDeArquivosReaisTest {

	private static final Path APPLICATION_YML = RaizDoProjeto.resolver("src/main/resources/application.yml");

	private static final Path MIGRATIONS = RaizDoProjeto.resolver("src/main/resources/db/migration");

	private static final Pattern PLACEHOLDER_COM_PADRAO = Pattern.compile("^\\$\\{[^:}]+:(.*)}$");

	private static Properties yml() {
		YamlPropertiesFactoryBean fabrica = new YamlPropertiesFactoryBean();
		fabrica.setResources(new FileSystemResource(APPLICATION_YML));
		return fabrica.getObject();
	}

	/** O valor que a chave tem SEM nenhuma variável de ambiente: o texto literal ou o padrão do {@code ${VAR:padrão}}. */
	private static String valorSemVariaveis(String chave) {
		String bruto = yml().getProperty(chave);
		if (bruto == null) {
			return null;
		}
		Matcher padrao = PLACEHOLDER_COM_PADRAO.matcher(bruto.strip());
		if (padrao.matches()) {
			return padrao.group(1).strip();
		}
		return bruto.strip().startsWith("${") ? "<sem padrão: a variável é obrigatória>" : bruto.strip();
	}

	@Test
	void deve_achar_a_pasta_do_projeto_e_a_do_repositorio_sem_depender_do_diretorio_de_trabalho() {
		assertThat(RaizDoProjeto.raiz().resolve("pom.xml")).exists();
		assertThat(RaizDoProjeto.raiz().getFileName().toString()).isEqualTo("etec");
		assertThat(RaizDoProjeto.repositorio().resolve("README.md")).exists();
		assertThat(RaizDoProjeto.resolver("src/main/resources/application.yml")).exists();
	}

	// ----- CORS -----

	@Test
	void deve_nao_ter_curinga_nem_origem_por_padrao_no_yml_real() {
		// O comportamento (a aplicação não sobe com *) está no CorsPropertiesTest; aqui, o arquivo real.
		assertThat(yml().getProperty("app.cors.allowed-origins")).isNotNull().doesNotContain("*");
		assertThat(valorSemVariaveis("app.cors.allowed-origins")).as("sem CORS por padrão").isEmpty();
	}

	// ----- limite de tentativas e proxy (Módulo 13A) -----

	private static Duration janelaDoYml(Properties p, String chave) {
		return DurationStyle.detectAndParse(p.getProperty(chave));
	}

	private static Properties ymlDe(Path arquivo) {
		YamlPropertiesFactoryBean fabrica = new YamlPropertiesFactoryBean();
		fabrica.setResources(new FileSystemResource(arquivo));
		return fabrica.getObject();
	}

	@Test
	void deve_manter_cada_limite_do_yml_real_numa_faixa_sa_e_a_janela_ate_1_hora() {
		Properties p = yml();
		String base = "app.rate-limit.";

		int emailOrigem = Integer.parseInt(p.getProperty(base + "signin-email-origem.limite"));
		int origem = Integer.parseInt(p.getProperty(base + "signin-origem.limite"));
		int geral = Integer.parseInt(p.getProperty(base + "auth-por-origem.limite"));
		assertThat(emailOrigem).as("signin e-mail+origem").isBetween(3, 10);
		assertThat(origem).as("signin origem").isBetween(20, 200);
		assertThat(geral).as("/auth/* por origem").isBetween(30, 600);

		assertThat(janelaDoYml(p, base + "signin-email-origem.janela")).isBetween(Duration.ofMinutes(5), Duration.ofHours(1));
		assertThat(janelaDoYml(p, base + "signin-origem.janela")).isBetween(Duration.ofMinutes(5), Duration.ofHours(1));
		assertThat(janelaDoYml(p, base + "auth-por-origem.janela")).isBetween(Duration.ofSeconds(30), Duration.ofMinutes(5));
		assertThat(origem).as("a origem é mais folgada que e-mail+origem").isGreaterThan(emailOrigem);
		assertThat(Integer.parseInt(p.getProperty(base + "troca-senha-usuario.limite"))).as("troca de senha por usuário")
				.isBetween(3, 10);
		assertThat(janelaDoYml(p, base + "troca-senha-usuario.janela")).isBetween(Duration.ofMinutes(5), Duration.ofHours(1));
	}

	@Test
	void deve_ter_a_limpeza_ligada_no_yml_real_e_desligada_no_de_teste_e_sem_os_placeholders_antigos() {
		assertThat(yml().getProperty("app.rate-limit.limpeza-ativa")).isEqualTo("true");
		assertThat(ymlDe(RaizDoProjeto.resolver("src/test/resources/application.yml")).getProperty("app.rate-limit.limpeza-ativa"))
				.isEqualTo("false");
		assertThat(yml().stringPropertyNames()).noneMatch(c -> c.equals("app.rate-limit.auth-por-ip-por-minuto"))
				.noneMatch(c -> c.equals("app.rate-limit.signin-por-email-por-minuto"));
	}

	@Test
	void deve_ter_a_politica_de_entitlements_stub_no_yml_real_e_no_de_teste() {
		assertThat(yml().getProperty("app.entitlements.politica")).as("yml real").isEqualTo("stub");
		assertThat(ymlDe(RaizDoProjeto.resolver("src/test/resources/application.yml")).getProperty("app.entitlements.politica"))
				.as("yml de teste").isEqualTo("stub");
	}

	@Test
	void deve_ter_a_limpeza_das_sessoes_ligada_no_yml_real_e_desligada_no_de_teste() {
		assertThat(yml().getProperty("app.sessoes.limpeza-ativa")).isEqualTo("true");
		assertThat(ymlDe(RaizDoProjeto.resolver("src/test/resources/application.yml")).getProperty("app.sessoes.limpeza-ativa"))
				.isEqualTo("false");
	}

	@Test
	void deve_nunca_ter_proxy_confiavel_permissivo_nem_ligado_sem_lista_nos_yml_reais() throws IOException {
		try (Stream<Path> arquivos = Files.list(RaizDoProjeto.resolver("src/main/resources"))) {
			for (Path arquivo : arquivos.filter(a -> a.getFileName().toString().matches("application.*\\.yml")).toList()) {
				Properties p = ymlDe(arquivo);
				String cabecalho = p.getProperty(ProxiesConfiaveis.CABECALHO);
				String lista = p.getProperty(ProxiesConfiaveis.LISTA);
				assertThat(cabecalho).as("o proxy confiável nasce DESLIGADO em %s (só por variável de ambiente)", arquivo.getFileName())
						.isNull();
				if (lista != null) {
					assertThat(ProxiesConfiaveis.permissiva(lista)).as("internal-proxies em %s", arquivo.getFileName()).isFalse();
				}
			}
		}
	}

	// ----- detalhes de erro -----

	@Test
	void deve_nunca_incluir_detalhe_de_erro_no_corpo_no_yml_real_e_no_de_teste() throws IOException {
		for (Path arquivo : new Path[] { APPLICATION_YML, RaizDoProjeto.resolver("src/test/resources/application.yml") }) {
			YamlPropertiesFactoryBean fabrica = new YamlPropertiesFactoryBean();
			fabrica.setResources(new FileSystemResource(arquivo));
			Properties p = fabrica.getObject();
			for (String chave : new String[] { "message", "stacktrace", "exception", "binding-errors" }) {
				assertThat(p.getProperty("server.error.include-" + chave)).as("%s em %s", chave, arquivo.getFileName())
						.isEqualTo("never");
			}
		}
	}

	// ----- Swagger e actuator -----

	@Test
	void deve_ter_o_swagger_desligado_por_padrao_no_yml_real() {
		assertThat(valorSemVariaveis("springdoc.api-docs.enabled")).isEqualTo("false");
		assertThat(valorSemVariaveis("springdoc.swagger-ui.enabled")).isEqualTo("false");
	}

	@Test
	void deve_expor_so_o_health_do_actuator_no_yml_real() {
		assertThat(yml().getProperty("management.endpoints.web.exposure.include")).isEqualTo("health");
		assertThat(yml().stringPropertyNames()).noneMatch(c -> c.startsWith("management.endpoints.web.exposure.exclude"))
				.noneMatch(c -> c.equals("management.endpoint.shutdown.enabled") || c.equals("management.endpoint.shutdown.access"))
				.noneMatch(c -> c.startsWith("management.endpoint.env") || c.startsWith("management.endpoint.heapdump"));
		assertThat(yml().getProperty("management.endpoint.health.show-details")).isEqualTo("never");
	}

	// ----- H2 e devtools -----

	@Test
	void deve_manter_o_h2_so_em_teste_e_o_devtools_opcional_no_pom_real() throws IOException {
		String pom = Files.readString(RaizDoProjeto.resolver("pom.xml"));

		assertThat(blocoDaDependencia(pom, "h2")).contains("<scope>test</scope>");
		assertThat(blocoDaDependencia(pom, "spring-boot-devtools")).contains("<optional>true</optional>")
				.doesNotContain("<scope>compile</scope>");
	}

	@Test
	void deve_nao_ligar_o_console_do_h2_em_nenhum_yml_real() throws IOException {
		try (Stream<Path> arquivos = Files.list(RaizDoProjeto.resolver("src/main/resources"))) {
			for (Path arquivo : arquivos.filter(a -> a.getFileName().toString().matches("application.*\\.ya?ml|application.*\\.properties")).toList()) {
				assertThat(Files.readString(arquivo)).as(arquivo.getFileName().toString()).doesNotContainIgnoringCase("h2.console")
						.doesNotContainIgnoringCase("h2-console");
			}
		}
	}

	/** O texto do {@code <dependency>} da artifactId dada. */
	static String blocoDaDependencia(String pom, String artifactId) {
		Matcher m = Pattern.compile("<dependency>(?:(?!</dependency>).)*?<artifactId>" + Pattern.quote(artifactId)
				+ "</artifactId>.*?</dependency>", Pattern.DOTALL).matcher(pom);
		assertThat(m.find()).as("dependência %s no pom", artifactId).isTrue();
		return m.group();
	}

	// ----- nenhum usuário semeado -----

	@Test
	void deve_nao_ter_migration_que_insere_ou_altera_usuario_nem_cria_conta_de_banco() throws IOException {
		List<String> culpados = new ArrayList<>();
		try (Stream<Path> arquivos = Files.list(MIGRATIONS)) {
			List<Path> sqls = arquivos.filter(a -> a.getFileName().toString().endsWith(".sql")).sorted().toList();
			assertThat(sqls).as("as migrations reais precisam estar na varredura").isNotEmpty();
			for (Path arquivo : sqls) {
				culpados.addAll(escritasEmUsuario(Files.readString(arquivo)).stream()
						.map(v -> arquivo.getFileName() + ": " + v).toList());
			}
		}

		assertThat(culpados).as("nenhuma migration semeia usuário (o admin nasce só pelo bootstrap, D45)").isEmpty();
	}

	@Test
	void deve_nao_ter_data_sql_nem_import_sql_no_codigo_de_producao() {
		for (String nome : new String[] { "data.sql", "import.sql", "data-mysql.sql", "schema.sql" }) {
			assertThat(RaizDoProjeto.resolver("src/main/resources/" + nome)).as(nome).doesNotExist();
		}
	}

	@Test
	void deve_pegar_escrita_em_usuario_nas_formas_que_uma_migration_poderia_usar() {
		assertThat(escritasEmUsuario("INSERT INTO usuario (nome) VALUES ('x');")).isNotEmpty();
		assertThat(escritasEmUsuario("insert into `usuario` (nome)\nvalues ('x');")).isNotEmpty();
		assertThat(escritasEmUsuario("INSERT IGNORE INTO etec.usuario VALUES (1);")).isNotEmpty();
		assertThat(escritasEmUsuario("REPLACE INTO usuario VALUES (1);")).isNotEmpty();
		assertThat(escritasEmUsuario("UPDATE usuario SET papel = 'ADMIN';")).isNotEmpty();
		assertThat(escritasEmUsuario("DELETE FROM usuario;")).isNotEmpty();
		assertThat(escritasEmUsuario("CREATE USER 'a'@'%' IDENTIFIED BY 'b';")).isNotEmpty();
		assertThat(escritasEmUsuario("INSERT INTO materia (nome) VALUES ('Matemática');")).isEmpty();
		assertThat(escritasEmUsuario("-- INSERT INTO usuario ... (só comentário)\nCREATE TABLE usuario (id BIGINT);")).isEmpty();
		assertThat(escritasEmUsuario("CREATE TABLE usuario (id BIGINT);")).isEmpty();
	}

	/** Comandos que escrevem em {@code usuario} (ou criam conta no MySQL), sem contar comentários. */
	static List<String> escritasEmUsuario(String sql) {
		String limpo = sql.replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("(?m)--.*$", " ").replaceAll("(?m)#.*$", " ");
		List<String> achados = new ArrayList<>();
		String[] padroes = {
				"(?is)\\b(insert|replace)\\b[^;]*?\\binto\\b\\s+(?:[`\\w]+\\.)?`?usuario`?\\b",
				"(?is)\\bupdate\\b\\s+(?:ignore\\s+)?(?:[`\\w]+\\.)?`?usuario`?\\b",
				"(?is)\\bdelete\\b\\s+from\\s+(?:[`\\w]+\\.)?`?usuario`?\\b",
				"(?is)\\btruncate\\b\\s+(?:table\\s+)?(?:[`\\w]+\\.)?`?usuario`?\\b",
				"(?is)\\bcreate\\s+user\\b", "(?is)\\bgrant\\b" };
		for (String padrao : padroes) {
			if (Pattern.compile(padrao).matcher(limpo).find()) {
				achados.add("comando proibido: " + padrao.substring(0, Math.min(40, padrao.length())));
			}
		}
		return achados;
	}

}
