package br.simulado.etec.shared.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

import br.simulado.etec.integracao.AbstractIntegrationTest;

/**
 * Erros e cabeçalhos pelo SERVIDOR REAL (HTTP de verdade, Tomcat embutido, MySQL real): nenhum tipo de erro que o
 * servidor produz vaza pilha, nome de classe, versão do servidor nem o caminho pedido, e todas as respostas levam os
 * cabeçalhos de segurança (D127). O 405 e o 406 não passam por aqui: o servidor real os produz do mesmo jeito que o
 * MockMvc, então ficam cobertos nos testes de contrato (MockMvc).
 */
class CaminhosSensiveisVazamentoTest extends AbstractIntegrationTest {

	/** Pedaços de texto que denunciam pilha, framework, classe ou versão do servidor. */
	private static final List<String> DENUNCIAS = List.of("Exception", "Tomcat", "Apache", "springframework", "java.", "javax.",
			"jakarta.", "br.simulado", "\tat ", "stackTrace", "\"trace\"", "org.hibernate", "com.mysql", "Whitelabel",
			"Caused by");

	private static void semVazamento(Response resposta, String contexto) {
		String corpo = resposta.asString();
		assertThat(corpo).as("corpo de %s", contexto).doesNotContain(DENUNCIAS.toArray(String[]::new));
		assertThat(resposta.header("Server")).as("Server de %s", contexto).satisfiesAnyOf(
				s -> assertThat(s).isNull(), s -> assertThat(s).doesNotContainIgnoringCase("tomcat").doesNotContainPattern("\\d"));
		assertThat(resposta.header("X-Powered-By")).as("X-Powered-By de %s", contexto).isNull();
	}

	private static void comCabecalhosDeSeguranca(Response resposta, String contexto) {
		assertThat(resposta.header("X-Content-Type-Options")).as("nosniff em %s", contexto).isEqualTo("nosniff");
		assertThat(resposta.header("X-Frame-Options")).as("X-Frame-Options em %s", contexto).isEqualTo("DENY");
	}

	// ----- erros do aplicativo (passam pela cadeia de segurança e pelo tratador) -----

	@Test
	void deve_nao_vazar_nada_nem_perder_cabecalhos_em_cada_erro_que_o_servidor_real_produz() {
		String id = UUID.randomUUID().toString();
		record Erro(String nome, int status, RequestSpecification req, String metodo, String caminho) {
		}
		List<Erro> erros = List.of(
				new Erro("401 sem token", 401, requisicaoSemToken(), "GET", "/api/v1/materias"),
				new Erro("401 token lixo", 401, requisicaoCom("lixo"), "GET", "/api/v1/materias"),
				new Erro("403 papel sem permissão", 403, requisicaoComoStudent().body("{\"nome\":\"x\"}"), "POST",
						"/api/v1/materias"),
				new Erro("404 recurso inexistente", 404, requisicao(), "GET", "/api/v1/materias/" + id),
				new Erro("404 rota inexistente (ADMIN)", 403, requisicao(), "GET", "/api/v1/nao-existe"),
				new Erro("400 id malformado", 400, requisicao(), "GET", "/api/v1/materias/abc"),
				new Erro("400 corpo inválido", 400, requisicao().body("{}"), "POST", "/api/v1/materias"),
				new Erro("400 JSON quebrado", 400, requisicao().body("{ isto não é json"), "POST", "/api/v1/materias"),
				new Erro("415 tipo de conteúdo", 415, requisicao().contentType("text/plain").body("x"), "POST",
						"/api/v1/materias"));

		for (Erro erro : erros) {
			Response r = erro.req().when().request(erro.metodo(), erro.caminho()).then().extract().response();

			assertThat(r.statusCode()).as(erro.nome()).isEqualTo(erro.status());
			semVazamento(r, erro.nome());
			comCabecalhosDeSeguranca(r, erro.nome());
		}
	}

	/**
	 * O container (Tomcat) responde 404 a /WEB-INF e /META-INF antes da cadeia de segurança, no formato padrão do Boot
	 * (timestamp, status, error, path). No Boot 4.2 o campo {@code path} é sempre incluído (não existe propriedade que o
	 * tire): ele devolve o caminho que o próprio cliente pediu, em JSON com nosniff. O que se prova aqui é que NADA além
	 * disso aparece: sem mensagem, exceção, pilha, classe nem versão.
	 */
	@Test
	void deve_responder_404_so_com_os_quatro_campos_padrao_no_web_inf_e_meta_inf() {
		for (String caminho : List.of("/WEB-INF/web.xml", "/META-INF/MANIFEST.MF", "/web-inf/web.xml")) {
			Response r = requisicaoSemToken().when().get(caminho).then().extract().response();

			assertThat(r.statusCode()).as(caminho).isEqualTo(404);
			assertThat(r.contentType()).as(caminho).startsWith("application/json");
			assertThat(r.jsonPath().getMap("$").keySet()).as("campos do corpo de " + caminho)
					.containsExactlyInAnyOrder("timestamp", "status", "error", "path");
			assertThat(r.jsonPath().getString("error")).isEqualTo("Not Found");
			assertThat(r.jsonPath().getString("path")).as("só o que o cliente pediu").isEqualTo(caminho);
			semVazamento(r, caminho);
			// Resposta do container, antes da cadeia: os cabeçalhos de segurança do Spring Security NÃO vêm aqui (seção 7).
			assertThat(r.header("X-Content-Type-Options")).as("limite conhecido do container em " + caminho).isNull();
		}
	}

	@Test
	void deve_nao_vazar_nada_quando_o_container_recusa_caminho_com_barra_invertida_ou_barra_codificada() {
		for (String caminho : List.of("/api/v1/materias/%5Cabc", "/api/v1/materias%2Fabc", "/api/v1/%2e%2e/%2e%2e/etc/passwd",
				"/api/v1/materias/..%5C..%5Cx")) {
			Response r = requisicaoSemToken().urlEncodingEnabled(false).when().get(caminho).then().extract().response();

			assertThat(r.statusCode()).as(caminho).isIn(400, 401, 404);
			semVazamento(r, caminho);
		}
	}

	@Test
	void deve_nao_expor_cabecalho_de_versao_nem_x_powered_by_em_resposta_de_sucesso() {
		Response r = requisicaoSemToken().when().get("/api/v1/termos/vigente").then().statusCode(200).extract().response();

		semVazamento(r, "200 termos");
		comCabecalhosDeSeguranca(r, "200 termos");
	}

}
