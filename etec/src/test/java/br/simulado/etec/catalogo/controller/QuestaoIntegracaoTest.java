package br.simulado.etec.catalogo.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;

import br.simulado.etec.catalogo.service.HashDaQuestao;
import io.restassured.response.Response;

/**
 * A questão contra MySQL REAL, por HTTP real e por JDBC: as migrations V9 e V10 lidas do {@code information_schema}, os CHECKs e os
 * índices únicos (inclusive os das colunas geradas) provados por INSERT direto, o subtópico de outro programa, a FK do subtópico e o
 * fluxo ponta a ponta. O banco é compartilhado: cada teste cria dados próprios.
 */
class QuestaoIntegracaoTest extends AbstractQuestaoIntegrationTest {

	// ----- o schema (V9 e V10), lido do banco -----

	private Map<String, String> colunas(String tabela) {
		Map<String, String> colunas = new LinkedHashMap<>();
		jdbc.query("SELECT column_name, column_type, is_nullable, extra FROM information_schema.columns "
				+ "WHERE table_schema = DATABASE() AND table_name = ? ORDER BY ordinal_position", resultado -> {
					colunas.put(resultado.getString("column_name"), resultado.getString("column_type") + "/"
							+ resultado.getString("is_nullable") + (resultado.getString("extra").contains("GENERATED") ? "/GERADA" : ""));
				}, tabela);
		return colunas;
	}

	private List<String> indice(String tabela, String nome, boolean unico) {
		return jdbc.queryForList("SELECT column_name FROM information_schema.statistics WHERE table_schema = DATABASE() "
				+ "AND table_name = ? AND index_name = ? AND non_unique = ? ORDER BY seq_in_index", String.class, tabela, nome,
				unico ? 0 : 1);
	}

	private String regraDeApagar(String fk) {
		return jdbc.queryForObject("SELECT delete_rule FROM information_schema.referential_constraints "
				+ "WHERE constraint_schema = DATABASE() AND constraint_name = ?", String.class, fk);
	}

	private Map<String, String> checks(String tabela) {
		Map<String, String> checks = new LinkedHashMap<>();
		jdbc.query("SELECT cc.constraint_name AS nome, cc.check_clause AS clausula, tc.enforced AS ativo "
				+ "FROM information_schema.check_constraints cc JOIN information_schema.table_constraints tc "
				+ "ON tc.constraint_schema = cc.constraint_schema AND tc.constraint_name = cc.constraint_name "
				+ "WHERE cc.constraint_schema = DATABASE() AND tc.table_name = ? AND tc.constraint_type = 'CHECK' "
				+ "ORDER BY cc.constraint_name", resultado -> {
					assertThat(resultado.getString("ativo")).as(resultado.getString("nome")).isEqualTo("YES");
					checks.put(resultado.getString("nome"), resultado.getString("clausula"));
				}, tabela);
		return checks;
	}

	@Test
	void deve_ter_as_colunas_da_questao_com_tipo_e_obrigatoriedade_do_plano() {
		Map<String, String> esperado = new LinkedHashMap<>();
		esperado.put("id", "bigint/NO");
		esperado.put("public_id", "binary(16)/NO");
		esperado.put("prova_id", "bigint/NO");
		esperado.put("numero", "smallint/NO");
		esperado.put("tipo", "varchar(30)/NO");
		esperado.put("hash_conteudo", "char(64)/NO");
		esperado.put("criado_em", "datetime(3)/NO");
		esperado.put("atualizado_em", "datetime(3)/NO");

		assertThat(colunas("questao")).containsExactlyEntriesOf(esperado);
		assertThat(colunas("questao")).as("sem programa_id (D143)").doesNotContainKey("programa_id");
	}

	@Test
	void deve_ter_os_indices_e_a_chave_estrangeira_da_questao() {
		assertThat(indice("questao", "uk_questao_prova_numero", true)).containsExactly("prova_id", "numero");
		assertThat(indice("questao", "uk_questao_public_id", true)).containsExactly("public_id");
		assertThat(indice("questao", "idx_questao_prova_hash", false)).as("NÃO único").containsExactly("prova_id", "hash_conteudo");
		assertThat(regraDeApagar("fk_questao_prova")).isEqualTo("RESTRICT");
		assertThat(checks("questao")).containsOnlyKeys("ck_questao_numero", "ck_questao_tipo");
	}

	@Test
	void deve_ter_as_colunas_da_versao_com_texto_em_text_e_as_duas_colunas_geradas() {
		Map<String, String> esperado = new LinkedHashMap<>();
		esperado.put("id", "bigint/NO");
		esperado.put("questao_id", "bigint/NO");
		esperado.put("versao", "int/NO");
		esperado.put("status", "varchar(20)/NO");
		esperado.put("subtopico_id", "bigint/NO");
		esperado.put("texto_apoio", "text/YES");
		esperado.put("grupo_apoio", "int/YES");
		esperado.put("enunciado", "text/NO");
		esperado.put("imagem_url", "varchar(500)/YES");
		esperado.put("gabarito", "char(1)/YES");
		esperado.put("dificuldade", "varchar(10)/YES");
		esperado.put("explicacao", "text/YES");
		esperado.put("publicada_em", "datetime(3)/YES");
		esperado.put("criado_em", "datetime(3)/NO");
		esperado.put("atualizado_em", "datetime(3)/NO");
		esperado.put("rascunho_aberto", "tinyint/YES/GERADA");
		esperado.put("publicada_vigente", "tinyint/YES/GERADA");

		assertThat(colunas("questao_versao")).containsExactlyEntriesOf(esperado);
	}

	@Test
	void deve_ter_os_indices_unicos_e_as_chaves_da_versao() {
		assertThat(indice("questao_versao", "uk_questao_versao_questao_versao", true)).containsExactly("questao_id", "versao");
		assertThat(indice("questao_versao", "uk_questao_versao_rascunho_aberto", true)).containsExactly("questao_id", "rascunho_aberto");
		assertThat(indice("questao_versao", "uk_questao_versao_publicada_vigente", true)).containsExactly("questao_id", "publicada_vigente");
		assertThat(indice("questao_versao", "idx_questao_versao_subtopico", false)).containsExactly("subtopico_id");
		assertThat(regraDeApagar("fk_questao_versao_questao")).isEqualTo("RESTRICT");
		assertThat(regraDeApagar("fk_questao_versao_subtopico")).isEqualTo("RESTRICT");
	}

	@Test
	void deve_ter_os_checks_da_versao_ativos_com_os_limites_do_plano() {
		Map<String, String> checks = checks("questao_versao");

		assertThat(checks).containsOnlyKeys("ck_questao_versao_versao", "ck_questao_versao_status", "ck_questao_versao_texto_apoio",
				"ck_questao_versao_enunciado", "ck_questao_versao_explicacao", "ck_questao_versao_grupo_apoio",
				"ck_questao_versao_gabarito", "ck_questao_versao_dificuldade", "ck_questao_versao_publicada_em");
		assertThat(checks.get("ck_questao_versao_publicada_em")).contains("utf8mb4_bin").contains("publicada_em")
				.contains("RASCUNHO");
		assertThat(checks.get("ck_questao_versao_enunciado")).contains("10000");
		assertThat(checks.get("ck_questao_versao_status")).contains("RASCUNHO").contains("PUBLICADA").contains("ARQUIVADA");
		assertThat(checks.get("ck_questao_versao_gabarito")).contains("utf8mb4_bin").containsPattern("A.*B.*C.*D.*E");
	}

	@Test
	void deve_ter_a_alternativa_com_letra_unica_por_versao_e_texto_de_ate_2000() {
		Map<String, String> esperado = new LinkedHashMap<>();
		esperado.put("id", "bigint/NO");
		esperado.put("questao_versao_id", "bigint/NO");
		esperado.put("letra", "char(1)/NO");
		esperado.put("texto", "varchar(2000)/NO");
		esperado.put("criado_em", "datetime(3)/NO");
		esperado.put("atualizado_em", "datetime(3)/NO");

		assertThat(colunas("alternativa")).containsExactlyEntriesOf(esperado);
		assertThat(indice("alternativa", "uk_alternativa_versao_letra", true)).containsExactly("questao_versao_id", "letra");
		assertThat(regraDeApagar("fk_alternativa_questao_versao")).isEqualTo("RESTRICT");
		assertThat(checks("alternativa")).containsOnlyKeys("ck_alternativa_letra");
	}

	// ----- os CHECKs recusam linha inválida por INSERT direto -----

	private long novaQuestaoNoBanco() {
		return inserirQuestao(idInternoDaProva(criarProva(10)), 1);
	}

	private long novoSubtopicoNoBanco() {
		return idInternoDoSubtopico(criarSubtopico());
	}

	@Test
	void deve_recusar_no_banco_numero_menor_que_1_e_tipo_desconhecido_ou_com_outra_caixa() {
		long prova = idInternoDaProva(criarProva(10));

		assertThatThrownBy(() -> inserirQuestao(prova, 0)).isInstanceOf(DataAccessException.class).hasMessageContaining("ck_questao_numero");
		assertThatThrownBy(() -> inserirQuestao(prova, -3)).isInstanceOf(DataAccessException.class).hasMessageContaining("ck_questao_numero");
		for (String tipo : new String[] { "DISCURSIVA", "OUTRO", "", "multipla_escolha", "Multipla_Escolha" }) {
			assertThatThrownBy(() -> inserirQuestao(prova, 1, tipo)).as("tipo '%s'", tipo).isInstanceOf(DataAccessException.class)
					.hasMessageContaining("ck_questao_tipo");
		}
		assertThat(inserirQuestao(prova, 1)).isPositive();
	}

	@Test
	void deve_recusar_no_banco_dois_numeros_iguais_na_mesma_prova_e_aceitar_em_provas_diferentes() {
		long prova = idInternoDaProva(criarProva(10));
		long outraProva = idInternoDaProva(criarProva(10));
		inserirQuestao(prova, 4);

		assertThatThrownBy(() -> inserirQuestao(prova, 4)).isInstanceOf(DataAccessException.class)
				.hasMessageContaining("uk_questao_prova_numero");
		assertThat(inserirQuestao(outraProva, 4)).isPositive();
	}

	@ParameterizedTest(name = "status {0} recusado")
	@CsvSource({ "OUTRO", "rascunho", "Publicada", "arquivada", "ABERTA" })
	void deve_recusar_no_banco_um_status_de_versao_desconhecido_ou_com_outra_caixa(String status) {
		long questao = novaQuestaoNoBanco();
		long subtopico = novoSubtopicoNoBanco();

		assertThatThrownBy(() -> inserirVersao(questao, 1, status, subtopico, "E")).isInstanceOf(DataAccessException.class)
				.hasMessageContaining("ck_questao_versao_status");
	}

	@ParameterizedTest(name = "gabarito {0} recusado")
	@CsvSource({ "F", "a", "AB", "1" })
	void deve_recusar_no_banco_um_gabarito_fora_de_a_a_e(String gabarito) {
		long questao = novaQuestaoNoBanco();
		long subtopico = novoSubtopicoNoBanco();

		assertThatThrownBy(() -> inserirVersaoCompleta(questao, 1, "RASCUNHO", subtopico, null, null, "E", gabarito, null, null))
				.isInstanceOf(DataAccessException.class);
	}

	@Test
	void deve_aceitar_no_banco_gabarito_de_a_a_e_e_nulo() {
		long questao = novaQuestaoNoBanco();
		long subtopico = novoSubtopicoNoBanco();
		int versao = 1;
		for (String gabarito : new String[] { "A", "B", "C", "D", "E", null }) {
			inserirVersaoCompleta(questao, versao++, "ARQUIVADA", subtopico, null, null, "E", gabarito, null, null);
		}

		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM questao_versao WHERE questao_id = ?", Integer.class, questao)).isEqualTo(6);
	}

	@ParameterizedTest(name = "dificuldade {0} recusada")
	@CsvSource({ "IMPOSSIVEL", "facil", "Media", "MEDIO" })
	void deve_recusar_no_banco_uma_dificuldade_fora_do_enum_ou_com_outra_caixa(String dificuldade) {
		long questao = novaQuestaoNoBanco();
		long subtopico = novoSubtopicoNoBanco();

		assertThatThrownBy(() -> inserirVersaoCompleta(questao, 1, "RASCUNHO", subtopico, null, null, "E", null, dificuldade, null))
				.isInstanceOf(DataAccessException.class).hasMessageContaining("ck_questao_versao_dificuldade");
	}

	@Test
	void deve_recusar_no_banco_grupo_de_apoio_menor_que_1_e_versao_menor_que_1() {
		long questao = novaQuestaoNoBanco();
		long subtopico = novoSubtopicoNoBanco();

		assertThatThrownBy(() -> inserirVersaoCompleta(questao, 1, "RASCUNHO", subtopico, null, 0, "E", null, null, null))
				.isInstanceOf(DataAccessException.class).hasMessageContaining("ck_questao_versao_grupo_apoio");
		assertThatThrownBy(() -> inserirVersao(questao, 0, "RASCUNHO", subtopico, "E")).isInstanceOf(DataAccessException.class)
				.hasMessageContaining("ck_questao_versao_versao");
	}

	@Test
	void deve_aceitar_10000_caracteres_e_recusar_10001_em_cada_texto_longo_contando_caracteres_e_nao_bytes() {
		long questao = novaQuestaoNoBanco();
		long subtopico = novoSubtopicoNoBanco();
		String limite = "ç".repeat(10000); // 2 bytes cada: contar bytes recusaria
		String acima = "ç".repeat(10001);

		inserirVersaoCompleta(questao, 1, "ARQUIVADA", subtopico, limite, null, limite, null, null, limite);

		assertThatThrownBy(() -> inserirVersaoCompleta(questao, 2, "ARQUIVADA", subtopico, null, null, acima, null, null, null))
				.isInstanceOf(DataAccessException.class).hasMessageContaining("ck_questao_versao_enunciado");
		assertThatThrownBy(() -> inserirVersaoCompleta(questao, 2, "ARQUIVADA", subtopico, acima, null, "E", null, null, null))
				.isInstanceOf(DataAccessException.class).hasMessageContaining("ck_questao_versao_texto_apoio");
		assertThatThrownBy(() -> inserirVersaoCompleta(questao, 2, "ARQUIVADA", subtopico, null, null, "E", null, null, acima))
				.isInstanceOf(DataAccessException.class).hasMessageContaining("ck_questao_versao_explicacao");
	}

	@Test
	void deve_recusar_no_banco_letra_de_alternativa_fora_de_a_a_e_repetida_ou_com_texto_acima_de_2000() {
		long questao = novaQuestaoNoBanco();
		long versao = inserirVersao(questao, 1, "RASCUNHO", novoSubtopicoNoBanco(), "E");

		for (String letra : new String[] { "F", "a", "1" }) {
			assertThatThrownBy(() -> inserirAlternativa(versao, letra, "x")).as("letra %s", letra)
					.isInstanceOf(DataAccessException.class).hasMessageContaining("ck_alternativa_letra");
		}
		inserirAlternativa(versao, "A", "x".repeat(2000));
		assertThatThrownBy(() -> inserirAlternativa(versao, "A", "outra")).isInstanceOf(DataAccessException.class)
				.hasMessageContaining("uk_alternativa_versao_letra");
		assertThatThrownBy(() -> inserirAlternativa(versao, "B", "x".repeat(2001))).isInstanceOf(DataAccessException.class);
	}

	// ----- "um rascunho aberto e uma publicada por questão" (colunas geradas e índices únicos) -----

	@Test
	void deve_recusar_no_banco_dois_rascunhos_da_mesma_questao() {
		long questao = novaQuestaoNoBanco();
		long subtopico = novoSubtopicoNoBanco();
		inserirVersao(questao, 1, "RASCUNHO", subtopico, "E");

		assertThatThrownBy(() -> inserirVersao(questao, 2, "RASCUNHO", subtopico, "E")).isInstanceOf(DataAccessException.class)
				.hasMessageContaining("uk_questao_versao_rascunho_aberto");
	}

	@Test
	void deve_recusar_no_banco_duas_publicadas_da_mesma_questao() {
		long questao = novaQuestaoNoBanco();
		long subtopico = novoSubtopicoNoBanco();
		inserirVersao(questao, 1, "PUBLICADA", subtopico, "E");

		assertThatThrownBy(() -> inserirVersao(questao, 2, "PUBLICADA", subtopico, "E")).isInstanceOf(DataAccessException.class)
				.hasMessageContaining("uk_questao_versao_publicada_vigente");
	}

	@Test
	void deve_aceitar_no_banco_varias_arquivadas_e_um_rascunho_e_uma_publicada_juntos() {
		long questao = novaQuestaoNoBanco();
		long subtopico = novoSubtopicoNoBanco();

		for (int v = 1; v <= 5; v++) {
			inserirVersao(questao, v, "ARQUIVADA", subtopico, "E");
		}
		inserirVersao(questao, 6, "PUBLICADA", subtopico, "E");
		inserirVersao(questao, 7, "RASCUNHO", subtopico, "E");

		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM questao_versao WHERE questao_id = ?", Integer.class, questao)).isEqualTo(7);
	}

	@Test
	void deve_aceitar_rascunho_e_publicada_de_questoes_diferentes_e_recusar_versao_repetida() {
		long subtopico = novoSubtopicoNoBanco();
		long prova = idInternoDaProva(criarProva(10));
		long q1 = inserirQuestao(prova, 1);
		long q2 = inserirQuestao(prova, 2);

		inserirVersao(q1, 1, "RASCUNHO", subtopico, "E");
		inserirVersao(q2, 1, "RASCUNHO", subtopico, "E");
		inserirVersao(q1, 2, "PUBLICADA", subtopico, "E");
		inserirVersao(q2, 2, "PUBLICADA", subtopico, "E");

		assertThatThrownBy(() -> inserirVersao(q1, 2, "ARQUIVADA", subtopico, "E")).isInstanceOf(DataAccessException.class)
				.hasMessageContaining("uk_questao_versao_questao_versao");
	}

	@Test
	void deve_calcular_as_colunas_geradas_pelo_status() {
		long questao = novaQuestaoNoBanco();
		long subtopico = novoSubtopicoNoBanco();
		inserirVersao(questao, 1, "ARQUIVADA", subtopico, "E");
		inserirVersao(questao, 2, "PUBLICADA", subtopico, "E");
		inserirVersao(questao, 3, "RASCUNHO", subtopico, "E");

		List<Map<String, Object>> linhas = jdbc.queryForList("SELECT versao, rascunho_aberto, publicada_vigente FROM questao_versao "
				+ "WHERE questao_id = ? ORDER BY versao", questao);

		assertThat(linhas.get(0)).containsEntry("rascunho_aberto", null).containsEntry("publicada_vigente", null);
		assertThat(linhas.get(1)).containsEntry("rascunho_aberto", null).containsEntry("publicada_vigente", 1);
		assertThat(linhas.get(2)).containsEntry("rascunho_aberto", 1).containsEntry("publicada_vigente", null);
	}

	// ----- subtópico de outro programa (D157) -----

	@Test
	void deve_devolver_400_no_campo_subtopicoid_quando_o_subtopico_e_de_materia_de_outro_programa() {
		String codigo = "ENEM" + sufixo().substring(0, 4).toUpperCase().replaceAll("[^A-Z0-9]", "X");
		jdbc.update("INSERT INTO programa (public_id, codigo, nome, criado_em, atualizado_em) "
				+ "VALUES (UNHEX(REPLACE(UUID(), '-', '')), ?, 'Outro', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))", codigo);
		long outroPrograma = jdbc.queryForObject("SELECT id FROM programa WHERE codigo = ?", Long.class, codigo);
		jdbc.update("INSERT INTO materia (public_id, programa_id, nome, ativo, criado_em, atualizado_em) "
				+ "VALUES (UNHEX(REPLACE(UUID(), '-', '')), ?, ?, TRUE, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))", outroPrograma,
				nomeUnico("Matéria de outro programa"));
		long materia = jdbc.queryForObject("SELECT MAX(id) FROM materia WHERE programa_id = ?", Long.class, outroPrograma);
		jdbc.update("INSERT INTO subtopico (public_id, materia_id, nome, ativo, criado_em, atualizado_em) "
				+ "VALUES (UNHEX(REPLACE(UUID(), '-', '')), ?, ?, TRUE, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))", materia,
				nomeUnico("Subtópico de outro programa"));
		String subtopicoDeOutro = jdbc.queryForObject("SELECT BIN_TO_UUID(public_id) FROM subtopico WHERE materia_id = ?", String.class,
				materia);
		String prova = criarProva(10);

		try {
			Response resposta = cadastrar(prova, corpo(subtopicoDeOutro, 1, "Enunciado"));

			verificarErro(resposta, 400, "Bad Request", "VALIDACAO_FALHOU");
			resposta.then().body("campos[0].campo", equalTo("subtopicoId")).body("campos", hasSize(1));
			assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM questao WHERE prova_id = ?", Integer.class, idInternoDaProva(prova)))
					.isZero();
		}
		finally {
			// O banco é COMPARTILHADO: o programa extra NÃO pode ficar (o ProgramaIntegracaoTest exige só a ETEC na tabela).
			jdbc.update("DELETE FROM subtopico WHERE materia_id = ?", materia);
			jdbc.update("DELETE FROM materia WHERE id = ?", materia);
			jdbc.update("DELETE FROM programa WHERE id = ?", outroPrograma);
		}
	}

	// ----- a FK do subtópico: DELETE /subtopicos/{id} (D146) -----

	@Test
	void deve_devolver_409_subtopico_com_questoes_e_manter_o_subtopico_quando_ele_tem_uma_questao_em_rascunho() {
		String subtopico = criarSubtopico();
		cadastrar(criarProva(10), corpo(subtopico, 1, "Enunciado")).then().statusCode(201);

		Response resposta = requisicao().delete("/api/v1/subtopicos/" + subtopico);

		verificarErro(resposta, 409, "Conflict", "SUBTOPICO_COM_QUESTOES");
		requisicao().get("/api/v1/subtopicos/" + subtopico).then().statusCode(200);
	}

	@ParameterizedTest(name = "versão {0} impede apagar o subtópico")
	@CsvSource({ "RASCUNHO", "PUBLICADA", "ARQUIVADA" })
	void deve_devolver_409_subtopico_com_questoes_quando_a_versao_esta_em_qualquer_status(String status) {
		String subtopico = criarSubtopico();
		long questao = inserirQuestao(idInternoDaProva(criarProva(10)), 1);
		inserirVersao(questao, 1, status, idInternoDoSubtopico(subtopico), "E");

		verificarErro(requisicao().delete("/api/v1/subtopicos/" + subtopico), 409, "Conflict", "SUBTOPICO_COM_QUESTOES");
	}

	@Test
	void deve_apagar_com_204_o_subtopico_sem_questoes_e_com_404_o_que_nao_existe() {
		String subtopico = criarSubtopico();
		// outra questão, de OUTRO subtópico, não impede
		cadastrar(criarProva(10), corpo(criarSubtopico(), 1, "Enunciado")).then().statusCode(201);

		requisicao().delete("/api/v1/subtopicos/" + subtopico).then().statusCode(204);
		verificarErro(requisicao().delete("/api/v1/subtopicos/" + subtopico), 404, "Not Found", "SUBTOPICO_NAO_ENCONTRADO");
	}

	@Test
	void deve_deixar_a_questao_e_as_versoes_intactas_depois_do_409_do_delete() {
		String subtopico = criarSubtopico();
		String questao = cadastrar(criarProva(10), corpo(subtopico, 1, "Enunciado")).then().statusCode(201).extract().path("id");

		requisicao().delete("/api/v1/subtopicos/" + subtopico).then().statusCode(409);

		buscarQuestao(questao).then().statusCode(200).body("subtopicoId", equalTo(subtopico)).body("alternativas", hasSize(5));
	}

	// ----- o que a API grava -----

	@Test
	void deve_gravar_o_hash_da_questao_calculado_com_o_id_publico_da_prova() {
		String prova = criarProva(10);
		String subtopico = criarSubtopico();
		String id = cadastrar(prova, corpo(subtopico, 3, "Quanto é 1+1?")).then().statusCode(201).extract().path("id");

		String gravado = jdbc.queryForObject("SELECT hash_conteudo FROM questao WHERE public_id = UUID_TO_BIN(?)", String.class, id);

		assertThat(gravado).isEqualTo(HashDaQuestao.calcular(UUID.fromString(prova), 3, null, null, "Quanto é 1+1?", null,
				Map.of("A", "um", "B", "dois", "C", "três", "D", "quatro", "E", "cinco")));
	}

	@Test
	void deve_gravar_a_identidade_a_versao_1_em_rascunho_e_as_5_alternativas_com_o_tipo_interno() {
		String prova = criarProva(10);
		String id = cadastrar(prova, corpo(criarSubtopico(), 2, "Enunciado")).then().statusCode(201).extract().path("id");
		long questao = idInternoDaQuestao(id);

		Map<String, Object> identidade = jdbc.queryForMap("SELECT tipo, numero, prova_id FROM questao WHERE id = ?", questao);
		Map<String, Object> versao = jdbc.queryForMap("SELECT id, versao, status, publicada_em, rascunho_aberto, publicada_vigente "
				+ "FROM questao_versao WHERE questao_id = ?", questao);
		List<String> letras = jdbc.queryForList("SELECT letra FROM alternativa WHERE questao_versao_id = ? ORDER BY letra", String.class,
				((Number) versao.get("ID")).longValue());

		assertThat(identidade.get("TIPO")).isEqualTo("MULTIPLA_ESCOLHA");
		assertThat(identidade.get("NUMERO")).isEqualTo(2);
		assertThat(((Number) identidade.get("PROVA_ID")).longValue()).isEqualTo(idInternoDaProva(prova));
		assertThat(versao.get("VERSAO")).isEqualTo(1);
		assertThat(versao.get("STATUS")).isEqualTo("RASCUNHO");
		assertThat(versao.get("PUBLICADA_EM")).isNull();
		assertThat(versao.get("RASCUNHO_ABERTO")).isEqualTo(1);
		assertThat(versao.get("PUBLICADA_VIGENTE")).isNull();
		assertThat(letras).containsExactly("A", "B", "C", "D", "E");
	}

	@Test
	void deve_guardar_e_devolver_textos_longos_e_emoji_sem_perder_nada() {
		String prova = criarProva(10);
		String longo = "ç😀".repeat(5000); // 10000 caracteres, com emoji (4 bytes em utf8mb4)
		String id = cadastrar(prova, "{\"numero\":1,\"subtopicoId\":\"" + criarSubtopico() + "\",\"enunciado\":\"" + longo
				+ "\",\"textoApoio\":\"" + longo + "\",\"explicacao\":\"" + longo + "\",\"alternativas\":"
				+ alternativas("😀", "b", "c", "d", "e") + "}").then().statusCode(201).extract().path("id");

		Response lida = buscarQuestao(id);

		lida.then().statusCode(200).body("enunciado", equalTo(longo)).body("textoApoio", equalTo(longo))
				.body("explicacao", equalTo(longo)).body("alternativas[0].texto", equalTo("😀"));
	}

	@Test
	void deve_devolver_409_prova_nao_editavel_quando_a_prova_esta_publicada() {
		String prova = criarProva(10);
		jdbc.update("UPDATE prova SET status = 'PUBLICADA', publicada_em = UTC_TIMESTAMP(3) WHERE public_id = UUID_TO_BIN(?)", prova);

		verificarErro(cadastrar(prova, corpo(criarSubtopico(), 1, "Enunciado")), 409, "Conflict", "PROVA_NAO_EDITAVEL");
	}

	@Test
	void deve_devolver_409_subtopico_inativo_pela_api_real_em_subtopico_e_materia_desativados() {
		String prova = criarProva(10);
		String subtopico = criarSubtopico();
		requisicao().patch("/api/v1/subtopicos/" + subtopico + "/disable").then().statusCode(200);
		verificarErro(cadastrar(prova, corpo(subtopico, 1, "E")), 409, "Conflict", "SUBTOPICO_INATIVO");

		String outro = criarSubtopico();
		String materia = requisicao().get("/api/v1/subtopicos/" + outro).path("materiaId");
		requisicao().patch("/api/v1/materias/" + materia + "/disable").then().statusCode(200);
		verificarErro(cadastrar(prova, corpo(outro, 2, "E")), 409, "Conflict", "SUBTOPICO_INATIVO");
	}

	// ----- fluxo ponta a ponta -----

	@Test
	void deve_criar_a_prova_cadastrar_as_questoes_e_le_las_por_id_de_ponta_a_ponta() {
		String prova = criarProva(3);
		String subtopico = criarSubtopico();
		String q1 = cadastrar(prova, corpo(subtopico, 1, "Primeira")).then().statusCode(201).extract().path("id");
		Response q2 = cadastrar(prova, "{\"numero\":2,\"subtopicoId\":\"" + subtopico + "\",\"textoApoio\":\"Texto <b>comum</b>\","
				+ "\"grupoApoio\":1,\"enunciado\":\"Segunda\",\"alternativas\":" + alternativas("a", "b", "c", "d", "e")
				+ ",\"gabarito\":\"D\",\"dificuldade\":\"DIFICIL\",\"explicacao\":\"Porque sim\"}");
		q2.then().statusCode(201).header("Location", "/api/v1/admin/questoes/" + q2.path("id"));
		String q3 = cadastrar(prova, corpo(subtopico, 3, "Terceira")).then().statusCode(201).extract().path("id");

		cadastrar(prova, corpo(subtopico, 1, "Primeira")).then().statusCode(200).body("id", equalTo(q1));
		verificarErro(cadastrar(prova, corpo(subtopico, 1, "Primeira alterada")), 409, "Conflict", "QUESTAO_NUMERO_EM_CONFLITO");
		cadastrar(prova, corpo(subtopico, 4, "Quarta")).then().statusCode(400).body("campos[0].campo", equalTo("numero"));

		buscarQuestao(q1).then().statusCode(200).body("id", equalTo(q1)).body("provaId", equalTo(prova)).body("numero", equalTo(1))
				.body("enunciado", equalTo("Primeira")).body("gabarito", nullValue()).body("status", equalTo("RASCUNHO"));
		buscarQuestao(q2.path("id")).then().statusCode(200).body("textoApoio", equalTo("Texto <b>comum</b>"))
				.body("grupoApoio", equalTo(1)).body("gabarito", equalTo("D")).body("dificuldade", equalTo("DIFICIL"))
				.body("explicacao", equalTo("Porque sim")).body("alternativas[3].letra", equalTo("D"));
		requisicao().get("/api/v1/admin/questoes/" + q3 + "?versao=1").then().statusCode(200).body("versao", equalTo(1));
		verificarErro(requisicao().get("/api/v1/admin/questoes/" + q3 + "?versao=2"), 404, "Not Found", "QUESTAO_NAO_ENCONTRADA");
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM questao WHERE prova_id = ?", Integer.class, idInternoDaProva(prova)))
				.isEqualTo(3);
	}

	@Test
	void deve_devolver_o_uuid_curto_como_400_pela_api_real() {
		verificarErro(requisicao().get("/api/v1/admin/questoes/11111111-1111-1111-1111-11111111111"), 400, "Bad Request",
				"PARAMETRO_INVALIDO");
		verificarErro(requisicao().get("/api/v1/provas/11111111-1111-1111-1111-11111111111"), 400, "Bad Request", "PARAMETRO_INVALIDO");
		verificarErro(requisicao().get("/api/v1/materias/11111111-1111-1111-1111-11111111111"), 400, "Bad Request",
				"PARAMETRO_INVALIDO");
		verificarErro(requisicao().delete("/api/v1/subtopicos/11111111-1111-1111-1111-11111111111"), 400, "Bad Request",
				"PARAMETRO_INVALIDO");
	}

	@Test
	void deve_manter_o_fk_da_versao_para_o_subtopico_como_dataintegrityviolation_quando_apagado_direto() {
		String subtopico = criarSubtopico();
		cadastrar(criarProva(10), corpo(subtopico, 1, "Enunciado")).then().statusCode(201);

		assertThatThrownBy(() -> jdbc.update("DELETE FROM subtopico WHERE id = ?", idInternoDoSubtopico(subtopico)))
				.isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("fk_questao_versao_subtopico");
	}

}
