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
@AutoConfigureMockMvc
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

	// ----- LocalDate: leitura estrita -----

	@Test
	void deve_aceitar_a_data_quando_o_formato_e_uuuu_mm_dd_e_a_data_existe() throws Exception {
		enviarData("2000-02-29").andExpect(status().isOk()).andExpect(jsonPath("$.data").value("2000-02-29"));
	}

	@Test
	void deve_devolver_400_quando_a_data_nao_existe() throws Exception {
		enviarData("2000-02-30").andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.codigo").value("CORPO_ILEGIVEL"))
				.andExpect(jsonPath("$.status").value(400))
				.andExpect(jsonPath("$.erro").value("Bad Request"))
				.andExpect(jsonPath("$.dataHora", matchesPattern(DATA_HORA_COM_3_CASAS)));
	}

	@Test
	void deve_devolver_400_quando_29_de_fevereiro_cai_em_ano_nao_bissexto() throws Exception {
		enviarData("2001-02-29").andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("CORPO_ILEGIVEL"));
	}

	@Test
	void deve_devolver_400_quando_a_data_nao_tem_zeros_a_esquerda() throws Exception {
		enviarData("2000-2-3").andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("CORPO_ILEGIVEL"));
	}

	@Test
	void deve_devolver_400_quando_o_mes_e_invalido() throws Exception {
		enviarData("2000-13-01").andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("CORPO_ILEGIVEL"));
	}

	@Test
	void deve_devolver_400_quando_o_dia_e_trinta_e_um_em_mes_de_trinta_dias() throws Exception {
		enviarData("2000-04-31").andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("CORPO_ILEGIVEL"));
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
