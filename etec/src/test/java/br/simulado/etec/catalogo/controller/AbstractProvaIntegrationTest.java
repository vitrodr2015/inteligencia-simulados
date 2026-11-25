package br.simulado.etec.catalogo.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import br.simulado.etec.integracao.AbstractIntegrationTest;
import io.restassured.response.Response;

/** Ajudantes HTTP e de banco da Prova para os testes de integração (mesma configuração do Spring da base). */
abstract class AbstractProvaIntegrationTest extends AbstractIntegrationTest {

	protected static final String CRIAR = "/api/v1/admin/provas";

	protected static final String BASE = "/api/v1/provas";

	protected static final String DATA_HORA = "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}Z";

	protected static String texto(String valor) {
		return (valor == null) ? "null" : "\"" + valor.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
	}

	protected static String json(String titulo, String banca, Object ano, Object semestre, Object duracao, Object quantidade) {
		return "{\"titulo\":" + texto(titulo) + ",\"banca\":" + texto(banca) + ",\"ano\":" + ano + ",\"semestre\":" + semestre
				+ ",\"duracaoMinutos\":" + duracao + ",\"quantidadeQuestoes\":" + quantidade + "}";
	}

	/** Prova válida com os campos de identidade dados (nulos = omitidos). */
	protected static String corpo(String titulo, String banca, Integer ano, Integer semestre) {
		return json(titulo, banca, ano, semestre, 180, 50);
	}

	protected Response criar(String corpo) {
		return requisicao().body(corpo).post(CRIAR);
	}

	/** Cria e confere o 201; devolve o id. */
	protected String criarEObterId(String corpo) {
		Response resposta = criar(corpo);
		resposta.then().statusCode(201);
		return resposta.path("id");
	}

	protected Response buscar(String id) {
		return requisicao().get(BASE + "/" + id);
	}

	/** Banca única, para a tripla de identidade da prova (D150) não colidir com a de outro teste. */
	protected static String bancaUnica() {
		return "Banca " + sufixo();
	}

	/** Todas as páginas da listagem com o sort dado (nulo = padrão), na ordem em que a API devolve. */
	@SuppressWarnings("unchecked")
	protected List<Map<String, Object>> listarTodas(String sort) {
		List<Map<String, Object>> todas = new ArrayList<>();
		int pagina = 0;
		int totalPaginas;
		do {
			var requisicao = requisicao().queryParam("size", 50).queryParam("page", pagina);
			if (sort != null) {
				requisicao = requisicao.queryParam("sort", sort);
			}
			Response resposta = requisicao.get(BASE);
			resposta.then().statusCode(200);
			todas.addAll((List<Map<String, Object>>) (List<?>) resposta.path("conteudo"));
			totalPaginas = resposta.path("totalPaginas");
			pagina++;
		}
		while (pagina < totalPaginas);
		return todas;
	}

	/** Insere direto no banco (JDBC), sem passar pela API: o único jeito de criar uma linha PUBLICADA nesta fatia. */
	protected int inserirNoBanco(String titulo, String banca, Object ano, Object semestre, Object duracao, Object quantidade,
			String status, String publicadaEm) {
		return jdbc.update("INSERT INTO prova (public_id, programa_id, titulo, banca, ano, semestre, duracao_minutos, "
				+ "quantidade_questoes, fonte_url, status, publicada_em, criado_em, atualizado_em) "
				+ "VALUES (UNHEX(REPLACE(UUID(), '-', '')), (SELECT id FROM programa WHERE codigo = 'ETEC'), ?, ?, ?, ?, ?, ?, "
				+ "NULL, ?, ?, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))", titulo, banca, ano, semestre, duracao, quantidade, status,
				publicadaEm);
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
