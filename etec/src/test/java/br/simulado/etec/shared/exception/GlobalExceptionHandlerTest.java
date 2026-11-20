package br.simulado.etec.shared.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.validation.annotation.Validated;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Prova que todo erro sai no mesmo formato. O controller é falso e vive dentro deste teste; o
 * Clock é fixo, com milissegundos zero, para provar que o dataHora vem dele e mantém as três casas.
 */
@WebMvcTest
@Import({ GlobalExceptionHandlerTest.ControllerFalso.class, GlobalExceptionHandlerTest.ControllerFalsoValidado.class })
class GlobalExceptionHandlerTest {

	private static final String DATA_ESPERADA = "2026-11-20T15:04:05.000Z";

	private static final String TEXTO_SENSIVEL = "SELECT senha_hash FROM usuario WHERE email = 'a@b.com' "
			+ "(br.simulado.etec.usuarios.model.Usuario)";

	@Autowired
	private MockMvc mockMvc;

	@TestConfiguration
	static class RelogioFixo {

		@Bean
		Clock clock() {
			return Clock.fixed(Instant.parse("2026-11-20T15:04:05Z"), ZoneOffset.UTC);
		}

	}

	@RestController
	@RequestMapping("/api/v1/teste")
	public static class ControllerFalso {

		public record Entrada(@NotBlank String nome, @NotNull @Min(1) Integer idade) {
		}

		@PostMapping(path = "/corpo", consumes = MediaType.APPLICATION_JSON_VALUE)
		public void receber(@Valid @RequestBody Entrada entrada) {
		}

		@GetMapping("/parametro")
		public String parametro(@RequestParam int numero) {
			return "ok";
		}

		@GetMapping("/validado")
		public String validado(@RequestParam @Size(min = 3) String codigo) {
			return "ok";
		}

		@GetMapping("/uuid/{id}")
		public String porUuid(@PathVariable UUID id) {
			return id.toString();
		}

		@GetMapping("/nao-encontrado")
		public String naoEncontrado() {
			throw new ResourceNotFoundException("Matéria não encontrada.");
		}

		@GetMapping("/requisicao-invalida")
		public String requisicaoInvalida() {
			throw new InvalidRequestException("A requisição não pode ser atendida.");
		}

		@GetMapping("/conflito")
		public String conflito() {
			throw new ConflictException(CodigoErro.CONFLITO, "Já existe uma tentativa em andamento.",
					Map.of("tentativaId", "abc-123"));
		}

		@GetMapping("/erro")
		public String erro() {
			throw new IllegalStateException(TEXTO_SENSIVEL);
		}

		@GetMapping(path = "/so-json", produces = MediaType.APPLICATION_JSON_VALUE)
		public Map<String, String> soJson() {
			return Map.of("ok", "sim");
		}

	}

	/**
	 * Com {@code @Validated} na classe, a validação é feita por proxy (AOP) e lança
	 * {@link ConstraintViolationException}, em vez da HandlerMethodValidationException do Spring.
	 */
	@RestController
	@Validated
	@RequestMapping("/api/v1/teste-validado")
	public static class ControllerFalsoValidado {

		@GetMapping("/restricao")
		public String restricao(@RequestParam @Min(1) int numero) {
			return "ok";
		}

	}

	private static ResultActions verificarFormato(ResultActions resposta, int status, String erro, CodigoErro codigo)
			throws Exception {
		return resposta
				.andExpect(status().is(status))
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.dataHora").value(DATA_ESPERADA))
				.andExpect(jsonPath("$.status").value(status))
				.andExpect(jsonPath("$.erro").value(erro))
				.andExpect(jsonPath("$.codigo").value(codigo.name()))
				.andExpect(jsonPath("$.mensagem").isNotEmpty());
	}

	// ----- 400 -----

	@Test
	void deve_devolver_400_com_campos_quando_o_corpo_falha_na_validacao() throws Exception {
		ResultActions resposta = mockMvc.perform(post("/api/v1/teste/corpo")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"nome\":\"\",\"idade\":0}"));

		verificarFormato(resposta, 400, "Bad Request", CodigoErro.VALIDACAO_FALHOU)
				.andExpect(jsonPath("$.campos.length()").value(2))
				.andExpect(jsonPath("$.campos[0].campo").value("idade"))
				.andExpect(jsonPath("$.campos[1].campo").value("nome"))
				.andExpect(jsonPath("$.campos[1].mensagem").value("não deve estar em branco"))
				.andExpect(jsonPath("$.detalhes").doesNotExist());
	}

	@Test
	void deve_responder_em_portugues_quando_o_accept_language_pede_ingles() throws Exception {
		ResultActions resposta = mockMvc.perform(post("/api/v1/teste/corpo")
				.header("Accept-Language", "en-US")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"nome\":\"\",\"idade\":1}"));

		verificarFormato(resposta, 400, "Bad Request", CodigoErro.VALIDACAO_FALHOU)
				.andExpect(jsonPath("$.campos[0].mensagem").value("não deve estar em branco"));
	}

	@Test
	void deve_devolver_400_sem_vazar_o_corpo_quando_o_json_esta_ilegivel() throws Exception {
		String enviado = "{\"nome\": \"SEGREDO-DO-CORPO\", ";

		ResultActions resposta = mockMvc.perform(post("/api/v1/teste/corpo")
				.contentType(MediaType.APPLICATION_JSON)
				.content(enviado));

		verificarFormato(resposta, 400, "Bad Request", CodigoErro.CORPO_ILEGIVEL)
				.andExpect(jsonPath("$.mensagem").value("O corpo da requisição está ilegível ou em formato inválido."))
				.andExpect(jsonPath("$.campos").doesNotExist());
		String corpo = resposta.andReturn().getResponse().getContentAsString();
		assertThat(corpo).doesNotContain("SEGREDO-DO-CORPO").doesNotContain("tools.jackson")
				.doesNotContain("Unexpected").doesNotContain("JSON parse");
	}

	@Test
	void deve_devolver_400_quando_falta_um_parametro_obrigatorio() throws Exception {
		ResultActions resposta = mockMvc.perform(get("/api/v1/teste/parametro"));

		verificarFormato(resposta, 400, "Bad Request", CodigoErro.PARAMETRO_AUSENTE)
				.andExpect(jsonPath("$.mensagem").value("O parâmetro 'numero' é obrigatório."));
	}

	@Test
	void deve_devolver_400_sem_vazar_o_valor_quando_o_uuid_esta_malformado() throws Exception {
		ResultActions resposta = mockMvc.perform(get("/api/v1/teste/uuid/isto-nao-e-um-uuid"));

		verificarFormato(resposta, 400, "Bad Request", CodigoErro.PARAMETRO_INVALIDO)
				.andExpect(jsonPath("$.mensagem").value("O parâmetro 'id' tem um valor inválido."));
		String corpo = resposta.andReturn().getResponse().getContentAsString();
		assertThat(corpo).doesNotContain("isto-nao-e-um-uuid").doesNotContain("java.util").doesNotContain("UUID");
	}

	@Test
	void deve_devolver_400_com_campos_quando_o_parametro_falha_na_validacao() throws Exception {
		ResultActions resposta = mockMvc.perform(get("/api/v1/teste/validado").param("codigo", "ab"));

		verificarFormato(resposta, 400, "Bad Request", CodigoErro.VALIDACAO_FALHOU)
				.andExpect(jsonPath("$.campos.length()").value(1))
				.andExpect(jsonPath("$.campos[0].campo").value("codigo"));
	}

	@Test
	void deve_devolver_400_com_campos_quando_ha_violacao_de_restricao_no_metodo() throws Exception {
		ResultActions resposta = mockMvc.perform(get("/api/v1/teste-validado/restricao").param("numero", "0"));

		verificarFormato(resposta, 400, "Bad Request", CodigoErro.VALIDACAO_FALHOU)
				.andExpect(jsonPath("$.campos.length()").value(1))
				.andExpect(jsonPath("$.campos[0].campo").value("numero"))
				.andExpect(jsonPath("$.campos[0].mensagem").value("deve ser maior que ou igual à 1"))
				.andExpect(jsonPath("$.detalhes").doesNotExist());
	}

	@Test
	void deve_devolver_400_com_o_codigo_da_excecao_quando_ha_requisicao_invalida() throws Exception {
		ResultActions resposta = mockMvc.perform(get("/api/v1/teste/requisicao-invalida"));

		verificarFormato(resposta, 400, "Bad Request", CodigoErro.VALIDACAO_FALHOU)
				.andExpect(jsonPath("$.mensagem").value("A requisição não pode ser atendida."));
	}

	// ----- 404 -----

	@Test
	void deve_devolver_404_quando_o_recurso_nao_e_encontrado() throws Exception {
		ResultActions resposta = mockMvc.perform(get("/api/v1/teste/nao-encontrado"));

		verificarFormato(resposta, 404, "Not Found", CodigoErro.RECURSO_NAO_ENCONTRADO)
				.andExpect(jsonPath("$.mensagem").value("Matéria não encontrada."))
				.andExpect(jsonPath("$.campos").doesNotExist())
				.andExpect(jsonPath("$.detalhes").doesNotExist());
	}

	@Test
	void deve_devolver_404_quando_a_rota_nao_existe() throws Exception {
		verificarFormato(mockMvc.perform(get("/api/v1/rota-que-nao-existe")), 404, "Not Found",
				CodigoErro.RECURSO_NAO_ENCONTRADO);
	}

	@Test
	void deve_devolver_404_quando_a_rota_nao_existe_em_um_metodo_com_corpo() throws Exception {
		verificarFormato(mockMvc.perform(post("/api/v1/rota-que-nao-existe").contentType(MediaType.APPLICATION_JSON)
				.content("{}")), 404, "Not Found", CodigoErro.RECURSO_NAO_ENCONTRADO);
	}

	@Test
	void deve_responder_json_quando_o_accept_pede_xml_numa_rota_inexistente() throws Exception {
		verificarFormato(mockMvc.perform(get("/api/v1/rota-que-nao-existe").accept(MediaType.APPLICATION_XML)), 404,
				"Not Found", CodigoErro.RECURSO_NAO_ENCONTRADO);
	}

	// ----- 405, 406, 415 -----

	@Test
	void deve_devolver_405_quando_o_metodo_nao_e_permitido() throws Exception {
		ResultActions resposta = mockMvc.perform(delete("/api/v1/teste/parametro"));

		verificarFormato(resposta, 405, "Method Not Allowed", CodigoErro.METODO_NAO_PERMITIDO)
				.andExpect(header().exists("Allow"));
	}

	@Test
	void deve_devolver_406_em_json_quando_o_accept_pede_xml() throws Exception {
		ResultActions resposta = mockMvc.perform(get("/api/v1/teste/so-json").accept(MediaType.APPLICATION_XML));

		verificarFormato(resposta, 406, "Not Acceptable", CodigoErro.FORMATO_NAO_ACEITO);
	}

	@Test
	void deve_devolver_415_quando_o_tipo_de_conteudo_nao_e_suportado() throws Exception {
		ResultActions resposta = mockMvc.perform(post("/api/v1/teste/corpo")
				.contentType(MediaType.TEXT_PLAIN)
				.content("texto"));

		verificarFormato(resposta, 415, "Unsupported Media Type", CodigoErro.TIPO_NAO_SUPORTADO);
	}

	// ----- 409 -----

	@Test
	void deve_devolver_409_com_detalhes_quando_ha_conflito() throws Exception {
		ResultActions resposta = mockMvc.perform(get("/api/v1/teste/conflito"));

		verificarFormato(resposta, 409, "Conflict", CodigoErro.CONFLITO)
				.andExpect(jsonPath("$.mensagem").value("Já existe uma tentativa em andamento."))
				.andExpect(jsonPath("$.detalhes.tentativaId").value("abc-123"))
				.andExpect(jsonPath("$.campos").doesNotExist());
	}

	// ----- 500 -----

	@Test
	void deve_devolver_500_generico_sem_vazar_nada_quando_ocorre_um_erro_inesperado() throws Exception {
		ResultActions resposta = mockMvc.perform(get("/api/v1/teste/erro"));

		verificarFormato(resposta, 500, "Internal Server Error", CodigoErro.ERRO_INTERNO)
				.andExpect(jsonPath("$.mensagem").value(GlobalExceptionHandler.MENSAGEM_ERRO_INTERNO))
				.andExpect(jsonPath("$.campos").doesNotExist())
				.andExpect(jsonPath("$.detalhes").doesNotExist());
		String corpo = resposta.andReturn().getResponse().getContentAsString();
		assertThat(corpo).doesNotContain("SELECT").doesNotContain("senha_hash").doesNotContain("a@b.com")
				.doesNotContain("br.simulado").doesNotContain("Usuario").doesNotContain("IllegalStateException")
				.doesNotContain("\tat ").doesNotContain("java.lang");
	}

	// ----- Clock -----

	@Test
	void deve_usar_o_clock_para_o_data_hora_quando_monta_o_erro() throws Exception {
		mockMvc.perform(get("/api/v1/teste/nao-encontrado"))
				.andExpect(jsonPath("$.dataHora").value("2026-11-20T15:04:05.000Z"));
	}

}
