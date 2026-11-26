package br.simulado.etec.shared.validacao;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Set;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/** Ver {@link MarcacaoMinima}. Um percurso só, com uma pilha das tags abertas. */
public class MarcacaoMinimaValidator implements ConstraintValidator<MarcacaoMinima, CharSequence> {

	private static final Set<String> COM_FECHAMENTO = Set.of("b", "u", "i", "sup", "sub");

	private static final Set<String> ENTIDADES = Set.of("&lt;", "&gt;", "&amp;");

	@Override
	public boolean isValid(CharSequence texto, ConstraintValidatorContext contexto) {
		return texto == null || valido(texto.toString());
	}

	/** {@code true} se o texto só usa a marcação permitida, bem formada. */
	public static boolean valido(String texto) {
		Deque<String> abertas = new ArrayDeque<>();
		int i = 0;
		int n = texto.length();
		while (i < n) {
			char c = texto.charAt(i);
			if (c == '<') {
				int fim = texto.indexOf('>', i);
				if (fim < 0) {
					return false;
				}
				String tag = texto.substring(i + 1, fim);
				if (tag.equals("br")) {
					// <br> não tem fechamento
				}
				else if (COM_FECHAMENTO.contains(tag)) {
					abertas.push(tag);
				}
				else if (tag.startsWith("/") && COM_FECHAMENTO.contains(tag.substring(1))) {
					if (abertas.isEmpty() || !abertas.pop().equals(tag.substring(1))) {
						return false;
					}
				}
				else {
					return false;
				}
				i = fim + 1;
			}
			else if (c == '>') {
				return false;
			}
			else if (c == '&') {
				int fim = texto.indexOf(';', i);
				if (fim < 0 || !ENTIDADES.contains(texto.substring(i, fim + 1))) {
					return false;
				}
				i = fim + 1;
			}
			else {
				i++;
			}
		}
		return abertas.isEmpty();
	}

}
