package br.simulado.etec.shared.config;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Origens de navegador que podem chamar a API ({@code app.cors.allowed-origins}, variável {@code CORS_ALLOWED_ORIGINS},
 * separadas por vírgula). Vazia significa SEM CORS (nenhuma origem). A aplicação NÃO sobe com {@code *}, com origem fora
 * do formato {@code http(s)://domínio[:porta]} (nada de barra, caminho, consulta, usuário ou curinga) nem com uma
 * entrada vazia no meio da lista. A regra de produção (só https) é conferida na {@link CorsConfig}, que conhece o perfil.
 * Esquema e domínio são guardados em minúsculas, sem repetição.
 */
@ConfigurationProperties("app.cors")
public record CorsProperties(List<String> allowedOrigins) {

	/** Esquema, domínio (rótulos de letras, dígitos e hífen, sem hífen nas pontas) e porta opcional. */
	private static final Pattern ORIGEM = Pattern.compile(
			"^((?i:https?))://([A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?)*)(?::([0-9]{1,5}))?$");

	public CorsProperties {
		allowedOrigins = normalizar(allowedOrigins);
	}

	private static List<String> normalizar(List<String> brutas) {
		if (brutas == null) {
			return List.of();
		}
		// "CORS_ALLOWED_ORIGINS=" (vazia) chega como uma lista com um único texto em branco: significa "sem CORS".
		if (brutas.size() == 1 && brutas.get(0) != null && brutas.get(0).isBlank()) {
			return List.of();
		}
		LinkedHashSet<String> origens = new LinkedHashSet<>();
		for (String bruta : brutas) {
			origens.add(validar(bruta));
		}
		return List.copyOf(new ArrayList<>(origens));
	}

	private static String validar(String bruta) {
		if (bruta == null || bruta.isBlank()) {
			throw new IllegalArgumentException("app.cors.allowed-origins tem uma origem vazia.");
		}
		String origem = bruta.strip();
		if (origem.contains("*")) {
			throw new IllegalArgumentException(
					"app.cors.allowed-origins não aceita curinga (*): liste cada origem, no formato http(s)://domínio[:porta].");
		}
		Matcher partes = ORIGEM.matcher(origem);
		if (!partes.matches()) {
			throw new IllegalArgumentException("app.cors.allowed-origins: origem fora do formato http(s)://domínio[:porta]"
					+ " (sem barra, caminho, consulta ou usuário): " + origem);
		}
		if (partes.group(3) != null) {
			int porta = Integer.parseInt(partes.group(3));
			if (porta < 1 || porta > 65535) {
				throw new IllegalArgumentException("app.cors.allowed-origins: porta fora de 1 a 65535: " + origem);
			}
		}
		return partes.group(1).toLowerCase(Locale.ROOT) + "://" + partes.group(2).toLowerCase(Locale.ROOT)
				+ (partes.group(3) != null ? ":" + Integer.parseInt(partes.group(3)) : "");
	}

}
