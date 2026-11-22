package br.simulado.etec.catalogo.controller;

import java.util.Map;

import io.restassured.response.Response;

/**
 * Ajudantes HTTP do Subtópico para os testes de integração (MySQL real). Reaproveita os da Matéria: um teste
 * de subtópico sempre começa criando a própria matéria, com nome único, para não depender de ordem nem de limpeza.
 */
abstract class AbstractSubtopicoIntegrationTest extends AbstractMateriaIntegrationTest {

	protected static final String BASE_SUBTOPICOS = "/api/v1/subtopicos";

	/** Cria uma matéria ativa com nome único e devolve o id. */
	protected String novaMateria() {
		return criarEObterId(nomeUnico("Matéria de subtópicos"));
	}

	protected static String jsonSubtopico(String materiaId, String nome) {
		String materia = (materiaId == null) ? "" : "\"materiaId\":\"" + materiaId + "\",";
		return "{" + materia + "\"nome\":" + texto(nome) + "}";
	}

	private static String texto(String valor) {
		return (valor == null) ? "null"
				: "\"" + valor.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\t", "\\t") + "\"";
	}

	protected Response criarSubtopico(String materiaId, String nome) {
		return requisicao().body(jsonSubtopico(materiaId, nome)).post(BASE_SUBTOPICOS);
	}

	/** Cria e confere o 201; devolve o id. */
	protected String criarSubtopicoEObterId(String materiaId, String nome) {
		Response resposta = criarSubtopico(materiaId, nome);
		resposta.then().statusCode(201);
		return resposta.path("id");
	}

	protected Response buscarSubtopico(String id) {
		return requisicao().get(BASE_SUBTOPICOS + "/" + id);
	}

	/** PUT com materiaId opcional ({@code null} = não envia o campo). */
	protected Response atualizarSubtopico(String id, String materiaId, String nome) {
		return requisicao().body(jsonSubtopico(materiaId, nome)).put(BASE_SUBTOPICOS + "/" + id);
	}

	protected Response desativarSubtopico(String id) {
		return requisicao().patch(BASE_SUBTOPICOS + "/" + id + "/disable");
	}

	protected Response ativarSubtopico(String id) {
		return requisicao().patch(BASE_SUBTOPICOS + "/" + id + "/enable");
	}

	protected Response apagarSubtopico(String id) {
		return requisicao().delete(BASE_SUBTOPICOS + "/" + id);
	}

	protected Response listarSubtopicos(Map<String, ?> parametros) {
		return requisicao().queryParams(parametros).get(BASE_SUBTOPICOS);
	}

	protected static void verificarErroComCampo(Response resposta, String campo) {
		verificarErro(resposta, 400, "Bad Request", "VALIDACAO_FALHOU");
		resposta.then().body("campos.campo", org.hamcrest.Matchers.hasItem(campo));
	}

}
