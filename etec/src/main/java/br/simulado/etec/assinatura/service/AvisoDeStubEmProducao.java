package br.simulado.etec.assinatura.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import br.simulado.etec.shared.config.Perfis;

/**
 * Em produção com o STUB ativo, registra UM aviso por subida (D142): o stub libera os recursos que conhece a qualquer
 * STUDENT e ADMIN, e tem de ser trocado antes de cobrar. Só avisa: na fase 1 o stub é intencional e não derruba a subida.
 */
@Component
public class AvisoDeStubEmProducao implements SmartInitializingSingleton {

	private static final Logger LOG = LoggerFactory.getLogger(AvisoDeStubEmProducao.class);

	private final PoliticaDeEntitlements politica;

	private final Environment ambiente;

	public AvisoDeStubEmProducao(PoliticaDeEntitlements politica, Environment ambiente) {
		this.politica = politica;
		this.ambiente = ambiente;
	}

	@Override
	public void afterSingletonsInstantiated() {
		if (Perfis.emProducao(ambiente) && politica instanceof PoliticaStubLiberaTudo) {
			LOG.warn("Entitlements: a política STUB está ativa em produção (libera tudo o que conhece); troque-a por regras "
					+ "reais antes de cobrar.");
		}
	}

}
