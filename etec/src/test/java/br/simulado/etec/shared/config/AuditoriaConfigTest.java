package br.simulado.etec.shared.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.springframework.data.auditing.DateTimeProvider;

class AuditoriaConfigTest {

	private static DateTimeProvider provedorCom(String instante) {
		Clock relogio = Clock.fixed(Instant.parse(instante), ZoneOffset.UTC);
		return new AuditoriaConfig().auditoriaDateTimeProvider(relogio);
	}

	@Test
	void deve_truncar_para_milissegundos_quando_o_clock_tem_mais_precisao() {
		// O MySQL arredondaria .8509 para .851 em DATETIME(3); a API trunca. Truncar na origem evita a diferença.
		DateTimeProvider provedor = provedorCom("2026-11-20T17:45:18.850999999Z");

		assertThat(provedor.getNow()).contains(Instant.parse("2026-11-20T17:45:18.850Z"));
	}

	@Test
	void deve_manter_o_instante_quando_ja_esta_em_milissegundos() {
		assertThat(provedorCom("2026-11-20T15:04:05.123Z").getNow()).contains(Instant.parse("2026-11-20T15:04:05.123Z"));
	}

}
