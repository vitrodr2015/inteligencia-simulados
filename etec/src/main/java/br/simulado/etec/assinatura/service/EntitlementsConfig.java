package br.simulado.etec.assinatura.service;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Cria a ÚNICA política de entitlements, escolhida por {@code app.entitlements.politica} (D142). A política nunca é
 * {@code @Component}: o único jeito de existir uma é este bean explícito, então nunca há duas por acidente.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(EntitlementsProperties.class)
public class EntitlementsConfig {

	@Bean
	public PoliticaDeEntitlements politicaDeEntitlements(EntitlementsProperties propriedades) {
		return switch (propriedades.politica()) {
			case STUB -> new PoliticaStubLiberaTudo();
		};
	}

}
