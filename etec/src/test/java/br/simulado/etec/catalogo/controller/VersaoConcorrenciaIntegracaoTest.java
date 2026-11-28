package br.simulado.etec.catalogo.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import io.restassured.response.Response;

/**
 * Concorrência da nova versão e da publicação avulsa (D175, D181, D182) contra MySQL REAL. Corridas repetidas, a ordem na fila
 * da trava fixada para os dois desfechos e os casos determinísticos que provam que a trava da prova é a PRIMEIRA leitura.
 */
class VersaoConcorrenciaIntegracaoTest extends AbstractPublicacaoIntegrationTest {

	/** [prova publicada, questão com a v1 publicada e a v2 em rascunho, subtópico]; o rascunho tem o enunciado "Rascunho". */
	private List<String> questaoEmRevisao() {
		String subtopico = criarSubtopico();
		List<String> ids = questaoPublicada(subtopico);
		novaVersao(ids.get(1)).then().statusCode(201);
		editarQuestao(ids.get(1), corpoDoPut(subtopico, "Rascunho", "\"gabarito\":\"B\"")).then().statusCode(200);
		return List.of(ids.get(0), ids.get(1), subtopico);
	}

	private Response editar(List<String> ids, String enunciado) {
		return editarQuestao(ids.get(1), corpoDoPut(ids.get(2), enunciado, "\"gabarito\":\"C\""));
	}

	private static String codigo(Response resposta) {
		return resposta.path("codigo");
	}

	// ----- corridas -----

	@RepeatedTest(5)
	void deve_dar_um_200_e_um_409_sem_rascunho_em_duas_publicacoes_simultaneas_do_mesmo_rascunho() throws Exception {
		String questao = questaoEmRevisao().get(1);

		List<Response> respostas = executarSimultaneamente(
				List.of(() -> publicarQuestao(questao), () -> publicarQuestao(questao)));

		assertThat(respostas.stream().map(Response::statusCode)).containsExactlyInAnyOrder(200, 409);
		assertThat(codigo(respostas.stream().filter(r -> r.statusCode() == 409).findFirst().orElseThrow()))
				.isEqualTo("QUESTAO_SEM_RASCUNHO");
		assertThat(historico(questao)).containsExactly("1 ARQUIVADA", "2 PUBLICADA");
	}

	@RepeatedTest(5)
	void deve_dar_um_201_e_um_409_em_duas_novas_versoes_simultaneas() throws Exception {
		String questao = questaoPublicada(criarSubtopico()).get(1);

		List<Response> respostas = executarSimultaneamente(List.of(() -> novaVersao(questao), () -> novaVersao(questao)));

		assertThat(respostas.stream().map(Response::statusCode)).containsExactlyInAnyOrder(201, 409);
		assertThat(codigo(respostas.stream().filter(r -> r.statusCode() == 409).findFirst().orElseThrow()))
				.isEqualTo("QUESTAO_COM_RASCUNHO");
		assertThat(historico(questao)).containsExactly("1 PUBLICADA", "2 RASCUNHO");
	}

	/** Os dois desfechos são consistentes: o PUT que respondeu 200 está no que foi publicado; o que perdeu soube com 409. */
	@RepeatedTest(6)
	void deve_ficar_consistente_quando_o_put_do_rascunho_corre_junto_com_a_publicacao() throws Exception {
		List<String> ids = questaoEmRevisao();

		List<Response> respostas = executarSimultaneamente(
				List.of(() -> publicarQuestao(ids.get(1)), () -> editar(ids, "Editado")));

		assertThat(respostas.get(0).statusCode()).as(respostas.get(0).asString()).isEqualTo(200);
		assertThat(respostas.get(1).statusCode()).as(respostas.get(1).asString()).isIn(200, 409);
		boolean putEntrou = respostas.get(1).statusCode() == 200;
		if (!putEntrou) {
			assertThat(codigo(respostas.get(1))).isEqualTo("QUESTAO_SEM_RASCUNHO");
		}
		assertThat(historico(ids.get(1))).as("a publicação nunca é desfeita").containsExactly("1 ARQUIVADA", "2 PUBLICADA");
		assertThat(enunciadoDa(ids.get(1), "PUBLICADA")).isEqualTo(putEntrou ? "Editado" : "Rascunho");
	}

	// ----- os dois desfechos, com a ordem na fila da trava fixada -----

	@Test
	void deve_publicar_o_conteudo_do_put_quando_o_put_chega_antes_da_publicacao() throws Exception {
		List<String> ids = questaoEmRevisao();

		List<Response> respostas = naOrdemDaFila(ids.get(0), () -> editar(ids, "Editado"),
				() -> publicarQuestao(ids.get(1)));

		assertThat(respostas.stream().map(Response::statusCode)).containsExactly(200, 200);
		assertThat(historico(ids.get(1))).containsExactly("1 ARQUIVADA", "2 PUBLICADA");
		assertThat(enunciadoDa(ids.get(1), "PUBLICADA")).isEqualTo("Editado");
	}

	@Test
	void deve_recusar_o_put_quando_a_publicacao_chega_antes_dele() throws Exception {
		List<String> ids = questaoEmRevisao();

		List<Response> respostas = naOrdemDaFila(ids.get(0), () -> publicarQuestao(ids.get(1)),
				() -> editar(ids, "Editado"));

		assertThat(respostas.stream().map(Response::statusCode)).containsExactly(200, 409);
		assertThat(codigo(respostas.get(1))).isEqualTo("QUESTAO_SEM_RASCUNHO");
		assertThat(historico(ids.get(1))).containsExactly("1 ARQUIVADA", "2 PUBLICADA");
		assertThat(enunciadoDa(ids.get(1), "PUBLICADA")).isEqualTo("Rascunho");
	}

	@Test
	void deve_abrir_a_versao_3_quando_a_publicacao_chega_antes_da_nova_versao() throws Exception {
		List<String> ids = questaoEmRevisao();

		List<Response> respostas = naOrdemDaFila(ids.get(0), () -> publicarQuestao(ids.get(1)), () -> novaVersao(ids.get(1)));

		assertThat(respostas.stream().map(Response::statusCode)).containsExactly(200, 201);
		assertThat(historico(ids.get(1))).containsExactly("1 ARQUIVADA", "2 PUBLICADA", "3 RASCUNHO");
		assertThat(enunciadoDa(ids.get(1), "RASCUNHO")).isEqualTo("Rascunho");
	}

	@Test
	void deve_recusar_a_nova_versao_quando_ela_chega_antes_da_publicacao() throws Exception {
		List<String> ids = questaoEmRevisao();

		List<Response> respostas = naOrdemDaFila(ids.get(0), () -> novaVersao(ids.get(1)), () -> publicarQuestao(ids.get(1)));

		assertThat(respostas.stream().map(Response::statusCode)).containsExactly(409, 200);
		assertThat(codigo(respostas.get(0))).isEqualTo("QUESTAO_COM_RASCUNHO");
		assertThat(historico(ids.get(1))).containsExactly("1 ARQUIVADA", "2 PUBLICADA");
	}

	// ----- a trava é a primeira leitura (determinísticos) -----

	@Test
	void deve_enxergar_o_gabarito_apagado_enquanto_a_publicacao_avulsa_esperava_a_trava() throws Exception {
		List<String> ids = questaoEmRevisao();
		long questao = idInternoDaQuestao(ids.get(1));

		Response resposta = comAProvaTravada(ids.get(0), () -> publicarQuestao(ids.get(1)), outra -> executar(outra,
				"UPDATE questao_versao SET gabarito = NULL WHERE questao_id = " + questao + " AND status = 'RASCUNHO'"));

		assertThat(pendencias(resposta)).containsExactly("1 SEM_GABARITO");
		assertThat(historico(ids.get(1))).containsExactly("1 PUBLICADA", "2 RASCUNHO");
	}

	@Test
	void deve_dar_sem_rascunho_quando_outro_publica_o_rascunho_enquanto_a_publicacao_esperava() throws Exception {
		List<String> ids = questaoEmRevisao();
		long questao = idInternoDaQuestao(ids.get(1));

		Response resposta = comAProvaTravada(ids.get(0), () -> publicarQuestao(ids.get(1)), outra -> {
			executar(outra, "UPDATE questao_versao SET status = 'ARQUIVADA' WHERE questao_id = " + questao
					+ " AND status = 'PUBLICADA'");
			executar(outra, "UPDATE questao_versao SET status = 'PUBLICADA', publicada_em = '2001-02-03 04:05:06.000' "
					+ "WHERE questao_id = " + questao + " AND status = 'RASCUNHO'");
		});

		verificarErro(resposta, 409, "Conflict", "QUESTAO_SEM_RASCUNHO");
		assertThat(jdbc.queryForObject("SELECT CAST(publicada_em AS CHAR) FROM questao_versao WHERE questao_id = ? "
				+ "AND status = 'PUBLICADA'", String.class, questao)).as("a data de quem publicou não é regravada")
				.isEqualTo("2001-02-03 04:05:06.000");
	}

	@Test
	void deve_enxergar_a_prova_publicada_enquanto_a_publicacao_avulsa_esperava_a_trava() throws Exception {
		String prova = novaProva(1);
		String questao = cadastrarPronta(prova, criarSubtopico(), 1);
		long id = idInternoDaProva(prova);

		Response resposta = comAProvaTravada(prova, () -> publicarQuestao(questao), outra -> executar(outra,
				"UPDATE prova SET status = 'PUBLICADA', publicada_em = UTC_TIMESTAMP(3) WHERE id = " + id));

		assertThat(resposta.statusCode()).as(resposta.asString()).isEqualTo(200);
		assertThat(historico(questao)).containsExactly("1 PUBLICADA");
	}

	@Test
	void deve_dar_409_quando_aparece_um_rascunho_enquanto_a_nova_versao_esperava_a_trava() throws Exception {
		List<String> ids = questaoPublicada(criarSubtopico());
		long questao = idInternoDaQuestao(ids.get(1));

		Response resposta = comAProvaTravada(ids.get(0), () -> novaVersao(ids.get(1)), outra -> executar(outra,
				"INSERT INTO questao_versao (questao_id, versao, status, subtopico_id, enunciado, criado_em, atualizado_em) "
						+ "SELECT questao_id, 2, 'RASCUNHO', subtopico_id, 'De outro', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3) "
						+ "FROM questao_versao WHERE questao_id = " + questao + " AND versao = 1"));

		verificarErro(resposta, 409, "Conflict", "QUESTAO_COM_RASCUNHO");
		assertThat(historico(ids.get(1))).containsExactly("1 PUBLICADA", "2 RASCUNHO");
	}

	@Test
	void deve_copiar_a_publicada_que_surgiu_enquanto_a_nova_versao_esperava_a_trava() throws Exception {
		List<String> ids = questaoEmRevisao();
		long questao = idInternoDaQuestao(ids.get(1));

		Response resposta = comAProvaTravada(ids.get(0), () -> novaVersao(ids.get(1)), outra -> {
			executar(outra, "UPDATE questao_versao SET status = 'ARQUIVADA' WHERE questao_id = " + questao
					+ " AND status = 'PUBLICADA'");
			executar(outra, "UPDATE questao_versao SET status = 'PUBLICADA', publicada_em = UTC_TIMESTAMP(3) "
					+ "WHERE questao_id = " + questao + " AND status = 'RASCUNHO'");
		});

		assertThat(resposta.statusCode()).as(resposta.asString()).isEqualTo(201);
		assertThat((Integer) resposta.path("versao")).isEqualTo(3);
		assertThat((String) resposta.path("enunciado")).isEqualTo("Rascunho");
		assertThat(historico(ids.get(1))).containsExactly("1 ARQUIVADA", "2 PUBLICADA", "3 RASCUNHO");
	}

	@Test
	void deve_dar_404_nas_duas_rotas_quando_a_questao_some_enquanto_esperam_a_trava() throws Exception {
		for (boolean publicacao : new boolean[] { true, false }) {
			List<String> ids = questaoPublicada(criarSubtopico());
			String questao = ids.get(1);
			long id = idInternoDaQuestao(questao);

			Response resposta = comAProvaTravada(ids.get(0),
					() -> publicacao ? publicarQuestao(questao) : novaVersao(questao), outra -> {
						executar(outra, "DELETE a FROM alternativa a JOIN questao_versao v ON v.id = a.questao_versao_id "
								+ "WHERE v.questao_id = " + id);
						executar(outra, "DELETE FROM questao_versao WHERE questao_id = " + id);
						executar(outra, "DELETE FROM questao WHERE id = " + id);
					});

			verificarErro(resposta, 404, "Not Found", "QUESTAO_NAO_ENCONTRADA");
		}
	}

}
