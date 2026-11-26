package br.simulado.etec.catalogo.dto;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Ver {@link AlternativasValidas}. A regra mora em {@link #valida(List)}, que o service de questão também chama: o cadastro não
 * depende de ter passado pela validação do HTTP (a importação da fatia 3 chama o mesmo service).
 */
public class AlternativasValidasValidator implements ConstraintValidator<AlternativasValidas, List<AlternativaRequest>> {

	private static final Set<String> LETRAS = Set.of("A", "B", "C", "D", "E");

	@Override
	public boolean isValid(List<AlternativaRequest> alternativas, ConstraintValidatorContext contexto) {
		return alternativas == null || valida(alternativas);
	}

	/** {@code true} se são exatamente 5 itens e as letras, juntas, são A, B, C, D e E (sem repetir nem faltar). */
	public static boolean valida(List<AlternativaRequest> alternativas) {
		if (alternativas == null || alternativas.size() != LETRAS.size()
				|| alternativas.stream().anyMatch(java.util.Objects::isNull)) {
			return false;
		}
		Set<String> letras = new TreeSet<>();
		for (AlternativaRequest alternativa : alternativas) {
			if (alternativa.letra() == null) {
				return false;
			}
			letras.add(alternativa.letra());
		}
		return letras.equals(LETRAS);
	}

}
