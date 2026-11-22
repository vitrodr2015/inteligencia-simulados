package br.simulado.etec.shared.config;

import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

/**
 * Nomes dos perfis do Spring que mudam regras da aplicação. O perfil de PRODUÇÃO tem UM nome só, aqui. Toda regra "só em
 * produção" (CORS só com https hoje; o bootstrap do admin, D45, e a {@code ValidacaoDeAmbiente} quando precisarem) usa
 * {@link #PRODUCAO} ou {@link #emProducao(Environment)}, nunca um texto solto. Um teste quebra se outro lugar do código
 * escrever o nome por conta própria ou se um {@code application-<perfil>.yml} usar um nome de produção diferente.
 */
public final class Perfis {

	/** Perfil de produção. */
	public static final String PRODUCAO = "prod";

	private Perfis() {
	}

	public static boolean emProducao(Environment ambiente) {
		return ambiente.acceptsProfiles(Profiles.of(PRODUCAO));
	}

}
