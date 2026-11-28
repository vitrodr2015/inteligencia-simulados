package br.simulado.etec.catalogo.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.function.BiFunction;
import java.util.stream.Stream;

import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import io.restassured.response.Response;

/**
 * Concorrência das exclusões (D175, D184, D185) contra MySQL REAL: corridas repetidas, a ordem na fila da trava fixada para os
 * dois desfechos, quem esperava a trava de uma prova apagada, e os casos determinísticos que provam que a trava da prova é a
 * PRIMEIRA leitura das duas exclusões.
 */
class ExclusaoConcorrenciaIntegracaoTest extends AbstractPublicacaoIntegrationTest {

	private static String codigo(Response resposta) {
		return resposta.path("codigo");
	}

	private int linhasDaProva(long idInterno) {
		return jdbc.queryForObject("SELECT (SELECT COUNT(*) FROM prova WHERE id = ?) + (SELECT COUNT(*) FROM questao "
				+ "WHERE prova_id = ?)", Integer.class, idInterno, idInterno);
	}

	/** Os ids públicos das questões que estão no numero dado da prova. */
	private List<String> noNumero(String prova, int numero) {
		return jdbc.queryForList("SELECT BIN_TO_UUID(public_id) FROM questao WHERE prova_id = ? AND numero = ?", String.class,
				idInternoDaProva(prova), numero);
	}

	/** A escrita que corre contra a exclusão da prova: [prova, subtópico] na entrada. */
	private enum Escrita {
		CADASTRO, IMPORTACAO, PUBLICACAO
	}

	private Callable<Response> escrever(Escrita escrita, String prova, String subtopico) {
		return switch (escrita) {
			case CADASTRO -> () -> cadastrar(prova, questaoComGabarito(subtopico, 5, "B"));
			case IMPORTACAO -> () -> importar(lote(prova, List.of(questaoComGabarito(subtopico, 5, "B"))));
			case PUBLICACAO -> () -> publicar(prova);
		};
	}

	/** Uma prova de 5 questões: faltando a 5 para o cadastro e a importação; completa para a publicação. */
	private String provaPara(Escrita escrita, String subtopico) {
		String prova = novaProva(5);
		importarProntas(prova, subtopico, 1, escrita == Escrita.PUBLICACAO ? 5 : 4);
		return prova;
	}

	/** Os dois desfechos são inteiros: ou a escrita veio antes (e a exclusão a alcançou), ou a exclusão veio antes. */
	private void exigirDesfechoDaProva(Escrita escrita, String prova, long idInterno, Response exclusao, Response outra) {
		if (escrita == Escrita.PUBLICACAO && outra.statusCode() == 200) {
			verificarErro(exclusao, 409, "Conflict", "PROVA_NAO_EDITAVEL");
			assertThat(statusDaProva(prova)).isEqualTo("PUBLICADA");
			exigirSemEstadoParcial(prova);
			return;
		}
		assertThat(exclusao.statusCode()).as(exclusao.asString()).isEqualTo(204);
		assertThat(linhasDaProva(idInterno)).as("nada sobra").isZero();
		switch (escrita) {
			case CADASTRO -> assertThat(outra.statusCode()).as(outra.asString()).isIn(201, 404);
			case IMPORTACAO -> assertThat(outra.statusCode()).as(outra.asString()).isIn(200, 400);
			case PUBLICACAO -> assertThat(outra.statusCode()).as(outra.asString()).isEqualTo(404);
		}
	}

	// ----- corridas contra a exclusão da PROVA -----

	@RepeatedTest(4)
	void deve_ficar_consistente_quando_apagar_a_prova_corre_junto_com_o_cadastro() throws Exception {
		corridaContraApagarAProva(Escrita.CADASTRO);
	}

	@RepeatedTest(4)
	void deve_ficar_consistente_quando_apagar_a_prova_corre_junto_com_a_importacao() throws Exception {
		corridaContraApagarAProva(Escrita.IMPORTACAO);
	}

	@RepeatedTest(4)
	void deve_ficar_consistente_quando_apagar_a_prova_corre_junto_com_a_publicacao() throws Exception {
		corridaContraApagarAProva(Escrita.PUBLICACAO);
	}

	private void corridaContraApagarAProva(Escrita escrita) throws Exception {
		String subtopico = criarSubtopico();
		String prova = provaPara(escrita, subtopico);
		long id = idInternoDaProva(prova);

		List<Response> respostas = executarSimultaneamente(
				List.of(() -> apagarProva(prova), escrever(escrita, prova, subtopico)));

		exigirDesfechoDaProva(escrita, prova, id, respostas.get(0), respostas.get(1));
	}

	/** Com a ordem na fila fixada, os dois desfechos de cada par são provados, e não só o que o acaso produzir. */
	@ParameterizedTest
	@ValueSource(strings = { "CADASTRO", "IMPORTACAO", "PUBLICACAO" })
	void deve_apagar_tambem_o_que_foi_escrito_quando_a_escrita_chega_antes_da_exclusao(String qual) throws Exception {
		Escrita escrita = Escrita.valueOf(qual);
		String subtopico = criarSubtopico();
		String prova = provaPara(escrita, subtopico);
		long id = idInternoDaProva(prova);

		List<Response> respostas = naOrdemDaFila(prova, escrever(escrita, prova, subtopico), () -> apagarProva(prova));

		assertThat(respostas.get(0).statusCode()).as(respostas.get(0).asString())
				.isEqualTo(escrita == Escrita.CADASTRO ? 201 : 200);
		exigirDesfechoDaProva(escrita, prova, id, respostas.get(1), respostas.get(0));
	}

	// ----- quem esperava a trava de uma prova apagada -----

	/** Cada rota que escreve, com o status e o código esperados: [prova, questão 1, subtópico] na entrada. */
	static Stream<Arguments> quemEsperava() {
		return Stream.of(
				esperando("cadastro", 404, "PROVA_NAO_ENCONTRADA",
						(t, ids) -> t.cadastrar(ids.get(0), questaoComGabarito(ids.get(2), 2, "A"))),
				// a prova vem no CORPO da importação: referência inexistente é 400 em campos[provaId] (D163)
				esperando("importação", 400, "VALIDACAO_FALHOU",
						(t, ids) -> t.importar(lote(ids.get(0), List.of(questaoComGabarito(ids.get(2), 2, "A"))))),
				esperando("PUT da prova", 404, "PROVA_NAO_ENCONTRADA",
						(t, ids) -> t.editarProva(ids.get(0), corpoDaProva("Nova", null, null, null, 2))),
				esperando("publicar a prova", 404, "PROVA_NAO_ENCONTRADA", (t, ids) -> t.publicar(ids.get(0))),
				esperando("gabarito em lote", 404, "PROVA_NAO_ENCONTRADA", (t, ids) -> t.gabarito(ids.get(0), item(1, "A"))),
				esperando("apagar a prova", 404, "PROVA_NAO_ENCONTRADA", (t, ids) -> t.apagarProva(ids.get(0))),
				esperando("PUT da questão", 404, "QUESTAO_NAO_ENCONTRADA",
						(t, ids) -> t.editarQuestao(ids.get(1), corpoDoPut(ids.get(2), "Depois", ""))),
				esperando("nova versão", 404, "QUESTAO_NAO_ENCONTRADA", (t, ids) -> t.novaVersao(ids.get(1))),
				esperando("publicar a questão", 404, "QUESTAO_NAO_ENCONTRADA", (t, ids) -> t.publicarQuestao(ids.get(1))),
				esperando("apagar a questão", 404, "QUESTAO_NAO_ENCONTRADA", (t, ids) -> t.apagarQuestao(ids.get(1))));
	}

	private static Arguments esperando(String nome, int status, String codigo,
			BiFunction<ExclusaoConcorrenciaIntegracaoTest, List<String>, Response> pedido) {
		return Arguments.of(Named.of(nome, pedido), status, codigo);
	}

	@ParameterizedTest(name = "{0} → {1}")
	@MethodSource("quemEsperava")
	void deve_responder_que_nao_existe_a_quem_esperava_a_trava_da_prova_apagada(
			BiFunction<ExclusaoConcorrenciaIntegracaoTest, List<String>, Response> pedido, int status, String codigo)
			throws Exception {
		String subtopico = criarSubtopico();
		String prova = novaProva(2);
		String questao = cadastrarPronta(prova, subtopico, 1);
		long id = idInternoDaProva(prova);

		List<Response> respostas = naOrdemDaFila(prova, () -> apagarProva(prova),
				() -> pedido.apply(this, List.of(prova, questao, subtopico)));

		assertThat(respostas.get(0).statusCode()).as(respostas.get(0).asString()).isEqualTo(204);
		assertThat(respostas.get(1).statusCode()).as(respostas.get(1).asString()).isEqualTo(status);
		assertThat(codigo(respostas.get(1))).isEqualTo(codigo);
		if (status == 400) {
			assertThat(respostas.get(1).<String>path("campos[0].campo")).isEqualTo("provaId");
		}
		assertThat(linhasDaProva(id)).isZero();
	}

	// ----- contra a exclusão da QUESTÃO -----

	/** [prova de 2 questões, subtópico, a questão 2, que será apagada]. */
	private List<String> questaoParaApagar() {
		String subtopico = criarSubtopico();
		String prova = novaProva(2);
		cadastrarPronta(prova, subtopico, 1);
		return List.of(prova, subtopico, cadastrarPronta(prova, subtopico, 2));
	}

	private Callable<Response> reenviarCorrigida(boolean porImportacao, List<String> ids) {
		String corrigida = corpo(ids.get(1), 2, "Corrigida");
		return porImportacao ? () -> importar(lote(ids.get(0), List.of(corrigida))) : () -> cadastrar(ids.get(0), corrigida);
	}

	/** Exclusão antes: o conteúdo corrigido entra. Depois: ele conflita, e a questão antiga some mesmo assim. */
	private void exigirDesfechoDaReentrada(boolean porImportacao, List<String> ids, Response exclusao, Response entrada) {
		assertThat(exclusao.statusCode()).as(exclusao.asString()).isEqualTo(204);
		assertThat(entrada.statusCode()).as(entrada.asString()).isIn(porImportacao ? 200 : 201, 409);
		if (entrada.statusCode() == 409) {
			assertThat(codigo(entrada)).isEqualTo("QUESTAO_NUMERO_EM_CONFLITO");
			assertThat(noNumero(ids.get(0), 2)).isEmpty();
		}
		else {
			assertThat(noNumero(ids.get(0), 2)).hasSize(1).doesNotContain(ids.get(2));
		}
		assertThat(noNumero(ids.get(0), 1)).hasSize(1);
	}

	@RepeatedTest(6)
	void deve_ficar_consistente_quando_apagar_a_questao_corre_junto_com_o_cadastro_do_mesmo_numero() throws Exception {
		List<String> ids = questaoParaApagar();

		List<Response> respostas = executarSimultaneamente(
				List.of(() -> apagarQuestao(ids.get(2)), reenviarCorrigida(false, ids)));

		exigirDesfechoDaReentrada(false, ids, respostas.get(0), respostas.get(1));
	}

	@RepeatedTest(6)
	void deve_ficar_consistente_quando_apagar_a_questao_corre_junto_com_a_importacao_do_mesmo_numero() throws Exception {
		List<String> ids = questaoParaApagar();

		List<Response> respostas = executarSimultaneamente(
				List.of(() -> apagarQuestao(ids.get(2)), reenviarCorrigida(true, ids)));

		exigirDesfechoDaReentrada(true, ids, respostas.get(0), respostas.get(1));
	}

	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	void deve_dar_os_dois_desfechos_conforme_a_ordem_entre_apagar_a_questao_e_reenviar_o_numero(boolean porImportacao)
			throws Exception {
		List<String> antes = questaoParaApagar();
		List<Response> exclusaoPrimeiro = naOrdemDaFila(antes.get(0), () -> apagarQuestao(antes.get(2)),
				reenviarCorrigida(porImportacao, antes));
		exigirDesfechoDaReentrada(porImportacao, antes, exclusaoPrimeiro.get(0), exclusaoPrimeiro.get(1));
		assertThat(exclusaoPrimeiro.get(1).statusCode()).isEqualTo(porImportacao ? 200 : 201);

		List<String> depois = questaoParaApagar();
		List<Response> entradaPrimeiro = naOrdemDaFila(depois.get(0), reenviarCorrigida(porImportacao, depois),
				() -> apagarQuestao(depois.get(2)));
		exigirDesfechoDaReentrada(porImportacao, depois, entradaPrimeiro.get(1), entradaPrimeiro.get(0));
		assertThat(entradaPrimeiro.get(0).statusCode()).isEqualTo(409);
	}

	/** Exclusão antes: a publicação acha o numero ausente. Depois: a questão já está publicada e não se apaga. */
	private void exigirDesfechoDaCascata(String prova, Response exclusao, Response publicacao) {
		if (publicacao.statusCode() == 200) {
			verificarErro(exclusao, 409, "Conflict", "QUESTAO_SEM_RASCUNHO");
			assertThat(statusDaProva(prova)).isEqualTo("PUBLICADA");
		}
		else {
			assertThat(exclusao.statusCode()).as(exclusao.asString()).isEqualTo(204);
			assertThat(pendencias(publicacao)).containsExactly("2 QUESTAO_AUSENTE");
			assertThat(statusDaProva(prova)).isEqualTo("RASCUNHO");
		}
		exigirSemEstadoParcial(prova);
	}

	@RepeatedTest(6)
	void deve_ficar_consistente_quando_apagar_a_questao_corre_junto_com_a_publicacao_da_prova() throws Exception {
		List<String> ids = questaoParaApagar();

		List<Response> respostas = executarSimultaneamente(
				List.of(() -> apagarQuestao(ids.get(2)), () -> publicar(ids.get(0))));

		exigirDesfechoDaCascata(ids.get(0), respostas.get(0), respostas.get(1));
	}

	@Test
	void deve_dar_os_dois_desfechos_conforme_a_ordem_entre_apagar_a_questao_e_publicar_a_prova() throws Exception {
		List<String> antes = questaoParaApagar();
		List<Response> exclusaoPrimeiro = naOrdemDaFila(antes.get(0), () -> apagarQuestao(antes.get(2)),
				() -> publicar(antes.get(0)));
		assertThat(exclusaoPrimeiro.stream().map(Response::statusCode)).containsExactly(204, 409);
		exigirDesfechoDaCascata(antes.get(0), exclusaoPrimeiro.get(0), exclusaoPrimeiro.get(1));

		List<String> depois = questaoParaApagar();
		List<Response> publicacaoPrimeiro = naOrdemDaFila(depois.get(0), () -> publicar(depois.get(0)),
				() -> apagarQuestao(depois.get(2)));
		assertThat(publicacaoPrimeiro.stream().map(Response::statusCode)).containsExactly(200, 409);
		exigirDesfechoDaCascata(depois.get(0), publicacaoPrimeiro.get(1), publicacaoPrimeiro.get(0));
	}

	/** [prova publicada, questão com a v1 publicada e a v2 em rascunho]. */
	private List<String> questaoEmRevisao() {
		List<String> ids = questaoPublicada(criarSubtopico());
		novaVersao(ids.get(1)).then().statusCode(201);
		return ids;
	}

	@RepeatedTest(6)
	void deve_ficar_com_uma_publicada_quando_descartar_corre_junto_com_publicar_o_mesmo_rascunho() throws Exception {
		String questao = questaoEmRevisao().get(1);

		List<Response> respostas = executarSimultaneamente(
				List.of(() -> apagarQuestao(questao), () -> publicarQuestao(questao)));

		if (respostas.get(0).statusCode() == 204) {
			verificarErro(respostas.get(1), 409, "Conflict", "QUESTAO_SEM_RASCUNHO");
			assertThat(historico(questao)).containsExactly("1 PUBLICADA");
		}
		else {
			assertThat(respostas.get(1).statusCode()).as(respostas.get(1).asString()).isEqualTo(200);
			verificarErro(respostas.get(0), 409, "Conflict", "QUESTAO_SEM_RASCUNHO");
			assertThat(historico(questao)).containsExactly("1 ARQUIVADA", "2 PUBLICADA");
		}
	}

	@RepeatedTest(4)
	void deve_dar_um_204_e_um_409_em_dois_descartes_simultaneos_do_mesmo_rascunho() throws Exception {
		String questao = questaoEmRevisao().get(1);

		List<Response> respostas = executarSimultaneamente(List.of(() -> apagarQuestao(questao), () -> apagarQuestao(questao)));

		assertThat(respostas.stream().map(Response::statusCode)).containsExactlyInAnyOrder(204, 409);
		assertThat(historico(questao)).containsExactly("1 PUBLICADA");
	}

	@RepeatedTest(4)
	void deve_dar_um_204_e_um_404_em_duas_exclusoes_simultaneas_da_questao_nunca_publicada() throws Exception {
		String questao = questaoParaApagar().get(2);

		List<Response> respostas = executarSimultaneamente(List.of(() -> apagarQuestao(questao), () -> apagarQuestao(questao)));

		assertThat(respostas.stream().map(Response::statusCode)).containsExactlyInAnyOrder(204, 404);
	}

	// ----- a trava é a primeira leitura (determinísticos) -----

	@Test
	void deve_enxergar_a_publicacao_feita_enquanto_apagar_a_questao_esperava_a_trava() throws Exception {
		List<String> ids = questaoParaApagar();
		long prova = idInternoDaProva(ids.get(0));
		long questao = idInternoDaQuestao(ids.get(2));

		Response resposta = comAProvaTravada(ids.get(0), () -> apagarQuestao(ids.get(2)), outra -> {
			publicarVersoesPor(outra, "questao_id = " + questao);
			executar(outra, "UPDATE prova SET status = 'PUBLICADA', publicada_em = UTC_TIMESTAMP(3) WHERE id = " + prova);
		});

		verificarErro(resposta, 409, "Conflict", "QUESTAO_SEM_RASCUNHO");
		assertThat(historico(ids.get(2))).as("a publicada continua lá").containsExactly("1 PUBLICADA");
	}

	@Test
	void deve_descartar_o_rascunho_aberto_enquanto_apagar_a_questao_esperava_a_trava() throws Exception {
		List<String> ids = questaoPublicada(criarSubtopico());
		long questao = idInternoDaQuestao(ids.get(1));

		Response resposta = comAProvaTravada(ids.get(0), () -> apagarQuestao(ids.get(1)), outra -> executar(outra,
				"INSERT INTO questao_versao (questao_id, versao, status, subtopico_id, enunciado, criado_em, atualizado_em) "
						+ "SELECT questao_id, 2, 'RASCUNHO', subtopico_id, 'De outro', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3) "
						+ "FROM questao_versao WHERE questao_id = " + questao + " AND versao = 1"));

		assertThat(resposta.statusCode()).as(resposta.asString()).isEqualTo(204);
		assertThat(historico(ids.get(1))).containsExactly("1 PUBLICADA");
	}

	@Test
	void deve_enxergar_a_publicacao_da_prova_feita_enquanto_apagar_a_prova_esperava_a_trava() throws Exception {
		List<String> ids = questaoParaApagar();
		long prova = idInternoDaProva(ids.get(0));

		Response resposta = comAProvaTravada(ids.get(0), () -> apagarProva(ids.get(0)), outra -> {
			publicarVersoesPor(outra, "questao_id IN (SELECT id FROM questao WHERE prova_id = " + prova + ")");
			executar(outra, "UPDATE prova SET status = 'PUBLICADA', publicada_em = UTC_TIMESTAMP(3) WHERE id = " + prova);
		});

		verificarErro(resposta, 409, "Conflict", "PROVA_NAO_EDITAVEL");
		assertThat(versoesPorStatus(ids.get(0))).containsExactly("PUBLICADA=2");
	}

	@Test
	void deve_apagar_tambem_a_questao_que_chegou_enquanto_apagar_a_prova_esperava_a_trava() throws Exception {
		String prova = novaProva(2);
		String subtopico = criarSubtopico();
		cadastrarPronta(prova, subtopico, 1);
		long id = idInternoDaProva(prova);
		long idDoSubtopico = idInternoDoSubtopico(subtopico);

		Response resposta = comAProvaTravada(prova, () -> apagarProva(prova), outra -> {
			executar(outra, "INSERT INTO questao (public_id, prova_id, numero, tipo, hash_conteudo, criado_em, atualizado_em) "
					+ "VALUES (UNHEX(REPLACE(UUID(), '-', '')), " + id + ", 2, 'MULTIPLA_ESCOLHA', '" + "b".repeat(64)
					+ "', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))");
			executar(outra, "INSERT INTO questao_versao (questao_id, versao, status, subtopico_id, enunciado, criado_em, "
					+ "atualizado_em) SELECT id, 1, 'RASCUNHO', " + idDoSubtopico + ", 'Chegou depois', UTC_TIMESTAMP(3), "
					+ "UTC_TIMESTAMP(3) FROM questao WHERE prova_id = " + id + " AND numero = 2");
		});

		assertThat(resposta.statusCode()).as(resposta.asString()).isEqualTo(204);
		assertThat(linhasDaProva(id)).isZero();
	}

	private static void publicarVersoesPor(Connection outra, String condicao) throws java.sql.SQLException {
		executar(outra, "UPDATE questao_versao SET status = 'PUBLICADA', publicada_em = UTC_TIMESTAMP(3) WHERE " + condicao
				+ " AND status = 'RASCUNHO'");
	}

}
