package br.simulado.etec.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * Entrada do refresh (D96): o token vai só no corpo, nunca na URL. Ausente, nulo ou que não é texto = 400 (D86);
 * texto vazio ou qualquer texto que não seja um refresh válido = 401.
 */
@Schema(description = "Refresh token a trocar por um par novo.")
public record RefreshRequest(

		@Schema(description = "Refresh token recebido no signin ou no refresh anterior.")
		@NotNull(message = "O refresh token é obrigatório.")
		String refreshToken) {

	/** Nunca mostra o token. */
	@Override
	public String toString() {
		return "RefreshRequest[refreshToken=***]";
	}

}
