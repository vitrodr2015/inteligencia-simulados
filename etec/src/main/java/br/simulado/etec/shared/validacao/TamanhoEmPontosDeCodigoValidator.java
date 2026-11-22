package br.simulado.etec.shared.validacao;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/** Conta com {@link String#codePointCount(int, int)}. {@code null} é válido (ver {@link TamanhoEmPontosDeCodigo}). */
public class TamanhoEmPontosDeCodigoValidator implements ConstraintValidator<TamanhoEmPontosDeCodigo, CharSequence> {

	private int minimo;

	private int maximo;

	@Override
	public void initialize(TamanhoEmPontosDeCodigo anotacao) {
		if (anotacao.min() < 0 || anotacao.max() < anotacao.min()) {
			throw new IllegalArgumentException("Limites inválidos em @TamanhoEmPontosDeCodigo: min=" + anotacao.min()
					+ ", max=" + anotacao.max());
		}
		this.minimo = anotacao.min();
		this.maximo = anotacao.max();
	}

	@Override
	public boolean isValid(CharSequence texto, ConstraintValidatorContext contexto) {
		if (texto == null) {
			return true;
		}
		int pontos = Character.codePointCount(texto, 0, texto.length());
		return pontos >= minimo && pontos <= maximo;
	}

}
