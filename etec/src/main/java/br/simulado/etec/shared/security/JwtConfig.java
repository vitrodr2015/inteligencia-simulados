package br.simulado.etec.shared.security;

import java.time.Clock;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Liga as propriedades do JWT e cria o provedor de tokens (um só, com o relógio da aplicação). */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(JwtProperties.class)
public class JwtConfig {

	@Bean
	public JwtTokenProvider jwtTokenProvider(JwtProperties propriedades, Clock relogio) {
		return new JwtTokenProvider(propriedades, relogio);
	}

}
