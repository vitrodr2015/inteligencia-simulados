package br.simulado.etec.catalogo.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import io.restassured.response.Response;
import jakarta.persistence.EntityManagerFactory;

/**
 * A listagem de questões do ADMIN e o contador {@code questoesCadastradas} contra MySQL REAL. As versões ARQUIVADA, PUBLICADA e o
 * rascunho são inseridas por SQL direto, em cada teste, para montar qualquer combinação de versões sem depender das rotas de
 * publicação e de nova versão (inclusive combinações que elas não produzem), e TUDO o que o teste insere é apagado no fim
 * ({@link #apagarOQueOTesteCriou()}). Como o banco é compartilhado, as listas sem filtro são conferidas pela sequência inteira
 * ou só pelos itens do próprio teste.
 */
class QuestaoListagemIntegracaoTest extends AbstractQuestaoIntegrationTest {

	private static final String LISTA = "/api/v1/admin/questoes";

	@Autowired
	private EntityManagerFactory fabrica;

	private final List<String> provasDoTeste = new ArrayList<>();

	private String novaProva() {
		String prova = criarProva(300);
		provasDoTeste.add(prova);
		return prova;
	}

	/** Todo dado de questão do teste (inclusive o inserido por SQL) sai do banco compartilhado. */
	@AfterEach
	void apagarOQueOTesteCriou() {
		for (String prova : provasDoTeste) {
			long id = idInternoDaProva(prova);
			jdbc.update("DELETE a FROM alternativa a JOIN questao_versao v ON v.id = a.questao_versao_id "
					+ "JOIN questao q ON q.id = v.questao_id WHERE q.prova_id = ?", id);
			jdbc.update("DELETE v FROM questao_versao v JOIN questao q ON q.id = v.questao_id WHERE q.prova_id = ?", id);
			jdbc.update("DELETE FROM questao WHERE prova_id = ?", id);
			jdbc.update("DELETE FROM prova WHERE id = ?", id);
		}
		provasDoTeste.clear();
	}

	// ----- apoio -----

	private String cadastrarEObterId(String prova, String subtopico, int numero) {
		return cadastrar(prova, corpo(subtopico, numero, "Enunciado " + numero)).then().statusCode(201).extract().path("id");
	}

	/** Versão com 5 alternativas, por SQL direto, com o enunciado marcando de qual versão é. */
	private void inserirVersaoComAlternativas(long questao, int versao, String status, long subtopico) {
		long id = inserirVersao(questao, versao, status, subtopico, "versao " + versao + " " + status);
		for (String letra : new String[] { "A", "B", "C", "D", "E" }) {
			inserirAlternativa(id, letra, letra + " da versao " + versao);
		}
	}

	private Response listar(Object... paresDeFiltro) {
		var requisicao = requisicao().queryParam("size", 50);
		for (int i = 0; i < paresDeFiltro.length; i += 2) {
			requisicao = requisicao.queryParam((String) paresDeFiltro[i], paresDeFiltro[i + 1]);
		}
		Response resposta = requisicao.get(LISTA);
		resposta.then().statusCode(200);
		return resposta;
	}

	private String idPublico(long questaoInterna) {
		return jdbc.queryForObject("SELECT BIN_TO_UUID(public_id) FROM questao WHERE id = ?", String.class, questaoInterna);
	}

	// ----- só a versão mais recente (D19, D56) -----

	@Test
	void deve_mostrar_so_o_rascunho_aberto_quando_a_questao_tem_arquivada_publicada_e_rascunho() {
		String prova = novaProva();
		long subtopico = idInternoDoSubtopico(criarSubtopico());
		long questao = inserirQuestao(idInternoDaProva(prova), 1);
		inserirVersaoComAlternativas(questao, 1, "ARQUIVADA", subtopico);
		inserirVersaoComAlternativas(questao, 2, "PUBLICADA", subtopico);
		inserirVersaoComAlternativas(questao, 3, "RASCUNHO", subtopico);

		Response resposta = listar("provaId", prova);

		resposta.then().body("totalElementos", equalTo(1)).body("conteudo", hasSize(1))
				.body("conteudo[0].id", equalTo(idPublico(questao))).body("conteudo[0].versao", equalTo(3))
				.body("conteudo[0].status", equalTo("RASCUNHO")).body("conteudo[0].enunciado", equalTo("versao 3 RASCUNHO"))
				.body("conteudo[0].alternativas", hasSize(5))
				.body("conteudo[0].alternativas.letra", contains("A", "B", "C", "D", "E"))
				.body("conteudo[0].alternativas[0].texto", equalTo("A da versao 3"));
	}

	@Test
	void deve_mostrar_a_publicada_quando_nao_ha_rascunho_e_nao_listar_a_questao_que_so_tem_arquivadas() {
		String prova = novaProva();
		long subtopico = idInternoDoSubtopico(criarSubtopico());
		long publicada = inserirQuestao(idInternoDaProva(prova), 1);
		inserirVersaoComAlternativas(publicada, 1, "ARQUIVADA", subtopico);
		inserirVersaoComAlternativas(publicada, 2, "PUBLICADA", subtopico);
		long soArquivada = inserirQuestao(idInternoDaProva(prova), 2);
		inserirVersaoComAlternativas(soArquivada, 1, "ARQUIVADA", subtopico);

		Response resposta = listar("provaId", prova);

		resposta.then().body("totalElementos", equalTo(1)).body("conteudo[0].id", equalTo(idPublico(publicada)))
				.body("conteudo[0].versao", equalTo(2)).body("conteudo[0].status", equalTo("PUBLICADA"));
	}

	@Test
	void deve_aplicar_o_filtro_de_status_a_versao_exibida_e_nao_a_qualquer_versao() {
		String prova = novaProva();
		long subtopico = idInternoDoSubtopico(criarSubtopico());
		long comRascunho = inserirQuestao(idInternoDaProva(prova), 1);
		inserirVersaoComAlternativas(comRascunho, 1, "PUBLICADA", subtopico);
		inserirVersaoComAlternativas(comRascunho, 2, "RASCUNHO", subtopico);
		long soPublicada = inserirQuestao(idInternoDaProva(prova), 2);
		inserirVersaoComAlternativas(soPublicada, 1, "PUBLICADA", subtopico);

		Response rascunhos = listar("provaId", prova, "status", "RASCUNHO");
		Response publicadas = listar("provaId", prova, "status", "PUBLICADA");

		rascunhos.then().body("totalElementos", equalTo(1)).body("conteudo[0].id", equalTo(idPublico(comRascunho)))
				.body("conteudo[0].versao", equalTo(2));
		// a questão com rascunho aberto TEM uma publicada, mas o que ela exibe é o rascunho: fora do filtro PUBLICADA
		publicadas.then().body("totalElementos", equalTo(1)).body("conteudo[0].id", equalTo(idPublico(soPublicada)));
	}

	@Test
	void deve_filtrar_o_subtopico_pela_versao_exibida() {
		String prova = novaProva();
		String antigo = criarSubtopico();
		String novo = criarSubtopico();
		long questao = inserirQuestao(idInternoDaProva(prova), 1);
		inserirVersaoComAlternativas(questao, 1, "PUBLICADA", idInternoDoSubtopico(antigo));
		inserirVersaoComAlternativas(questao, 2, "RASCUNHO", idInternoDoSubtopico(novo));

		listar("subtopicoId", novo).then().body("totalElementos", equalTo(1)).body("conteudo[0].subtopicoId", equalTo(novo));
		listar("subtopicoId", antigo).then().body("totalElementos", equalTo(0));
	}

	// ----- várias provas e várias questões, filtros combinados, ordem -----

	@Test
	void deve_listar_varias_provas_com_filtros_combinados_em_ordem_de_numero() {
		String provaA = novaProva();
		String provaB = novaProva();
		String algebra = criarSubtopico();
		String geometria = criarSubtopico();
		String a3 = cadastrarEObterId(provaA, algebra, 3);
		String a1 = cadastrarEObterId(provaA, geometria, 1);
		String a2 = cadastrarEObterId(provaA, algebra, 2);
		String b1 = cadastrarEObterId(provaB, algebra, 1);

		assertThat(listar("provaId", provaA).<List<String>>path("conteudo.id")).containsExactly(a1, a2, a3);
		assertThat(listar("provaId", provaB).<List<String>>path("conteudo.id")).containsExactly(b1);
		// o subtópico filtra entre provas: numero 1 (da B), 2 e 3 (da A), em ordem de numero
		assertThat(listar("subtopicoId", algebra).<List<String>>path("conteudo.id")).containsExactly(b1, a2, a3);
		assertThat(listar("provaId", provaA, "subtopicoId", algebra).<List<String>>path("conteudo.id")).containsExactly(a2, a3);
		assertThat(listar("provaId", provaA, "subtopicoId", algebra, "status", "RASCUNHO").<List<String>>path("conteudo.id"))
				.containsExactly(a2, a3);
		listar("provaId", provaA, "subtopicoId", algebra, "status", "PUBLICADA").then().body("totalElementos", equalTo(0));
		listar("provaId", provaB, "subtopicoId", geometria).then().body("totalElementos", equalTo(0));
	}

	@Test
	void deve_desempatar_o_mesmo_numero_pelo_id_interno_na_ordem_de_criacao() {
		String subtopico = criarSubtopico();
		String primeiraProva = novaProva();
		String segundaProva = novaProva();
		String terceiraProva = novaProva();
		String primeira = cadastrarEObterId(primeiraProva, subtopico, 5);
		String segunda = cadastrarEObterId(segundaProva, subtopico, 5);
		String terceira = cadastrarEObterId(terceiraProva, subtopico, 5);

		Response resposta = listar("subtopicoId", subtopico);

		assertThat(resposta.<List<String>>path("conteudo.id")).containsExactly(primeira, segunda, terceira);
	}

	@Test
	void deve_paginar_sem_repetir_nem_perder_questoes_na_lista_sem_filtro() {
		String prova = novaProva();
		String subtopico = criarSubtopico();
		for (int numero = 1; numero <= 9; numero++) {
			cadastrarEObterId(prova, subtopico, numero);
		}

		List<String> ids = new ArrayList<>();
		List<Integer> numeros = new ArrayList<>();
		int total = requisicao().get(LISTA).path("totalElementos");
		int pagina = 0;
		List<Map<String, Object>> conteudo;
		do {
			conteudo = requisicao().queryParam("size", 4).queryParam("page", pagina++).get(LISTA).path("conteudo");
			conteudo.forEach(q -> {
				ids.add((String) q.get("id"));
				numeros.add((Integer) q.get("numero"));
			});
		}
		while (!conteudo.isEmpty());

		assertThat(ids).hasSize(total).doesNotHaveDuplicates();
		assertThat(numeros).as("a lista inteira está em ordem de numero crescente").isSorted();
	}

	@Test
	void deve_devolver_a_mesma_questao_completa_do_get_por_id() {
		String prova = novaProva();
		String id = cadastrar(prova, "{\"numero\":4,\"subtopicoId\":\"" + criarSubtopico() + "\",\"textoApoio\":\"Apoio\","
				+ "\"grupoApoio\":2,\"enunciado\":\"Qual?\",\"alternativas\":" + alternativas("a", "b", "c", "d", "e")
				+ ",\"gabarito\":\"B\",\"dificuldade\":\"FACIL\",\"explicacao\":\"Porque\"}").then().statusCode(201).extract()
				.path("id");

		Map<String, Object> daLista = listar("provaId", prova).path("conteudo[0]");
		Map<String, Object> porId = buscarQuestao(id).then().statusCode(200).extract().path("$");

		assertThat(daLista).isEqualTo(porId);
		assertThat(listar("provaId", prova).asString()).doesNotContain("programa").doesNotContain("\"tipo\"");
	}

	// ----- questoesCadastradas -----

	private Map<String, Integer> contagensNaListagemDeProvas() {
		Map<String, Integer> contagens = new java.util.HashMap<>();
		int pagina = 0;
		int totalPaginas;
		do {
			Response resposta = requisicao().queryParam("size", 50).queryParam("page", pagina++).get("/api/v1/provas");
			resposta.then().statusCode(200);
			List<Map<String, Object>> provas = resposta.path("conteudo");
			provas.forEach(p -> contagens.put((String) p.get("id"), (Integer) p.get("questoesCadastradas")));
			totalPaginas = resposta.path("totalPaginas");
		}
		while (pagina < totalPaginas);
		return contagens;
	}

	@Test
	void deve_contar_so_as_questoes_de_cada_prova_na_listagem_e_no_get_por_id() {
		String comTres = novaProva();
		String comUma = novaProva();
		String semNenhuma = novaProva();
		String subtopico = criarSubtopico();
		cadastrarEObterId(comTres, subtopico, 1);
		cadastrarEObterId(comTres, subtopico, 2);
		cadastrarEObterId(comTres, subtopico, 3);
		cadastrarEObterId(comUma, subtopico, 1);

		Map<String, Integer> contagens = contagensNaListagemDeProvas();

		assertThat(contagens).containsEntry(comTres, 3).containsEntry(comUma, 1).containsEntry(semNenhuma, 0);
		requisicao().get("/api/v1/provas/" + comTres).then().body("questoesCadastradas", equalTo(3));
		requisicao().get("/api/v1/provas/" + comUma).then().body("questoesCadastradas", equalTo(1));
		requisicao().get("/api/v1/provas/" + semNenhuma).then().body("questoesCadastradas", equalTo(0));
	}

	@Test
	void deve_contar_identidades_e_nao_versoes_em_qualquer_status() {
		String prova = novaProva();
		long subtopico = idInternoDoSubtopico(criarSubtopico());
		long comTresVersoes = inserirQuestao(idInternoDaProva(prova), 1);
		inserirVersaoComAlternativas(comTresVersoes, 1, "ARQUIVADA", subtopico);
		inserirVersaoComAlternativas(comTresVersoes, 2, "PUBLICADA", subtopico);
		inserirVersaoComAlternativas(comTresVersoes, 3, "RASCUNHO", subtopico);
		long soArquivada = inserirQuestao(idInternoDaProva(prova), 2);
		inserirVersaoComAlternativas(soArquivada, 1, "ARQUIVADA", subtopico);

		// 2 questões (4 versões): a arquivada conta ("em qualquer status"), embora não apareça na listagem de questões
		requisicao().get("/api/v1/provas/" + prova).then().body("questoesCadastradas", equalTo(2));
		assertThat(contagensNaListagemDeProvas()).containsEntry(prova, 2);
		listar("provaId", prova).then().body("totalElementos", equalTo(1));
	}

	@Test
	void deve_devolver_zero_na_criacao_da_prova() {
		Response criada = requisicao().body("{\"titulo\":\"Nova\",\"duracaoMinutos\":60,\"quantidadeQuestoes\":10}")
				.post("/api/v1/admin/provas");
		criada.then().statusCode(201).body("questoesCadastradas", equalTo(0));
		provasDoTeste.add(criada.path("id"));
	}

	// ----- sem N+1 -----

	/**
	 * Quantas instruções SQL o Hibernate preparou durante a requisição (inclusive a leitura do usuário pelo filtro de segurança). As
	 * estatísticas são ligadas só durante a medição, no mesmo SessionFactory do contexto compartilhado, sem mudar a configuração dele.
	 */
	private long instrucoesDe(Runnable requisicaoHttp) {
		Statistics estatisticas = fabrica.unwrap(SessionFactory.class).getStatistics();
		boolean antes = estatisticas.isStatisticsEnabled();
		estatisticas.setStatisticsEnabled(true);
		estatisticas.clear();
		try {
			requisicaoHttp.run();
			return estatisticas.getPrepareStatementCount();
		}
		finally {
			estatisticas.setStatisticsEnabled(antes);
		}
	}

	@Test
	void deve_usar_o_mesmo_numero_de_instrucoes_com_1_ou_com_6_questoes_na_pagina_da_listagem() {
		String provaDeUma = novaProva();
		cadastrarEObterId(provaDeUma, criarSubtopico(), 1);
		String provaDeSeis = novaProva();
		for (int numero = 1; numero <= 6; numero++) {
			// um subtópico DIFERENTE por questão: sem a carga em lote, cada um viraria um select a mais
			cadastrarEObterId(provaDeSeis, criarSubtopico(), numero);
		}

		// as duas páginas vêm CHEIAS (size igual ao total), então as duas fazem a consulta de contagem
		long comUma = instrucoesDe(() -> requisicao().queryParam("provaId", provaDeUma).queryParam("size", 1).get(LISTA).then()
				.statusCode(200).body("conteudo", hasSize(1)));
		long comSeis = instrucoesDe(() -> requisicao().queryParam("provaId", provaDeSeis).queryParam("size", 6).get(LISTA).then()
				.statusCode(200).body("conteudo", hasSize(6)));

		assertThat(comSeis).as("instruções para 6 questões x para 1").isEqualTo(comUma);
		assertThat(comSeis).as("usuário do token, ids, contagem e carga").isLessThanOrEqualTo(4);
	}

	@Test
	void deve_usar_o_mesmo_numero_de_instrucoes_com_questoes_de_provas_diferentes_na_pagina() {
		for (int i = 0; i < 6; i++) {
			cadastrarEObterId(novaProva(), criarSubtopico(), 1);
		}

		long comUma = instrucoesDe(() -> requisicao().queryParam("size", 1).get(LISTA).then().statusCode(200)
				.body("conteudo", hasSize(1)));
		long comSeis = instrucoesDe(() -> requisicao().queryParam("size", 6).get(LISTA).then().statusCode(200)
				.body("conteudo", hasSize(6)));

		assertThat(comSeis).isEqualTo(comUma);
	}

	@Test
	void deve_contar_as_questoes_da_listagem_de_provas_com_o_mesmo_numero_de_instrucoes_com_1_ou_com_6_provas() {
		String subtopico = criarSubtopico();
		for (int i = 0; i < 6; i++) {
			cadastrarEObterId(novaProva(), subtopico, 1);
		}

		long comUma = instrucoesDe(() -> requisicao().queryParam("size", 1).get("/api/v1/provas").then().statusCode(200)
				.body("conteudo", hasSize(1)));
		long comSeis = instrucoesDe(() -> requisicao().queryParam("size", 6).get("/api/v1/provas").then().statusCode(200)
				.body("conteudo", hasSize(6)));

		assertThat(comSeis).as("uma consulta agrupada, não uma por prova").isEqualTo(comUma);
		assertThat(comSeis).as("usuário do token, página, contagem da página e contagem agrupada").isLessThanOrEqualTo(4);
	}

}
