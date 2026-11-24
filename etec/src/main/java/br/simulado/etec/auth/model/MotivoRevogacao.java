package br.simulado.etec.auth.model;

/**
 * Por que um refresh foi revogado (coluna {@code refresh_token.motivo_revogacao}). Só {@link #ROTACAO} distingue o REUSO:
 * reapresentar um refresh revogado por rotação é o sinal de roubo (D49, D134); os outros motivos dão só 401.
 */
public enum MotivoRevogacao {

	/** Trocado por um par novo no /auth/refresh. */
	ROTACAO,

	/** Encerrado por POST /auth/logout. */
	LOGOUT,

	/** Revogado em massa porque um refresh JÁ ROTACIONADO foi reapresentado. */
	REUSO,

	/** Revogado em massa pela troca de senha. */
	TROCA_SENHA,

	/** Revogado em massa pela exclusão da conta (DELETE /me, ainda não implementado). */
	EXCLUSAO

}
