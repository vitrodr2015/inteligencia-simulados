package br.simulado.etec.shared.security.limite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import br.simulado.etec.shared.exception.LimiteExcedidoException;
import br.simulado.etec.shared.security.SecurityErrorWriter;
import br.simulado.etec.shared.security.limite.RateLimitProperties.Regra;

class LimiteDeOrigemFilterTest {

	private static final Regra REGRA = new Regra(120, Duration.ofMinutes(1));

	private final LimiteDeTentativas limite = mock(LimiteDeTentativas.class);

	private final SecurityErrorWriter escritor = mock(SecurityErrorWriter.class);

	private LimiteDeOrigemFilter filtro;

	@BeforeEach
	void montar() {
		RateLimitProperties props = new RateLimitProperties(REGRA, new Regra(5, Duration.ofMinutes(15)),
				new Regra(50, Duration.ofMinutes(15)), false);
		filtro = new LimiteDeOrigemFilter(limite, props, new OrigemDaRequisicao(), escritor);
	}

	private MockHttpServletRequest requisicao(String metodo, String caminho) {
		MockHttpServletRequest requisicao = new MockHttpServletRequest(metodo, caminho);
		requisicao.setRemoteAddr("203.0.113.7");
		return requisicao;
	}

	@ParameterizedTest
	@ValueSource(strings = { "/api/v1/auth/register", "/api/v1/auth/signin", "/api/v1/auth/refresh",
			"/api/v1/auth/logout" })
	void deve_contar_na_origem_toda_requisicao_post_das_quatro_rotas(String caminho) throws Exception {
		MockFilterChain cadeia = new MockFilterChain();

		filtro.doFilter(requisicao("POST", caminho), new MockHttpServletResponse(), cadeia);

		verify(limite).consumir(Escopo.AUTH_ORIGEM, "v4:203.0.113.7", REGRA);
		assertThat(cadeia.getRequest()).as("a requisição seguiu").isNotNull();
	}

	@Test
	void deve_nao_contar_o_preflight_de_cors_nem_outros_metodos_nem_outras_rotas() throws Exception {
		MockHttpServletRequest preflight = requisicao("OPTIONS", "/api/v1/auth/signin");
		preflight.addHeader("Origin", "https://app.exemplo.invalid");
		preflight.addHeader("Access-Control-Request-Method", "POST");

		filtro.doFilter(preflight, new MockHttpServletResponse(), new MockFilterChain());
		filtro.doFilter(requisicao("GET", "/api/v1/auth/signin"), new MockHttpServletResponse(), new MockFilterChain());
		filtro.doFilter(requisicao("POST", "/api/v1/materias"), new MockHttpServletResponse(), new MockFilterChain());
		filtro.doFilter(requisicao("POST", "/api/v1/auth/signin/"), new MockHttpServletResponse(), new MockFilterChain());
		filtro.doFilter(requisicao("GET", "/api/v1/termos/vigente"), new MockHttpServletResponse(), new MockFilterChain());

		verifyNoInteractions(limite);
	}

	@Test
	void deve_responder_429_e_nao_deixar_a_requisicao_seguir_quando_o_limite_estoura() throws Exception {
		doThrow(new LimiteExcedidoException(42)).when(limite).consumir(any(), any(), any());
		MockHttpServletResponse resposta = new MockHttpServletResponse();
		MockFilterChain cadeia = new MockFilterChain();

		filtro.doFilter(requisicao("POST", "/api/v1/auth/signin"), resposta, cadeia);

		verify(escritor).limiteExcedido(resposta, 42);
		assertThat(cadeia.getRequest()).as("a cadeia NÃO foi chamada").isNull();
	}

	@Test
	void deve_responder_500_generico_e_nao_deixar_passar_quando_o_contador_falha() throws Exception {
		doThrow(new IllegalStateException("banco fora")).when(limite).consumir(any(), any(), any());
		MockHttpServletResponse resposta = new MockHttpServletResponse();
		MockFilterChain cadeia = new MockFilterChain();

		filtro.doFilter(requisicao("POST", "/api/v1/auth/signin"), resposta, cadeia);

		verify(escritor).erroInterno(resposta);
		verify(escritor, never()).limiteExcedido(eq(resposta), org.mockito.ArgumentMatchers.anyLong());
		assertThat(cadeia.getRequest()).as("sem contador não há limite: nada passa").isNull();
	}

}
