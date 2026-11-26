package br.simulado.etec.shared.config;

import java.util.List;
import java.util.Locale;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Hosts permitidos para {@code imagemUrl} das questões (D36, D46): a propriedade {@code app.imagens.hosts-permitidos}, definida por
 * ambiente (em desenvolvimento, localhost; onde as imagens ficam fisicamente é do módulo de infraestrutura). Lista de nomes de host
 * exatos, sem esquema, porta nem caminho, comparados sem distinguir maiúsculas. Vazia = nenhuma imagem é aceita.
 */
@ConfigurationProperties(prefix = "app.imagens")
public record ImagensProperties(List<String> hostsPermitidos) {

	public ImagensProperties {
		hostsPermitidos = (hostsPermitidos == null) ? List.of()
				: hostsPermitidos.stream().map(String::strip).filter(h -> !h.isEmpty()).map(h -> h.toLowerCase(Locale.ROOT))
						.toList();
	}

	public boolean permite(String host) {
		return host != null && hostsPermitidos.contains(host.toLowerCase(Locale.ROOT));
	}

}
