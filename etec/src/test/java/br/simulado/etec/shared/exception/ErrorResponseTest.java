package br.simulado.etec.shared.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import br.simulado.etec.shared.exception.ErrorResponse.CampoInvalido;
import tools.jackson.databind.json.JsonMapper;

class ErrorResponseTest {

	private final JsonMapper json = JsonMapper.builder().build();

	@Test
	void deve_manter_tres_casas_de_milissegundos_quando_os_milissegundos_sao_zero() {
		Instant semMilissegundos = Instant.parse("2026-11-20T15:04:05Z");

		assertThat(ErrorResponse.formatar(semMilissegundos)).isEqualTo("2026-11-20T15:04:05.000Z");
	}

	@Test
	void deve_formatar_com_tres_casas_quando_ha_milissegundos() {
		assertThat(ErrorResponse.formatar(Instant.parse("2026-11-20T15:04:05.123Z")))
				.isEqualTo("2026-11-20T15:04:05.123Z");
	}

	@Test
	void deve_truncar_para_tres_casas_quando_ha_mais_precisao() {
		assertThat(ErrorResponse.formatar(Instant.parse("2026-11-20T15:04:05.123456789Z")))
				.isEqualTo("2026-11-20T15:04:05.123Z");
	}

	@Test
	void deve_serializar_a_data_com_tres_casas_no_json_quando_os_milissegundos_sao_zero() {
		Clock relogio = Clock.fixed(Instant.parse("2026-11-20T15:04:05Z"), ZoneOffset.UTC);
		ErrorResponse erro = ErrorResponse.de(relogio, 409, CodigoErro.CONFLITO, "Conflito.", null, null);

		assertThat(json.writeValueAsString(erro)).contains("\"dataHora\":\"2026-11-20T15:04:05.000Z\"");
	}

	@Test
	void deve_omitir_campos_e_detalhes_do_json_quando_sao_nulos() {
		Clock relogio = Clock.fixed(Instant.parse("2026-11-20T15:04:05Z"), ZoneOffset.UTC);
		ErrorResponse erro = ErrorResponse.de(relogio, 404, CodigoErro.RECURSO_NAO_ENCONTRADO, "Não encontrado.",
				null, null);

		String texto = json.writeValueAsString(erro);

		assertThat(texto).doesNotContain("campos").doesNotContain("detalhes");
		assertThat(texto).contains("\"status\":404", "\"erro\":\"Not Found\"",
				"\"codigo\":\"RECURSO_NAO_ENCONTRADO\"", "\"mensagem\":\"Não encontrado.\"");
	}

	@Test
	void deve_incluir_campos_e_detalhes_no_json_quando_informados() {
		Clock relogio = Clock.fixed(Instant.parse("2026-11-20T15:04:05Z"), ZoneOffset.UTC);
		ErrorResponse erro = ErrorResponse.de(relogio, 400, CodigoErro.VALIDACAO_FALHOU, "Campos inválidos.",
				List.of(new CampoInvalido("nome", "não deve estar em branco")), Map.of("tentativaId", "abc"));

		String texto = json.writeValueAsString(erro);

		assertThat(texto).contains("\"campos\":[{\"campo\":\"nome\",\"mensagem\":\"não deve estar em branco\"}]",
				"\"detalhes\":{\"tentativaId\":\"abc\"}");
	}

	@Test
	void deve_usar_a_frase_http_padrao_em_ingles_quando_monta_o_erro() {
		Clock relogio = Clock.fixed(Instant.parse("2026-11-20T15:04:05Z"), ZoneOffset.UTC);

		assertThat(ErrorResponse.de(relogio, 409, CodigoErro.CONFLITO, "x", null, null).erro()).isEqualTo("Conflict");
		assertThat(ErrorResponse.de(relogio, 405, CodigoErro.METODO_NAO_PERMITIDO, "x", null, null).erro())
				.isEqualTo("Method Not Allowed");
	}

}
