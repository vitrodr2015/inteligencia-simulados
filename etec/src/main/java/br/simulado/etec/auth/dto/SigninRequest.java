package br.simulado.etec.auth.dto;

import java.util.Locale;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * Entrada do signin. Só a PRESENÇA é validada (ausente ou nulo = 400, D86): e-mail de formato inválido, senha vazia
 * ou longa demais seguem para a conferência e dão o mesmo 401 de qualquer outra falha, sem revelar nada. O e-mail é
 * aparado e vai para minúsculas (D105); a senha nunca é alterada.
 */
@Schema(description = "Credenciais do signin.")
public record SigninRequest(

		@Schema(description = "E-mail do cadastro.")
		@NotNull(message = "O e-mail é obrigatório.")
		String email,

		@Schema(description = "Senha.")
		@NotNull(message = "A senha é obrigatória.")
		String senha) {

	public SigninRequest {
		email = (email == null) ? null : email.strip().toLowerCase(Locale.ROOT);
	}

	/** Nunca mostra a senha. */
	@Override
	public String toString() {
		return "SigninRequest[email=" + email + ", senha=***]";
	}

}
