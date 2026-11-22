package br.simulado.etec.shared.config;

import java.util.List;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import br.simulado.etec.shared.security.CorsProcessorComErrorResponse;
import br.simulado.etec.shared.security.SecurityErrorWriter;

/**
 * CORS (contrato, D100 e D123). As origens vêm só de {@code app.cors.allowed-origins}; vazia = sem CORS (nenhuma
 * configuração registrada). Métodos GET, POST, PUT, PATCH, DELETE e OPTIONS; cabeçalhos Authorization e Content-Type;
 * SEM credenciais; Max-Age de 3600 s; {@code Location} exposto. Em produção (perfil {@link Perfis#PRODUCAO}) só origens https: a
 * aplicação não sobe com http.
 *
 * <p>O {@link CorsFilter} é um bean chamado {@code corsFilter} e fica DESLIGADO como filtro do servlet: quem o aplica é o
 * {@code .cors()} da {@code SecurityConfig}, no lugar certo da cadeia (antes da autenticação, então os 401 e 403 também
 * levam os cabeçalhos de CORS). Sem o {@code .cors()}, não há CORS.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CorsProperties.class)
public class CorsConfig {

	static final long MAX_AGE_EM_SEGUNDOS = 3600;

	static final List<String> METODOS = List.of(HttpMethod.GET.name(), HttpMethod.POST.name(), HttpMethod.PUT.name(),
			HttpMethod.PATCH.name(), HttpMethod.DELETE.name(), HttpMethod.OPTIONS.name());

	static final List<String> CABECALHOS_PERMITIDOS = List.of(HttpHeaders.AUTHORIZATION, HttpHeaders.CONTENT_TYPE);

	static final List<String> CABECALHOS_EXPOSTOS = List.of(HttpHeaders.LOCATION);

	@Bean
	public CorsConfigurationSource corsConfigurationSource(CorsProperties propriedades, Environment ambiente) {
		List<String> origens = propriedades.allowedOrigins();
		if (Perfis.emProducao(ambiente)) {
			origens.stream().filter(origem -> origem.startsWith("http://")).findFirst().ifPresent(origem -> {
				throw new IllegalStateException(
						"app.cors.allowed-origins: em produção só são aceitas origens https, e esta é http: " + origem);
			});
		}
		UrlBasedCorsConfigurationSource fonte = new UrlBasedCorsConfigurationSource();
		if (origens.isEmpty()) {
			return fonte; // sem registro nenhum: sem CORS
		}
		CorsConfiguration configuracao = new CorsConfiguration();
		configuracao.setAllowedOrigins(origens);
		configuracao.setAllowedMethods(METODOS);
		configuracao.setAllowedHeaders(CABECALHOS_PERMITIDOS);
		configuracao.setExposedHeaders(CABECALHOS_EXPOSTOS);
		configuracao.setAllowCredentials(false);
		configuracao.setMaxAge(MAX_AGE_EM_SEGUNDOS);
		fonte.registerCorsConfiguration("/**", configuracao);
		return fonte;
	}

	/** O nome {@code corsFilter} é o que o {@code .cors()} do Spring Security procura. */
	@Bean
	public CorsFilter corsFilter(CorsConfigurationSource fonte, SecurityErrorWriter escritorDeErros) {
		CorsFilter filtro = new CorsFilter(fonte);
		filtro.setCorsProcessor(new CorsProcessorComErrorResponse(escritorDeErros));
		return filtro;
	}

	/** Desliga o registro automático do filtro no servlet: ele só roda dentro da cadeia, pelo {@code .cors()}. */
	@Bean
	public FilterRegistrationBean<CorsFilter> corsFilterForaDoServlet(CorsFilter filtro) {
		FilterRegistrationBean<CorsFilter> registro = new FilterRegistrationBean<>(filtro);
		registro.setEnabled(false);
		return registro;
	}

}
