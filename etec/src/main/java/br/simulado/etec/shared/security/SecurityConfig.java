package br.simulado.etec.shared.security;

import java.util.function.Consumer;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsUtils;

import jakarta.servlet.DispatcherType;

import br.simulado.etec.shared.security.limite.LimiteDeOrigemFilter;
import br.simulado.etec.shared.security.limite.LimiteDeTentativas;
import br.simulado.etec.shared.security.limite.OrigemDaRequisicao;
import br.simulado.etec.shared.security.limite.RateLimitProperties;

/**
 * Cadeia de segurança e QUEM PODE O QUÊ, num lugar só (contrato, seções 1 e 2): stateless, CSRF desligado, sem
 * formulário, sem Basic, sem logout do Spring, e NEGAR POR PADRÃO: o que não tem regra aqui é negado ({@code denyAll}),
 * com 401 para o anônimo e 403 para o autenticado, inclusive rota que nem existe (D121). O 404 fica para o recurso
 * inexistente DEPOIS da autorização. Cada recurso novo traz a sua regra e a sua matriz de testes no próprio PR.
 *
 * <p>As regras são por MÉTODO e caminho exatos (nunca {@code /**}), e o HEAD segue o GET: o Spring Security compara o
 * método exato, e o MVC responde HEAD pelo handler do GET. As autoridades vêm só de {@link Authorities}, e o papel vem
 * do banco a cada requisição (D87). GUARDIAN não tem nenhuma permissão na fase 1 (D122).
 *
 * <p>O 401 e o 403 saem em {@code ErrorResponse} pelo {@link SecurityErrorWriter}, inclusive em {@code /actuator/**}
 * e {@code /error} (D120). Os cabeçalhos extras do Spring Security (Cache-Control, X-Content-Type-Options etc.) ficam
 * ligados; o {@code no-store} do signin e do refresh é gravado antes e não é sobrescrito.
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
public class SecurityConfig {

	/** Rotas públicas de autenticação (todas POST). */
	static final String[] PUBLICAS_POST = { "/api/v1/auth/register", "/api/v1/auth/signin", "/api/v1/auth/refresh" };

	/** Os três caminhos de saúde do actuator (a liveness e a readiness vêm das probes, D99). */
	static final String[] SAUDE = { "/actuator/health", "/actuator/health/liveness", "/actuator/health/readiness" };

	/** O usuário logado. PATCH e DELETE trazem a regra quando a rota nascer (até lá, negados). */
	static final String[] ME = { "/api/v1/me" };

	/** Leitura do catálogo: STUDENT e ADMIN. */
	static final String[] CATALOGO_LEITURA = { "/api/v1/materias", "/api/v1/materias/{id}", "/api/v1/subtopicos",
			"/api/v1/subtopicos/{id}" };

	/** Escrita do catálogo: só ADMIN. */
	static final String[] CATALOGO_CRIACAO = { "/api/v1/materias", "/api/v1/subtopicos" };

	static final String[] CATALOGO_ITEM = { "/api/v1/materias/{id}", "/api/v1/subtopicos/{id}" };

	static final String[] CATALOGO_ESTADO = { "/api/v1/materias/{id}/disable", "/api/v1/materias/{id}/enable",
			"/api/v1/subtopicos/{id}/disable", "/api/v1/subtopicos/{id}/enable" };

	/** Documentação OpenAPI: só entra como pública quando está ligada (desenvolvimento e homologação, D34). */
	static final String[] DOCUMENTACAO = { "/v3/api-docs", "/v3/api-docs/**", "/v3/api-docs.yaml", "/swagger-ui.html",
			"/swagger-ui/**" };

	@Bean
	public SecurityFilterChain cadeiaDeSeguranca(HttpSecurity http, AutenticadorDeToken autenticador,
			SecurityErrorWriter escritorDeErros, Environment ambiente, LimiteDeTentativas limite,
			RateLimitProperties limites, OrigemDaRequisicao origens) throws Exception {
		boolean documentacaoLigada = ambiente.getProperty("springdoc.api-docs.enabled", Boolean.class, false)
				|| ambiente.getProperty("springdoc.swagger-ui.enabled", Boolean.class, false);

		http.csrf(csrf -> csrf.disable())
				// CORS: usa o bean corsFilter (origens de app.cors.allowed-origins). Sem isto não há CORS.
				.cors(Customizer.withDefaults())
				.formLogin(formulario -> formulario.disable())
				.httpBasic(basic -> basic.disable())
				.logout(logout -> logout.disable())
				.requestCache(cache -> cache.disable())
				.sessionManagement(sessao -> sessao.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.exceptionHandling(erros -> erros.authenticationEntryPoint(escritorDeErros)
						.accessDeniedHandler(escritorDeErros))
				.addFilterBefore(new JwtTokenFilter(autenticador, escritorDeErros),
						UsernamePasswordAuthenticationFilter.class)
				// Limite por origem de /auth/* (D128): depois do CORS (o 429 leva os mesmos cabeçalhos) e antes do JWT e do corpo.
				.addFilterBefore(new LimiteDeOrigemFilter(limite, limites, origens, escritorDeErros), JwtTokenFilter.class)
				.authorizeHttpRequests(regras -> {
					// Despacho de ERRO (o container repassando um erro real): mantém o status original. Quem pede
					// /error direto é despacho normal e cai no denyAll abaixo.
					regras.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll();
					// Preflight de CORS de verdade (OPTIONS com Origin e Access-Control-Request-Method): não exige autenticação
					// (D100). Um OPTIONS comum, sem esses cabeçalhos, NÃO é liberado e cai no denyAll.
					regras.requestMatchers(request -> CorsUtils.isPreFlightRequest(request)).permitAll();
					// ----- públicas -----
					regras.requestMatchers(HttpMethod.POST, PUBLICAS_POST).permitAll();
					leitura(regras, new String[] { "/api/v1/termos/vigente" }, url -> url.permitAll());
					leitura(regras, SAUDE, url -> url.permitAll());
					if (documentacaoLigada) {
						leitura(regras, DOCUMENTACAO, url -> url.permitAll());
					}
					// ----- STUDENT e ADMIN -----
					leitura(regras, ME, url -> url.hasAnyAuthority(Authorities.ROLE_STUDENT, Authorities.ROLE_ADMIN));
					leitura(regras, CATALOGO_LEITURA,
							url -> url.hasAnyAuthority(Authorities.ROLE_STUDENT, Authorities.ROLE_ADMIN));
					// ----- só ADMIN (escrita do catálogo) -----
					regras.requestMatchers(HttpMethod.POST, CATALOGO_CRIACAO).hasAuthority(Authorities.ROLE_ADMIN);
					regras.requestMatchers(HttpMethod.PUT, CATALOGO_ITEM).hasAuthority(Authorities.ROLE_ADMIN);
					regras.requestMatchers(HttpMethod.DELETE, CATALOGO_ITEM).hasAuthority(Authorities.ROLE_ADMIN);
					regras.requestMatchers(HttpMethod.PATCH, CATALOGO_ESTADO).hasAuthority(Authorities.ROLE_ADMIN);
					// D113: o resto de /actuator e /error = 401 para anônimo e 403 para autenticado, nunca 404.
					regras.requestMatchers("/actuator/**", "/actuator", "/error", "/error/**").denyAll();
					// D121: o que não tem regra é NEGADO (401 anônimo, 403 autenticado), inclusive rota inexistente.
					regras.anyRequest().denyAll();
				});
		return http.build();
	}

	/** GET e HEAD com a mesma regra: o Spring Security compara o método exato, e o MVC atende HEAD pelo handler do GET. */
	private static void leitura(
			AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry regras,
			String[] caminhos, Consumer<AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizedUrl> regra) {
		regra.accept(regras.requestMatchers(HttpMethod.GET, caminhos));
		regra.accept(regras.requestMatchers(HttpMethod.HEAD, caminhos));
	}

	/**
	 * Não existe login por usuário e senha do Spring (o signin é nosso). Declarar este serviço, que nunca encontra
	 * ninguém, impede o Spring Boot de criar o usuário "user" com uma senha gerada e IMPRESSA no log.
	 */
	@Bean
	UserDetailsService semUsuariosDoSpring() {
		return nome -> {
			throw new UsernameNotFoundException("Não há login por UserDetailsService.");
		};
	}

}
