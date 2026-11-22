package br.simulado.etec.shared.validacao;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/** Confere a data de nascimento pelo relógio da aplicação (ver {@link DataDeNascimento}). */
public class DataDeNascimentoValidator implements ConstraintValidator<DataDeNascimento, LocalDate> {

	static final LocalDate PRIMEIRA_DATA_ACEITA = LocalDate.of(1900, 1, 1);

	private final Clock relogio;

	public DataDeNascimentoValidator(Clock relogio) {
		this.relogio = relogio;
	}

	@Override
	public boolean isValid(LocalDate data, ConstraintValidatorContext contexto) {
		if (data == null) {
			return true;
		}
		LocalDate hoje = LocalDate.now(relogio.withZone(ZoneOffset.UTC));
		return data.isBefore(hoje) && !data.isBefore(PRIMEIRA_DATA_ACEITA);
	}

}
