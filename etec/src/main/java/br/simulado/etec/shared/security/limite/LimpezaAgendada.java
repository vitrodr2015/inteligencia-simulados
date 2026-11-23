package br.simulado.etec.shared.security.limite;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Apaga de hora em hora as linhas de janela vencida. Uma janela vencida equivale a nenhuma linha (o contador reinicia),
 * então apagar só libera espaço; com a janela de no máximo 1 hora, nenhuma linha passa de 1 dia no banco. Com várias
 * instâncias, cada uma roda o mesmo DELETE, que é idempotente. Falha da limpeza só vira aviso: o limite não depende dela.
 */
public class LimpezaAgendada {

	private static final Logger LOG = LoggerFactory.getLogger(LimpezaAgendada.class);

	private final LimiteDeTentativas limite;

	public LimpezaAgendada(LimiteDeTentativas limite) {
		this.limite = limite;
	}

	@Scheduled(initialDelayString = "PT5M", fixedDelayString = "PT1H")
	public void limpar() {
		try {
			int apagadas = limite.limparVencidas();
			LOG.debug("Limpeza do limite: {} linhas vencidas apagadas.", apagadas);
		}
		catch (RuntimeException e) {
			LOG.warn("A limpeza dos contadores do limite falhou; o limite continua valendo.", e);
		}
	}

}
