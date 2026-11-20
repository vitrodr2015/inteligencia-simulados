package br.simulado.etec.shared.exception;

import java.util.Map;

/** Conflito com o estado atual do recurso (409). */
public class ConflictException extends ApiException {

	public ConflictException(String mensagem) {
		super(CodigoErro.CONFLITO, mensagem, null);
	}

	public ConflictException(CodigoErro codigo, String mensagem) {
		super(codigo, mensagem, null);
	}

	public ConflictException(CodigoErro codigo, String mensagem, Map<String, Object> detalhes) {
		super(codigo, mensagem, detalhes);
	}

}
