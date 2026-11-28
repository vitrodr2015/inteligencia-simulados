package br.simulado.etec.shared.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.annotation.UserConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import br.simulado.etec.shared.security.SecurityErrorWriter;
import tools.jackson.databind.json.JsonMapper;

/**
 * Validação das origens de CORS na subida (D123): vazia = sem CORS; sem curinga, sem barra nem caminho, só
 * http(s)://domínio[:porta]; em produção só https (provado com o contexto, perfil {@code prod}).
 */
class CorsPropertiesTest {

	private static List<String> origens(String... textos) {
		return new CorsProperties(List.of(textos)).allowedOrigins();
	}

	@Test
	void deve_aceitar_origens_http_e_https_com_ou_sem_porta_e_normalizar_a_caixa() {
		assertThat(origens("https://app.exemplo.invalid", "http://localhost:5173", "HTTPS://Painel.Exemplo.INVALID:8443",
				"http://127.0.0.1:3000")).containsExactly("https://app.exemplo.invalid", "http://localhost:5173",
						"https://painel.exemplo.invalid:8443", "http://127.0.0.1:3000");
	}

	@Test
	void deve_tratar_vazia_nula_ou_em_branco_como_sem_cors() {
		assertThat(new CorsProperties(null).allowedOrigins()).isEmpty();
		assertThat(new CorsProperties(List.of()).allowedOrigins()).isEmpty();
		assertThat(new CorsProperties(List.of("")).allowedOrigins()).isEmpty();
		assertThat(new CorsProperties(List.of("   ")).allowedOrigins()).isEmpty();
	}

	@Test
	void deve_tirar_repeticoes_e_espacos_das_pontas() {
		assertThat(origens(" https://a.exemplo.invalid ", "https://a.exemplo.invalid", "HTTPS://A.exemplo.invalid"))
				.containsExactly("https://a.exemplo.invalid");
	}

	@ParameterizedTest
	@ValueSource(strings = { "*", "https://*", "https://*.exemplo.invalid", "http://*:3000", "https://app.exemplo.invalid/",
			"https://app.exemplo.invalid/caminho", "https://app.exemplo.invalid?x=1", "https://app.exemplo.invalid#a",
			"https://user@app.exemplo.invalid", "app.exemplo.invalid", "//app.exemplo.invalid", "ftp://app.exemplo.invalid",
			"https://", "https://-app.exemplo.invalid", "https://app-.exemplo.invalid", "https://app..exemplo.invalid",
			"https://app.exemplo.invalid:", "https://app.exemplo.invalid:abc", "https://app.exemplo.invalid:0",
			"https://app.exemplo.invalid:65536", "https://app.exemplo.invalid:123456", "https://[::1]:3000",
			"https://app.exemplo.invalid:443/", "https://app exemplo.invalid", "null", "https://ápp.exemplo.invalid" })
	void deve_recusar_origem_fora_do_formato(String origem) {
		assertThatThrownBy(() -> new CorsProperties(List.of(origem))).as(origem)
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("app.cors.allowed-origins");
	}

	@Test
	void deve_recusar_o_curinga_misturado_a_origens_validas_e_a_entrada_vazia_no_meio() {
		assertThatThrownBy(() -> new CorsProperties(List.of("https://a.exemplo.invalid", "*")))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("curinga");
		assertThatThrownBy(() -> new CorsProperties(List.of("https://a.exemplo.invalid", "")))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("vazia");
	}

	// ----- a subida, com o perfil -----

	private ApplicationContextRunner contexto(String... perfis) {
		return new ApplicationContextRunner()
				.withInitializer(c -> c.getEnvironment().setActiveProfiles(perfis))
				.withBean(SecurityErrorWriter.class, () -> new SecurityErrorWriter(JsonMapper.builder().build(),
						java.time.Clock.systemUTC()))
				.withConfiguration(UserConfigurations.of(CorsConfig.class));
	}

	@Test
	void deve_subir_sem_cors_quando_a_lista_esta_vazia_e_nao_registrar_configuracao_nenhuma() {
		contexto().run(ctx -> {
			assertThat(ctx).hasNotFailed();
			CorsConfigurationSource fonte = ctx.getBean(CorsConfigurationSource.class);
			assertThat(((UrlBasedCorsConfigurationSource) fonte).getCorsConfigurations()).isEmpty();
		});
	}

	@Test
	void deve_configurar_metodos_cabecalhos_max_age_sem_credenciais_e_location_exposto() {
		contexto().withPropertyValues("app.cors.allowed-origins=https://app.exemplo.invalid").run(ctx -> {
			assertThat(ctx).hasNotFailed();
			var configuracoes = ((UrlBasedCorsConfigurationSource) ctx.getBean(CorsConfigurationSource.class))
					.getCorsConfigurations();
			CorsConfiguration configuracao = configuracoes.get("/**");

			assertThat(configuracao.getAllowedOrigins()).containsExactly("https://app.exemplo.invalid");
			assertThat(configuracao.getAllowedMethods()).containsExactly("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");
			assertThat(configuracao.getAllowedHeaders()).containsExactly("Authorization", "Content-Type");
			assertThat(configuracao.getAllowCredentials()).isFalse();
			assertThat(configuracao.getMaxAge()).isEqualTo(3600L);
			assertThat(configuracao.getExposedHeaders()).containsExactly("Location", "Retry-After");
			assertThat(ctx.getBean("corsFilter")).isInstanceOf(CorsFilter.class);
		});
	}

	@Test
	void deve_falhar_na_subida_com_curinga_ou_origem_fora_do_formato() {
		contexto().withPropertyValues("app.cors.allowed-origins=*").run(ctx -> assertThat(ctx).hasFailed());
		contexto().withPropertyValues("app.cors.allowed-origins=https://app.exemplo.invalid/").run(
				ctx -> assertThat(ctx).hasFailed());
		contexto().withPropertyValues("app.cors.allowed-origins=https://a.exemplo.invalid,*").run(
				ctx -> assertThat(ctx).hasFailed());
	}

	@Test
	void deve_aceitar_http_em_desenvolvimento_e_teste_mas_nunca_em_producao() {
		String http = "app.cors.allowed-origins=http://localhost:5173,https://app.exemplo.invalid";
		contexto().withPropertyValues(http).run(ctx -> assertThat(ctx).hasNotFailed());
		contexto("local").withPropertyValues(http).run(ctx -> assertThat(ctx).hasNotFailed());
		contexto("testcontainers").withPropertyValues(http).run(ctx -> assertThat(ctx).hasNotFailed());

		contexto(Perfis.PRODUCAO).withPropertyValues(http).run(ctx -> {
			assertThat(ctx).hasFailed();
			assertThat(ctx.getStartupFailure()).hasStackTraceContaining("só são aceitas origens https")
					.hasStackTraceContaining("http://localhost:5173");
		});
		contexto(Perfis.PRODUCAO, "outro").withPropertyValues(http).run(ctx -> assertThat(ctx).hasFailed());
	}

	@Test
	void deve_subir_em_producao_so_com_https_ou_sem_origens() {
		contexto(Perfis.PRODUCAO).withPropertyValues("app.cors.allowed-origins=https://app.exemplo.invalid,https://painel.exemplo.invalid:8443")
				.run(ctx -> assertThat(ctx).hasNotFailed());
		contexto(Perfis.PRODUCAO).run(ctx -> assertThat(ctx).hasNotFailed());
	}

}
