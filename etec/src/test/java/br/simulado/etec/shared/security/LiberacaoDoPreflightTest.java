package br.simulado.etec.shared.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.web.DefaultSecurityFilterChain;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.AnyRequestMatcher;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.filter.CorsFilter;

import jakarta.servlet.Filter;

/**
 * A liberação do preflight na AUTORIZAÇÃO, separada do {@code .cors()}. Em funcionamento normal o {@code CorsFilter} responde
 * todo preflight antes da autorização, então a falta da liberação do OPTIONS passaria despercebida (um cobre a falta do
 * outro). Aqui a cadeia real é montada SEM o {@code CorsFilter}: o preflight de verdade tem de passar pela autorização
 * sozinho (D100), e o OPTIONS comum, sem cabeçalhos de preflight, continua negado.
 */
@SpringBootTest
class LiberacaoDoPreflightTest {

	@Autowired
	private WebApplicationContext contexto;

	@Autowired
	private List<SecurityFilterChain> cadeias;

	private MockMvc mvcSemCorsFilter() {
		List<Filter> semCors = cadeias.get(0).getFilters().stream().filter(f -> !(f instanceof CorsFilter)).toList();
		assertThat(semCors).as("o CorsFilter existia na cadeia real").hasSize(cadeias.get(0).getFilters().size() - 1);
		FilterChainProxy proxy = new FilterChainProxy(new DefaultSecurityFilterChain(AnyRequestMatcher.INSTANCE, semCors));
		return MockMvcBuilders.webAppContextSetup(contexto).addFilters(proxy).build();
	}

	@Test
	void deve_liberar_o_preflight_de_verdade_na_autorizacao_mesmo_sem_o_cors_filter_na_cadeia() throws Exception {
		MockMvc mvc = mvcSemCorsFilter();

		for (String caminho : new String[] { "/api/v1/materias", "/api/v1/me", "/api/v1/materias/abc", "/api/v1/rota-qualquer" }) {
			int status = mvc.perform(options(caminho).header("Origin", "https://app.exemplo.invalid")
					.header("Access-Control-Request-Method", "POST")).andReturn().getResponse().getStatus();

			assertThat(status).as(caminho).isNotIn(401, 403);
		}
	}

	@Test
	void deve_continuar_negando_o_options_comum_sem_os_cabecalhos_de_preflight_mesmo_sem_o_cors_filter() throws Exception {
		MockMvc mvc = mvcSemCorsFilter();

		assertThat(mvc.perform(options("/api/v1/materias")).andReturn().getResponse().getStatus()).isEqualTo(401);
		assertThat(mvc.perform(options("/api/v1/materias").header("Origin", "https://app.exemplo.invalid")).andReturn()
				.getResponse().getStatus()).isEqualTo(401);
		assertThat(mvc.perform(options("/api/v1/materias").header("Access-Control-Request-Method", "POST")).andReturn()
				.getResponse().getStatus()).isEqualTo(401);
	}

}
