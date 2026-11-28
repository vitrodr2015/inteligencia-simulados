package br.simulado.etec.catalogo.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.emptyString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import io.restassured.response.Response;

/** Apagar questão e prova (D184, D185) contra MySQL REAL: o que some, o que nunca some e o tudo ou nada. */
class ExclusaoIntegracaoTest extends AbstractPublicacaoIntegrationTest {

	private List<Long> idsDasVersoes(String questao) {
		return jdbc.queryForList("SELECT id FROM questao_versao WHERE questao_id = ? ORDER BY versao", Long.class,
				idInternoDaQuestao(questao));
	}

	private int alternativasDas(List<Long> versoes) {
		return jdbc.queryForObject("SELECT COUNT(*) FROM alternativa WHERE questao_versao_id IN ("
				+ versoes.stream().map(String::valueOf).collect(Collectors.joining(",")) + ")", Integer.class);
	}

	private Map<String, Object> linhaDaProva(String prova) {
		return jdbc.queryForMap("SELECT * FROM prova WHERE id = ?", idInternoDaProva(prova));
	}

	private Map<String, Object> identidade(String questao) {
		return jdbc.queryForMap("SELECT * FROM questao WHERE id = ?", idInternoDaQuestao(questao));
	}

	/** Tudo o que há nas versões PUBLICADAS e ARQUIVADAS de uma questão, linha por linha, inclusive as alternativas. */
	private List<Object> oQueNuncaSeApaga(String questao) {
		long id = idInternoDaQuestao(questao);
		List<Object> linhas = new java.util.ArrayList<>(jdbc.queryForList(
				"SELECT * FROM questao_versao WHERE questao_id = ? AND status <> 'RASCUNHO' ORDER BY id", id));
		linhas.addAll(jdbc.queryForList("SELECT a.* FROM alternativa a JOIN questao_versao v ON v.id = a.questao_versao_id "
				+ "WHERE v.questao_id = ? AND v.status <> 'RASCUNHO' ORDER BY a.id", id));
		return linhas;
	}

	private static void exigirApagado(Response resposta) {
		resposta.then().statusCode(204).body(emptyString());
	}

	// ----- questão nunca publicada -----

	@Test
	void deve_apagar_a_questao_nunca_publicada_e_aceitar_a_reimportacao_corrigida_no_mesmo_numero() {
		String prova = novaProva(2);
		String subtopico = criarSubtopico();
		String um = cadastrarPronta(prova, subtopico, 1);
		String errada = cadastrarPronta(prova, subtopico, 2);
		String corrigida = lote(prova, List.of(corpo(subtopico, 2, "Enunciado corrigido")));
		// antes de apagar, o hash imutável (D51) recusa o conteúdo corrigido
		verificarErro(importar(corrigida), 409, "Conflict", "QUESTAO_NUMERO_EM_CONFLITO");
		List<Long> versoes = idsDasVersoes(errada);
		assertThat(alternativasDas(versoes)).isEqualTo(5);
		long idInterno = idInternoDaQuestao(errada);
		List<Map<String, Object>> vizinha = fotoDaVersao(um, 1);
		Map<String, Object> provaAntes = linhaDaProva(prova);

		exigirApagado(apagarQuestao(errada));

		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM questao WHERE id = ?", Integer.class, idInterno)).isZero();
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM questao_versao WHERE questao_id = ?", Integer.class, idInterno))
				.isZero();
		assertThat(alternativasDas(versoes)).isZero();
		assertThat(fotoDaVersao(um, 1)).isEqualTo(vizinha);
		assertThat(linhaDaProva(prova)).usingRecursiveComparison().isEqualTo(provaAntes);
		verificarErro(buscarQuestao(errada), 404, "Not Found", "QUESTAO_NAO_ENCONTRADA");
		verificarErro(apagarQuestao(errada), 404, "Not Found", "QUESTAO_NAO_ENCONTRADA");
		requisicao().get("/api/v1/provas/" + prova).then().body("questoesCadastradas", equalTo(1));

		importar(corrigida).then().statusCode(200).body("questoesCriadas", equalTo(1)).body("questoesIgnoradas", equalTo(0));

		requisicao().get("/api/v1/admin/questoes?provaId=" + prova).then().body("conteudo.numero", equalTo(List.of(1, 2)))
				.body("conteudo[1].enunciado", equalTo("Enunciado corrigido"));
	}

	// ----- questão publicada: só o rascunho -----

	@Test
	void deve_descartar_so_o_rascunho_e_deixar_a_questao_no_estado_publicado() {
		String subtopico = criarSubtopico();
		List<String> ids = questaoPublicada(subtopico);
		String prova = ids.get(0);
		String questao = ids.get(1);
		novaVersao(questao).then().statusCode(201);
		editarQuestao(questao, corpoDoPut(subtopico, "Desistência", "\"gabarito\":\"B\"")).then().statusCode(200);
		List<Map<String, Object>> publicada = fotoDaVersao(questao, 1);
		List<Long> versoes = idsDasVersoes(questao);
		Map<String, Object> identidade = identidade(questao);
		Map<String, Object> provaAntes = linhaDaProva(prova);

		exigirApagado(apagarQuestao(questao));

		assertThat(historico(questao)).containsExactly("1 PUBLICADA");
		assertThat(fotoDaVersao(questao, 1)).as("a publicada não é tocada, nem o atualizado_em").isEqualTo(publicada);
		assertThat(alternativasDas(List.of(versoes.get(1)))).isZero();
		assertThat(identidade(questao)).usingRecursiveComparison().isEqualTo(identidade);
		assertThat(linhaDaProva(prova)).usingRecursiveComparison().isEqualTo(provaAntes);
		buscarQuestao(questao).then().statusCode(200).body("versao", equalTo(1)).body("status", equalTo("PUBLICADA"))
				.body("enunciado", equalTo("Questão 1")).body("alternativas", hasSize(5));
		verificarErro(lerVersao(questao, 2), 404, "Not Found", "QUESTAO_NAO_ENCONTRADA");
		requisicao().get("/api/v1/admin/questoes?provaId=" + prova).then().body("conteudo.versao", equalTo(List.of(1)))
				.body("conteudo.status", equalTo(List.of("PUBLICADA")));

		// o numero da versão descartada é usado de novo (maior que existe + 1, Q21)
		novaVersao(questao).then().statusCode(201).body("versao", equalTo(2)).body("enunciado", equalTo("Questão 1"));
		publicarQuestao(questao).then().statusCode(200);
		assertThat(historico(questao)).containsExactly("1 ARQUIVADA", "2 PUBLICADA");
	}

	@Test
	void deve_recusar_apagar_a_questao_publicada_sem_rascunho_e_nao_mudar_nenhuma_linha() {
		List<String> ids = questaoPublicada(criarSubtopico());
		List<Object> antes = fotoDaProva(ids.get(0));

		verificarErro(apagarQuestao(ids.get(1)), 409, "Conflict", "QUESTAO_SEM_RASCUNHO");

		assertThat(fotoDaProva(ids.get(0))).usingRecursiveComparison().isEqualTo(antes);
	}

	/** D183: nenhum caminho de exclusão toca numa versão publicada ou arquivada, nem na identidade que a tem. */
	@Test
	void deve_manter_as_publicadas_e_as_arquivadas_identicas_em_toda_tentativa_de_apagar() {
		String subtopico = criarSubtopico();
		List<String> ids = questaoPublicada(subtopico);
		String prova = ids.get(0);
		String questao = ids.get(1);
		novaVersao(questao).then().statusCode(201);
		publicarQuestao(questao).then().statusCode(200);
		List<Object> intocaveis = oQueNuncaSeApaga(questao);
		assertThat(intocaveis).as("1 arquivada e 1 publicada, com 5 alternativas cada").hasSize(12);

		verificarErro(apagarQuestao(questao), 409, "Conflict", "QUESTAO_SEM_RASCUNHO");
		verificarErro(apagarProva(prova), 409, "Conflict", "PROVA_NAO_EDITAVEL");
		assertThat(oQueNuncaSeApaga(questao)).usingRecursiveComparison().isEqualTo(intocaveis);

		novaVersao(questao).then().statusCode(201).body("versao", equalTo(3));
		verificarErro(apagarProva(prova), 409, "Conflict", "PROVA_NAO_EDITAVEL");
		exigirApagado(apagarQuestao(questao));

		assertThat(oQueNuncaSeApaga(questao)).usingRecursiveComparison().isEqualTo(intocaveis);
		assertThat(historico(questao)).containsExactly("1 ARQUIVADA", "2 PUBLICADA");
		assertThat(statusDaProva(prova)).isEqualTo("PUBLICADA");
	}

	/** Estado montado à mão (a API não o produz): numa prova em rascunho, a questão que já foi publicada também fica. */
	@Test
	void deve_descartar_so_o_rascunho_da_questao_publicada_tambem_numa_prova_em_rascunho() {
		String prova = novaProva(1);
		String questao = cadastrarPronta(prova, criarSubtopico(), 1);
		publicarNoBanco(questao);
		abrirRascunhoNoBanco(questao);
		List<Map<String, Object>> publicada = fotoDaVersao(questao, 1);

		exigirApagado(apagarQuestao(questao));

		assertThat(historico(questao)).containsExactly("1 PUBLICADA");
		assertThat(fotoDaVersao(questao, 1)).isEqualTo(publicada);
		verificarErro(apagarQuestao(questao), 409, "Conflict", "QUESTAO_SEM_RASCUNHO");
	}

	/** Defensivo: numa prova publicada toda questão já foi publicada; uma que não foi, montada à mão, não se apaga. */
	@Test
	void deve_recusar_apagar_a_questao_nunca_publicada_de_uma_prova_publicada() {
		String subtopico = criarSubtopico();
		String prova = questaoPublicada(subtopico).get(0);
		long deFora = inserirQuestao(idInternoDaProva(prova), 2);
		inserirVersao(deFora, 1, "RASCUNHO", idInternoDoSubtopico(subtopico), "Montada à mão");
		String publicId = jdbc.queryForObject("SELECT BIN_TO_UUID(public_id) FROM questao WHERE id = ?", String.class, deFora);
		List<Object> antes = fotoDaProva(prova);

		verificarErro(apagarQuestao(publicId), 409, "Conflict", "PROVA_NAO_EDITAVEL");

		assertThat(fotoDaProva(prova)).usingRecursiveComparison().isEqualTo(antes);
	}

	@Test
	void deve_desfazer_a_exclusao_da_questao_inteira_quando_o_banco_falha_no_ultimo_passo() throws Exception {
		String prova = novaProva(1);
		String questao = cadastrarPronta(prova, criarSubtopico(), 1);
		List<Object> antes = fotoDaProva(prova);

		comGatilhoQueRecusaApagar("questao", idInternoDaQuestao(questao),
				() -> verificarErro(apagarQuestao(questao), 500, "Internal Server Error", "ERRO_INTERNO"));

		assertThat(fotoDaProva(prova)).usingRecursiveComparison().isEqualTo(antes);
		exigirApagado(apagarQuestao(questao));
	}

	// ----- prova -----

	@Test
	void deve_apagar_a_prova_em_rascunho_com_50_questoes_e_nada_alem_dela() {
		String subtopico = criarSubtopico();
		String outra = novaProva(1);
		importarProntas(outra, subtopico, 1, 1);
		List<Object> vizinha = fotoDaProva(outra);
		String prova = novaProva(50);
		importarProntas(prova, subtopico, 1, 50);
		long id = idInternoDaProva(prova);
		List<Long> questoes = jdbc.queryForList("SELECT id FROM questao WHERE prova_id = ?", Long.class, id);
		List<Long> versoes = jdbc.queryForList(
				"SELECT v.id FROM questao_versao v JOIN questao q ON q.id = v.questao_id WHERE q.prova_id = ?", Long.class, id);
		String umaQuestao = jdbc.queryForObject("SELECT BIN_TO_UUID(public_id) FROM questao WHERE id = ?", String.class,
				questoes.getFirst());
		assertThat(List.of(questoes.size(), versoes.size(), alternativasDas(versoes))).containsExactly(50, 50, 250);

		long inicio = System.nanoTime();
		Response resposta = apagarProva(prova);
		long milissegundos = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - inicio);

		exigirApagado(resposta);
		// informativo: sem asserção de tempo
		System.out.println("TEMPO DA EXCLUSAO de prova com 50 questoes (informativo): " + milissegundos + " ms");
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM prova WHERE id = ?", Integer.class, id)).isZero();
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM questao WHERE prova_id = ?", Integer.class, id)).isZero();
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM questao_versao WHERE id IN ("
				+ versoes.stream().map(String::valueOf).collect(Collectors.joining(",")) + ")", Integer.class)).isZero();
		assertThat(alternativasDas(versoes)).isZero();
		assertThat(fotoDaProva(outra)).usingRecursiveComparison().isEqualTo(vizinha);
		verificarErro(requisicao().get("/api/v1/provas/" + prova), 404, "Not Found", "PROVA_NAO_ENCONTRADA");
		verificarErro(buscarQuestao(umaQuestao), 404, "Not Found", "QUESTAO_NAO_ENCONTRADA");
		verificarErro(apagarProva(prova), 404, "Not Found", "PROVA_NAO_ENCONTRADA");
	}

	@Test
	void deve_apagar_a_prova_em_rascunho_sem_nenhuma_questao() {
		String prova = novaProva(5);

		exigirApagado(apagarProva(prova));

		verificarErro(requisicao().get("/api/v1/provas/" + prova), 404, "Not Found", "PROVA_NAO_ENCONTRADA");
	}

	/**
	 * Uma falha do banco no MEIO da exclusão, por um gatilho temporário: ou na questão 25 de 50 (alternativas e versões já
	 * apagadas), ou na própria prova (tudo o mais já apagado). Nos dois casos nada some.
	 */
	@ParameterizedTest
	@ValueSource(strings = { "questao", "prova" })
	void deve_desfazer_tudo_quando_a_exclusao_da_prova_falha_no_meio(String tabela) throws Exception {
		String prova = novaProva(50);
		importarProntas(prova, criarSubtopico(), 1, 50);
		long alvo = tabela.equals("prova") ? idInternoDaProva(prova)
				: jdbc.queryForObject("SELECT id FROM questao WHERE prova_id = ? AND numero = 25", Long.class,
						idInternoDaProva(prova));
		List<Object> antes = fotoDaProva(prova);

		comGatilhoQueRecusaApagar(tabela, alvo,
				() -> verificarErro(apagarProva(prova), 500, "Internal Server Error", "ERRO_INTERNO"));

		assertThat(fotoDaProva(prova)).hasSize(1 + 50 + 50 + 250).usingRecursiveComparison().isEqualTo(antes);
		exigirApagado(apagarProva(prova));
	}

	private void comGatilhoQueRecusaApagar(String tabela, long id, Runnable acao) throws SQLException {
		String gatilho = "recusar_exclusao_" + sufixo();
		comoRoot("CREATE TRIGGER " + gatilho + " BEFORE DELETE ON " + tabela + " FOR EACH ROW BEGIN IF OLD.id = " + id
				+ " THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'falha provocada pelo teste'; END IF; END");
		try {
			acao.run();
		}
		finally {
			comoRoot("DROP TRIGGER " + gatilho);
		}
	}

	@Test
	void deve_recusar_apagar_a_prova_publicada_e_nao_mudar_nenhuma_linha() {
		List<String> ids = questaoPublicada(criarSubtopico());
		List<Object> antes = fotoDaProva(ids.get(0));

		verificarErro(apagarProva(ids.get(0)), 409, "Conflict", "PROVA_NAO_EDITAVEL");

		assertThat(fotoDaProva(ids.get(0))).usingRecursiveComparison().isEqualTo(antes);
	}

	/**
	 * DEFESA DE INVARIANTE (D185): a publicação é em cascata, então a API não deixa uma prova em rascunho com questão
	 * publicada. Montado por SQL: a questão 2 publicada com rascunho novo, a 3 só arquivada.
	 */
	@Test
	void deve_recusar_apagar_a_prova_em_rascunho_com_questao_ja_publicada_listando_os_numeros() {
		String prova = novaProva(4);
		String subtopico = criarSubtopico();
		cadastrarPronta(prova, subtopico, 1);
		String dois = cadastrarPronta(prova, subtopico, 2);
		String tres = cadastrarPronta(prova, subtopico, 3);
		publicarNoBanco(dois);
		abrirRascunhoNoBanco(dois);
		publicarNoBanco(tres);
		jdbc.update("UPDATE questao_versao SET status = 'ARQUIVADA' WHERE questao_id = ?", idInternoDaQuestao(tres));
		List<Object> antes = fotoDaProva(prova);

		Response resposta = apagarProva(prova);

		verificarErro(resposta, 409, "Conflict", "PROVA_COM_QUESTOES_PUBLICADAS");
		assertThat(resposta.<List<Integer>>path("detalhes.numerosPublicados")).containsExactly(2, 3);
		assertThat(fotoDaProva(prova)).usingRecursiveComparison().isEqualTo(antes);
	}

}
