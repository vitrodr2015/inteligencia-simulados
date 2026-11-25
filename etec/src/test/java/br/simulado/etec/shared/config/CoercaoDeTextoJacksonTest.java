package br.simulado.etec.shared.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Campo de texto só aceita texto (D86: tipo errado = 400). O Jackson, por padrão, converte número e booleano em
 * String em silêncio ({"senha":123} viraria "123"); com isso o signin responderia 401 em vez de 400 para um
 * {"email":123}. Usa o JsonMapper da aplicação, como o Spring MVC.
 */
@SpringBootTest
class CoercaoDeTextoJacksonTest {

	record Entrada(String texto) {
	}

	@Autowired
	private JsonMapper jsonMapper;

	@Test
	void deve_ler_o_texto_e_o_nulo_normalmente() {
		assertThat(jsonMapper.readValue("{\"texto\":\"abc\"}", Entrada.class).texto()).isEqualTo("abc");
		assertThat(jsonMapper.readValue("{\"texto\":null}", Entrada.class).texto()).isNull();
		assertThat(jsonMapper.readValue("{}", Entrada.class).texto()).isNull();
		assertThat(jsonMapper.readValue("{\"texto\":\"\"}", Entrada.class).texto()).isEmpty();
	}

	record EntradaInteira(Integer numero) {
	}

	@Test
	void deve_ler_o_inteiro_e_o_nulo_normalmente() {
		assertThat(jsonMapper.readValue("{\"numero\":2024}", EntradaInteira.class).numero()).isEqualTo(2024);
		assertThat(jsonMapper.readValue("{\"numero\":null}", EntradaInteira.class).numero()).isNull();
		assertThat(jsonMapper.readValue("{}", EntradaInteira.class).numero()).isNull();
	}

	@Test
	void deve_recusar_texto_decimal_booleano_lista_e_objeto_no_lugar_do_inteiro() {
		java.util.List<String> aceitosPorEngano = new java.util.ArrayList<>();
		for (String json : new String[] { "{\"numero\":\"2024\"}", "{\"numero\":\"\"}", "{\"numero\":2024.5}",
				"{\"numero\":2024.0}", "{\"numero\":true}", "{\"numero\":[1]}", "{\"numero\":{\"x\":1}}" }) {
			try {
				jsonMapper.readValue(json, EntradaInteira.class);
				aceitosPorEngano.add(json);
			}
			catch (JacksonException esperado) {
				// recusado: é o que se quer
			}
		}
		assertThat(aceitosPorEngano).as("entradas que o mapper aceitou em vez de recusar").isEmpty();
	}

	@Test
	void deve_recusar_numero_booleano_lista_e_objeto_no_lugar_do_texto() {
		for (String json : new String[] { "{\"texto\":123}", "{\"texto\":1.5}", "{\"texto\":true}", "{\"texto\":[\"a\"]}",
				"{\"texto\":{\"x\":1}}" }) {
			assertThatThrownBy(() -> jsonMapper.readValue(json, Entrada.class)).as(json)
					.isInstanceOf(JacksonException.class);
		}
	}

}
