package br.simulado.etec.shared.exception;

/** E-mail ou senha não conferem no signin (401). Mesma resposta para qualquer causa (contrato, seção 3). */
public class CredenciaisInvalidasException extends AutenticacaoException {

	public static final String MENSAGEM = "E-mail ou senha inválidos.";

	public CredenciaisInvalidasException() {
		super(CodigoErro.CREDENCIAIS_INVALIDAS, MENSAGEM);
	}

}
