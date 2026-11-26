package br.simulado.etec.catalogo.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;

import br.simulado.etec.integracao.AbstractIntegrationTest;
import io.restassured.response.Response;

/** Ajudantes HTTP e de banco da Questão para os testes de integração (mesma configuração do Spring da base). */
abstract class AbstractQuestaoIntegrationTest extends AbstractIntegrationTest {

	protected static final String DATA_HORA = "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}Z";

	/** Uma prova nova (rascunho) com a quantidade dada; devolve o id público. */
	protected String criarProva(int quantidade) {
		return requisicao().body("{\"titulo\":\"Prova " + sufixo() + "\",\"duracaoMinutos\":180,\"quantidadeQuestoes\":" + quantidade + "}")
				.post("/api/v1/admin/provas").then().statusCode(201).extract().path("id");
	}

	/** Matéria nova + subtópico novo no programa ETEC; devolve o id público do subtópico. */
	protected String criarSubtopico() {
		String materia = requisicao().body("{\"nome\":\"" + nomeUnico("Matéria da questão") + "\"}").post("/api/v1/materias").then()
				.statusCode(201).extract().path("id");
		return requisicao().body("{\"materiaId\":\"" + materia + "\",\"nome\":\"" + nomeUnico("Subtópico da questão") + "\"}")
				.post("/api/v1/subtopicos").then().statusCode(201).extract().path("id");
	}

	protected static String alternativas(String... textos) {
		StringBuilder json = new StringBuilder("[");
		String letras = "ABCDE";
		for (int i = 0; i < textos.length; i++) {
			json.append(i > 0 ? "," : "").append("{\"letra\":\"").append(letras.charAt(i)).append("\",\"texto\":\"").append(textos[i])
					.append("\"}");
		}
		return json.append("]").toString();
	}

	protected static String corpo(String subtopicoId, int numero, String enunciado) {
		return "{\"numero\":" + numero + ",\"subtopicoId\":\"" + subtopicoId + "\",\"enunciado\":\"" + enunciado
				+ "\",\"alternativas\":" + alternativas("um", "dois", "três", "quatro", "cinco") + "}";
	}

	protected Response cadastrar(String provaId, String corpo) {
		return requisicao().body(corpo).post("/api/v1/admin/provas/" + provaId + "/questoes");
	}

	protected Response buscarQuestao(String id) {
		return requisicao().get("/api/v1/admin/questoes/" + id);
	}

	// ----- banco direto -----

	protected long idInternoDaProva(String provaPublicId) {
		return jdbc.queryForObject("SELECT id FROM prova WHERE public_id = UUID_TO_BIN(?)", Long.class, provaPublicId);
	}

	protected long idInternoDoSubtopico(String subtopicoPublicId) {
		return jdbc.queryForObject("SELECT id FROM subtopico WHERE public_id = UUID_TO_BIN(?)", Long.class, subtopicoPublicId);
	}

	protected long idInternoDaQuestao(String questaoPublicId) {
		return jdbc.queryForObject("SELECT id FROM questao WHERE public_id = UUID_TO_BIN(?)", Long.class, questaoPublicId);
	}

	/** Insere uma identidade de questão direto no banco e devolve o id interno. */
	protected long inserirQuestao(long provaId, int numero, String tipo) {
		jdbc.update("INSERT INTO questao (public_id, prova_id, numero, tipo, hash_conteudo, criado_em, atualizado_em) "
				+ "VALUES (UNHEX(REPLACE(UUID(), '-', '')), ?, ?, ?, ?, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))", provaId, numero, tipo,
				"a".repeat(64));
		return jdbc.queryForObject("SELECT id FROM questao WHERE prova_id = ? AND numero = ?", Long.class, provaId, numero);
	}

	protected long inserirQuestao(long provaId, int numero) {
		return inserirQuestao(provaId, numero, "MULTIPLA_ESCOLHA");
	}

	/** Insere uma versão direto no banco, com o enunciado e o status dados (o resto, válido), e devolve o id interno. */
	protected long inserirVersao(long questaoId, int versao, String status, long subtopicoId, String enunciado) {
		return inserirVersaoCompleta(questaoId, versao, status, subtopicoId, null, null, enunciado, null, null, null);
	}

	protected long inserirVersaoCompleta(long questaoId, int versao, String status, long subtopicoId, String textoApoio,
			Object grupoApoio, String enunciado, String gabarito, String dificuldade, String explicacao) {
		jdbc.update("INSERT INTO questao_versao (questao_id, versao, status, subtopico_id, texto_apoio, grupo_apoio, enunciado, "
				+ "gabarito, dificuldade, explicacao, criado_em, atualizado_em) "
				+ "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))", questaoId, versao, status, subtopicoId,
				textoApoio, grupoApoio, enunciado, gabarito, dificuldade, explicacao);
		return jdbc.queryForObject("SELECT id FROM questao_versao WHERE questao_id = ? AND versao = ?", Long.class, questaoId, versao);
	}

	protected int inserirAlternativa(long versaoId, String letra, String texto) {
		return jdbc.update("INSERT INTO alternativa (questao_versao_id, letra, texto, criado_em, atualizado_em) "
				+ "VALUES (?, ?, ?, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))", versaoId, letra, texto);
	}

	/** Formato de erro completo (seção 1 do contrato): dataHora com 3 casas, status, erro, codigo e mensagem. */
	protected static void verificarErro(Response resposta, int status, String erro, String codigo) {
		resposta.then().statusCode(status).contentType(containsString("application/json"))
				.body("dataHora", matchesPattern(DATA_HORA)).body("status", equalTo(status)).body("erro", equalTo(erro))
				.body("codigo", equalTo(codigo)).body("mensagem", not(emptyOrNullString()));
	}

}
