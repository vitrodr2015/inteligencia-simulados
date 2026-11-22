package br.simulado.etec.shared.config;

import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;

/**
 * Esquema de segurança da documentação: Bearer com o access token (JWT). É exigido por padrão em todas as rotas; as
 * rotas públicas (autenticação e termos) o dispensam com {@code @SecurityRequirements} vazio.
 */
@Configuration(proxyBeanMethods = false)
@OpenAPIDefinition(security = @SecurityRequirement(name = OpenApiConfig.BEARER))
@SecurityScheme(name = OpenApiConfig.BEARER, type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT",
		description = "Access token recebido em POST /auth/signin, enviado em Authorization: Bearer <token>.")
public class OpenApiConfig {

	public static final String BEARER = "bearerAuth";

}
