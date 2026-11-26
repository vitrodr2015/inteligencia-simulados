package br.simulado.etec.catalogo.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

/**
 * Hash do conteúdo da questão (D51, D90): SHA-256 em hexadecimal minúsculo (64 caracteres) de EXATAMENTE estes campos, nesta ordem: id
 * PÚBLICO da prova (nunca o interno), numero, textoApoio, grupoApoio, enunciado, imagemUrl e as alternativas A a E. FORA do hash:
 * subtópico, dificuldade, explicação e gabarito (D90). Guardado com a questão e nunca muda (D51).
 *
 * <p>Forma canônica: cada texto é aparado e tem as quebras de linha normalizadas para {@code \n}; nulo vale vazio; e cada campo entra
 * como {@code nome:<bytes UTF-8>:valor\n}, com o COMPRIMENTO em bytes antes do valor, para que a concatenação de campos nunca dê
 * colisão (por exemplo, mover texto do fim de um campo para o começo do seguinte muda o hash). A primeira linha é a versão do formato
 * ({@code questao-v1}). Mudar QUALQUER coisa aqui muda o hash de tudo que já está gravado: não mude.
 */
public final class HashDaQuestao {

	private static final String[] LETRAS = { "A", "B", "C", "D", "E" };

	private HashDaQuestao() {
	}

	/**
	 * @param textoPorLetra o texto de cada alternativa, pela letra (A a E); letra ausente vale vazio (o service já exigiu as 5)
	 */
	public static String calcular(UUID provaPublicId, int numero, String textoApoio, Integer grupoApoio, String enunciado,
			String imagemUrl, Map<String, String> textoPorLetra) {
		StringBuilder canonico = new StringBuilder("questao-v1\n");
		campo(canonico, "prova", provaPublicId.toString().toLowerCase());
		campo(canonico, "numero", Integer.toString(numero));
		campo(canonico, "textoApoio", normalizar(textoApoio));
		campo(canonico, "grupoApoio", (grupoApoio == null) ? "" : Integer.toString(grupoApoio));
		campo(canonico, "enunciado", normalizar(enunciado));
		campo(canonico, "imagemUrl", normalizar(imagemUrl));
		for (String letra : LETRAS) {
			campo(canonico, "alternativa" + letra, normalizar(textoPorLetra.get(letra)));
		}
		return sha256(canonico.toString());
	}

	/** Apara as pontas, converte {@code \r\n} e {@code \r} em {@code \n}; nulo vira vazio. */
	static String normalizar(String texto) {
		if (texto == null) {
			return "";
		}
		return texto.replace("\r\n", "\n").replace('\r', '\n').strip();
	}

	private static void campo(StringBuilder saida, String nome, String valor) {
		saida.append(nome).append(':').append(valor.getBytes(StandardCharsets.UTF_8).length).append(':').append(valor).append('\n');
	}

	private static String sha256(String texto) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(texto.getBytes(StandardCharsets.UTF_8)));
		}
		catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 indisponível na JVM.", e);
		}
	}

}
