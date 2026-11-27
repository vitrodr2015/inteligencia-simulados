package br.simulado.etec.shared.exception;

/** O corpo da requisição passa do limite de bytes da rota (413 CORPO_MUITO_GRANDE). */
public class CorpoMuitoGrandeException extends ApiException {

	public CorpoMuitoGrandeException(String mensagem) {
		super(CodigoErro.CORPO_MUITO_GRANDE, mensagem, null);
	}

}
