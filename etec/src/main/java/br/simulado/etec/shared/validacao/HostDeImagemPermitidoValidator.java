package br.simulado.etec.shared.validacao;

import java.net.URI;
import java.net.URISyntaxException;

import br.simulado.etec.shared.config.ImagensProperties;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/** Ver {@link HostDeImagemPermitido}. As propriedades chegam por injeção (o Spring cria o validador). */
public class HostDeImagemPermitidoValidator implements ConstraintValidator<HostDeImagemPermitido, CharSequence> {

	private final ImagensProperties propriedades;

	public HostDeImagemPermitidoValidator(ImagensProperties propriedades) {
		this.propriedades = propriedades;
	}

	@Override
	public boolean isValid(CharSequence texto, ConstraintValidatorContext contexto) {
		if (texto == null) {
			return true;
		}
		try {
			String host = new URI(texto.toString()).getHost();
			// sem host (URL malformada, relativa...): quem recusa é o @UrlHttps; aqui só se confere host que existe
			return host == null || propriedades.permite(host);
		}
		catch (URISyntaxException e) {
			return true;
		}
	}

}
