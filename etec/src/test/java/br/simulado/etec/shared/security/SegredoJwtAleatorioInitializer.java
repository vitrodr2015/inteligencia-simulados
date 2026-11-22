package br.simulado.etec.shared.security;

import java.util.Map;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;

/**
 * Entrega a TODO contexto Spring dos testes o segredo do JWT gerado ao acaso para esta execução
 * ({@link SegredoDeTeste#DA_EXECUCAO}). Registrado em {@code META-INF/spring.factories} de teste. Assim nenhum valor
 * com cara de segredo fica no repositório, e os helpers dos testes assinam tokens com o mesmo segredo do contexto.
 */
public class SegredoJwtAleatorioInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

	@Override
	public void initialize(ConfigurableApplicationContext contexto) {
		contexto.getEnvironment().getPropertySources().addFirst(
				new MapPropertySource("segredoJwtAleatorioDeTeste", Map.of("security.jwt.secret", SegredoDeTeste.DA_EXECUCAO)));
	}

}
