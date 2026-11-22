package br.simulado.etec.shared.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * Sem {@code app.cors.allowed-origins} (o padrão): SEM CORS. Nenhuma origem recebe cabeçalho de CORS, e o preflight de
 * verdade não é barrado pela segurança (D100). ATENÇÃO: com o {@code CorsFilter} na cadeia, ele responde todo preflight
 * antes da autorização, então ESTE teste não detecta a falta da liberação do OPTIONS na autorização; quem detecta é o
 * {@link LiberacaoDoPreflightTest}, que monta a cadeia real sem o {@code CorsFilter}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CorsSemConfiguracaoTest {

	@LocalServerPort
	private int porta;

	private HttpResponse<String> preflight(String caminho, String origem) throws Exception {
		HttpRequest requisicao = HttpRequest.newBuilder(URI.create("http://localhost:" + porta + caminho))
				.header("Origin", origem).header("Access-Control-Request-Method", "POST")
				.header("Access-Control-Request-Headers", "authorization,content-type")
				.method("OPTIONS", HttpRequest.BodyPublishers.noBody()).build();
		return HttpClient.newHttpClient().send(requisicao, HttpResponse.BodyHandlers.ofString());
	}

	@Test
	void deve_deixar_o_preflight_de_verdade_passar_pela_seguranca_mesmo_sem_cors_configurado_e_sem_cabecalhos_de_cors()
			throws Exception {
		for (String caminho : new String[] { "/api/v1/materias", "/api/v1/me", "/api/v1/materias/abc" }) {
			HttpResponse<String> resposta = preflight(caminho, "https://app.exemplo.invalid");

			// Não é 401 nem 403 da cadeia: o preflight não exige autenticação (D100).
			assertThat(resposta.statusCode()).as(caminho).isNotIn(401, 403);
			// E sem CORS configurado, nenhuma origem recebe cabeçalho de CORS: o navegador bloqueia.
			assertThat(resposta.headers().firstValue("Access-Control-Allow-Origin")).as(caminho).isEmpty();
			assertThat(resposta.headers().firstValue("Access-Control-Allow-Methods")).as(caminho).isEmpty();
		}
	}

	@Test
	void deve_nao_dar_cabecalho_de_cors_em_requisicao_real_nenhuma() throws Exception {
		HttpRequest requisicao = HttpRequest.newBuilder(URI.create("http://localhost:" + porta + "/api/v1/termos/vigente"))
				.header("Origin", "https://app.exemplo.invalid").GET().build();

		HttpResponse<String> resposta = HttpClient.newHttpClient().send(requisicao, HttpResponse.BodyHandlers.ofString());

		assertThat(resposta.statusCode()).isEqualTo(200);
		assertThat(resposta.headers().firstValue("Access-Control-Allow-Origin")).isEmpty();
	}

	@Test
	void deve_negar_o_options_comum_sem_cors_configurado() throws Exception {
		HttpRequest requisicao = HttpRequest.newBuilder(URI.create("http://localhost:" + porta + "/api/v1/materias"))
				.method("OPTIONS", HttpRequest.BodyPublishers.noBody()).build();

		assertThat(HttpClient.newHttpClient().send(requisicao, HttpResponse.BodyHandlers.ofString()).statusCode())
				.isEqualTo(401);
	}

}
