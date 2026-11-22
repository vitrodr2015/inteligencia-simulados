package br.simulado.etec.shared.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import tools.jackson.databind.json.JsonMapper;

/**
 * Formato global de datas (JacksonConfig): leitura estrita de LocalDate e saída de Instant em UTC
 * com exatamente três casas de milissegundos. Usa um controller falso, só nos testes, com um
 * campo LocalDate, porque a Matéria não tem data de entrada.
 */
@SpringBootTest
// SEM filtros de segurança (addFilters = false): este teste cobre o CONTRATO da rota e NÃO testa segurança.
// O 401 e o 403 da cadeia são provados nos testes de integração, com os filtros ligados.
@AutoConfigureMockMvc(addFilters = false)
@Import(DatasJacksonTest.ControllerDeDatasFalso.class)
class DatasJacksonTest {

	private static final String DATA_HORA_COM_3_CASAS = "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}Z";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JsonMapper jsonMapper;

	@RestController
	@RequestMapping("/api/v1/teste-datas")
	public static class ControllerDeDatasFalso {

		public record Entrada(LocalDate data) {
		}

		public record Saida(Instant instante) {
		}

		@PostMapping
		public Entrada eco(@RequestBody Entrada entrada) {
			return entrada;
		}

		@GetMapping("/instante-sem-milissegundos")
		public Saida semMilissegundos() {
			return new Saida(Instant.parse("2026-11-20T15:04:05Z"));
		}

	}

	private org.springframework.test.web.servlet.ResultActions enviarData(String data) throws Exception {
		return mockMvc.perform(post("/api/v1/teste-datas").contentType(MediaType.APPLICATION_JSON)
				.content("{\"data\":\"" + data + "\"}"));
	}

	// ----- LocalDate: leitura estrita (D60) -----

	@Test
	void deve_aceitar_a_data_quando_o_formato_e_uuuu_mm_dd_e_a_data_existe() throws Exception {
		enviarData("2000-02-29").andExpect(status().isOk()).andExpect(jsonPath("$.data").value("2000-02-29"));
	}

	/** D60: texto fora do formato ou data inexistente = 400 VALIDACAO_FALHOU, campos[campo] e a mensagem exata. */
	private void deveSerDataInvalida(org.springframework.test.web.servlet.ResultActions resposta) throws Exception {
		resposta.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.codigo").value("VALIDACAO_FALHOU"))
				.andExpect(jsonPath("$.status").value(400))
				.andExpect(jsonPath("$.erro").value("Bad Request"))
				.andExpect(jsonPath("$.mensagem").value("Há campos inválidos na requisição."))
				.andExpect(jsonPath("$.campos.length()").value(1))
				.andExpect(jsonPath("$.campos[0].campo").value("data"))
				.andExpect(jsonPath("$.campos[0].mensagem").value("Data inválida. Use o formato aaaa-mm-dd."))
				.andExpect(jsonPath("$.dataHora", matchesPattern(DATA_HORA_COM_3_CASAS)));
	}

	@Test
	void deve_devolver_400_validacao_falhou_quando_a_data_nao_existe() throws Exception {
		deveSerDataInvalida(enviarData("2000-02-30"));
	}

	@Test
	void deve_devolver_400_validacao_falhou_quando_29_de_fevereiro_cai_em_ano_nao_bissexto() throws Exception {
		deveSerDataInvalida(enviarData("2001-02-29"));
	}

	@Test
	void deve_devolver_400_validacao_falhou_quando_a_data_nao_tem_zeros_a_esquerda() throws Exception {
		deveSerDataInvalida(enviarData("2000-2-3"));
	}

	@Test
	void deve_devolver_400_validacao_falhou_quando_o_mes_e_invalido() throws Exception {
		deveSerDataInvalida(enviarData("2000-13-01"));
	}

	@Test
	void deve_devolver_400_validacao_falhou_quando_o_dia_e_trinta_e_um_em_mes_de_trinta_dias() throws Exception {
		deveSerDataInvalida(enviarData("2000-04-31"));
	}

	@Test
	void deve_devolver_400_validacao_falhou_quando_a_data_vem_com_hora_espaco_ou_barras() throws Exception {
		deveSerDataInvalida(enviarData("2000-01-01T00:00:00Z"));
		deveSerDataInvalida(enviarData(" 2000-01-01"));
		deveSerDataInvalida(enviarData("01/01/2000"));
		deveSerDataInvalida(enviarData("texto qualquer"));
		deveSerDataInvalida(enviarData("2000-01-01 "));
		deveSerDataInvalida(enviarData("+12345-01-01"));
		deveSerDataInvalida(enviarData(""));
	}

	@Test
	void deve_nunca_repetir_o_valor_enviado_na_resposta_de_data_invalida() throws Exception {
		enviarData("2000-02-30-VALOR-SENSIVEL").andExpect(status().isBadRequest())
				.andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("VALOR-SENSIVEL"))));
	}

	/** D86: valor que não é texto continua CORPO_ILEGIVEL, sem campos. */
	@Test
	void deve_devolver_corpo_ilegivel_quando_a_data_nao_e_texto() throws Exception {
		for (String valor : new String[] { "20000101", "true", "[2000,1,1]", "{\"ano\":2000}" }) {
			mockMvc.perform(post("/api/v1/teste-datas").contentType(MediaType.APPLICATION_JSON)
					.content("{\"data\":" + valor + "}")).andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.codigo").value("CORPO_ILEGIVEL")).andExpect(jsonPath("$.campos").doesNotExist());
		}
	}

	// ----- Instant: saída em UTC com três casas -----

	@Test
	void deve_serializar_com_tres_casas_quando_os_milissegundos_sao_zero() throws Exception {
		String json = jsonMapper.writeValueAsString(new ControllerDeDatasFalso.Saida(Instant.parse("2026-11-20T15:04:05Z")));

		assertThat(json).isEqualTo("{\"instante\":\"2026-11-20T15:04:05.000Z\"}");
	}

	@Test
	void deve_truncar_para_tres_casas_quando_ha_nanosegundos() throws Exception {
		String json = jsonMapper
				.writeValueAsString(new ControllerDeDatasFalso.Saida(Instant.parse("2026-11-20T15:04:05.123456789Z")));

		assertThat(json).isEqualTo("{\"instante\":\"2026-11-20T15:04:05.123Z\"}");
	}

	@Test
	void deve_manter_os_milissegundos_quando_ha() throws Exception {
		String json = jsonMapper
				.writeValueAsString(new ControllerDeDatasFalso.Saida(Instant.parse("2026-11-20T15:04:05.450Z")));

		assertThat(json).isEqualTo("{\"instante\":\"2026-11-20T15:04:05.450Z\"}");
	}

	@Test
	void deve_devolver_tres_casas_pela_api_quando_os_milissegundos_sao_zero() throws Exception {
		mockMvc.perform(get("/api/v1/teste-datas/instante-sem-milissegundos")).andExpect(status().isOk())
				.andExpect(jsonPath("$.instante").value("2026-11-20T15:04:05.000Z"));
	}

}
