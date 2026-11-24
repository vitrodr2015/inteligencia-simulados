package br.simulado.etec.shared.validacao;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A política de senha, num lugar só: usada pelo cadastro (aluno, D91), pelo bootstrap do administrador (D125) e pela troca de
 * senha (D139). Devolve as VIOLAÇÕES (nunca o valor): cada chamador monta o seu texto, e nada daqui guarda ou mostra a senha.
 *
 * <p>Aluno: de 8 a 72 bytes em UTF-8 (72 é o limite do BCrypt). Administrador: de 12 a 72 bytes, sem espaço nem tabulação nem
 * quebra de linha nas pontas, sem {@code \r} nem {@code \n} em posição alguma e que não seja marcador de exemplo.
 */
public final class PoliticaDeSenha {

	public static final int MINIMO_DO_ALUNO_EM_BYTES = 8;

	public static final int MINIMO_DO_ADMIN_EM_BYTES = 12;

	public static final int MAXIMO_EM_BYTES = 72;

	/** O que a senha viola. */
	public enum Violacao {

		MUITO_CURTA, MUITO_LONGA, ESPACO_NAS_PONTAS, QUEBRA_DE_LINHA, MARCADOR_DE_EXEMPLO

	}

	private PoliticaDeSenha() {
	}

	/** Aluno (D91): só o tamanho, de 8 a 72 bytes. */
	public static List<Violacao> verificarDoAluno(String senha) {
		List<Violacao> violacoes = new ArrayList<>();
		tamanho(senha, MINIMO_DO_ALUNO_EM_BYTES, violacoes);
		return violacoes;
	}

	/** Administrador (D125): tamanho de 12 a 72, pontas, quebra de linha e marcador de exemplo. */
	public static List<Violacao> verificarDoAdmin(String senha) {
		List<Violacao> violacoes = new ArrayList<>();
		tamanho(senha, MINIMO_DO_ADMIN_EM_BYTES, violacoes);
		if (senha.isEmpty()) {
			return violacoes;
		}
		if (Character.isWhitespace(senha.charAt(0)) || Character.isWhitespace(senha.charAt(senha.length() - 1))) {
			violacoes.add(Violacao.ESPACO_NAS_PONTAS);
		}
		if (senha.indexOf('\n') >= 0 || senha.indexOf('\r') >= 0) {
			violacoes.add(Violacao.QUEBRA_DE_LINHA);
		}
		if (pareceMarcadorDeExemplo(senha)) {
			violacoes.add(Violacao.MARCADOR_DE_EXEMPLO);
		}
		return violacoes;
	}

	/**
	 * Placeholder do projeto ("troque-a-senha-do-admin", "<defina-aqui>"): começa com "troque-" ou tem "<" e ">", sem
	 * diferenciar maiúsculas. Quem copia um exemplo sem trocar o valor não pode virar administrador.
	 */
	public static boolean pareceMarcadorDeExemplo(String senha) {
		String minuscula = senha.strip().toLowerCase(Locale.ROOT);
		return minuscula.startsWith("troque-") || (minuscula.contains("<") && minuscula.contains(">"));
	}

	private static void tamanho(String senha, int minimo, List<Violacao> violacoes) {
		int bytes = senha.getBytes(StandardCharsets.UTF_8).length;
		if (bytes < minimo) {
			violacoes.add(Violacao.MUITO_CURTA);
		}
		else if (bytes > MAXIMO_EM_BYTES) {
			violacoes.add(Violacao.MUITO_LONGA);
		}
	}

}
