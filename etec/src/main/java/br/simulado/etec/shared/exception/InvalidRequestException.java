package br.simulado.etec.shared.exception;

import java.util.Map;

/** Requisição inválida por regra do nosso código (400). */
public class InvalidRequestException extends ApiException {

	public InvalidRequestException(String mensagem) {
		super(CodigoErro.VALIDACAO_FALHOU, mensagem, null);
	}

	public InvalidRequestException(CodigoErro codigo, String mensagem) {
		super(codigo, mensagem, null);
	}

	public InvalidRequestException(CodigoErro codigo, String mensagem, Map<String, Object> detalhes) {
		super(codigo, mensagem, detalhes);
	}

}
