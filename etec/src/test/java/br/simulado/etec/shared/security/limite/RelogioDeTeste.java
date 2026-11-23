package br.simulado.etec.shared.security.limite;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Relógio CONTROLÁVEL dos testes: o tempo só anda quando o teste manda ({@link #avancar(Duration)}), nunca sozinho.
 * Nenhum teste do limite usa {@code Thread.sleep}. Seguro entre threads (o teste de 40 requisições paralelas lê o mesmo
 * relógio).
 */
public class RelogioDeTeste extends Clock {

	private final AtomicReference<Instant> agora;

	public RelogioDeTeste() {
		this(Instant.now());
	}

	public RelogioDeTeste(Instant inicio) {
		this.agora = new AtomicReference<>(inicio.truncatedTo(ChronoUnit.MILLIS));
	}

	public void avancar(Duration duracao) {
		agora.updateAndGet(i -> i.plus(duracao));
	}

	public void definir(Instant instante) {
		agora.set(instante.truncatedTo(ChronoUnit.MILLIS));
	}

	@Override
	public Instant instant() {
		return agora.get();
	}

	@Override
	public ZoneId getZone() {
		return ZoneOffset.UTC;
	}

	@Override
	public Clock withZone(ZoneId zona) {
		return this;
	}

}
