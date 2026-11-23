package br.simulado.etec.shared.security.limite;

import java.util.Locale;

/**
 * Regra da lista de proxies confiáveis ({@code server.tomcat.remoteip.internal-proxies}, uma expressão regular do Tomcat):
 * ela precisa ser EXPLÍCITA. Lista vazia (que ativaria os padrões privados do Tomcat) ou permissiva (casa qualquer
 * endereço) deixaria um cliente qualquer se passar por proxy e inventar a própria origem no {@code X-Forwarded-For}.
 */
public final class ProxiesConfiaveis {

	/** O nome das duas propriedades da válvula nativa do Tomcat. */
	public static final String CABECALHO = "server.tomcat.remoteip.remote-ip-header";

	public static final String LISTA = "server.tomcat.remoteip.internal-proxies";

	private ProxiesConfiaveis() {
	}

	/**
	 * {@code true} se a lista é vazia ou se ALGUMA alternativa (separada por {@code |}) casa qualquer endereço:
	 * {@code .*}, {@code .+}, {@code 0.0.0.0/0}, {@code ::/0} e variações com âncoras e parênteses.
	 */
	public static boolean permissiva(String lista) {
		if (lista == null || lista.isBlank()) {
			return true;
		}
		for (String alternativa : lista.split("\\|", -1)) {
			String nucleo = alternativa.strip().toLowerCase(Locale.ROOT).replaceAll("[\\^$()]", "");
			if (nucleo.isEmpty() || nucleo.equals(".*") || nucleo.equals(".+") || nucleo.equals(".*?")
					|| nucleo.equals(".+?") || nucleo.equals("0.0.0.0/0") || nucleo.equals("::/0")
					|| nucleo.equals("0\\.0\\.0\\.0/0")) {
				return true;
			}
		}
		return false;
	}

}
