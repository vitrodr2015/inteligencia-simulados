package br.simulado.etec.shared.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

import br.simulado.etec.catalogo.service.MateriaService;
import br.simulado.etec.shared.security.JwtTokenProvider.TipoToken;

/**
 * Um erro 500 REAL (exceção inesperada no service) com a cadeia de segurança LIGADA e por HTTP de verdade, numa rota
 * que TEM regra ({@code GET /api/v1/materias}): continua 500, no {@code ErrorResponse} genérico, sem pilha nem texto da
 * exceção, e não vira 401 nem 403 pela cadeia. O {@code /error} pedido direto continua 401 (anônimo) e 403
 * (autenticado), D113. H2, sem Docker.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(Erro500ComSegurancaTest.ServicoQueFalha.class)
class Erro500ComSegurancaTest {

	/** Usuário de exemplo do data.sql de teste (H2), papel STUDENT. */
	private static final UUID USUARIO_DE_EXEMPLO = UUID.fromString("11111111-1111-1111-1111-111111111111");

	private static final String TEXTO_SENSIVEL = "SELECT senha_hash FROM usuario -- br.simulado.etec.usuarios.model.Usuario";

	/** Um MateriaService que lança uma exceção inesperada em qualquer método, qualquer que seja a assinatura. */
	@TestConfiguration(proxyBeanMethods = false)
	static class ServicoQueFalha {

		@Bean
		@Primary
		MateriaService materiaServiceQueFalha() {
			return mock(MateriaService.class, invocacao -> {
				throw new IllegalStateException(TEXTO_SENSIVEL);
			});
		}

	}

	@LocalServerPort
	private int porta;

	@Autowired
	private JwtTokenProvider provedorDeTokens;

	private HttpResponse<String> get(String caminho, String token) throws Exception {
		HttpRequest.Builder requisicao = HttpRequest.newBuilder(URI.create("http://localhost:" + porta + caminho))
				.header("Accept", "application/json");
		if (token != null) {
			requisicao.header("Authorization", "Bearer " + token);
		}
		return HttpClient.newHttpClient().send(requisicao.build(), HttpResponse.BodyHandlers.ofString());
	}

	private String token() {
		return provedorDeTokens.emitir(TipoToken.ACCESS, USUARIO_DE_EXEMPLO, "STUDENT", 0).valor();
	}

	@Test
	void deve_devolver_500_em_error_response_generico_quando_um_erro_real_acontece_com_a_seguranca_ligada()
			throws Exception {
		HttpResponse<String> resposta = get("/api/v1/materias", token());

		assertThat(resposta.statusCode()).isEqualTo(500);
		assertThat(resposta.headers().firstValue("Content-Type")).hasValue("application/json");
		assertThat(resposta.body()).contains("\"status\":500").contains("\"erro\":\"Internal Server Error\"")
				.contains("\"codigo\":\"ERRO_INTERNO\"").contains("Ocorreu um erro interno.")
				.doesNotContain("SELECT").doesNotContain("senha_hash").doesNotContain("br.simulado")
				.doesNotContain("IllegalState").doesNotContain("\tat ").doesNotContain("campos");
	}

	@Test
	void deve_dar_401_e_nao_500_quando_o_anonimo_chama_a_rota_que_falha() throws Exception {
		HttpResponse<String> resposta = get("/api/v1/materias", null);

		assertThat(resposta.statusCode()).isEqualTo(401);
		assertThat(resposta.body()).contains("\"codigo\":\"NAO_AUTENTICADO\"").doesNotContain("SELECT");
	}

	@Test
	void deve_dar_401_ao_anonimo_e_403_a_quem_esta_autenticado_no_error_pedido_direto() throws Exception {
		HttpResponse<String> anonimo = get("/error", null);
		HttpResponse<String> autenticado = get("/error", token());

		assertThat(anonimo.statusCode()).isEqualTo(401);
		assertThat(anonimo.body()).contains("\"codigo\":\"NAO_AUTENTICADO\"");
		assertThat(autenticado.statusCode()).isEqualTo(403);
		assertThat(autenticado.body()).contains("\"codigo\":\"ACESSO_NEGADO\"");
		assertThat(autenticado.headers().firstValue("Content-Type")).hasValue("application/json");
	}

}
