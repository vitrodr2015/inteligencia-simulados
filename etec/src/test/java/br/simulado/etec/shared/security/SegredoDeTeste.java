package br.simulado.etec.shared.security;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * Segredo do JWT dos testes: gerado ao acaso a cada execução da JVM. Nenhum valor parecido com segredo fica no
 * repositório, e dois segredos gerados aqui nunca coincidem (usado também para "token de outro segredo").
 */
public final class SegredoDeTeste {

	private static final SecureRandom ALEATORIO = new SecureRandom();

	/** Segredo único da execução, o mesmo para toda a JVM (o contexto do Spring e os helpers usam este). */
	public static final String DA_EXECUCAO = gerar();

	private SegredoDeTeste() {
	}

	/** Um segredo novo, de 48 bytes aleatórios em texto Base64 (64 caracteres). */
	public static String gerar() {
		byte[] bytes = new byte[48];
		ALEATORIO.nextBytes(bytes);
		return Base64.getEncoder().encodeToString(bytes);
	}

}
