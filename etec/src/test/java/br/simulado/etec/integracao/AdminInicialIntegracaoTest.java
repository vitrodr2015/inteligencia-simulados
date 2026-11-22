package br.simulado.etec.integracao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import br.simulado.etec.EtecApplication;
import br.simulado.etec.shared.config.Perfis;

/**
 * O bootstrap do administrador contra MySQL REAL e a aplicação inteira (Flyway + contexto + inicializador), cada cenário
 * num SCHEMA próprio do mesmo contêiner (o banco compartilhado dos outros testes não serve: aqui o ponto é o banco
 * NOVO, sem usuário). As senhas são aleatórias, geradas em memória a cada execução.
 *
 * <p>Cenários agrupados para subir poucos contextos.
 */
@ExtendWith(OutputCaptureExtension.class)
class AdminInicialIntegracaoTest {

	@SuppressWarnings("resource") // singleton: o Ryuk remove ao fim da JVM
	private static final MySQLContainer MYSQL = new MySQLContainer(DockerImageName.parse("mysql:8.4"))
			.withDatabaseName("etec")
			.withCommand("--character-set-server=utf8mb4", "--collation-server=utf8mb4_unicode_ci");

	static {
		MYSQL.start();
	}

	private static final SecureRandom ALEATORIO = new SecureRandom();

	private static String senhaAleatoria() {
		byte[] bytes = new byte[24];
		ALEATORIO.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	/** Cria um schema novo e vazio e devolve o nome. */
	private static String novoSchema() throws SQLException {
		String nome = "etec_admin_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
		try (Connection c = DriverManager.getConnection(MYSQL.getJdbcUrl(), "root", MYSQL.getPassword());
				Statement s = c.createStatement()) {
			s.execute("CREATE DATABASE " + nome + " CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
		}
		return nome;
	}

	private static JdbcTemplate jdbc(String schema) {
		return new JdbcTemplate(new DriverManagerDataSource(urlDo(schema), "root", MYSQL.getPassword()));
	}

	private static String urlDo(String schema) {
		return MYSQL.getJdbcUrl().replaceFirst("/etec(\\?|$)", "/" + schema + "$1");
	}

	/** Sobe a aplicação inteira (sem servidor web) no schema dado; {@code producao} liga o perfil de produção. */
	private static ConfigurableApplicationContext subir(String schema, boolean producao, String email, String senha) {
		SpringApplicationBuilder b = new SpringApplicationBuilder(EtecApplication.class).web(WebApplicationType.NONE)
				.profiles(producao ? new String[] { "testcontainers", Perfis.PRODUCAO } : new String[] { "testcontainers" });
		// Argumentos em memória (prioridade maior que o perfil testcontainers); nunca uma linha de comando de processo.
		return b.run("--spring.datasource.url=" + urlDo(schema), "--spring.datasource.username=root",
				"--spring.datasource.password=" + MYSQL.getPassword(), "--app.termos.url=https://exemplo.invalid/termos",
				"--ADMIN_EMAIL=" + (email == null ? "" : email), "--ADMIN_SENHA=" + (senha == null ? "" : senha));
	}

	private static List<Map<String, Object>> usuarios(String schema) {
		return jdbc(schema).queryForList("SELECT email, papel, senha_hash, excluido_em FROM usuario");
	}

	@Test
	void deve_nao_ter_nenhum_usuario_no_banco_novo_e_em_producao_nao_subir_sem_admin_nem_variaveis() throws SQLException {
		String schema = novoSchema();

		assertThatThrownBy(() -> subir(schema, true, null, null)).hasStackTraceContaining("Nenhum administrador existe");

		// o Flyway rodou (a tabela existe) e nada foi semeado
		assertThat(usuarios(schema)).as("banco novo, sem usuário").isEmpty();
	}

	@Test
	void deve_criar_o_admin_so_uma_vez_e_ignorar_variaveis_em_subidas_seguintes(CapturedOutput saida) throws SQLException {
		String schema = novoSchema();
		String email = "admin." + UUID.randomUUID().toString().substring(0, 8) + "@exemplo.invalid";
		String senha1 = senhaAleatoria();
		String senha2 = senhaAleatoria();

		// 1ª subida (produção, com variáveis): cria
		try (var ctx = subir(schema, true, email, senha1)) {
			assertThat(ctx.isRunning()).isTrue();
		}
		List<Map<String, Object>> aposPrimeira = usuarios(schema);
		assertThat(aposPrimeira).hasSize(1);
		assertThat(aposPrimeira.get(0)).containsEntry("papel", "ADMIN").containsEntry("email", email);
		String hash = (String) aposPrimeira.get(0).get("senha_hash");
		assertThat(hash).startsWith("{bcrypt}").doesNotContain(senha1);

		// 2ª subida com OUTRA senha: não recria e não troca a senha
		try (var ctx = subir(schema, true, email, senha2)) {
			assertThat(ctx.isRunning()).isTrue();
		}
		assertThat(usuarios(schema)).hasSize(1);
		assertThat(usuarios(schema).get(0).get("senha_hash")).isEqualTo(hash);

		// 3ª subida sem variáveis (produção): sobe, o admin segue lá
		try (var ctx = subir(schema, true, null, null)) {
			assertThat(ctx.isRunning()).isTrue();
		}
		assertThat(usuarios(schema)).hasSize(1);
		assertThat(usuarios(schema).get(0).get("senha_hash")).isEqualTo(hash);

		assertThat(saida.getAll()).doesNotContain(senha1).doesNotContain(senha2).doesNotContain(hash).doesNotContain(email);
	}

	@Test
	void deve_so_avisar_em_desenvolvimento_sem_variaveis_e_nao_criar_ninguem(CapturedOutput saida) throws SQLException {
		String schema = novoSchema();

		try (var ctx = subir(schema, false, null, null)) {
			assertThat(ctx.isRunning()).isTrue();
		}

		assertThat(usuarios(schema)).isEmpty();
		assertThat(saida.getAll()).contains("Nenhum administrador existe");
	}

	@Test
	void deve_nunca_promover_usuario_comum_existente_com_o_email_do_admin(CapturedOutput saida) throws SQLException {
		String schema = novoSchema();
		String email = "aluno." + UUID.randomUUID().toString().substring(0, 8) + "@exemplo.invalid";
		String senha = senhaAleatoria();
		// banco migrado e com um aluno comum (a primeira subida em desenvolvimento só migra)
		try (var ctx = subir(schema, false, null, null)) {
			assertThat(ctx.isRunning()).isTrue();
		}
		jdbc(schema).update("INSERT INTO usuario (public_id, nome, email, senha_hash, data_nascimento, aceite_termos_versao, "
				+ "papel, criado_em, atualizado_em) VALUES (UUID_TO_BIN(?), 'Aluno', ?, 'hash-falso', '2008-05-10', 'v1', 'STUDENT', NOW(6), NOW(6))",
				UUID.randomUUID().toString(), email);

		assertThatThrownBy(() -> subir(schema, true, email, senha)).hasStackTraceContaining("nunca promove");

		assertThat(usuarios(schema)).hasSize(1);
		assertThat(usuarios(schema).get(0)).containsEntry("papel", "STUDENT");
		assertThat(saida.getAll()).doesNotContain(senha);
	}

	@Test
	void deve_criar_novo_admin_quando_o_unico_admin_foi_excluido() throws SQLException {
		String schema = novoSchema();
		String email = "admin." + UUID.randomUUID().toString().substring(0, 8) + "@exemplo.invalid";
		try (var ctx = subir(schema, true, email, senhaAleatoria())) {
			assertThat(ctx.isRunning()).isTrue();
		}
		// recuperação documentada: remover o admin por SQL e subir de novo com as variáveis
		assertThat(jdbc(schema).update("DELETE FROM usuario WHERE papel = 'ADMIN'")).isEqualTo(1);

		try (var ctx = subir(schema, true, email, senhaAleatoria())) {
			assertThat(ctx.isRunning()).isTrue();
		}

		assertThat(usuarios(schema)).hasSize(1).first().satisfies(u -> assertThat(u).containsEntry("papel", "ADMIN"));
	}

}
