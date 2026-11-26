package br.simulado.etec.shared.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class UuidEstritoConfigTest {

	@Test
	void deve_ler_o_uuid_canonico_em_minusculas_e_maiusculas() {
		UUID esperado = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");

		assertThat(UuidEstritoConfig.paraUuid("123e4567-e89b-12d3-a456-426614174000")).isEqualTo(esperado);
		assertThat(UuidEstritoConfig.paraUuid("123E4567-E89B-12D3-A456-426614174000")).isEqualTo(esperado);
	}

	@ParameterizedTest
	@ValueSource(strings = { "11111111-1111-1111-1111-11111111111", "1-1-1-1-1", "1-1-1-1-111111111111",
			"123e4567e89b12d3a456426614174000", "123e4567-e89b-12d3-a456-4266141740000", "123e4567-e89b-12d3-a456-42661417400g",
			"", " ", "abc", " 123e4567-e89b-12d3-a456-426614174000", "123e4567-e89b-12d3-a456-426614174000 ",
			"+23e4567-e89b-12d3-a456-426614174000", "-23e4567-e89b-12d3-a456-426614174000" })
	void deve_recusar_o_que_nao_e_exatamente_8_4_4_4_12_hexadecimais(String texto) {
		// o UUID.fromString do Java aceita vários destes (grupos curtos, sinais); o parser estrito não
		assertThatThrownBy(() -> UuidEstritoConfig.paraUuid(texto)).as(texto).isInstanceOf(IllegalArgumentException.class);
	}

}
