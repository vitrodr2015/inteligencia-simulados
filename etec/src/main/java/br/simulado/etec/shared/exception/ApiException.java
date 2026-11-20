package br.simulado.etec.shared.exception;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Base das exceções de negócio. A mensagem é escrita pelo nosso código, em português, e vai
 * para o cliente; {@code detalhes} (opcional) vira o campo de mesmo nome no ErrorResponse (D44).
 */
public abstract class ApiException extends RuntimeException {

	private final CodigoErro codigo;

	private final Map<String, Object> detalhes;

	protected ApiException(CodigoErro codigo, String mensagem, Map<String, Object> detalhes) {
		super(mensagem);
		this.codigo = codigo;
		this.detalhes = (detalhes == null) ? null : Collections.unmodifiableMap(new LinkedHashMap<>(detalhes));
	}

	public CodigoErro getCodigo() {
		return codigo;
	}

	public Map<String, Object> getDetalhes() {
		return detalhes;
	}

}
