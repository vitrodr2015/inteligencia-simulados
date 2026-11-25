package br.simulado.etec.assinatura.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Qual política de entitlements vale ({@code app.entitlements.politica}, D142). SEM valor padrão no código: propriedade
 * ausente ou com valor desconhecido derruba a subida (falhar cedo), e nunca cai num "libera tudo" silencioso.
 */
@ConfigurationProperties("app.entitlements")
public record EntitlementsProperties(Politica politica) {

	/** As políticas que existem. Cada valor novo exige um {@code case} em {@link EntitlementsConfig}. */
	public enum Politica {

		/** O stub da fase 1: {@link PoliticaStubLiberaTudo}. */
		STUB

	}

	public EntitlementsProperties {
		if (politica == null) {
			throw new IllegalArgumentException("app.entitlements.politica não está definida (valores aceitos: stub).");
		}
	}

}
