package br.simulado.etec.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Entrada da troca de senha (D139). SEM anotações de validação de propósito: o 429 vem ANTES do 400 de formato, então a
 * validação (ausente, política da nova senha) é feita pelo service depois de contar a tentativa. Texto que não é texto = 400
 * (corpo ilegível). Nunca mostra as senhas.
 */
@Schema(description = "Senha atual e nova senha.")
public record TrocaDeSenhaRequest(

		@Schema(description = "A senha atual do usuário logado.")
		String senhaAtual,

		@Schema(description = "A nova senha: STUDENT de 8 a 72 bytes; ADMIN de 12 a 72 bytes, sem espaço nas pontas, sem quebra de linha e sem marcador de exemplo.")
		String novaSenha) {

	/** Nunca mostra as senhas. */
	@Override
	public String toString() {
		return "TrocaDeSenhaRequest[senhaAtual=***, novaSenha=***]";
	}

}
