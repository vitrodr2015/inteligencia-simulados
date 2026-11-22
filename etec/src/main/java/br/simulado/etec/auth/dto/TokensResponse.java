package br.simulado.etec.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Saída do signin e do refresh (D96, D107). {@code tokenType} é sempre "Bearer"; {@code expiresIn} é a validade do
 * access token em segundos.
 */
@JsonPropertyOrder({ "accessToken", "refreshToken", "tokenType", "expiresIn" })
@JsonInclude(JsonInclude.Include.ALWAYS)
@Schema(description = "Par de tokens.")
public record TokensResponse(

		@Schema(description = "Access token (JWT), enviado em Authorization: Bearer.")
		String accessToken,

		@Schema(description = "Refresh token (JWT), usado só em POST /auth/refresh.")
		String refreshToken,

		@Schema(description = "Sempre Bearer.", example = "Bearer")
		String tokenType,

		@Schema(description = "Validade do access token, em segundos.", example = "900")
		long expiresIn) {

	public static final String TIPO = "Bearer";

	/** Nunca mostra os tokens. */
	@Override
	public String toString() {
		return "TokensResponse[tokenType=" + tokenType + ", expiresIn=" + expiresIn + "]";
	}

}
