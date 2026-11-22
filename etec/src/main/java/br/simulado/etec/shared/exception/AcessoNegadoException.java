package br.simulado.etec.shared.exception;

/**
 * Autenticado, mas sem permissão para o que pediu (403). Defesa em profundidade: a cadeia de segurança já nega antes de
 * chegar ao controller, e esta exceção só sai se um código que precisa do papel o encontrar ausente ou sem permissão.
 */
public class AcessoNegadoException extends ApiException {

	public static final String MENSAGEM = "Você não tem permissão para acessar este recurso.";

	public AcessoNegadoException() {
		super(CodigoErro.ACESSO_NEGADO, MENSAGEM, null);
	}

}
