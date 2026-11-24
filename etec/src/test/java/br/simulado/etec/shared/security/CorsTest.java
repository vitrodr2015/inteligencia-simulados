package br.simulado.etec.shared.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import br.simulado.etec.shared.security.JwtTokenProvider.TipoToken;

/**
 * CORS de ponta a ponta (HTTP de verdade, filtros LIGADOS, H2): origem permitida passa e a não permitida é barrada;
 * os 401 e 403 também levam os cabeçalhos de CORS (senão o navegador esconde a mensagem). Origens do teste: uma https e
 * uma http (fora de produção).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = "app.cors.allowed-origins=https://app.exemplo.invalid,http://localhost:5173")
class CorsTest {

	private static final String PERMITIDA = "https://app.exemplo.invalid";

	private static final String OUTRA_PERMITIDA = "http://localhost:5173";

	private static final String NAO_PERMITIDA = "https://invasor.exemplo.invalid";

	/** Usuário de exemplo do data.sql de teste (H2), papel STUDENT. */
	private static final UUID USUARIO_DE_EXEMPLO = UUID.fromString("11111111-1111-1111-1111-111111111111");

	@LocalServerPort
	private int porta;

	@Autowired
	private JwtTokenProvider provedorDeTokens;

	private HttpResponse<String> enviar(String metodo, String caminho, String origem, String token, String... cabecalhos)
			throws Exception {
		HttpRequest.Builder requisicao = HttpRequest.newBuilder(URI.create("http://localhost:" + porta + caminho))
				.header("Accept", "application/json").method(metodo, HttpRequest.BodyPublishers.noBody());
		if (origem != null) {
			requisicao.header("Origin", origem);
		}
		if (token != null) {
			requisicao.header("Authorization", "Bearer " + token);
		}
		for (int i = 0; i < cabecalhos.length; i += 2) {
			requisicao.header(cabecalhos[i], cabecalhos[i + 1]);
		}
		return HttpClient.newHttpClient().send(requisicao.build(), HttpResponse.BodyHandlers.ofString());
	}

	private HttpResponse<String> preflight(String caminho, String origem, String metodoPedido, String cabecalhosPedidos)
			throws Exception {
		return enviar("OPTIONS", caminho, origem, null, "Access-Control-Request-Method", metodoPedido,
				"Access-Control-Request-Headers", cabecalhosPedidos);
	}

	private String token() {
		return provedorDeTokens.emitir(TipoToken.ACCESS, USUARIO_DE_EXEMPLO, "STUDENT", 0).valor();
	}

	private static String cabecalho(HttpResponse<?> resposta, String nome) {
		return resposta.headers().firstValue(nome).orElse(null);
	}

	// ----- TESTE 1: o .cors() está ligado (preflight de origem permitida, sem token, recebe os cabeçalhos) -----

	@Test
	void deve_responder_o_preflight_da_origem_permitida_sem_exigir_autenticacao_com_os_cabecalhos_de_cors() throws Exception {
		for (String origem : new String[] { PERMITIDA, OUTRA_PERMITIDA }) {
			for (String caminho : new String[] { "/api/v1/materias", "/api/v1/materias/" + UUID.randomUUID(), "/api/v1/me",
					"/api/v1/auth/signin", "/api/v1/rota-que-nao-existe" }) {
				HttpResponse<String> resposta = preflight(caminho, origem, "POST", "authorization,content-type");

				assertThat(resposta.statusCode()).as(origem + " " + caminho).isEqualTo(200);
				assertThat(cabecalho(resposta, "Access-Control-Allow-Origin")).isEqualTo(origem);
				assertThat(cabecalho(resposta, "Access-Control-Allow-Methods")).contains("GET", "POST", "PUT", "PATCH",
						"DELETE", "OPTIONS");
				assertThat(cabecalho(resposta, "Access-Control-Allow-Headers")).containsIgnoringCase("Authorization")
						.containsIgnoringCase("Content-Type");
				assertThat(cabecalho(resposta, "Access-Control-Max-Age")).isEqualTo("3600");
				assertThat(cabecalho(resposta, "Access-Control-Allow-Credentials")).isNull();
				assertThat(cabecalho(resposta, "Vary")).contains("Origin");
			}
		}
	}

	@Test
	void deve_aceitar_todos_os_metodos_da_lista_no_preflight() throws Exception {
		for (String metodo : new String[] { "GET", "POST", "PUT", "PATCH", "DELETE" }) {
			HttpResponse<String> resposta = preflight("/api/v1/materias", PERMITIDA, metodo, "authorization");

			assertThat(resposta.statusCode()).as(metodo).isEqualTo(200);
			assertThat(cabecalho(resposta, "Access-Control-Allow-Origin")).isEqualTo(PERMITIDA);
		}
	}

	// ----- origem não permitida é barrada, em ErrorResponse (D82) -----

	@Test
	void deve_barrar_com_403_em_error_response_o_preflight_de_origem_nao_permitida_sem_cabecalhos_de_cors() throws Exception {
		HttpResponse<String> resposta = preflight("/api/v1/materias", NAO_PERMITIDA, "POST", "authorization,content-type");

		assertThat(resposta.statusCode()).isEqualTo(403);
		assertThat(cabecalho(resposta, "Access-Control-Allow-Origin")).isNull();
		assertThat(cabecalho(resposta, "Content-Type")).isEqualTo("application/json");
		assertThat(resposta.body()).contains("\"status\":403").contains("\"erro\":\"Forbidden\"")
				.contains("\"codigo\":\"ACESSO_NEGADO\"").doesNotContain("Invalid CORS").doesNotContain("campos");
	}

	@Test
	void deve_barrar_o_preflight_de_metodo_ou_cabecalho_fora_da_lista() throws Exception {
		HttpResponse<String> metodo = preflight("/api/v1/materias", PERMITIDA, "TRACE", "authorization");
		HttpResponse<String> cabecalho = preflight("/api/v1/materias", PERMITIDA, "GET", "x-cabecalho-estranho");

		assertThat(metodo.statusCode()).isEqualTo(403);
		assertThat(cabecalho.statusCode()).isEqualTo(403);
		assertThat(metodo.body()).contains("\"codigo\":\"ACESSO_NEGADO\"");
		assertThat(cabecalho.body()).contains("\"codigo\":\"ACESSO_NEGADO\"");
	}

	@Test
	void deve_barrar_a_requisicao_real_de_origem_nao_permitida_com_403_em_error_response() throws Exception {
		HttpResponse<String> comToken = enviar("GET", "/api/v1/materias", NAO_PERMITIDA, token());
		HttpResponse<String> semToken = enviar("GET", "/api/v1/termos/vigente", NAO_PERMITIDA, null);

		for (HttpResponse<String> resposta : java.util.List.of(comToken, semToken)) {
			assertThat(resposta.statusCode()).isEqualTo(403);
			assertThat(cabecalho(resposta, "Access-Control-Allow-Origin")).isNull();
			assertThat(resposta.body()).contains("\"codigo\":\"ACESSO_NEGADO\"");
		}
	}

	@Test
	void deve_nunca_devolver_curinga_nem_credenciais() throws Exception {
		for (String origem : new String[] { PERMITIDA, NAO_PERMITIDA, "null", "*" }) {
			HttpResponse<String> resposta = preflight("/api/v1/materias", origem, "GET", "authorization");

			assertThat(cabecalho(resposta, "Access-Control-Allow-Origin")).as(origem).isNotEqualTo("*");
			assertThat(cabecalho(resposta, "Access-Control-Allow-Credentials")).as(origem).isNull();
		}
	}

	// ----- requisição real: permitida passa; 401 e 403 levam os cabeçalhos -----

	@Test
	void deve_deixar_passar_a_requisicao_real_da_origem_permitida_e_expor_o_location() throws Exception {
		HttpResponse<String> resposta = enviar("GET", "/api/v1/termos/vigente", PERMITIDA, null);

		assertThat(resposta.statusCode()).isEqualTo(200);
		assertThat(cabecalho(resposta, "Access-Control-Allow-Origin")).isEqualTo(PERMITIDA);
		assertThat(cabecalho(resposta, "Access-Control-Expose-Headers")).contains("Location");
		assertThat(cabecalho(resposta, "Access-Control-Allow-Credentials")).isNull();
	}

	@Test
	void deve_levar_os_cabecalhos_de_cors_no_401_de_quem_chama_de_uma_origem_permitida() throws Exception {
		HttpResponse<String> resposta = enviar("GET", "/api/v1/materias", PERMITIDA, null);

		assertThat(resposta.statusCode()).isEqualTo(401);
		assertThat(resposta.body()).contains("\"codigo\":\"NAO_AUTENTICADO\"");
		assertThat(cabecalho(resposta, "Access-Control-Allow-Origin")).isEqualTo(PERMITIDA);
		assertThat(cabecalho(resposta, "Access-Control-Expose-Headers")).contains("Location");
	}

	@Test
	void deve_levar_os_cabecalhos_de_cors_no_403_de_quem_chama_de_uma_origem_permitida() throws Exception {
		HttpResponse<String> resposta = enviar("DELETE", "/api/v1/materias/" + UUID.randomUUID(), OUTRA_PERMITIDA, token());

		assertThat(resposta.statusCode()).isEqualTo(403);
		assertThat(resposta.body()).contains("\"codigo\":\"ACESSO_NEGADO\"");
		assertThat(cabecalho(resposta, "Access-Control-Allow-Origin")).isEqualTo(OUTRA_PERMITIDA);
	}

	@Test
	void deve_levar_os_cabecalhos_de_cors_no_401_de_token_adulterado_e_no_404_de_recurso() throws Exception {
		HttpResponse<String> adulterado = enviar("GET", "/api/v1/me", PERMITIDA, token() + "x");
		HttpResponse<String> recurso = enviar("GET", "/api/v1/materias/" + UUID.randomUUID(), PERMITIDA, token());

		assertThat(adulterado.statusCode()).isEqualTo(401);
		assertThat(cabecalho(adulterado, "Access-Control-Allow-Origin")).isEqualTo(PERMITIDA);
		assertThat(recurso.statusCode()).isEqualTo(404);
		assertThat(cabecalho(recurso, "Access-Control-Allow-Origin")).isEqualTo(PERMITIDA);
	}

	@Test
	void deve_nao_mexer_nas_requisicoes_sem_origem_que_nao_vem_de_navegador() throws Exception {
		HttpResponse<String> publica = enviar("GET", "/api/v1/termos/vigente", null, null);
		HttpResponse<String> protegida = enviar("GET", "/api/v1/materias", null, null);

		assertThat(publica.statusCode()).isEqualTo(200);
		assertThat(cabecalho(publica, "Access-Control-Allow-Origin")).isNull();
		assertThat(protegida.statusCode()).isEqualTo(401);
	}

	// ----- OPTIONS comum (sem cabeçalhos de preflight) NÃO é liberado -----

	@Test
	void deve_negar_o_options_comum_sem_os_cabecalhos_de_preflight() throws Exception {
		assertThat(enviar("OPTIONS", "/api/v1/materias", null, null).statusCode()).isEqualTo(401);
		assertThat(enviar("OPTIONS", "/api/v1/materias", PERMITIDA, null).statusCode()).isEqualTo(401); // só Origin
		assertThat(enviar("OPTIONS", "/api/v1/materias", null, token(), "Access-Control-Request-Method", "GET")
				.statusCode()).isEqualTo(403); // só Request-Method, sem Origin: não é preflight
		assertThat(enviar("OPTIONS", "/api/v1/materias", null, token()).statusCode()).isEqualTo(403);
	}

}
