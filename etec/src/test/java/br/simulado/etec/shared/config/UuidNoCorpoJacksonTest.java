package br.simulado.etec.shared.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * UUID em CORPO JSON só no formato canônico (D159). O Jackson, por padrão, aceita também outras formas (por exemplo, o UUID
 * em Base64 de 24 caracteres). Usa o JsonMapper da aplicação, como o Spring MVC.
 */
@SpringBootTest
class UuidNoCorpoJacksonTest {

	record Entrada(UUID id) {
	}

	@Autowired
	private JsonMapper jsonMapper;

	@Test
	void deve_ler_o_uuid_canonico_em_qualquer_caixa_e_o_nulo() {
		UUID esperado = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");

		assertThat(jsonMapper.readValue("{\"id\":\"123e4567-e89b-12d3-a456-426614174000\"}", Entrada.class).id()).isEqualTo(esperado);
		assertThat(jsonMapper.readValue("{\"id\":\"123E4567-E89B-12D3-A456-426614174000\"}", Entrada.class).id()).isEqualTo(esperado);
		assertThat(jsonMapper.readValue("{\"id\":null}", Entrada.class).id()).isNull();
		assertThat(jsonMapper.readValue("{}", Entrada.class).id()).isNull();
	}

	@Test
	void deve_recusar_toda_forma_que_nao_e_a_canonica() {
		List<String> aceitosPorEngano = new ArrayList<>();
		for (String valor : new String[] { "\"11111111-1111-1111-1111-11111111111\"", "\"1-1-1-1-1\"",
				"\"123e4567e89b12d3a456426614174000\"", "\"Ej5FZ+ibEtOkVkJmFBdAAA==\"", "\"\"", "\"   \"", "\"abc\"",
				"\" 123e4567-e89b-12d3-a456-426614174000\"", "123", "true", "[\"123e4567-e89b-12d3-a456-426614174000\"]",
				"{\"x\":1}" }) {
			try {
				jsonMapper.readValue("{\"id\":" + valor + "}", Entrada.class);
				aceitosPorEngano.add(valor);
			}
			catch (JacksonException esperado) {
				// recusado: é o que se quer
			}
		}
		assertThat(aceitosPorEngano).as("formas que o mapper aceitou em vez de recusar").isEmpty();
	}

}
