package br.simulado.etec.shared.security.limite;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import br.simulado.etec.shared.config.Perfis;

/**
 * Em produção sem proxy confiável configurado, o limite usa o endereço do socket. Atrás de um balanceador isso é o
 * endereço DELE, e todos os usuários passariam a dividir o mesmo contador. Só um AVISO no log (pode não haver proxy), sem
 * derrubar a subida; o item de checklist do Módulo 17 cobra a configuração. Nada de IP ou valor de configuração no log.
 */
@Component
public class AvisoDeProxyEmProducao implements SmartInitializingSingleton {

	/** A propriedade da válvula nativa do Tomcat que liga a leitura do cabeçalho do proxy. */
	static final String PROPRIEDADE = "server.tomcat.remoteip.remote-ip-header";

	private static final Logger LOG = LoggerFactory.getLogger(AvisoDeProxyEmProducao.class);

	private final Environment ambiente;

	public AvisoDeProxyEmProducao(Environment ambiente) {
		this.ambiente = ambiente;
	}

	@Override
	public void afterSingletonsInstantiated() {
		if (Perfis.emProducao(ambiente) && !StringUtils.hasText(ambiente.getProperty(PROPRIEDADE))) {
			LOG.warn("Limite de tentativas: nenhum proxy confiável está configurado ({}); o limite usa o endereço do "
					+ "socket. Atrás de um balanceador, configure o proxy ou todos os usuários dividirão o mesmo limite.",
					PROPRIEDADE);
		}
	}

}
