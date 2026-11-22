package br.simulado.etec.shared.validacao;

import java.nio.charset.StandardCharsets;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/** Conta os bytes UTF-8 do texto. {@code null} é válido (ver {@link TamanhoEmBytes}). */
public class TamanhoEmBytesValidator implements ConstraintValidator<TamanhoEmBytes, CharSequence> {

	private int minimo;

	private int maximo;

	@Override
	public void initialize(TamanhoEmBytes anotacao) {
		if (anotacao.min() < 0 || anotacao.max() < anotacao.min()) {
			throw new IllegalArgumentException(
					"Limites inválidos em @TamanhoEmBytes: min=" + anotacao.min() + ", max=" + anotacao.max());
		}
		this.minimo = anotacao.min();
		this.maximo = anotacao.max();
	}

	@Override
	public boolean isValid(CharSequence texto, ConstraintValidatorContext contexto) {
		if (texto == null) {
			return true;
		}
		int bytes = texto.toString().getBytes(StandardCharsets.UTF_8).length;
		return bytes >= minimo && bytes <= maximo;
	}

}
