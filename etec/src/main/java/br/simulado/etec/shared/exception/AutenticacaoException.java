package br.simulado.etec.shared.exception;

/**
 * Falha de autenticação (401). A mensagem é genérica de propósito: nunca diz qual parte falhou (usuário, senha,
 * assinatura, prazo ou tipo), para não ajudar quem está tentando adivinhar.
 */
public abstract class AutenticacaoException extends ApiException {

	protected AutenticacaoException(CodigoErro codigo, String mensagem) {
		super(codigo, mensagem, null);
	}

}
