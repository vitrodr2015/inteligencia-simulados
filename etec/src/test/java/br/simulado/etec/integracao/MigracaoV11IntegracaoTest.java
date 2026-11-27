package br.simulado.etec.integracao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * A migration V11 (CHECK entre o status e o publicada_em da versão, D179) contra MySQL REAL. Cada cenário usa um SCHEMA
 * próprio dentro do contêiner compartilhado, levado pelo Flyway até a versão pedida, porque o ponto é migrar sobre dados que
 * já existiam; o schema é apagado no fim. Nenhum contexto nem contêiner novo.
 */
class MigracaoV11IntegracaoTest extends AbstractIntegrationTest {

	private static final String CHECK = "ck_questao_versao_publicada_em";

	private String schema;

	private JdbcTemplate banco;

	@BeforeEach
	void criarSchema() throws SQLException {
		schema = "etec_v11_" + sufixo();
		comoRoot("CREATE DATABASE " + schema + " CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
		banco = new JdbcTemplate(new DriverManagerDataSource(urlDoSchema(), "root", MYSQL.getPassword()));
	}

	@AfterEach
	void apagarSchema() throws SQLException {
		comoRoot("DROP DATABASE " + schema);
	}

	private static void comoRoot(String sql) throws SQLException {
		try (Connection root = DriverManager.getConnection(MYSQL.getJdbcUrl(), "root", MYSQL.getPassword());
				Statement comando = root.createStatement()) {
			comando.execute(sql);
		}
	}

	private String urlDoSchema() {
		return MYSQL.getJdbcUrl().replaceFirst("/etec(\\?|$)", "/" + schema + "$1");
	}

	private void migrarAte(String versao) {
		Flyway.configure().dataSource(urlDoSchema(), "root", MYSQL.getPassword()).locations("classpath:db/migration")
				.target(versao).load().migrate();
	}

	/** Uma prova com {@code quantas} questões, cada uma com uma versão em RASCUNHO sem data, como a API gravava até a V10. */
	private void gravarRascunhosComoAApiGravava(int quantas) {
		banco.update("INSERT INTO materia (public_id, programa_id, nome, ativo, criado_em, atualizado_em) SELECT "
				+ "UNHEX(REPLACE(UUID(), '-', '')), id, 'Matemática', TRUE, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3) FROM programa");
		banco.update("INSERT INTO subtopico (public_id, materia_id, nome, ativo, criado_em, atualizado_em) SELECT "
				+ "UNHEX(REPLACE(UUID(), '-', '')), id, 'Álgebra', TRUE, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3) FROM materia");
		banco.update("INSERT INTO prova (public_id, programa_id, titulo, duracao_minutos, quantidade_questoes, status, "
				+ "criado_em, atualizado_em) SELECT UNHEX(REPLACE(UUID(), '-', '')), id, 'Prova', 180, 50, 'RASCUNHO', "
				+ "UTC_TIMESTAMP(3), UTC_TIMESTAMP(3) FROM programa");
		for (int numero = 1; numero <= quantas; numero++) {
			banco.update("INSERT INTO questao (public_id, prova_id, numero, tipo, hash_conteudo, criado_em, atualizado_em) "
					+ "SELECT UNHEX(REPLACE(UUID(), '-', '')), id, ?, 'MULTIPLA_ESCOLHA', ?, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3) "
					+ "FROM prova", numero, "a".repeat(64));
			inserirVersao(numero, 1, "RASCUNHO", null);
		}
	}

	private int inserirVersao(int numeroDaQuestao, int versao, String status, String publicadaEm) {
		return banco.update("INSERT INTO questao_versao (questao_id, versao, status, subtopico_id, enunciado, publicada_em, "
				+ "criado_em, atualizado_em) SELECT q.id, ?, ?, s.id, 'Enunciado', ?, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3) "
				+ "FROM questao q CROSS JOIN subtopico s WHERE q.numero = ?", versao, status, publicadaEm, numeroDaQuestao);
	}

	private List<Map<String, Object>> versoes() {
		return banco.queryForList("SELECT * FROM questao_versao ORDER BY id");
	}

	private int checksComEsteNome() {
		return banco.queryForObject("SELECT COUNT(*) FROM information_schema.check_constraints "
				+ "WHERE constraint_schema = DATABASE() AND constraint_name = ?", Integer.class, CHECK);
	}

	@Test
	void deve_migrar_os_rascunhos_que_ja_existiam_sem_mudar_nenhuma_linha() {
		migrarAte("10");
		gravarRascunhosComoAApiGravava(3);
		List<Map<String, Object>> antes = versoes();
		assertThat(checksComEsteNome()).as("antes da V11 não há o CHECK").isZero();

		migrarAte("11");

		assertThat(checksComEsteNome()).isEqualTo(1);
		assertThat(versoes()).hasSize(3).usingRecursiveComparison().isEqualTo(antes);
	}

	@ParameterizedTest
	@CsvSource(nullValues = "-", value = { "RASCUNHO, 2026-11-28 10:00:00.000", "PUBLICADA, -", "ARQUIVADA, -" })
	void deve_recusar_no_banco_o_status_que_nao_combina_com_a_data(String status, String publicadaEm) {
		migrarAte("11");
		gravarRascunhosComoAApiGravava(1);

		assertThatThrownBy(() -> inserirVersao(1, 2, status, publicadaEm)).isInstanceOf(DataAccessException.class)
				.hasMessageContaining(CHECK);
		assertThat(versoes()).hasSize(1);
	}

	@Test
	void deve_aceitar_no_banco_as_combinacoes_coerentes_e_recusar_publicar_sem_data_por_update() {
		migrarAte("11");
		gravarRascunhosComoAApiGravava(2);

		assertThatCode(() -> {
			inserirVersao(1, 2, "PUBLICADA", "2026-11-28 10:00:00.000");
			inserirVersao(1, 3, "ARQUIVADA", "2026-11-27 10:00:00.000");
		}).doesNotThrowAnyException();
		assertThatThrownBy(() -> banco.update("UPDATE questao_versao v JOIN questao q ON q.id = v.questao_id "
				+ "SET v.status = 'PUBLICADA' WHERE q.numero = 2")).isInstanceOf(DataAccessException.class)
				.hasMessageContaining(CHECK);
	}

	/** O que avisar no primeiro deploy: o MySQL confere as linhas existentes, e a V11 não passa por cima de dado incoerente. */
	@Test
	void deve_falhar_a_migration_quando_ja_existe_versao_publicada_sem_data() {
		migrarAte("10");
		gravarRascunhosComoAApiGravava(1);
		inserirVersao(1, 2, "PUBLICADA", null);

		assertThatThrownBy(() -> migrarAte("11")).isInstanceOf(FlywayException.class).hasMessageContaining(CHECK);
		assertThat(checksComEsteNome()).isZero();
	}

}
