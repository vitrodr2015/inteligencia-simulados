package br.simulado.etec.shared.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.core.env.ConfigurableEnvironment;

/**
 * Recusa subir a aplicação com segredos ausentes ou malformados.
 *
 * <p>Lê as variáveis cruas (DB_PASSWORD, JWT_SECRET) e não as chaves do application.yml,
 * porque um placeholder não resolvido geraria outro erro, menos claro. As mensagens
 * nunca mostram o valor do segredo.
 */
public final class ValidacaoDeAmbiente {

	static final int TAMANHO_MINIMO_JWT_SECRET = 32;

	private static final String DB_PASSWORD = "DB_PASSWORD";
	private static final String JWT_SECRET = "JWT_SECRET";

	private ValidacaoDeAmbiente() {
	}

	/**
	 * @throws IllegalStateException se a configuração for inválida, listando todos os problemas
	 */
	public static void validar(ConfigurableEnvironment ambiente) {
		List<String> problemas = new ArrayList<>();

		if (temTexto(ambiente.getProperty("spring.datasource.url"))) {
			verificarPresencaEPontas(ambiente, DB_PASSWORD, problemas);
		}

		String jwtSecret = ambiente.getProperty(JWT_SECRET);
		if (verificarPresencaEPontas(ambiente, JWT_SECRET, problemas)
				&& jwtSecret.length() < TAMANHO_MINIMO_JWT_SECRET) {
			problemas.add(JWT_SECRET + " precisa ter pelo menos " + TAMANHO_MINIMO_JWT_SECRET + " caracteres.");
		}

		// Administrador inicial (D45, D91, D125): em PRODUÇÃO, variável inválida derruba a subida, ANTES de existir contexto e
		// de o servidor abrir. Fora de produção nada falha aqui (o bootstrap só registra o erro). A falta de admin com as
		// variáveis ausentes depende do banco e é conferida depois, ainda antes de o servidor aceitar conexões.
		if (Perfis.emProducao(ambiente)
				&& AdminInicialAmbiente.ler(ambiente) instanceof AdminInicialAmbiente.Invalida invalida) {
			problemas.add("Administrador inicial: " + invalida.resumo());
		}
		if (!problemas.isEmpty()) {
			StringBuilder mensagem = new StringBuilder("Configuração inválida, a aplicação não vai subir:");
			problemas.forEach(problema -> mensagem.append("\n - ").append(problema));
			mensagem.append("\nDefina as variáveis de ambiente ou use o perfil local ")
					.append("(config/application-local.yml, a partir de config/application-local.yml.example).");
			throw new IllegalStateException(mensagem.toString());
		}
	}

	/** Devolve true se a variável existe e não tem espaço ou quebra de linha nas pontas. */
	private static boolean verificarPresencaEPontas(ConfigurableEnvironment ambiente, String nome,
			List<String> problemas) {
		String valor = ambiente.getProperty(nome);
		if (valor == null || valor.isBlank()) {
			problemas.add(nome + " não está definida ou está vazia.");
			return false;
		}
		if (Character.isWhitespace(valor.charAt(0)) || Character.isWhitespace(valor.charAt(valor.length() - 1))) {
			problemas.add(nome + " tem espaço ou quebra de linha no início ou no fim; remova-os.");
			return false;
		}
		return true;
	}

	private static boolean temTexto(String texto) {
		return texto != null && !texto.isBlank();
	}

}
