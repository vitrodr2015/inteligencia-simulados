package br.simulado.etec.shared.config;

import java.util.Map;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Hash de senha: encoder delegante com SÓ o BCrypt registrado. O hash gravado leva o prefixo {@code {bcrypt}}, o que
 * deixa trocar de algoritmo no futuro sem quebrar os hashes antigos. {@code {noop}}, {@code {md5}}, {@code {sha256}} e
 * qualquer outro prefixo (ou hash sem prefixo, ou malformado) NÃO conferem: {@code matches} devolve falso, nunca
 * lança exceção (o signin responderia 500) e nunca aceita a senha em texto puro.
 */
@Configuration(proxyBeanMethods = false)
public class PasswordConfig {

	/** Custo do BCrypt (2^10 rodadas). */
	static final int CUSTO_DO_BCRYPT = 10;

	@Bean
	public PasswordEncoder passwordEncoder() {
		DelegatingPasswordEncoder encoder = new DelegatingPasswordEncoder("bcrypt",
				Map.of("bcrypt", new BCryptPasswordEncoder(CUSTO_DO_BCRYPT)));
		encoder.setDefaultPasswordEncoderForMatches(new NuncaConfere());
		return encoder;
	}

	/** Para hash sem prefixo conhecido: nunca confere e nunca aceita codificar. */
	private static final class NuncaConfere implements PasswordEncoder {

		@Override
		public String encode(CharSequence senha) {
			throw new UnsupportedOperationException("Este encoder não codifica.");
		}

		@Override
		public boolean matches(CharSequence senha, String hash) {
			return false;
		}

	}

}
