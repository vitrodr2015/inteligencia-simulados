package br.simulado.etec.auth.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Apaga de hora em hora, em lotes, os refresh tokens vencidos há mais de 1 dia (D137). Linha revogada e ainda não vencida
 * NÃO é apagada: é ela que detecta o reuso. Só existe com {@code app.sessoes.limpeza-ativa=true} (ligada no yml real e
 * DESLIGADA no yml de teste, onde o teste chama o método do serviço direto, com o relógio de teste). Falha da limpeza só vira
 * aviso. Com várias instâncias, cada uma roda o mesmo DELETE, que é idempotente.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(prefix = "app.sessoes", name = "limpeza-ativa", havingValue = "true", matchIfMissing = true)
public class LimpezaDeSessoesAgendada {

	private static final Logger LOG = LoggerFactory.getLogger(LimpezaDeSessoesAgendada.class);

	private final SessaoService sessoes;

	public LimpezaDeSessoesAgendada(SessaoService sessoes) {
		this.sessoes = sessoes;
	}

	@Scheduled(initialDelayString = "PT10M", fixedDelayString = "PT1H")
	public void limpar() {
		try {
			LOG.debug("Limpeza de sessões: {} refresh vencidos apagados.", sessoes.limparVencidas());
		}
		catch (RuntimeException e) {
			LOG.warn("A limpeza dos refresh tokens vencidos falhou; as sessões continuam valendo.", e);
		}
	}

}
