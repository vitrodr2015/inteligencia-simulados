package br.simulado.etec.catalogo.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;

import br.simulado.etec.integracao.AbstractIntegrationTest;
import io.restassured.response.Response;

/** Ajudantes HTTP da Matéria para os testes de integração (mesma configuração do Spring da base). */
abstract class AbstractMateriaIntegrationTest extends AbstractIntegrationTest {

	protected static final String BASE = "/api/v1/materias";

	protected static final String DATA_HORA = "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}Z";

	protected static String json(String nome, String descricao) {
		return "{\"nome\":" + texto(nome) + ",\"descricao\":" + texto(descricao) + "}";
	}

	private static String texto(String valor) {
		return (valor == null) ? "null" : "\"" + valor.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
	}

	protected Response criar(String nome, String descricao) {
		return requisicao().body(json(nome, descricao)).post(BASE);
	}

	/** Cria e confere o 201; devolve o id. */
	protected String criarEObterId(String nome) {
		Response resposta = criar(nome, "Descrição de " + nome);
		resposta.then().statusCode(201);
		return resposta.path("id");
	}

	protected Response buscar(String id) {
		return requisicao().get(BASE + "/" + id);
	}

	protected Response atualizar(String id, String nome, String descricao) {
		return requisicao().body(json(nome, descricao)).put(BASE + "/" + id);
	}

	protected Response desativar(String id) {
		return requisicao().patch(BASE + "/" + id + "/disable");
	}

	protected Response ativar(String id) {
		return requisicao().patch(BASE + "/" + id + "/enable");
	}

	protected Response apagar(String id) {
		return requisicao().delete(BASE + "/" + id);
	}

	/** Formato de erro completo (seção 1 do contrato): dataHora com 3 casas, status, erro, codigo e mensagem. */
	protected static void verificarErro(Response resposta, int status, String erro, String codigo) {
		resposta.then()
				.statusCode(status)
				.contentType(containsString("application/json"))
				.body("dataHora", matchesPattern(DATA_HORA))
				.body("status", equalTo(status))
				.body("erro", equalTo(erro))
				.body("codigo", equalTo(codigo))
				.body("mensagem", not(emptyOrNullString()));
	}

}
