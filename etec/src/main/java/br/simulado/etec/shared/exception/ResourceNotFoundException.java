package br.simulado.etec.shared.exception;

import java.util.Map;

/** Recurso inexistente (404). */
public class ResourceNotFoundException extends ApiException {

	public ResourceNotFoundException(String mensagem) {
		super(CodigoErro.RECURSO_NAO_ENCONTRADO, mensagem, null);
	}

	public ResourceNotFoundException(CodigoErro codigo, String mensagem) {
		super(codigo, mensagem, null);
	}

	public ResourceNotFoundException(CodigoErro codigo, String mensagem, Map<String, Object> detalhes) {
		super(codigo, mensagem, detalhes);
	}

}
