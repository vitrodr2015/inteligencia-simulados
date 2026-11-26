package br.simulado.etec.shared.validacao;

import java.net.URI;
import java.net.URISyntaxException;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/** Aceita só URL absoluta {@code https} com host e sem usuário e senha na URL. {@code null} é válido (ver {@link UrlHttps}). */
public class UrlHttpsValidator implements ConstraintValidator<UrlHttps, CharSequence> {

	@Override
	public boolean isValid(CharSequence texto, ConstraintValidatorContext contexto) {
		if (texto == null) {
			return true;
		}
		try {
			URI uri = new URI(texto.toString());
			return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null && !uri.getHost().isBlank()
					&& uri.getRawUserInfo() == null;
		}
		catch (URISyntaxException e) {
			return false;
		}
	}

}
