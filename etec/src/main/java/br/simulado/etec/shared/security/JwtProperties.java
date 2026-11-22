package br.simulado.etec.shared.security;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuração do JWT ({@code security.jwt.*}). O segredo é tratado como TEXTO UTF-8 (não como Base64) e precisa ter
 * pelo menos 32 bytes (HS256). A validação aqui complementa a {@code ValidacaoDeAmbiente}, que só roda no
 * {@code main()}: o provedor nunca sobe com segredo curto, em nenhum contexto. As mensagens nunca mostram o valor.
 */
@ConfigurationProperties("security.jwt")
public record JwtProperties(String secret, String issuer, Duration accessTokenExpiration,
		Duration refreshTokenExpiration) {

	public static final int TAMANHO_MINIMO_SEGREDO_EM_BYTES = 32;

	public JwtProperties {
		if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < TAMANHO_MINIMO_SEGREDO_EM_BYTES) {
			throw new IllegalArgumentException(
					"security.jwt.secret precisa ter pelo menos " + TAMANHO_MINIMO_SEGREDO_EM_BYTES + " bytes (UTF-8).");
		}
		if (issuer == null || issuer.isBlank()) {
			throw new IllegalArgumentException("security.jwt.issuer não está definido.");
		}
		if (accessTokenExpiration == null || accessTokenExpiration.isZero() || accessTokenExpiration.isNegative()) {
			throw new IllegalArgumentException("security.jwt.access-token-expiration precisa ser positiva.");
		}
		if (refreshTokenExpiration == null || refreshTokenExpiration.isZero() || refreshTokenExpiration.isNegative()) {
			throw new IllegalArgumentException("security.jwt.refresh-token-expiration precisa ser positiva.");
		}
		if (refreshTokenExpiration.compareTo(accessTokenExpiration) <= 0) {
			throw new IllegalArgumentException(
					"security.jwt.refresh-token-expiration precisa ser maior que a do access token.");
		}
	}

	/** Nunca mostra o segredo. */
	@Override
	public String toString() {
		return "JwtProperties[secret=***, issuer=" + issuer + ", accessTokenExpiration=" + accessTokenExpiration
				+ ", refreshTokenExpiration=" + refreshTokenExpiration + "]";
	}

}
