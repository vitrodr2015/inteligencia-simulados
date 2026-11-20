package br.simulado.etec.shared.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

class ClockConfigTest {

	@Test
	void deve_usar_utc_quando_cria_o_relogio_da_aplicacao() {
		Clock relogio = new ClockConfig().clock();

		assertThat(relogio.getZone()).isEqualTo(ZoneOffset.UTC);
	}

	@Test
	void deve_devolver_o_instante_atual_quando_le_o_relogio_da_aplicacao() {
		Clock relogio = new ClockConfig().clock();

		assertThat(Duration.between(relogio.instant(), Instant.now()).abs()).isLessThan(Duration.ofSeconds(5));
	}

}
