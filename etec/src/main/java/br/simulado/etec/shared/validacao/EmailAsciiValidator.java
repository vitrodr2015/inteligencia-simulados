package br.simulado.etec.shared.validacao;

import java.util.regex.Pattern;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Formato do e-mail ASCII: parte local de 1 a 64 caracteres (letras, dígitos e {@code .!#$%&'*+/=?^_`{|}~-}, sem
 * ponto no começo, no fim nem repetido) e domínio com pelo menos um ponto, rótulos de letras, dígitos e hífen (sem
 * hífen nas pontas). Sem espaços, sem aspas, sem comentários, sem colchetes de IP.
 */
public class EmailAsciiValidator implements ConstraintValidator<EmailAscii, CharSequence> {

	private static final String ATOMO = "[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+";

	private static final String ROTULO = "[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?";

	private static final Pattern FORMATO = Pattern
			.compile("^" + ATOMO + "(?:\\." + ATOMO + ")*@" + ROTULO + "(?:\\." + ROTULO + ")+$");

	private static final int TAMANHO_MAXIMO_DA_PARTE_LOCAL = 64;

	@Override
	public boolean isValid(CharSequence texto, ConstraintValidatorContext contexto) {
		if (texto == null) {
			return true;
		}
		String email = texto.toString();
		int arroba = email.lastIndexOf('@');
		if (arroba < 1 || arroba > TAMANHO_MAXIMO_DA_PARTE_LOCAL) {
			return false;
		}
		return FORMATO.matcher(email).matches();
	}

}
