package br.simulado.etec.catalogo.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.simulado.etec.catalogo.model.Materia;
import br.simulado.etec.catalogo.service.MateriaService;
import br.simulado.etec.shared.config.JacksonConfig;
import br.simulado.etec.shared.config.SomenteJsonConfig;
import br.simulado.etec.shared.exception.CodigoErro;
import br.simulado.etec.shared.exception.ConflictException;

/**
 * Formato da resposta, em TEXTO EXATO (a string inteira, não campo a campo): ordem dos campos, nulos
 * presentes, datas em UTC com 3 casas, JSON compacto e Content-Type. O service é um mock e o Clock é fixo,
 * então id e datas são conhecidos e o texto esperado é literal. Se alguém mudar a ordem de um campo, o
 * tratamento de nulos ou o formato das datas, estes testes quebram.
 */
@WebMvcTest(controllers = { MateriaController.class, MateriaFormatoWebTest.ControllerSemProduces.class })
@Import({ JacksonConfig.class, SomenteJsonConfig.class, MateriaFormatoWebTest.ControllerSemProduces.class })
class MateriaFormatoWebTest {

	private static final String ID = "11111111-1111-1111-1111-111111111111";

	private static final String OUTRO_ID = "22222222-2222-2222-2222-222222222222";

	/** Milissegundos zero: a data precisa sair com ".000". */
	private static final Instant CRIADO_EM = Instant.parse("2026-11-20T15:04:05Z");

	/** Nanossegundos: precisam ser truncados para ".123". */
	private static final Instant ATUALIZADO_EM = Instant.parse("2026-11-20T16:30:00.123456789Z");

	private static final String DATA_DO_ERRO = "2026-11-20T15:04:05.000Z";

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private MateriaService servico;

	@TestConfiguration
	static class RelogioFixo {

		@Bean
		Clock clock() {
			return Clock.fixed(CRIADO_EM, ZoneOffset.UTC);
		}

	}

	/** Controller SEM produces: prova que o "só JSON" vale mesmo quando alguém esquece de declará-lo. */
	@RestController
	@RequestMapping("/api/v1/teste-formato")
	public static class ControllerSemProduces {

		public record Item(String nome) {
		}

		@GetMapping("/sem-produces")
		public Item semProduces() {
			return new Item("x");
		}

	}

	private static Materia materia(String id, String nome, String descricao, boolean ativo) {
		Materia materia = new Materia(nome, descricao);
		ReflectionTestUtils.setField(materia, "publicId", UUID.fromString(id));
		ReflectionTestUtils.setField(materia, "criadoEm", CRIADO_EM);
		ReflectionTestUtils.setField(materia, "atualizadoEm", ATUALIZADO_EM);
		if (!ativo) {
			materia.desativar();
		}
		return materia;
	}

	private static String corpo(ResultActions resposta) throws Exception {
		// UTF-8 explícito: o Content-Type é application/json, sem charset.
		return resposta.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
	}

	private static final String JSON_DA_MATERIA_COM_DESCRICAO = "{\"id\":\"" + ID + "\",\"nome\":\"Matemática\","
			+ "\"descricao\":\"Álgebra e geometria\",\"ativo\":true,\"criadoEm\":\"2026-11-20T15:04:05.000Z\","
			+ "\"atualizadoEm\":\"2026-11-20T16:30:00.123Z\"}";

	private static final String JSON_DA_MATERIA_SEM_DESCRICAO = "{\"id\":\"" + OUTRO_ID + "\",\"nome\":\"História\","
			+ "\"descricao\":null,\"ativo\":false,\"criadoEm\":\"2026-11-20T15:04:05.000Z\","
			+ "\"atualizadoEm\":\"2026-11-20T16:30:00.123Z\"}";

	// ----- (a) e (b): uma matéria -----

	@Test
	void deve_devolver_o_texto_exato_da_materia_quando_a_descricao_esta_preenchida() throws Exception {
		when(servico.buscar(UUID.fromString(ID))).thenReturn(materia(ID, "Matemática", "Álgebra e geometria", true));

		ResultActions resposta = mockMvc.perform(get("/api/v1/materias/" + ID));

		resposta.andExpect(status().isOk());
		assertThat(corpo(resposta)).isEqualTo(JSON_DA_MATERIA_COM_DESCRICAO);
	}

	@Test
	void deve_devolver_descricao_null_no_texto_exato_quando_a_descricao_e_nula() throws Exception {
		when(servico.buscar(UUID.fromString(OUTRO_ID))).thenReturn(materia(OUTRO_ID, "História", null, false));

		ResultActions resposta = mockMvc.perform(get("/api/v1/materias/" + OUTRO_ID));

		resposta.andExpect(status().isOk());
		assertThat(corpo(resposta)).isEqualTo(JSON_DA_MATERIA_SEM_DESCRICAO);
		assertThat(corpo(resposta)).contains("\"descricao\":null");
	}

	@Test
	void deve_devolver_o_content_type_exatamente_application_json_e_o_json_compacto() throws Exception {
		when(servico.buscar(UUID.fromString(ID))).thenReturn(materia(ID, "Matemática", "Álgebra e geometria", true));

		ResultActions resposta = mockMvc.perform(get("/api/v1/materias/" + ID));

		// O Spring NÃO acrescenta charset: o JSON é UTF-8 por definição.
		resposta.andExpect(header().string("Content-Type", "application/json"));
		assertThat(corpo(resposta)).doesNotContain("\n").doesNotContain("\r").doesNotContain("\": ").doesNotContain(", \"");
	}

	// ----- (c): envelope da listagem -----

	@Test
	void deve_devolver_o_texto_exato_do_envelope_quando_lista_as_materias() throws Exception {
		List<Materia> itens = List.of(materia(ID, "Matemática", "Álgebra e geometria", true),
				materia(OUTRO_ID, "História", null, false));
		when(servico.listar(any(), any(), any())).thenReturn(new PageImpl<>(itens, PageRequest.of(0, 20), 2));

		ResultActions resposta = mockMvc.perform(get("/api/v1/materias"));

		resposta.andExpect(status().isOk()).andExpect(header().string("Content-Type", "application/json"));
		assertThat(corpo(resposta)).isEqualTo("{\"conteudo\":[" + JSON_DA_MATERIA_COM_DESCRICAO + ","
				+ JSON_DA_MATERIA_SEM_DESCRICAO + "],\"pagina\":0,\"tamanho\":20,\"totalElementos\":2,\"totalPaginas\":1}");
	}

	@Test
	void deve_devolver_o_texto_exato_do_envelope_vazio_quando_a_pagina_esta_alem_do_fim() throws Exception {
		when(servico.listar(any(), any(), any())).thenReturn(new PageImpl<>(List.of(), PageRequest.of(99, 10), 5));

		ResultActions resposta = mockMvc.perform(get("/api/v1/materias").param("page", "99").param("size", "10"));

		assertThat(corpo(resposta))
				.isEqualTo("{\"conteudo\":[],\"pagina\":99,\"tamanho\":10,\"totalElementos\":5,\"totalPaginas\":1}");
	}

	// ----- (d): erros 409 e 400 -----

	@Test
	void deve_devolver_o_texto_exato_do_409_com_detalhes_na_ordem_do_erro() throws Exception {
		Map<String, Object> detalhes = new LinkedHashMap<>();
		detalhes.put("tentativaId", "abc-123");
		detalhes.put("prazoEm", "2026-11-20T15:04:05.000Z");
		when(servico.criar(any()))
				.thenThrow(new ConflictException(CodigoErro.CONFLITO, "Já existe uma tentativa em andamento.", detalhes));

		ResultActions resposta = mockMvc.perform(post("/api/v1/materias").contentType(MediaType.APPLICATION_JSON)
				.content("{\"nome\":\"Biologia\",\"descricao\":null}"));

		resposta.andExpect(status().isConflict()).andExpect(header().string("Content-Type", "application/json"));
		assertThat(corpo(resposta)).isEqualTo("{\"dataHora\":\"" + DATA_DO_ERRO + "\",\"status\":409,"
				+ "\"erro\":\"Conflict\",\"codigo\":\"CONFLITO\",\"mensagem\":\"Já existe uma tentativa em andamento.\","
				+ "\"detalhes\":{\"tentativaId\":\"abc-123\",\"prazoEm\":\"2026-11-20T15:04:05.000Z\"}}");
	}

	@Test
	void deve_devolver_o_texto_exato_do_400_com_campos_e_sem_detalhes() throws Exception {
		ResultActions resposta = mockMvc.perform(post("/api/v1/materias").contentType(MediaType.APPLICATION_JSON)
				.content("{\"nome\":\"\",\"descricao\":null}"));

		resposta.andExpect(status().isBadRequest());
		assertThat(corpo(resposta)).isEqualTo("{\"dataHora\":\"" + DATA_DO_ERRO + "\",\"status\":400,"
				+ "\"erro\":\"Bad Request\",\"codigo\":\"VALIDACAO_FALHOU\","
				+ "\"mensagem\":\"Há campos inválidos na requisição.\","
				+ "\"campos\":[{\"campo\":\"nome\",\"mensagem\":\"O nome deve ter de 2 a 100 caracteres.\"}]}");
	}

	@Test
	void deve_omitir_campos_e_detalhes_no_texto_exato_quando_eles_sao_nulos() throws Exception {
		when(servico.buscar(any())).thenThrow(new br.simulado.etec.shared.exception.ResourceNotFoundException(
				CodigoErro.MATERIA_NAO_ENCONTRADA, "Matéria não encontrada."));

		ResultActions resposta = mockMvc.perform(get("/api/v1/materias/" + ID));

		assertThat(corpo(resposta)).isEqualTo("{\"dataHora\":\"" + DATA_DO_ERRO + "\",\"status\":404,"
				+ "\"erro\":\"Not Found\",\"codigo\":\"MATERIA_NAO_ENCONTRADA\",\"mensagem\":\"Matéria não encontrada.\"}");
	}

	// ----- (e): 406 para XML e YAML -----

	private static final String JSON_DO_406 = "{\"dataHora\":\"" + DATA_DO_ERRO + "\",\"status\":406,"
			+ "\"erro\":\"Not Acceptable\",\"codigo\":\"FORMATO_NAO_ACEITO\","
			+ "\"mensagem\":\"Formato de resposta não aceito. Esta API responde apenas em JSON.\"}";

	@Test
	void deve_devolver_o_texto_exato_do_406_em_json_quando_o_accept_pede_xml() throws Exception {
		ResultActions resposta = mockMvc.perform(get("/api/v1/materias/" + ID).accept(MediaType.APPLICATION_XML));

		resposta.andExpect(status().isNotAcceptable()).andExpect(header().string("Content-Type", "application/json"));
		assertThat(corpo(resposta)).isEqualTo(JSON_DO_406);
	}

	@Test
	void deve_devolver_o_texto_exato_do_406_em_json_quando_o_accept_pede_yaml() throws Exception {
		for (String yaml : List.of("application/yaml", "application/x-yaml", "text/yaml")) {
			ResultActions resposta = mockMvc.perform(get("/api/v1/materias/" + ID).header("Accept", yaml));

			resposta.andExpect(status().isNotAcceptable()).andExpect(header().string("Content-Type", "application/json"));
			assertThat(corpo(resposta)).as("Accept: %s", yaml).isEqualTo(JSON_DO_406);
		}
	}

	@Test
	void deve_devolver_406_em_json_para_xml_e_yaml_mesmo_num_controller_sem_produces() throws Exception {
		for (String tipo : List.of("application/xml", "text/xml", "application/yaml", "application/x-yaml")) {
			ResultActions resposta = mockMvc.perform(get("/api/v1/teste-formato/sem-produces").header("Accept", tipo));

			resposta.andExpect(status().isNotAcceptable());
			assertThat(corpo(resposta)).as("Accept: %s", tipo).isEqualTo(JSON_DO_406);
		}
	}

	@Test
	void deve_responder_json_quando_o_accept_e_json_ou_esta_ausente_num_controller_sem_produces() throws Exception {
		ResultActions comAccept = mockMvc.perform(get("/api/v1/teste-formato/sem-produces").accept(MediaType.APPLICATION_JSON));
		ResultActions semAccept = mockMvc.perform(get("/api/v1/teste-formato/sem-produces"));

		assertThat(corpo(comAccept)).isEqualTo("{\"nome\":\"x\"}");
		assertThat(corpo(semAccept)).isEqualTo("{\"nome\":\"x\"}");
		semAccept.andExpect(header().string("Content-Type", "application/json"));
	}

}
