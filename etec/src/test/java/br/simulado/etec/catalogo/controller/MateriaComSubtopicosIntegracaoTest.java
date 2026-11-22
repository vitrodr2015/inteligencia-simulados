package br.simulado.etec.catalogo.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.ByteBuffer;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * DELETE de matéria com e sem subtópicos (contrato 4.1), contra o MySQL real. A garantia é a chave estrangeira com
 * ON DELETE RESTRICT da V3; a aplicação só traduz essa violação em 409 MATERIA_COM_SUBTOPICOS. Também prova a
 * estrutura da tabela criada pela migration (FK, índices, collation) direto no catálogo do MySQL.
 */
class MateriaComSubtopicosIntegracaoTest extends AbstractSubtopicoIntegrationTest {

	@Autowired
	private JdbcTemplate jdbc;

	// ----- DELETE da matéria, por HTTP -----

	@Test
	void deve_devolver_409_materia_com_subtopicos_e_manter_tudo_quando_apaga_materia_com_subtopico_ativo() {
		String materiaId = novaMateria();
		String subtopicoId = criarSubtopicoEObterId(materiaId, nomeUnico("Dependente"));

		verificarErro(apagar(materiaId), 409, "Conflict", "MATERIA_COM_SUBTOPICOS");

		buscar(materiaId).then().statusCode(200);
		buscarSubtopico(subtopicoId).then().statusCode(200);
	}

	@Test
	void deve_devolver_409_materia_com_subtopicos_tambem_quando_o_subtopico_esta_inativo() {
		String materiaId = novaMateria();
		String subtopicoId = criarSubtopicoEObterId(materiaId, nomeUnico("Dependente inativo"));
		desativarSubtopico(subtopicoId).then().statusCode(200);

		verificarErro(apagar(materiaId), 409, "Conflict", "MATERIA_COM_SUBTOPICOS");
	}

	@Test
	void deve_devolver_409_tambem_quando_a_materia_esta_desativada_e_tem_subtopico() {
		String materiaId = novaMateria();
		criarSubtopicoEObterId(materiaId, nomeUnico("Dependente de matéria inativa"));
		desativar(materiaId).then().statusCode(200);

		verificarErro(apagar(materiaId), 409, "Conflict", "MATERIA_COM_SUBTOPICOS");
	}

	@Test
	void deve_apagar_a_materia_quando_o_ultimo_subtopico_e_apagado() {
		String materiaId = novaMateria();
		String primeiro = criarSubtopicoEObterId(materiaId, nomeUnico("Primeiro"));
		String segundo = criarSubtopicoEObterId(materiaId, nomeUnico("Segundo"));

		apagarSubtopico(primeiro).then().statusCode(204);
		verificarErro(apagar(materiaId), 409, "Conflict", "MATERIA_COM_SUBTOPICOS");
		apagarSubtopico(segundo).then().statusCode(204);

		apagar(materiaId).then().statusCode(204);
		verificarErro(buscar(materiaId), 404, "Not Found", "MATERIA_NAO_ENCONTRADA");
	}

	@Test
	void deve_continuar_apagando_a_materia_sem_subtopicos_como_antes() {
		String materiaId = novaMateria();

		apagar(materiaId).then().statusCode(204);
		verificarErro(apagar(materiaId), 404, "Not Found", "MATERIA_NAO_ENCONTRADA");
	}

	@Test
	void deve_apagar_a_materia_que_so_tem_subtopicos_em_outra_materia() {
		String materiaSemFilhos = novaMateria();
		String materiaComFilhos = novaMateria();
		criarSubtopicoEObterId(materiaComFilhos, nomeUnico("De outra matéria"));

		apagar(materiaSemFilhos).then().statusCode(204);
		verificarErro(apagar(materiaComFilhos), 409, "Conflict", "MATERIA_COM_SUBTOPICOS");
	}

	// ----- a garantia é o banco (FK real) -----

	private static byte[] bytes(UUID id) {
		return ByteBuffer.allocate(16).putLong(id.getMostSignificantBits()).putLong(id.getLeastSignificantBits()).array();
	}

	@Test
	void deve_recusar_no_proprio_banco_o_delete_direto_de_uma_materia_com_subtopico() {
		String materiaId = novaMateria();
		criarSubtopicoEObterId(materiaId, nomeUnico("Protegido pela FK"));

		assertThatThrownBy(() -> jdbc.update("DELETE FROM materia WHERE public_id = ?", (Object) bytes(UUID.fromString(materiaId))))
				.isInstanceOf(DataIntegrityViolationException.class);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM materia WHERE public_id = ?", Long.class,
				(Object) bytes(UUID.fromString(materiaId)))).isEqualTo(1L);
	}

	@Test
	void deve_recusar_no_banco_um_subtopico_sem_materia_ou_com_materia_inexistente() {
		LocalDateTime agora = LocalDateTime.now();
		String sql = "INSERT INTO subtopico (public_id, materia_id, nome, ativo, criado_em, atualizado_em) VALUES (?, ?, ?, TRUE, ?, ?)";

		assertThatThrownBy(() -> jdbc.update(sql, (Object) bytes(UUID.randomUUID()), null, "Sem matéria", agora, agora))
				.isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> jdbc.update(sql, (Object) bytes(UUID.randomUUID()), 999_999_999L, "Matéria que não existe",
				agora, agora)).isInstanceOf(DataIntegrityViolationException.class);
	}

	// ----- estrutura criada pela V3 -----

	@Test
	void deve_ter_criado_a_fk_com_on_delete_restrict_e_a_collation_unicode_ci() {
		String regra = jdbc.queryForObject("SELECT delete_rule FROM information_schema.referential_constraints "
				+ "WHERE constraint_schema = DATABASE() AND constraint_name = 'fk_subtopico_materia'", String.class);
		String colacao = jdbc.queryForObject("SELECT table_collation FROM information_schema.tables "
				+ "WHERE table_schema = DATABASE() AND table_name = 'subtopico'", String.class);
		String colacaoDoNome = jdbc.queryForObject("SELECT collation_name FROM information_schema.columns "
				+ "WHERE table_schema = DATABASE() AND table_name = 'subtopico' AND column_name = 'nome'", String.class);

		assertThat(regra).isEqualTo("RESTRICT");
		assertThat(colacao).isEqualTo("utf8mb4_unicode_ci");
		assertThat(colacaoDoNome).isEqualTo("utf8mb4_unicode_ci");
	}

	@Test
	void deve_ter_materia_id_not_null_e_os_indices_da_listagem_e_da_fk() {
		String anulavel = jdbc.queryForObject("SELECT is_nullable FROM information_schema.columns "
				+ "WHERE table_schema = DATABASE() AND table_name = 'subtopico' AND column_name = 'materia_id'", String.class);
		List<String> unicoNaMateria = jdbc.queryForList("SELECT column_name FROM information_schema.statistics "
				+ "WHERE table_schema = DATABASE() AND table_name = 'subtopico' AND index_name = 'uk_subtopico_materia_nome' "
				+ "AND non_unique = 0 ORDER BY seq_in_index", String.class);
		List<String> indices = jdbc.queryForList("SELECT DISTINCT index_name FROM information_schema.statistics "
				+ "WHERE table_schema = DATABASE() AND table_name = 'subtopico'", String.class);

		assertThat(anulavel).isEqualTo("NO");
		assertThat(unicoNaMateria).containsExactly("materia_id", "nome");
		assertThat(indices).contains("PRIMARY", "uk_subtopico_public_id", "uk_subtopico_materia_nome", "idx_subtopico_nome",
				"idx_subtopico_criado_em");
	}

	@Test
	void deve_ter_as_colunas_de_auditoria_e_o_ativo_padrao_true_como_na_materia() {
		List<String> colunas = jdbc.queryForList("SELECT column_name FROM information_schema.columns "
				+ "WHERE table_schema = DATABASE() AND table_name = 'subtopico' ORDER BY ordinal_position", String.class);
		String tipoCriado = jdbc.queryForObject("SELECT column_type FROM information_schema.columns WHERE table_schema = DATABASE() "
				+ "AND table_name = 'subtopico' AND column_name = 'criado_em'", String.class);

		assertThat(colunas).containsExactly("id", "public_id", "materia_id", "nome", "ativo", "criado_em", "atualizado_em");
		assertThat(tipoCriado).isEqualTo("datetime(3)");
	}

}
