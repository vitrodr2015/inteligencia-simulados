package br.simulado.etec.shared.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import jakarta.servlet.DispatcherType;

/**
 * Cadeia de segurança (contrato, seções 1, 2 e 3): stateless, CSRF desligado, sem formulário, sem Basic, sem logout
 * do Spring, e NEGAR POR PADRÃO: tudo exige autenticação, exceto o que está listado abaixo, por MÉTODO e caminho.
 * Rota nova nasce fechada. Os papéis nas rotas (ADMIN escreve, STUDENT lê) NÃO entram aqui: são do Módulo 11, e até
 * lá qualquer usuário autenticado acessa as rotas autenticadas. CORS e preflight também ficam para o Módulo 11.
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

	/** Documentação OpenAPI: só entra como pública quando está ligada (desenvolvimento e homologação, D34). */
	static final String[] DOCUMENTACAO = { "/v3/api-docs", "/v3/api-docs/**", "/v3/api-docs.yaml", "/swagger-ui.html",
			"/swagger-ui/**" };

	@Bean
	public SecurityFilterChain cadeiaDeSeguranca(HttpSecurity http, AutenticadorDeToken autenticador,
			SecurityErrorWriter escritorDeErros, Environment ambiente) throws Exception {
		boolean documentacaoLigada = ambiente.getProperty("springdoc.api-docs.enabled", Boolean.class, false)
				|| ambiente.getProperty("springdoc.swagger-ui.enabled", Boolean.class, false);

		http.csrf(csrf -> csrf.disable())
				.formLogin(formulario -> formulario.disable())
				.httpBasic(basic -> basic.disable())
				.logout(logout -> logout.disable())
				.requestCache(cache -> cache.disable())
				.sessionManagement(sessao -> sessao.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.exceptionHandling(erros -> erros.authenticationEntryPoint(escritorDeErros)
						.accessDeniedHandler(escritorDeErros))
				.addFilterBefore(new JwtTokenFilter(autenticador, escritorDeErros),
						UsernamePasswordAuthenticationFilter.class)
				.authorizeHttpRequests(regras -> {
					// Despacho de ERRO (o container repassando um erro real): mantém o status original. Quem pede
					// /error direto é despacho normal e cai no denyAll abaixo.
					regras.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll();
					regras.requestMatchers(HttpMethod.POST, PUBLICAS_POST).permitAll();
					regras.requestMatchers(HttpMethod.GET, "/api/v1/termos/vigente").permitAll();
					regras.requestMatchers(HttpMethod.GET, SAUDE).permitAll();
					if (documentacaoLigada) {
						regras.requestMatchers(HttpMethod.GET, DOCUMENTACAO).permitAll();
					}
					// D113: o resto de /actuator e /error = 401 para anônimo e 403 para autenticado, nunca 404.
					regras.requestMatchers("/actuator/**", "/actuator", "/error", "/error/**").denyAll();
					regras.anyRequest().authenticated();
				});
		return http.build();
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
