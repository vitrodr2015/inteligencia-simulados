package br.simulado.etec.shared.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Termos de uso vigentes (D23, D94): {@code app.termos.versao} e {@code app.termos.url}, por ambiente. Sem valor
 * padrão no application.yml: faltando um dos dois, a aplicação não sobe. A versão cabe na coluna
 * {@code usuario.aceite_termos_versao} (20 caracteres).
 */
@ConfigurationProperties("app.termos")
public record TermosProperties(String versao, String url) {

	public static final int TAMANHO_MAXIMO_DA_VERSAO = 20;

	public TermosProperties {
		if (versao == null || versao.isBlank()) {
			throw new IllegalArgumentException("app.termos.versao não está definida.");
		}
		if (versao.length() > TAMANHO_MAXIMO_DA_VERSAO) {
			throw new IllegalArgumentException(
					"app.termos.versao deve ter no máximo " + TAMANHO_MAXIMO_DA_VERSAO + " caracteres.");
		}
		if (url == null || url.isBlank()) {
			throw new IllegalArgumentException("app.termos.url não está definida.");
		}
	}

}
