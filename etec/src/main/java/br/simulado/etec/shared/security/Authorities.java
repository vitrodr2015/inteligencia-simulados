package br.simulado.etec.shared.security;

import br.simulado.etec.usuarios.model.Papel;

/**
 * As autoridades do Spring Security, uma por papel. As regras de acesso da {@link SecurityConfig} usam SÓ estas
 * constantes, nunca texto solto. A autoridade vem do papel gravado no BANCO a cada requisição (D87), nunca do claim do
 * token. Um teste garante que existe uma constante para cada valor de {@link Papel}.
 */
public final class Authorities {

	public static final String ROLE_STUDENT = "ROLE_STUDENT";

	public static final String ROLE_ADMIN = "ROLE_ADMIN";

	/** Sem nenhuma permissão na fase 1 (D122): nenhuma regra concede acesso a este papel. */
	public static final String ROLE_GUARDIAN = "ROLE_GUARDIAN";

	private Authorities() {
	}

	/** A autoridade do papel. */
	public static String de(Papel papel) {
		return switch (papel) {
			case STUDENT -> ROLE_STUDENT;
			case ADMIN -> ROLE_ADMIN;
			case GUARDIAN -> ROLE_GUARDIAN;
		};
	}

}
