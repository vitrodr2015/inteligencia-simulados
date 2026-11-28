package br.simulado.etec.catalogo.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import br.simulado.etec.usuarios.model.Papel;
import io.restassured.response.Response;

/** A publicação da prova em cascata (D178) contra MySQL REAL: o fluxo inteiro, o tudo ou nada e o que fica fechado depois. */
class PublicacaoIntegracaoTest extends AbstractPublicacaoIntegrationTest {

	private static final int ETEC = 50;

	@Test
	void deve_publicar_uma_prova_de_50_questoes_de_ponta_a_ponta() {
		String prova = novaProva(ETEC);
		String subtopico = criarSubtopico();
		importar(lote(prova, IntStream.rangeClosed(1, ETEC).mapToObj(n -> questaoComGabarito(subtopico, n, null))
				.collect(Collectors.toList()))).then().statusCode(200).body("questoesCriadas", equalTo(ETEC));
		gabarito(prova, IntStream.rangeClosed(1, ETEC).mapToObj(n -> item(n, String.valueOf("ABCDE".charAt(n % 5))))
				.toArray(String[]::new)).then().statusCode(200).body("atualizadas", equalTo(ETEC));

		long antes = System.nanoTime();
		Response publicacao = publicar(prova);
		long milissegundos = (System.nanoTime() - antes) / 1_000_000;
		System.out.println("TEMPO DA PUBLICACAO de " + ETEC + " questoes (informativo): " + milissegundos + " ms");

		publicacao.then().statusCode(200).body("id", equalTo(prova)).body("status", equalTo("PUBLICADA"))
				.body("publicadaEm", notNullValue()).body("questoesCadastradas", equalTo(ETEC));
		assertThat(versoesPorStatus(prova)).containsExactly("PUBLICADA=50");
		exigirSemEstadoParcial(prova);
		requisicao().get("/api/v1/admin/questoes?provaId=" + prova + "&status=PUBLICADA&size=50").then().statusCode(200)
				.body("totalElementos", equalTo(ETEC)).body("conteudo", hasSize(ETEC))
				.body("conteudo.status", everyItem(equalTo("PUBLICADA")));
		requisicao().get("/api/v1/admin/questoes?provaId=" + prova + "&status=RASCUNHO").then().body("totalElementos", equalTo(0));
		requisicao().get("/api/v1/provas/" + prova).then().body("publicadaEm", equalTo(publicacao.path("publicadaEm")));
	}

	// ----- tudo ou nada -----

	@Test
	void deve_nao_publicar_nada_quando_a_questao_50_esta_sem_gabarito() {
		String prova = novaProva(ETEC);
		String subtopico = criarSubtopico();
		importarProntas(prova, subtopico, 1, ETEC - 1);
		cadastrar(prova, questaoComGabarito(subtopico, ETEC, null)).then().statusCode(201);
		List<Object> antes = fotoDaProva(prova);

		assertThat(pendencias(publicar(prova))).containsExactly("50 SEM_GABARITO");

		assertThat(fotoDaProva(prova)).usingRecursiveComparison().isEqualTo(antes);
		exigirSemEstadoParcial(prova);
	}

	@Test
	void deve_nao_publicar_nada_quando_o_subtopico_da_questao_50_esta_inativo() {
		String prova = novaProva(ETEC);
		importarProntas(prova, criarSubtopico(), 1, ETEC - 1);
		String doUltimo = criarSubtopico();
		cadastrar(prova, questaoComGabarito(doUltimo, ETEC, "A")).then().statusCode(201);
		requisicao().patch("/api/v1/subtopicos/" + doUltimo + "/disable").then().statusCode(200);
		List<Object> antes = fotoDaProva(prova);

		assertThat(pendencias(publicar(prova))).containsExactly("50 SUBTOPICO_INATIVO");

		assertThat(fotoDaProva(prova)).usingRecursiveComparison().isEqualTo(antes);
		exigirSemEstadoParcial(prova);
		requisicao().patch("/api/v1/subtopicos/" + doUltimo + "/enable").then().statusCode(200);
		publicar(prova).then().statusCode(200);
	}

	@Test
	void deve_contar_a_materia_desativada_como_subtopico_inativo() {
		String prova = novaProva(1);
		String subtopico = criarSubtopico();
		importarProntas(prova, subtopico, 1, 1);
		String materia = requisicao().get("/api/v1/subtopicos/" + subtopico).then().extract().path("materiaId");
		requisicao().patch("/api/v1/materias/" + materia + "/disable").then().statusCode(200);

		assertThat(pendencias(publicar(prova))).containsExactly("1 SUBTOPICO_INATIVO");
	}

	@Test
	void deve_listar_todas_as_pendencias_de_todas_as_questoes_num_unico_409() {
		String prova = novaProva(7);
		String bom = criarSubtopico();
		String inativo = criarSubtopico();
		cadastrar(prova, questaoComGabarito(bom, 1, "A")).then().statusCode(201);
		cadastrar(prova, questaoComGabarito(bom, 3, null)).then().statusCode(201);
		cadastrar(prova, questaoComGabarito(inativo, 4, null)).then().statusCode(201);
		requisicao().patch("/api/v1/subtopicos/" + inativo + "/disable").then().statusCode(200);
		String comImagem = cadastrar(prova, comImagem(bom, 5)).then().statusCode(201).extract().path("id");
		trocarImagemNoBanco(comImagem, "https://fora.exemplo.invalid/a.png");
		String publicadaSemRascunho = cadastrar(prova, questaoComGabarito(bom, 6, "A")).then().statusCode(201).extract().path("id");
		publicarNoBanco(publicadaSemRascunho);
		String deOutro = cadastrar(prova, questaoComGabarito(bom, 7, "A")).then().statusCode(201).extract().path("id");

		comSubtopicoDeOutroPrograma(deOutro, bom, () -> assertThat(pendencias(publicar(prova))).containsExactly(
				"2 QUESTAO_AUSENTE", "3 SEM_GABARITO", "4 SEM_GABARITO", "4 SUBTOPICO_INATIVO", "5 IMAGEM_NAO_PERMITIDA",
				"6 QUESTAO_SEM_RASCUNHO", "7 SUBTOPICO_DE_OUTRO_PROGRAMA"));

		assertThat(statusDaProva(prova)).isEqualTo("RASCUNHO");
		assertThat(versoesPorStatus(prova)).containsExactly("PUBLICADA=1", "RASCUNHO=5");
	}

	@Test
	void deve_revalidar_o_host_da_imagem_contra_a_configuracao_atual() {
		String prova = novaProva(1);
		String questao = cadastrar(prova, comImagem(criarSubtopico(), 1)).then().statusCode(201).extract().path("id");
		// como se o host tivesse saído da lista depois de a questão ser gravada
		trocarImagemNoBanco(questao, "https://saiu-da-lista.exemplo.invalid/a.png");

		assertThat(pendencias(publicar(prova))).containsExactly("1 IMAGEM_NAO_PERMITIDA");
		exigirSemEstadoParcial(prova);

		trocarImagemNoBanco(questao, "https://LOCALHOST/a.png");
		publicar(prova).then().statusCode(200);
	}

	private static String comImagem(String subtopico, int numero) {
		String corpo = questaoComGabarito(subtopico, numero, "A");
		return corpo.substring(0, corpo.length() - 1) + ",\"imagemUrl\":\"https://imagens.exemplo.invalid/a.png\"}";
	}

	private void trocarImagemNoBanco(String questao, String url) {
		assertThat(jdbc.update("UPDATE questao_versao SET imagem_url = ? WHERE questao_id = ?", url, idInternoDaQuestao(questao)))
				.isEqualTo(1);
	}

	/**
	 * Põe a questão num subtópico de um segundo programa (a API não cria programa nem deixa isso acontecer), roda a ação e
	 * desfaz tudo: o banco é COMPARTILHADO e o ProgramaIntegracaoTest exige só a ETEC na tabela.
	 */
	private void comSubtopicoDeOutroPrograma(String questao, String subtopicoOriginal, Runnable acao) {
		String codigo = "ENEM" + sufixo().substring(0, 4).toUpperCase().replaceAll("[^A-Z0-9]", "X");
		jdbc.update("INSERT INTO programa (public_id, codigo, nome, criado_em, atualizado_em) "
				+ "VALUES (UNHEX(REPLACE(UUID(), '-', '')), ?, 'Outro', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))", codigo);
		long programa = jdbc.queryForObject("SELECT id FROM programa WHERE codigo = ?", Long.class, codigo);
		jdbc.update("INSERT INTO materia (public_id, programa_id, nome, ativo, criado_em, atualizado_em) "
				+ "VALUES (UNHEX(REPLACE(UUID(), '-', '')), ?, ?, TRUE, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))", programa,
				nomeUnico("Matéria de outro programa"));
		long materia = jdbc.queryForObject("SELECT MAX(id) FROM materia WHERE programa_id = ?", Long.class, programa);
		jdbc.update("INSERT INTO subtopico (public_id, materia_id, nome, ativo, criado_em, atualizado_em) "
				+ "VALUES (UNHEX(REPLACE(UUID(), '-', '')), ?, ?, TRUE, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))", materia,
				nomeUnico("Subtópico de outro programa"));
		long deFora = jdbc.queryForObject("SELECT id FROM subtopico WHERE materia_id = ?", Long.class, materia);
		long versoes = idInternoDaQuestao(questao);
		try {
			jdbc.update("UPDATE questao_versao SET subtopico_id = ? WHERE questao_id = ?", deFora, versoes);
			acao.run();
		}
		finally {
			jdbc.update("UPDATE questao_versao SET subtopico_id = ? WHERE questao_id = ?",
					idInternoDoSubtopico(subtopicoOriginal), versoes);
			jdbc.update("DELETE FROM subtopico WHERE materia_id = ?", materia);
			jdbc.update("DELETE FROM materia WHERE id = ?", materia);
			jdbc.update("DELETE FROM programa WHERE id = ?", programa);
		}
	}

	// ----- o que fica fechado depois -----

	@Test
	void deve_recusar_toda_escrita_depois_de_publicada_e_nao_mudar_nenhuma_linha() {
		String prova = novaProva(2);
		String subtopico = criarSubtopico();
		String um = cadastrar(prova, questaoComGabarito(subtopico, 1, "A")).then().statusCode(201).extract().path("id");
		cadastrar(prova, questaoComGabarito(subtopico, 2, "B")).then().statusCode(201);
		publicar(prova).then().statusCode(200);
		List<Object> publicada = fotoDaProva(prova);

		Map<String, Response> escritas = Map.of(
				"PUT da prova", editarProva(prova, corpoDaProva("Nova", null, null, null, 2)),
				"cadastro da mesma questão", cadastrar(prova, questaoComGabarito(subtopico, 1, "A")),
				"cadastro de outro conteúdo", cadastrar(prova, corpo(subtopico, 1, "Outro")),
				"gabarito em lote", gabarito(prova, item(1, "E")),
				"importação", importar(lote(prova, List.of(questaoComGabarito(subtopico, 1, "A")))),
				"publicar de novo", publicar(prova));

		escritas.forEach((nome, resposta) -> {
			assertThat(resposta.statusCode()).as(nome).isEqualTo(409);
			assertThat((String) resposta.path("codigo")).as(nome).isEqualTo("PROVA_NAO_EDITAVEL");
		});
		// o PUT de questão é decidido só pela versão (D171): depois de publicada não há rascunho
		verificarErro(editarQuestao(um, corpoDoPut(subtopico, "Depois", "")), 409, "Conflict", "QUESTAO_SEM_RASCUNHO");
		assertThat(fotoDaProva(prova)).usingRecursiveComparison().isEqualTo(publicada);
	}

	@Test
	void deve_deixar_o_aluno_ver_so_o_resumo_da_prova_publicada_sem_as_questoes_e_sem_poder_publicar() {
		String prova = novaProva(1);
		importarProntas(prova, criarSubtopico(), 1, 1);
		requisicaoComoStudent().get("/api/v1/provas/" + prova).then().statusCode(404);
		assertThat(idsDe(listarTodasComo(Papel.STUDENT, Map.of()))).doesNotContain(prova);
		publicar(prova).then().statusCode(200);

		Response detalhe = requisicaoComoStudent().get("/api/v1/provas/" + prova);
		detalhe.then().statusCode(200).body("id", equalTo(prova));
		assertThat(detalhe.jsonPath().getMap("$").keySet()).map(Object::toString).containsExactlyElementsOf(CAMPOS_DO_ALUNO);
		assertThat(listarTodasComo(Papel.STUDENT, Map.of())).filteredOn(item -> prova.equals(item.get("id"))).singleElement()
				.satisfies(item -> assertThat(item.keySet()).containsExactlyElementsOf(CAMPOS_DO_ALUNO));

		requisicaoComoStudent().get("/api/v1/admin/questoes?provaId=" + prova).then().statusCode(403);
		requisicaoComoStudent().patch("/api/v1/provas/" + prova + "/publish").then().statusCode(403);
		for (String caminho : new String[] { "/api/v1/provas", "/api/v1/provas/" + prova }) {
			requisicaoComoGuardian().get(caminho).then().statusCode(403);
		}
	}

	/**
	 * Defensivo: numa prova em rascunho a API não deixa uma questão ter publicada E rascunho. Se tiver, a publicada vira
	 * ARQUIVADA antes de o rascunho ser publicado; só o MySQL real prova que isso não viola o índice de "uma publicada".
	 */
	@Test
	void deve_arquivar_a_publicada_antiga_sem_violar_o_indice_unico_ao_publicar_o_rascunho_novo() {
		String prova = novaProva(1);
		String questao = cadastrar(prova, questaoComGabarito(criarSubtopico(), 1, "A")).then().statusCode(201).extract()
				.path("id");
		publicarNoBanco(questao);
		abrirRascunhoNoBanco(questao);
		Map<String, Object> antiga = jdbc.queryForMap("SELECT publicada_em, enunciado, gabarito FROM questao_versao "
				+ "WHERE questao_id = ? AND versao = 1", idInternoDaQuestao(questao));

		publicar(prova).then().statusCode(200);

		assertThat(versoesPorStatus(prova)).containsExactly("ARQUIVADA=1", "PUBLICADA=1");
		assertThat(jdbc.queryForMap("SELECT publicada_em, enunciado, gabarito FROM questao_versao WHERE questao_id = ? "
				+ "AND versao = 1", idInternoDaQuestao(questao))).isEqualTo(antiga);
		buscarQuestao(questao).then().body("versao", equalTo(2)).body("status", equalTo("PUBLICADA"));
		requisicao().get("/api/v1/admin/questoes/" + questao + "?versao=1").then().statusCode(200)
				.body("status", equalTo("ARQUIVADA"));
	}

	/**
	 * A cascata percorre só os numeros de 1 a N. Um numero acima de N a API não deixa existir (o cadastro e o PUT da prova
	 * impedem); montado por SQL, a cascata o IGNORA: não confere (ele não tem gabarito nem alternativas), não publica e não toca.
	 */
	@Test
	void deve_ignorar_na_cascata_a_questao_com_numero_acima_da_quantidade_da_prova() {
		String prova = novaProva(1);
		String subtopico = criarSubtopico();
		importarProntas(prova, subtopico, 1, 1);
		long deFora = inserirQuestao(idInternoDaProva(prova), 2);
		inserirVersao(deFora, 1, "RASCUNHO", idInternoDoSubtopico(subtopico), "Fora de 1..N");
		Map<String, Object> antes = jdbc.queryForMap("SELECT * FROM questao_versao WHERE questao_id = ?", deFora);

		publicar(prova).then().statusCode(200);

		assertThat(jdbc.queryForMap("SELECT * FROM questao_versao WHERE questao_id = ?", deFora)).isEqualTo(antes);
		assertThat(versoesPorStatus(prova)).containsExactly("PUBLICADA=1", "RASCUNHO=1");
	}

}
