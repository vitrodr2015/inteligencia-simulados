package br.simulado.etec.shared.security.limite;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HexFormat;
import java.util.regex.Pattern;

import jakarta.servlet.http.HttpServletRequest;

/**
 * De onde veio a requisição, para o limite de tentativas (D130).
 *
 * <p><b>Só o endereço do socket</b> ({@code getRemoteAddr()}). O {@code X-Forwarded-For} NUNCA é lido aqui: quem o
 * inventa foge do limite. Atrás de um balanceador, o único jeito de o IP real valer é a válvula nativa do Tomcat
 * ({@code server.tomcat.remoteip.*}), ligada só por propriedade e com a lista EXPLÍCITA de proxies confiáveis: ela lê o
 * cabeçalho da direita para a esquerda, pulando os proxies confiáveis, e o {@code getRemoteAddr()} já chega corrigido.
 *
 * <p>Normalização: IPv4 como está; IPv6 pelo prefixo /64 (quem troca de endereço dentro do bloco não foge do limite);
 * IPv4 mapeado em IPv6 ({@code ::ffff:a.b.c.d}) vale como o IPv4. O resultado é só uma chave de agrupamento: nunca vai
 * para o log nem para o banco em claro.
 */
public class OrigemDaRequisicao {

	static final String DESCONHECIDA = "desconhecida";

	private static final Pattern IPV4 = Pattern.compile("\\d{1,3}(\\.\\d{1,3}){3}");

	private static final Pattern IPV6 = Pattern.compile("[0-9A-Fa-f:.]+");

	public String de(HttpServletRequest requisicao) {
		return normalizar(requisicao.getRemoteAddr());
	}

	static String normalizar(String endereco) {
		if (endereco == null || endereco.isBlank()) {
			return DESCONHECIDA;
		}
		String texto = endereco.strip();
		int zona = texto.indexOf('%');
		if (zona >= 0) {
			texto = texto.substring(0, zona);
		}
		// Só literais de IP chegam ao InetAddress: assim nunca há consulta de DNS.
		boolean ipv4 = IPV4.matcher(texto).matches();
		boolean ipv6 = texto.indexOf(':') >= 0 && IPV6.matcher(texto).matches();
		if (!ipv4 && !ipv6) {
			return DESCONHECIDA;
		}
		try {
			InetAddress ip = InetAddress.getByName(texto);
			if (ip instanceof Inet6Address) {
				return "v6:" + HexFormat.of().formatHex(ip.getAddress(), 0, 8);
			}
			return "v4:" + ip.getHostAddress();
		}
		catch (UnknownHostException e) {
			return DESCONHECIDA;
		}
	}

}
