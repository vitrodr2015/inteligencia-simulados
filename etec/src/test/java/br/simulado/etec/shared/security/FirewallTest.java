package br.simulado.etec.shared.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import br.simulado.etec.shared.security.JwtTokenProvider.TipoToken;

/**
 * O firewall do Spring Security recusa URLs malformadas ou perigosas ANTES das regras. Em vez da resposta crua do
 * container, sai 400 REQUISICAO_INVALIDA em ErrorResponse (D124), com mensagem fixa e sem nenhum detalhe técnico.
 * HTTP de verdade (o MockMvc normaliza a URL), H2, sem Docker.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class FirewallTest {

	/** Usuário de exemplo do data.sql de teste (H2), papel STUDENT. */
	private static final UUID USUARIO_DE_EXEMPLO = UUID.fromString("11111111-1111-1111-1111-111111111111");

	@LocalServerPort
	private int porta;

	@Autowired
	private JwtTokenProvider provedorDeTokens;

	private HttpResponse<String> get(String caminhoCru, String token, String accept) throws Exception {
		HttpRequest.Builder requisicao = HttpRequest.newBuilder(URI.create("http://localhost:" + porta + caminhoCru))
				.header("Accept", accept);
		if (token != null) {
			requisicao.header("Authorization", "Bearer " + token);
		}
		return HttpClient.newHttpClient().send(requisicao.build(), HttpResponse.BodyHandlers.ofString());
	}

	private String token() {
		return provedorDeTokens.emitir(TipoToken.ACCESS, USUARIO_DE_EXEMPLO, "STUDENT", 0).valor();
	}

	@ParameterizedTest
	@ValueSource(strings = { "/api/v1//materias", "/api/v1/materias;jsessionid=ABC123", "/api/v1/materias/%2e%2e/me",
			"/api/v1/materias/%2E", "/api/v1/materias/%252e" })
	void deve_devolver_400_requisicao_invalida_em_error_response_para_url_recusada_pelo_firewall(String caminho)
			throws Exception {
		for (String token : new String[] { null, token() }) {
			HttpResponse<String> resposta = get(caminho, token, "application/json");

			assertThat(resposta.statusCode()).as(caminho).isEqualTo(400);
			assertThat(resposta.headers().firstValue("Content-Type")).as(caminho).hasValue("application/json");
			assertThat(resposta.body()).as(caminho).contains("\"status\":400").contains("\"erro\":\"Bad Request\"")
					.contains("\"codigo\":\"REQUISICAO_INVALIDA\"").contains("\"mensagem\":\"A requisição é inválida.\"")
					.doesNotContain("campos").doesNotContain("detalhes");
		}
	}

	/**
	 * LIMITAÇÃO conhecida: a barra invertida e a barra codificada ({@code %2F}) são recusadas pelo próprio Tomcat, ANTES
	 * da cadeia de segurança, com a página de erro do container (400, text/html), que não é o {@code ErrorResponse}.
	 * O que se prova aqui é que continua 400 e que nada técnico vaza.
	 */
	@ParameterizedTest
	@ValueSource(strings = { "/api/v1/materias%5Cx", "/api/v1/materias%2F..%2Fme" })
	void deve_continuar_devolvendo_400_sem_vazar_detalhe_quando_o_proprio_container_recusa_a_url(String caminho)
			throws Exception {
		HttpResponse<String> resposta = get(caminho, null, "application/json");

		assertThat(resposta.statusCode()).as(caminho).isEqualTo(400);
		assertThat(resposta.body()).as(caminho).doesNotContain("Exception").doesNotContain("org.springframework")
				.doesNotContain("\tat ").doesNotContain("jsessionid");
	}

	@Test
	void deve_nunca_vazar_detalhe_tecnico_nem_a_url_na_resposta_do_firewall() throws Exception {
		HttpResponse<String> resposta = get("/api/v1/materias;jsessionid=SEGREDO-NA-URL", null, "application/json");

		assertThat(resposta.statusCode()).isEqualTo(400);
		assertThat(resposta.body()).doesNotContain("SEGREDO-NA-URL").doesNotContain("jsessionid")
				.doesNotContain("RequestRejected").doesNotContain("firewall").doesNotContain("Firewall")
				.doesNotContain("org.springframework").doesNotContain("\tat ");
	}

	@Test
	void deve_responder_em_json_mesmo_com_accept_de_xml_ou_yaml() throws Exception {
		for (String tipo : new String[] { "application/xml", "application/yaml", "text/html" }) {
			HttpResponse<String> resposta = get("/api/v1//materias", null, tipo);

			assertThat(resposta.statusCode()).as(tipo).isEqualTo(400);
			assertThat(resposta.headers().firstValue("Content-Type")).as(tipo).hasValue("application/json");
			assertThat(resposta.body()).as(tipo).contains("\"codigo\":\"REQUISICAO_INVALIDA\"");
		}
	}

	@Test
	void deve_seguir_atendendo_normalmente_as_urls_validas_e_negar_a_barra_final_sem_regra() throws Exception {
		assertThat(get("/api/v1/materias", token(), "application/json").statusCode()).isEqualTo(200);
		assertThat(get("/api/v1/termos/vigente", null, "application/json").statusCode()).isEqualTo(200);
		// Barra no fim não é recusada pelo firewall: simplesmente não tem regra (D121).
		assertThat(get("/api/v1/materias/", null, "application/json").statusCode()).isEqualTo(401);
		assertThat(get("/api/v1/materias/", token(), "application/json").statusCode()).isEqualTo(403);
	}

}
