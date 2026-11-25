package br.simulado.etec.integracao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import br.simulado.etec.shared.programa.Programa;

/**
 * A migration V7 (programa e matéria por programa, D143 e D144) contra MySQL REAL, cada cenário num SCHEMA próprio (o banco
 * compartilhado dos outros testes não serve: aqui o ponto é migrar sobre dados que já existiam e inserir um segundo programa
 * sem sujar os demais).
 */
class ProgramaMigracaoIntegracaoTest {

	@SuppressWarnings("resource") // singleton: o Ryuk remove ao fim da JVM
	private static final MySQLContainer MYSQL = new MySQLContainer(DockerImageName.parse("mysql:8.4"))
			.withDatabaseName("etec")
			.withCommand("--character-set-server=utf8mb4", "--collation-server=utf8mb4_unicode_ci");

	static {
		MYSQL.start();
	}

	private static String novoSchema() throws SQLException {
		String nome = "etec_prog_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
		try (Connection c = DriverManager.getConnection(MYSQL.getJdbcUrl(), "root", MYSQL.getPassword());
				Statement s = c.createStatement()) {
			s.execute("CREATE DATABASE " + nome + " CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
		}
		return nome;
	}

	private static String urlDo(String schema) {
		return MYSQL.getJdbcUrl().replaceFirst("/etec(\\?|$)", "/" + schema + "$1");
	}

	private static JdbcTemplate jdbc(String schema) {
		return new JdbcTemplate(new DriverManagerDataSource(urlDo(schema), "root", MYSQL.getPassword()));
	}

	/** Leva o Flyway ATÉ a versão dada (inclusive). */
	private static void migrarAte(String schema, String versao) {
		Flyway.configure().dataSource(urlDo(schema), "root", MYSQL.getPassword()).locations("classpath:db/migration")
				.target(versao).load().migrate();
	}

	private static void inserirMateria(JdbcTemplate jdbc, String nome) {
		jdbc.update("INSERT INTO materia (public_id, nome, descricao, ativo, criado_em, atualizado_em) "
				+ "VALUES (UNHEX(REPLACE(UUID(), '-', '')), ?, NULL, TRUE, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))", nome);
	}

	// ----- (c) a migration sobre dados que já existiam -----

	@Test
	void deve_preencher_programa_id_com_etec_nas_materias_que_ja_existiam_ao_migrar_da_v6_para_a_v7() throws SQLException {
		String schema = novoSchema();
		JdbcTemplate jdbc = jdbc(schema);
		migrarAte(schema, "6");
		inserirMateria(jdbc, "Matemática");
		inserirMateria(jdbc, "Português");
		jdbc.update("INSERT INTO subtopico (public_id, materia_id, nome, ativo, criado_em, atualizado_em) "
				+ "SELECT UNHEX(REPLACE(UUID(), '-', '')), id, 'Álgebra', TRUE, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3) "
				+ "FROM materia WHERE nome = 'Matemática'");
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() "
				+ "AND table_name = 'programa'", Integer.class)).as("antes da V7 não existe programa").isZero();

		migrarAte(schema, "7");

		Long idEtec = jdbc.queryForObject("SELECT id FROM programa WHERE codigo = 'ETEC'", Long.class);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM materia", Integer.class)).isEqualTo(2);
		assertThat(jdbc.queryForList("SELECT DISTINCT programa_id FROM materia", Long.class)).containsExactly(idEtec);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM subtopico", Integer.class)).as("o subtópico segue intacto")
				.isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() "
				+ "AND table_name = 'subtopico' AND column_name = 'programa_id'", Integer.class))
				.as("o subtópico NÃO ganha coluna de programa").isZero();
	}

	@Test
	void deve_deixar_programa_id_not_null_com_chave_estrangeira_restrict_e_o_indice_unico_novo() throws SQLException {
		String schema = novoSchema();
		JdbcTemplate jdbc = jdbc(schema);
		migrarAte(schema, "7");

		String anulavel = jdbc.queryForObject("SELECT is_nullable FROM information_schema.columns "
				+ "WHERE table_schema = DATABASE() AND table_name = 'materia' AND column_name = 'programa_id'", String.class);
		List<String> unicoNovo = jdbc.queryForList("SELECT column_name FROM information_schema.statistics "
				+ "WHERE table_schema = DATABASE() AND table_name = 'materia' AND index_name = 'uk_materia_programa_nome' "
				+ "AND non_unique = 0 ORDER BY seq_in_index", String.class);
		Integer antigo = jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() "
				+ "AND table_name = 'materia' AND index_name = 'uk_materia_nome'", Integer.class);
		String regra = jdbc.queryForObject("SELECT delete_rule FROM information_schema.referential_constraints "
				+ "WHERE constraint_schema = DATABASE() AND constraint_name = 'fk_materia_programa'", String.class);

		assertThat(anulavel).isEqualTo("NO");
		assertThat(unicoNovo).containsExactly("programa_id", "nome");
		assertThat(antigo).as("o índice único global do nome saiu").isZero();
		assertThat(regra).isEqualTo("RESTRICT");
		assertThatThrownBy(() -> jdbc.update("INSERT INTO materia (public_id, programa_id, nome, ativo, criado_em, atualizado_em) "
				+ "VALUES (UNHEX(REPLACE(UUID(), '-', '')), NULL, 'Sem programa', TRUE, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))"))
				.isInstanceOf(DataAccessException.class);
	}

	// ----- (a) a linha da tabela casa com o código Java -----

	@Test
	void deve_ter_so_a_linha_etec_na_tabela_igual_ao_programa_etec_do_codigo() throws SQLException {
		String schema = novoSchema();
		JdbcTemplate jdbc = jdbc(schema);
		migrarAte(schema, "7");

		List<String> codigos = jdbc.queryForList("SELECT codigo FROM programa", String.class);

		assertThat(codigos).containsExactly("ETEC");
		assertThat(codigos.get(0)).isEqualTo(Programa.ETEC.codigo());
	}

	// ----- (b) o nome da matéria é único POR programa -----

	@Test
	void deve_aceitar_o_mesmo_nome_de_materia_em_dois_programas_e_recusar_no_mesmo_programa() throws SQLException {
		String schema = novoSchema();
		JdbcTemplate jdbc = jdbc(schema);
		migrarAte(schema, "7");
		jdbc.update("INSERT INTO programa (public_id, codigo, nome, criado_em, atualizado_em) "
				+ "VALUES (UNHEX(REPLACE(UUID(), '-', '')), 'ENEM', 'ENEM', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))");
		Long etec = jdbc.queryForObject("SELECT id FROM programa WHERE codigo = 'ETEC'", Long.class);
		Long enem = jdbc.queryForObject("SELECT id FROM programa WHERE codigo = 'ENEM'", Long.class);
		String sql = "INSERT INTO materia (public_id, programa_id, nome, ativo, criado_em, atualizado_em) "
				+ "VALUES (UNHEX(REPLACE(UUID(), '-', '')), ?, ?, TRUE, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))";

		jdbc.update(sql, etec, "Matemática");
		jdbc.update(sql, enem, "Matemática");

		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM materia WHERE nome = 'Matemática'", Integer.class)).isEqualTo(2);
		assertThatThrownBy(() -> jdbc.update(sql, etec, "Matemática")).isInstanceOf(DataAccessException.class)
				.hasMessageContaining("uk_materia_programa_nome");
		assertThatThrownBy(() -> jdbc.update(sql, enem, "MATEMATICA")).as("sem distinguir caixa nem acento")
				.isInstanceOf(DataAccessException.class).hasMessageContaining("uk_materia_programa_nome");
	}

	// ----- (d) o CHECK do código tem o mesmo formato do record Programa -----

	@Test
	void deve_aceitar_no_check_so_o_que_o_programa_java_aceita() throws SQLException {
		String schema = novoSchema();
		JdbcTemplate jdbc = jdbc(schema);
		migrarAte(schema, "7");
		String sql = "INSERT INTO programa (public_id, codigo, nome, criado_em, atualizado_em) "
				+ "VALUES (UNHEX(REPLACE(UUID(), '-', '')), ?, 'teste', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))";
		List<String> aceitos = List.of("ENEM", "OAB", "ENEM_2027", "AB", "A1", "A1234567890123456789");
		// recusados com a tabela só com a ETEC: a unicidade (collation sem caixa) não pode ser o motivo da recusa
		List<String> recusados = List.of("enem", "Oab", "A", "1ABC", "_ABC", "ENEM-2", "ENEM 2", "ÉNEM", " ABC", "ABC ", "");

		for (String codigo : aceitos) {
			Programa.de(codigo); // o Java aceita
			assertThat(jdbc.update(sql, codigo)).as("o CHECK aceita %s", codigo).isEqualTo(1);
		}
		jdbc.update("DELETE FROM programa WHERE codigo <> 'ETEC'");
		for (String codigo : recusados) {
			assertThatThrownBy(() -> Programa.de(codigo)).as("o Java recusa '%s'", codigo)
					.isInstanceOf(IllegalArgumentException.class);
			assertThatThrownBy(() -> jdbc.update(sql, codigo)).as("o CHECK recusa '%s'", codigo)
					.isInstanceOf(DataAccessException.class).hasMessageContaining("ck_programa_codigo");
		}
		// 21 caracteres: o Java recusa, e o banco também (a coluna tem 20; o limite do formato é o mesmo)
		String longo = "A" + "B".repeat(20);
		assertThatThrownBy(() -> Programa.de(longo)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> jdbc.update(sql, longo)).isInstanceOf(DataAccessException.class);
	}

}
