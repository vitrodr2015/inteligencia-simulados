package br.simulado.etec.shared.security.limite;

/**
 * O "tipo" de cada contador. O nome entra na derivação da chave (HMAC), então dois escopos nunca compartilham linha, e é
 * só ele que aparece no log (em DEBUG), nunca a origem nem o e-mail.
 */
public enum Escopo {

	/** Toda requisição POST de /auth/* por origem (proteção contra enxurrada). */
	AUTH_ORIGEM,

	/** Falhas de signin por e-mail normalizado e origem. */
	SIGNIN_EMAIL_ORIGEM,

	/** Falhas de signin por origem (qualquer e-mail). */
	SIGNIN_ORIGEM,

	/** Erros da senha atual na troca de senha, por usuário (D139). */
	TROCA_SENHA_USUARIO

}
