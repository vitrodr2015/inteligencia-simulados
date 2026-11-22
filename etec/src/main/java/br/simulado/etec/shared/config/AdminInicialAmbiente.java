package br.simulado.etec.shared.config;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.core.env.Environment;

import br.simulado.etec.shared.validacao.EmailAsciiValidator;

/**
 * Lê e valida as variáveis do administrador inicial ({@code ADMIN_EMAIL} e {@code ADMIN_SENHA}, D45 e D91) pelo
 * {@link Environment} do Spring, do mesmo jeito que a {@link ValidacaoDeAmbiente} lê o {@code JWT_SECRET} (as chaves
 * planas do arquivo local só chegam por ele). Valor em branco conta como AUSENTE: uma variável vazia no ambiente do
 * processo sobrepõe o arquivo local, e é assim que se simula "sem variáveis".
 *
 * <p>Regras: e-mail aparado, em minúsculas, ASCII, formato válido e até 255 caracteres (as mesmas do cadastro, D105 e
 * D119); senha de 12 a 72 bytes UTF-8, sem espaço nas pontas e sem {@code \r} nem {@code \n} em posição alguma (D125). As
 * duas variáveis vêm juntas ou nenhuma.
 *
 * <p>Nada aqui devolve, registra ou põe em exceção o VALOR de uma variável: os motivos de recusa são textos fixos, e o
 * {@code toString} do resultado válido esconde o e-mail e a senha.
 */
public final class AdminInicialAmbiente {

	public static final String VARIAVEL_EMAIL = "ADMIN_EMAIL";

	public static final String VARIAVEL_SENHA = "ADMIN_SENHA";

	public static final int SENHA_MINIMA_EM_BYTES = 12;

	public static final int SENHA_MAXIMA_EM_BYTES = 72;

	static final int EMAIL_MAXIMO = 255;

	private AdminInicialAmbiente() {
	}

	/** O que o ambiente tem: nada, um par válido ou motivos de recusa. */
	public sealed interface Leitura permits Ausente, Valida, Invalida {
	}

	/** Nenhuma das duas variáveis foi definida (ou vieram em branco). */
	public record Ausente() implements Leitura {
	}

	/** Par válido: e-mail já normalizado e senha exatamente como veio. */
	public record Valida(String email, String senha) implements Leitura {

		/** Nunca mostra o e-mail nem a senha. */
		@Override
		public String toString() {
			return "AdminInicial[email=***, senha=***]";
		}

	}

	/** Recusada: só motivos fixos, sem nenhum valor. */
	public record Invalida(List<String> motivos) implements Leitura {

		public Invalida {
			motivos = List.copyOf(motivos);
		}

		/** Os motivos num texto só, para a mensagem de erro. */
		public String resumo() {
			return String.join("; ", motivos);
		}

	}

	public static Leitura ler(Environment ambiente) {
		String email = valor(ambiente, VARIAVEL_EMAIL);
		String senha = valor(ambiente, VARIAVEL_SENHA);
		if (email == null && senha == null) {
			return new Ausente();
		}
		List<String> motivos = new ArrayList<>();
		if (email == null) {
			motivos.add(VARIAVEL_EMAIL + " não foi definida (as duas variáveis vêm juntas ou nenhuma).");
		}
		else {
			motivos.addAll(motivosDoEmail(email));
		}
		if (senha == null) {
			motivos.add(VARIAVEL_SENHA + " não foi definida (as duas variáveis vêm juntas ou nenhuma).");
		}
		else {
			motivos.addAll(motivosDaSenha(senha));
		}
		if (!motivos.isEmpty()) {
			return new Invalida(motivos);
		}
		return new Valida(email.strip().toLowerCase(Locale.ROOT), senha);
	}

	/** {@code null} para ausente ou em branco. */
	private static String valor(Environment ambiente, String nome) {
		String texto = ambiente.getProperty(nome);
		return (texto == null || texto.isBlank()) ? null : texto;
	}

	private static List<String> motivosDoEmail(String bruto) {
		String email = bruto.strip().toLowerCase(Locale.ROOT);
		List<String> motivos = new ArrayList<>();
		if (email.length() > EMAIL_MAXIMO) {
			motivos.add(VARIAVEL_EMAIL + " tem mais de " + EMAIL_MAXIMO + " caracteres.");
		}
		else if (!new EmailAsciiValidator().isValid(email, null)) {
			motivos.add(VARIAVEL_EMAIL + " não é um e-mail válido só com caracteres ASCII.");
		}
		return motivos;
	}

	private static List<String> motivosDaSenha(String senha) {
		List<String> motivos = new ArrayList<>();
		int bytes = senha.getBytes(StandardCharsets.UTF_8).length;
		if (bytes < SENHA_MINIMA_EM_BYTES) {
			motivos.add(VARIAVEL_SENHA + " tem menos de " + SENHA_MINIMA_EM_BYTES + " bytes.");
		}
		else if (bytes > SENHA_MAXIMA_EM_BYTES) {
			motivos.add(VARIAVEL_SENHA + " tem mais de " + SENHA_MAXIMA_EM_BYTES + " bytes.");
		}
		if (Character.isWhitespace(senha.charAt(0)) || Character.isWhitespace(senha.charAt(senha.length() - 1))) {
			motivos.add(VARIAVEL_SENHA + " tem espaço, tabulação ou quebra de linha no início ou no fim.");
		}
		if (senha.indexOf('\n') >= 0 || senha.indexOf('\r') >= 0) {
			motivos.add(VARIAVEL_SENHA + " tem quebra de linha.");
		}
		return motivos;
	}

}
