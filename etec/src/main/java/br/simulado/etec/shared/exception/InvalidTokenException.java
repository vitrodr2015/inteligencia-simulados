package br.simulado.etec.shared.exception;

/**
 * Token recusado, por qualquer motivo (assinatura, emissor, prazo, tipo, claim ausente, usuário excluído...). Um
 * código só (D107, D118): vencido, adulterado e inválido são a mesma resposta. Nunca guarda o token nem a causa no
 * texto.
 */
public class InvalidTokenException extends AutenticacaoException {

	public static final String MENSAGEM = "Token inválido ou expirado.";

	public InvalidTokenException() {
		super(CodigoErro.TOKEN_INVALIDO, MENSAGEM);
	}

	public InvalidTokenException(Throwable causa) {
		this();
		// Só o tipo da causa vai para o log de quem capturar; nunca o texto, que pode trazer pedaço do token.
		initCause(causa);
	}

}
