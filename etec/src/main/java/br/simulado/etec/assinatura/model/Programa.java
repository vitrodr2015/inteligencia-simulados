package br.simulado.etec.assinatura.model;

import java.util.regex.Pattern;

/**
 * O programa de prova (ETEC hoje; ENEM e OAB no futuro), como PARÂMETRO da consulta de entitlements (D140). É só um código:
 * a tabela {@code exam_program} ainda não existe, e quando nascer ela terá uma coluna {@code codigo} com este valor. Assim
 * nenhum domínio depende de outro por causa do programa. Código fora do formato é erro de programação.
 */
public record Programa(String codigo) {

	private static final Pattern FORMATO = Pattern.compile("[A-Z][A-Z0-9_]{1,19}");

	/** O único programa que existe na fase 1. */
	public static final Programa ETEC = new Programa("ETEC");

	public Programa {
		if (codigo == null || !FORMATO.matcher(codigo).matches()) {
			throw new IllegalArgumentException(
					"O código do programa precisa ter de 2 a 20 caracteres: A-Z, 0-9 e _, começando por letra.");
		}
	}

	public static Programa de(String codigo) {
		return new Programa(codigo);
	}

}
