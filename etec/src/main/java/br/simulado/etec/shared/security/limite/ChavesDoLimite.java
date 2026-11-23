package br.simulado.etec.shared.security.limite;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import br.simulado.etec.shared.security.JwtProperties;

/**
 * Chave de cada contador: HMAC-SHA256 em hexadecimal (64 caracteres) de {@code escopo + ":" + valor}. O e-mail e a origem
 * NUNCA ficam no banco em claro. A chave do HMAC é DERIVADA do {@code JWT_SECRET} por um rótulo de contexto fixo (outro
 * HMAC): não há segredo novo, o segredo de assinatura nunca é usado direto e trocar o {@code JWT_SECRET} só zera os
 * contadores (as chaves mudam e as linhas antigas expiram). Nada aqui é logado.
 */
public class ChavesDoLimite {

	private static final String ALGORITMO = "HmacSHA256";

	/** Rótulo de contexto da derivação; mudar este texto invalida todos os contadores. */
	static final String ROTULO_DE_CONTEXTO = "etec:limite-de-tentativas:v1";

	/** Acima disto o valor é cortado antes do HMAC (o e-mail da coluna tem 255): limita o gasto com corpo enorme. */
	static final int TAMANHO_MAXIMO_DO_VALOR = 255;

	private final SecretKeySpec chave;

	public ChavesDoLimite(JwtProperties jwt) {
		byte[] segredo = jwt.secret().getBytes(StandardCharsets.UTF_8);
		this.chave = new SecretKeySpec(hmac(new SecretKeySpec(segredo, ALGORITMO),
				ROTULO_DE_CONTEXTO.getBytes(StandardCharsets.UTF_8)), ALGORITMO);
	}

	/** A chave (64 caracteres hexadecimais) do valor dentro do escopo. */
	public String de(Escopo escopo, String valor) {
		String cortado = valor.length() > TAMANHO_MAXIMO_DO_VALOR ? valor.substring(0, TAMANHO_MAXIMO_DO_VALOR) : valor;
		byte[] entrada = (escopo.name() + ":" + cortado).getBytes(StandardCharsets.UTF_8);
		return HexFormat.of().formatHex(hmac(chave, entrada));
	}

	private static byte[] hmac(SecretKeySpec chave, byte[] mensagem) {
		try {
			Mac mac = Mac.getInstance(ALGORITMO);
			mac.init(chave);
			return mac.doFinal(mensagem);
		}
		catch (GeneralSecurityException e) {
			throw new IllegalStateException("HMAC-SHA256 indisponível.", e);
		}
	}

	/** Nunca mostra a chave. */
	@Override
	public String toString() {
		return "ChavesDoLimite[***]";
	}

}
