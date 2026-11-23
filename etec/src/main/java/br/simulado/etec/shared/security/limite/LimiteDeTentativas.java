package br.simulado.etec.shared.security.limite;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.ConcurrencyFailureException;

import br.simulado.etec.shared.exception.LimiteExcedidoException;
import br.simulado.etec.shared.security.limite.RateLimitProperties.Regra;

/**
 * Regra do limite de tentativas: conta ANTES (para valer mesmo sob concorrência), recusa com 429 acima do limite e deixa
 * os contadores expirarem sozinhos (nunca há bloqueio permanente). Não sabe de e-mail nem de signin: quem usa decide o
 * escopo e o valor. O log só diz o ESCOPO excedido, em DEBUG: nunca a origem, o e-mail, a chave ou o contador.
 */
public class LimiteDeTentativas {

	private static final Logger LOG = LoggerFactory.getLogger(LimiteDeTentativas.class);

	/**
	 * Retry-After quando o contador não consegue gravar por disputa de lock (deadlock que se repete): fixo e curto, sem dizer o
	 * motivo. Falha FECHADA: sem contar não se deixa passar.
	 */
	static final long RETRY_AFTER_POR_CONTENCAO_SEGUNDOS = 5;

	/** Linhas apagadas por rodada da limpeza (evita um DELETE longo). */
	static final int LOTE_DA_LIMPEZA = 1000;

	private final ContadorDeTentativas contadores;

	private final ChavesDoLimite chaves;

	private final Clock relogio;

	public LimiteDeTentativas(ContadorDeTentativas contadores, ChavesDoLimite chaves, Clock relogio) {
		this.contadores = contadores;
		this.chaves = chaves;
		this.relogio = relogio;
	}

	/**
	 * Conta uma ocorrência e recusa se passou do limite.
	 *
	 * @throws LimiteExcedidoException acima do limite, com o tempo até a janela fechar
	 */
	public void consumir(Escopo escopo, String valor, Regra regra) {
		Instant agora = relogio.instant();
		ContadorDeTentativas.Estado estado;
		try {
			estado = contadores.contar(chaves.de(escopo, valor), regra.limite(), regra.janela(), agora);
		}
		catch (ConcurrencyFailureException e) {
			// Esgotou as repetições por deadlock ou espera de lock: NÃO deixa passar sem contar (nem vira 500). Trata como limite
			// excedido, com um Retry-After curto e fixo, e não expõe o motivo.
			LOG.debug("Contador indisponível por disputa de lock no escopo {}; recusando.", escopo);
			throw new LimiteExcedidoException(RETRY_AFTER_POR_CONTENCAO_SEGUNDOS);
		}
		if (estado.contador() > regra.limite()) {
			LOG.debug("Limite excedido no escopo {}.", escopo);
			throw new LimiteExcedidoException(segundosAte(estado.janelaFim(), agora));
		}
	}

	/** Devolve uma ocorrência ao contador (sem passar de zero). */
	public void devolver(Escopo escopo, String valor) {
		try {
			contadores.devolverUma(chaves.de(escopo, valor), relogio.instant());
		}
		catch (ConcurrencyFailureException e) {
			// A ocorrência já foi contada antes; não devolver só deixa o contador mais restritivo. O login não cai por isso.
			LOG.debug("Devolução ignorada por disputa de lock no escopo {}.", escopo);
		}
	}

	/** Zera o contador da chave. */
	public void zerar(Escopo escopo, String valor) {
		try {
			contadores.apagar(chaves.de(escopo, valor));
		}
		catch (ConcurrencyFailureException e) {
			// Mesmo raciocínio: não zerar só deixa o contador mais restritivo; o login já foi decidido.
			LOG.debug("Zerar ignorado por disputa de lock no escopo {}.", escopo);
		}
	}

	/** Apaga as linhas vencidas, em lotes; devolve o total. */
	public int limparVencidas() {
		int total = 0;
		int apagadas;
		do {
			apagadas = contadores.limparVencidas(relogio.instant(), LOTE_DA_LIMPEZA);
			total += apagadas;
		}
		while (apagadas >= LOTE_DA_LIMPEZA);
		return total;
	}

	/** Segundos até o instante, arredondados para CIMA (nunca menos de 1). */
	static long segundosAte(Instant fim, Instant agora) {
		Duration falta = Duration.between(agora, fim);
		long segundos = falta.getSeconds() + (falta.getNano() > 0 ? 1 : 0);
		return Math.max(1, segundos);
	}

}
