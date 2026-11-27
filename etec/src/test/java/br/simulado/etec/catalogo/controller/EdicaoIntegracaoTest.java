package br.simulado.etec.catalogo.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.restassured.response.Response;

/** O que só o MySQL real prova da edição (fatia 4, passada 1): o que muda e o que não muda no banco. */
class EdicaoIntegracaoTest extends AbstractEdicaoIntegrationTest {

	private List<String> atualizadoEmDaProva(String prova) {
		return jdbc.queryForList("SELECT CAST(atualizado_em AS CHAR) FROM prova WHERE id = ?", String.class, idInternoDaProva(prova));
	}

	/** Envelhece o atualizado_em das três tabelas da questão, para qualquer regravação aparecer. */
	private void envelhecer(String questao) {
		long id = idInternoDaQuestao(questao);
		jdbc.update("UPDATE questao SET atualizado_em = ? WHERE id = ?", MOMENTO_ANTIGO, id);
		jdbc.update("UPDATE questao_versao SET atualizado_em = ? WHERE questao_id = ?", MOMENTO_ANTIGO, id);
		jdbc.update("UPDATE alternativa a JOIN questao_versao v ON v.id = a.questao_versao_id SET a.atualizado_em = ? "
				+ "WHERE v.questao_id = ?", MOMENTO_ANTIGO, id);
	}

	/** Quais linhas foram regravadas depois de {@link #envelhecer}: "questao", "versao" e a letra de cada alternativa. */
	private List<String> regravadas(String questao) {
		long id = idInternoDaQuestao(questao);
		return jdbc.queryForList("SELECT 'questao' FROM questao WHERE id = ? AND atualizado_em <> ? "
				+ "UNION ALL SELECT 'versao' FROM questao_versao WHERE questao_id = ? AND atualizado_em <> ? "
				+ "UNION ALL SELECT a.letra FROM alternativa a JOIN questao_versao v ON v.id = a.questao_versao_id "
				+ "WHERE v.questao_id = ? AND a.atualizado_em <> ?", String.class, id, MOMENTO_ANTIGO, id, MOMENTO_ANTIGO, id,
				MOMENTO_ANTIGO);
	}

	private List<Long> idsDasAlternativas(String questao) {
		return jdbc.queryForList("SELECT a.id FROM alternativa a JOIN questao_versao v ON v.id = a.questao_versao_id "
				+ "WHERE v.questao_id = ? ORDER BY a.letra", Long.class, idInternoDaQuestao(questao));
	}

	// ----- PUT da prova -----

	@Test
	void deve_gravar_o_put_da_prova_sem_mudar_status_programa_nem_o_hash_das_questoes() {
		String prova = novaProva(10);
		String questao = cadastrarId(prova, criarSubtopico(), 1, "Enunciado");
		String hash = hashDa(questao);
		Map<String, Object> antes = jdbc.queryForMap(
				"SELECT programa_id, status, publicada_em, public_id, criado_em FROM prova WHERE id = ?", idInternoDaProva(prova));

		editarProva(prova, corpoDaProva("Título novo", "Banca " + sufixo(), 2030, 1, 25)).then().statusCode(200)
				.body("titulo", equalTo("Título novo")).body("quantidadeQuestoes", equalTo(25))
				.body("questoesCadastradas", equalTo(1));

		assertThat(jdbc.queryForMap("SELECT programa_id, status, publicada_em, public_id, criado_em FROM prova WHERE id = ?",
				idInternoDaProva(prova))).usingRecursiveComparison().isEqualTo(antes);
		assertThat(hashDa(questao)).isEqualTo(hash);
	}

	@Test
	void deve_manter_o_atualizado_em_da_prova_quando_o_put_nao_muda_nada_e_mudar_quando_muda() {
		String prova = novaProva(10);
		String corpo = corpoDaProva("Mesma", "Banca " + sufixo(), 2031, 2, 10);
		editarProva(prova, corpo).then().statusCode(200);
		jdbc.update("UPDATE prova SET atualizado_em = ? WHERE id = ?", MOMENTO_ANTIGO, idInternoDaProva(prova));

		editarProva(prova, corpo).then().statusCode(200);
		assertThat(atualizadoEmDaProva(prova)).containsExactly(MOMENTO_ANTIGO);

		editarProva(prova, corpo.replace("Mesma", "Outra")).then().statusCode(200);
		assertThat(atualizadoEmDaProva(prova)).doesNotContain(MOMENTO_ANTIGO);
	}

	@Test
	void deve_comparar_a_identidade_pela_collation_no_put_e_deixar_a_propria_prova_trocar_so_a_caixa() {
		String banca = "Vunesp " + sufixo();
		String primeira = novaProva(10);
		editarProva(primeira, corpoDaProva("Primeira", banca, 2032, 1, 10)).then().statusCode(200);
		String segunda = novaProva(10);

		editarProva(primeira, corpoDaProva("Primeira", banca.toUpperCase(), 2032, 1, 10)).then().statusCode(200);
		verificarErro(editarProva(segunda, corpoDaProva("Segunda", "  " + banca.toLowerCase() + " ", 2032, 1, 10)), 409,
				"Conflict", "PROVA_DUPLICADA");
		editarProva(segunda, corpoDaProva("Segunda", banca, 2032, null, 10)).then().statusCode(200);
	}

	@Test
	void deve_dar_409_prova_nao_editavel_no_put_de_prova_publicada_sem_alterar_nada() {
		String prova = novaProva(10);
		jdbc.update("UPDATE prova SET status = 'PUBLICADA', publicada_em = UTC_TIMESTAMP(3) WHERE id = ?", idInternoDaProva(prova));
		Map<String, Object> antes = jdbc.queryForMap("SELECT * FROM prova WHERE id = ?", idInternoDaProva(prova));

		verificarErro(editarProva(prova, corpoDaProva("Nova", null, null, null, 20)), 409, "Conflict", "PROVA_NAO_EDITAVEL");

		assertThat(jdbc.queryForMap("SELECT * FROM prova WHERE id = ?", idInternoDaProva(prova)))
				.usingRecursiveComparison().isEqualTo(antes);
	}

	// ----- PUT de questão -----

	@Test
	void deve_trocar_o_conteudo_nas_mesmas_linhas_sem_criar_versao_nem_mudar_o_hash() {
		String prova = novaProva(10);
		String subtopico = criarSubtopico();
		String outroSubtopico = criarSubtopico();
		String questao = cadastrarId(prova, subtopico, 1, "Original");
		String hash = hashDa(questao);
		List<Long> linhas = idsDasAlternativas(questao);

		editarQuestao(questao, "{\"subtopicoId\":\"" + outroSubtopico + "\",\"enunciado\":\"Trocado\",\"textoApoio\":\"Apoio\","
				+ "\"grupoApoio\":3,\"alternativas\":" + alternativas("E", "D", "C", "B", "A")
				+ ",\"gabarito\":\"B\",\"dificuldade\":\"DIFICIL\",\"explicacao\":\"Explicação\"}").then().statusCode(200)
				.body("versao", equalTo(1)).body("enunciado", equalTo("Trocado")).body("subtopicoId", equalTo(outroSubtopico))
				.body("alternativas[0].texto", equalTo("E")).body("gabarito", equalTo("B"));
		buscarQuestao(questao).then().body("textoApoio", equalTo("Apoio")).body("grupoApoio", equalTo(3))
				.body("alternativas[4].texto", equalTo("A")).body("dificuldade", equalTo("DIFICIL"));

		assertThat(idsDasAlternativas(questao)).as("as mesmas 5 linhas").isEqualTo(linhas);
		assertThat(hashDa(questao)).isEqualTo(hash);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM questao_versao WHERE questao_id = ?", Integer.class,
				idInternoDaQuestao(questao))).isEqualTo(1);

		editarQuestao(questao, corpoDoPut(outroSubtopico, "Sem os opcionais", "")).then().statusCode(200)
				.body("gabarito", nullValue()).body("textoApoio", nullValue()).body("grupoApoio", nullValue())
				.body("dificuldade", nullValue()).body("explicacao", nullValue());
	}

	@Test
	void deve_nao_regravar_nenhuma_linha_quando_o_put_envia_o_que_ja_esta_gravado() {
		String prova = novaProva(10);
		String subtopico = criarSubtopico();
		String questao = cadastrarId(prova, subtopico, 1, "Igual");
		envelhecer(questao);

		editarQuestao(questao, corpoDoPut(subtopico, "  Igual  ", "\"numero\":1,\"provaId\":\"" + prova + "\"")).then()
				.statusCode(200);

		assertThat(regravadas(questao)).isEmpty();
	}

	@Test
	void deve_regravar_a_versao_e_so_a_alternativa_mudada_quando_so_um_texto_de_alternativa_muda() {
		String prova = novaProva(10);
		String subtopico = criarSubtopico();
		String questao = cadastrarId(prova, subtopico, 1, "Igual");
		envelhecer(questao);

		editarQuestao(questao, "{\"subtopicoId\":\"" + subtopico + "\",\"enunciado\":\"Igual\",\"alternativas\":"
				+ alternativas("um", "dois", "TRÊS", "quatro", "cinco") + "}").then().statusCode(200);

		assertThat(regravadas(questao)).containsExactlyInAnyOrder("versao", "C");
	}

	@Test
	void deve_ignorar_o_original_e_dar_conflito_ao_corrigido_no_reenvio_depois_do_put() {
		String prova = novaProva(10);
		String subtopico = criarSubtopico();
		String questao = cadastrarId(prova, subtopico, 1, "Com erro");

		editarQuestao(questao, corpoDoPut(subtopico, "Corrigida", "")).then().statusCode(200);

		cadastrar(prova, corpo(subtopico, 1, "Com erro")).then().statusCode(200).body("id", equalTo(questao))
				.body("enunciado", equalTo("Corrigida"));
		verificarErro(cadastrar(prova, corpo(subtopico, 1, "Corrigida")), 409, "Conflict", "QUESTAO_NUMERO_EM_CONFLITO");
		Response reimportacao = requisicao().body("{\"provaId\":\"" + prova + "\",\"questoes\":[" + corpo(subtopico, 1, "Com erro")
				+ "]}").post("/api/v1/admin/importacoes");
		reimportacao.then().statusCode(200).body("questoesIgnoradas", equalTo(1)).body("questoesCriadas", equalTo(0));
		buscarQuestao(questao).then().body("enunciado", equalTo("Corrigida"));
	}

	@Test
	void deve_recusar_o_put_de_questao_publicada_sem_rascunho_e_nao_tocar_em_nada() {
		String prova = novaProva(10);
		String subtopico = criarSubtopico();
		String questao = cadastrarId(prova, subtopico, 1, "Publicada");
		publicarNoBanco(questao);
		List<Map<String, Object>> antes = fotoDaVersao(questao, 1);

		verificarErro(editarQuestao(questao, corpoDoPut(subtopico, "Tentativa", "")), 409, "Conflict", "QUESTAO_SEM_RASCUNHO");

		assertThat(fotoDaVersao(questao, 1)).usingRecursiveComparison().isEqualTo(antes);
	}

	@Test
	void deve_editar_o_rascunho_novo_sem_tocar_na_versao_publicada_nem_na_prova_publicada() {
		String prova = novaProva(1);
		String subtopico = criarSubtopico();
		String questao = cadastrarId(prova, subtopico, 1, "Versão um");
		publicarNoBanco(questao);
		abrirRascunhoNoBanco(questao);
		jdbc.update("UPDATE prova SET status = 'PUBLICADA', publicada_em = UTC_TIMESTAMP(3) WHERE id = ?", idInternoDaProva(prova));
		List<Map<String, Object>> publicada = fotoDaVersao(questao, 1);
		Map<String, Object> provaAntes = jdbc.queryForMap("SELECT * FROM prova WHERE id = ?", idInternoDaProva(prova));

		editarQuestao(questao, "{\"subtopicoId\":\"" + subtopico + "\",\"enunciado\":\"Versão dois\",\"alternativas\":"
				+ alternativas("1", "2", "3", "4", "5") + ",\"gabarito\":\"A\"}").then().statusCode(200).body("versao", equalTo(2))
				.body("status", equalTo("RASCUNHO"));

		assertThat(fotoDaVersao(questao, 1)).as("a publicada é o que a tentativa referencia").usingRecursiveComparison()
				.isEqualTo(publicada);
		assertThat(jdbc.queryForMap("SELECT * FROM prova WHERE id = ?", idInternoDaProva(prova))).usingRecursiveComparison()
				.isEqualTo(provaAntes);
	}

}
