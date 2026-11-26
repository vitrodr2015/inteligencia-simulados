package br.simulado.etec.catalogo.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;

import br.simulado.etec.catalogo.repository.MateriaRepository;
import br.simulado.etec.catalogo.repository.ProvaRepository;
import br.simulado.etec.catalogo.repository.SubtopicoRepository;
import br.simulado.etec.shared.exception.CodigoErro;
import br.simulado.etec.shared.security.ComPrincipal;
import br.simulado.etec.usuarios.model.Papel;

/**
 * Contrato HTTP da questão (H2, sem filtros de segurança: o 401 e o 403 são da matriz de autorização, contra MySQL real). O que
 * depende do MySQL (CHECKs, colunas geradas, FK, concorrência) está em {@code QuestaoIntegracaoTest}.
 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ComPrincipal(Papel.ADMIN)
class QuestaoControllerTest {

	private static final String DATA_HORA = "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}Z";

	private static final String UUID_CURTO = "11111111-1111-1111-1111-11111111111";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private ProvaRepository provas;

	@Autowired
	private SubtopicoRepository subtopicos;

	@Autowired
	private MateriaRepository materias;

	private String provaId;

	private String subtopicoId;

	@BeforeEach
	void montar() throws Exception {
		limpar();
		provaId = criarProva(50);
		subtopicoId = criarSubtopico();
	}

	@AfterEach
	void limpar() {
		jdbc.update("DELETE FROM alternativa");
		jdbc.update("DELETE FROM questao_versao");
		jdbc.update("DELETE FROM questao");
		provas.deleteAll();
		subtopicos.deleteAll();
		materias.deleteAll();
	}

	// ----- apoio -----

	private String criarProva(int quantidade) throws Exception {
		String corpo = "{\"titulo\":\"Prova\",\"duracaoMinutos\":180,\"quantidadeQuestoes\":" + quantidade + "}";
		String resposta = mockMvc.perform(post("/api/v1/admin/provas").contentType(MediaType.APPLICATION_JSON).content(corpo))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		return JsonPath.read(resposta, "$.id");
	}

	private String criarSubtopico() throws Exception {
		String materia = JsonPath.read(mockMvc.perform(post("/api/v1/materias").contentType(MediaType.APPLICATION_JSON)
				.content("{\"nome\":\"Matemática\"}")).andExpect(status().isCreated()).andReturn().getResponse()
				.getContentAsString(), "$.id");
		String resposta = mockMvc.perform(post("/api/v1/subtopicos").contentType(MediaType.APPLICATION_JSON)
				.content("{\"materiaId\":\"" + materia + "\",\"nome\":\"Álgebra\"}")).andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return JsonPath.read(resposta, "$.id");
	}

	private static String alternativas(String... textos) {
		StringBuilder json = new StringBuilder("[");
		String letras = "ABCDEFG";
		for (int i = 0; i < textos.length; i++) {
			json.append(i > 0 ? "," : "").append("{\"letra\":\"").append(letras.charAt(i)).append("\",\"texto\":\"")
					.append(textos[i]).append("\"}");
		}
		return json.append("]").toString();
	}

	private static String cinco() {
		return alternativas("um", "dois", "três", "quatro", "cinco");
	}

	/** Corpo mínimo válido, com um campo a mais ou trocado: {@code extra} entra no fim (a última chave repetida vale). */
	private String corpo(int numero, String enunciado, String extra) {
		return "{\"numero\":" + numero + ",\"subtopicoId\":\"" + subtopicoId + "\",\"enunciado\":\"" + enunciado
				+ "\",\"alternativas\":" + cinco() + (extra.isEmpty() ? "" : "," + extra) + "}";
	}

	private String corpo(int numero) {
		return corpo(numero, "Quanto é 1+1?", "");
	}

	private ResultActions cadastrar(String json) throws Exception {
		return mockMvc.perform(post("/api/v1/admin/provas/" + provaId + "/questoes").contentType(MediaType.APPLICATION_JSON)
				.content(json));
	}

	private String cadastrarEObterId(int numero) throws Exception {
		return JsonPath.read(cadastrar(corpo(numero)).andExpect(status().isCreated()).andReturn().getResponse()
				.getContentAsString(), "$.id");
	}

	private static ResultActions verificarErro(ResultActions resposta, int status, String erro, CodigoErro codigo)
			throws Exception {
		return resposta.andExpect(status().is(status))
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.dataHora", matchesPattern(DATA_HORA)))
				.andExpect(jsonPath("$.status").value(status))
				.andExpect(jsonPath("$.erro").value(erro))
				.andExpect(jsonPath("$.codigo").value(codigo.name()))
				.andExpect(jsonPath("$.mensagem").isNotEmpty());
	}

	private ResultActions campoInvalido(String json, String campo) throws Exception {
		return verificarErro(cadastrar(json), 400, "Bad Request", CodigoErro.VALIDACAO_FALHOU)
				.andExpect(jsonPath("$.campos[?(@.campo=='" + campo + "')]").isNotEmpty());
	}

	// ----- POST: sucesso -----

	@Test
	void deve_devolver_201_com_location_e_a_questao_completa_em_rascunho_na_versao_1() throws Exception {
		ResultActions resposta = cadastrar("{\"numero\":7,\"subtopicoId\":\"" + subtopicoId
				+ "\",\"textoApoio\":\"Leia o <b>texto</b>.\",\"grupoApoio\":2,\"enunciado\":\"Qual é a <i>resposta</i>?\","
				+ "\"imagemUrl\":\"https://imagens.exemplo.invalid/a.png\",\"alternativas\":"
				+ alternativas("H<sub>2</sub>O", "CO<sub>2</sub>", "x<sup>2</sup>", "a &lt; b", "linha<br>outra")
				+ ",\"gabarito\":\"C\",\"dificuldade\":\"MEDIA\",\"explicacao\":\"Porque <u>sim</u>.\"}");

		String id = JsonPath.read(resposta.andReturn().getResponse().getContentAsString(), "$.id");
		resposta.andExpect(status().isCreated())
				.andExpect(header().string("Location", "/api/v1/admin/questoes/" + id))
				.andExpect(jsonPath("$.versao").value(1))
				.andExpect(jsonPath("$.provaId").value(provaId))
				.andExpect(jsonPath("$.numero").value(7))
				.andExpect(jsonPath("$.subtopicoId").value(subtopicoId))
				.andExpect(jsonPath("$.textoApoio").value("Leia o <b>texto</b>."))
				.andExpect(jsonPath("$.grupoApoio").value(2))
				.andExpect(jsonPath("$.enunciado").value("Qual é a <i>resposta</i>?"))
				.andExpect(jsonPath("$.imagemUrl").value("https://imagens.exemplo.invalid/a.png"))
				.andExpect(jsonPath("$.alternativas.length()").value(5))
				.andExpect(jsonPath("$.alternativas[*].letra").value(contains("A", "B", "C", "D", "E")))
				.andExpect(jsonPath("$.alternativas[0].texto").value("H<sub>2</sub>O"))
				.andExpect(jsonPath("$.gabarito").value("C"))
				.andExpect(jsonPath("$.dificuldade").value("MEDIA"))
				.andExpect(jsonPath("$.explicacao").value("Porque <u>sim</u>."))
				.andExpect(jsonPath("$.status").value("RASCUNHO"));
		assertThat(UUID.fromString(id)).isNotNull();
	}

	@Test
	void deve_nao_expor_programa_tipo_hash_nem_id_numerico_e_manter_a_ordem_do_contrato() throws Exception {
		String json = cadastrar(corpo(1)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();

		assertThat(json).doesNotContain("programa").doesNotContain("tipo").doesNotContain("MULTIPLA_ESCOLHA")
				.doesNotContain("hash").doesNotContain("publicId");
		assertThat(JsonPath.<Map<String, Object>>read(json, "$").keySet()).containsExactly("id", "versao", "provaId", "numero",
				"subtopicoId", "textoApoio", "grupoApoio", "enunciado", "imagemUrl", "alternativas", "gabarito", "dificuldade",
				"explicacao", "status");
	}

	@Test
	void deve_devolver_nulos_como_null_e_aparar_os_textos_quando_so_o_obrigatorio_e_enviado() throws Exception {
		cadastrar("{\"numero\":1,\"subtopicoId\":\"" + subtopicoId + "\",\"enunciado\":\"   Com espaços   \",\"textoApoio\":\"   \","
				+ "\"explicacao\":\"\",\"imagemUrl\":\"  \",\"alternativas\":" + cinco() + "}")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.enunciado").value("Com espaços"))
				.andExpect(jsonPath("$.textoApoio").value((Object) null))
				.andExpect(jsonPath("$.grupoApoio").value((Object) null))
				.andExpect(jsonPath("$.imagemUrl").value((Object) null))
				.andExpect(jsonPath("$.gabarito").value((Object) null))
				.andExpect(jsonPath("$.dificuldade").value((Object) null))
				.andExpect(jsonPath("$.explicacao").value((Object) null));
	}

	@Test
	void deve_devolver_as_alternativas_de_a_a_e_quando_elas_chegam_fora_de_ordem() throws Exception {
		String embaralhadas = "[{\"letra\":\"E\",\"texto\":\"cinco\"},{\"letra\":\"C\",\"texto\":\"três\"},"
				+ "{\"letra\":\"A\",\"texto\":\"um\"},{\"letra\":\"D\",\"texto\":\"quatro\"},{\"letra\":\"B\",\"texto\":\"dois\"}]";

		cadastrar("{\"numero\":1,\"subtopicoId\":\"" + subtopicoId + "\",\"enunciado\":\"E\",\"alternativas\":" + embaralhadas + "}")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.alternativas[*].letra").value(contains("A", "B", "C", "D", "E")))
				.andExpect(jsonPath("$.alternativas[*].texto").value(contains("um", "dois", "três", "quatro", "cinco")));
	}

	@Test
	void deve_ignorar_id_versao_status_provaid_programa_e_tipo_enviados_no_corpo() throws Exception {
		String extra = "\"id\":\"11111111-1111-1111-1111-111111111111\",\"versao\":9,\"status\":\"PUBLICADA\","
				+ "\"provaId\":\"22222222-2222-2222-2222-222222222222\",\"programa\":\"ENEM\",\"tipo\":\"OUTRO\"";

		cadastrar(corpo(1, "E", extra)).andExpect(status().isCreated())
				.andExpect(jsonPath("$.id", not("11111111-1111-1111-1111-111111111111")))
				.andExpect(jsonPath("$.versao").value(1))
				.andExpect(jsonPath("$.status").value("RASCUNHO"))
				.andExpect(jsonPath("$.provaId").value(provaId));
	}

	@Test
	void deve_aceitar_os_limites_de_tamanho_de_cada_campo() throws Exception {
		String enunciado = "e".repeat(10000);
		String longa = "a".repeat(2000);

		cadastrar("{\"numero\":50,\"subtopicoId\":\"" + subtopicoId + "\",\"textoApoio\":\"" + "t".repeat(10000)
				+ "\",\"explicacao\":\"" + "x".repeat(10000) + "\",\"enunciado\":\"" + enunciado + "\",\"alternativas\":"
				+ alternativas(longa, "b", "c", "d", "e") + "}").andExpect(status().isCreated());
	}

	// ----- POST: idempotência e conflito (D153) -----

	@Test
	void deve_devolver_200_sem_location_com_a_mesma_questao_quando_o_numero_repete_com_o_mesmo_conteudo() throws Exception {
		String id = cadastrarEObterId(3);

		cadastrar(corpo(3)).andExpect(status().isOk())
				.andExpect(header().doesNotExist("Location"))
				.andExpect(jsonPath("$.id").value(id))
				.andExpect(jsonPath("$.versao").value(1))
				.andExpect(jsonPath("$.numero").value(3));
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM questao", Integer.class)).isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM questao_versao", Integer.class)).isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM alternativa", Integer.class)).isEqualTo(5);
	}

	@Test
	void deve_mostrar_o_que_esta_gravado_quando_gabarito_dificuldade_ou_explicacao_divergem_no_numero_repetido() throws Exception {
		cadastrar(corpo(3, "Quanto é 1+1?", "\"gabarito\":\"A\",\"dificuldade\":\"FACIL\",\"explicacao\":\"antiga\""))
				.andExpect(status().isCreated());

		cadastrar(corpo(3, "Quanto é 1+1?", "\"gabarito\":\"E\",\"dificuldade\":\"DIFICIL\",\"explicacao\":\"nova\""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.gabarito").value("A"))
				.andExpect(jsonPath("$.dificuldade").value("FACIL"))
				.andExpect(jsonPath("$.explicacao").value("antiga"));
	}

	@Test
	void deve_tratar_espacos_nas_pontas_e_quebras_de_linha_como_o_mesmo_conteudo() throws Exception {
		cadastrar(corpo(3, "linha1\\r\\nlinha2", "")).andExpect(status().isCreated());

		cadastrar(corpo(3, "  linha1\\nlinha2  ", "")).andExpect(status().isOk());
	}

	@Test
	void deve_devolver_409_com_os_numeros_em_conflito_quando_o_numero_repete_com_enunciado_diferente() throws Exception {
		cadastrarEObterId(3);

		verificarErro(cadastrar(corpo(3, "Outro enunciado", "")), 409, "Conflict", CodigoErro.QUESTAO_NUMERO_EM_CONFLITO)
				.andExpect(jsonPath("$.detalhes.numerosEmConflito[0]").value(3))
				.andExpect(jsonPath("$.detalhes.numerosEmConflito.length()").value(1));
	}

	@Test
	void deve_devolver_409_quando_so_uma_alternativa_muda_no_numero_repetido() throws Exception {
		cadastrarEObterId(3);
		String outra = alternativas("um", "dois", "três", "quatro", "CINCO");

		verificarErro(cadastrar("{\"numero\":3,\"subtopicoId\":\"" + subtopicoId + "\",\"enunciado\":\"Quanto é 1+1?\","
				+ "\"alternativas\":" + outra + "}"), 409, "Conflict", CodigoErro.QUESTAO_NUMERO_EM_CONFLITO);
	}

	@Test
	void deve_aceitar_o_mesmo_conteudo_em_numeros_diferentes() throws Exception {
		cadastrarEObterId(1);
		cadastrarEObterId(2);

		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM questao", Integer.class)).isEqualTo(2);
	}

	// ----- POST: 404 e 409 de estado -----

	@Test
	void deve_devolver_404_prova_nao_encontrada_quando_a_prova_nao_existe() throws Exception {
		verificarErro(mockMvc.perform(post("/api/v1/admin/provas/" + UUID.randomUUID() + "/questoes")
				.contentType(MediaType.APPLICATION_JSON).content(corpo(1))), 404, "Not Found", CodigoErro.PROVA_NAO_ENCONTRADA);
	}

	@Test
	void deve_devolver_409_prova_nao_editavel_quando_a_prova_nao_esta_em_rascunho() throws Exception {
		jdbc.update("UPDATE prova SET status = 'PUBLICADA', publicada_em = CURRENT_TIMESTAMP");

		verificarErro(cadastrar(corpo(1)), 409, "Conflict", CodigoErro.PROVA_NAO_EDITAVEL);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM questao", Integer.class)).isZero();
	}

	@Test
	void deve_devolver_409_subtopico_inativo_quando_o_subtopico_esta_desativado() throws Exception {
		mockMvc.perform(patch("/api/v1/subtopicos/" + subtopicoId + "/disable")).andExpect(status().isOk());

		verificarErro(cadastrar(corpo(1)), 409, "Conflict", CodigoErro.SUBTOPICO_INATIVO);
	}

	@Test
	void deve_devolver_409_subtopico_inativo_quando_a_materia_do_subtopico_esta_desativada() throws Exception {
		String materia = jdbc.queryForObject("SELECT materia_id FROM subtopico", String.class);
		jdbc.update("UPDATE materia SET ativo = FALSE WHERE id = ?", Long.parseLong(materia));

		verificarErro(cadastrar(corpo(1)), 409, "Conflict", CodigoErro.SUBTOPICO_INATIVO);
	}

	// ----- POST: 400 de referência -----

	@Test
	void deve_devolver_400_no_campo_numero_quando_o_numero_passa_da_quantidade_da_prova() throws Exception {
		campoInvalido(corpo(51), "numero");
		cadastrar(corpo(50)).andExpect(status().isCreated());
	}

	@Test
	void deve_devolver_400_no_campo_subtopicoid_quando_o_subtopico_nao_existe() throws Exception {
		String json = corpo(1).replace(subtopicoId, UUID.randomUUID().toString());

		campoInvalido(json, "subtopicoId");
	}

	// ----- POST: 400 de validação -----

	@Test
	void deve_devolver_400_quando_falta_numero_subtopico_enunciado_ou_alternativas() throws Exception {
		campoInvalido("{\"subtopicoId\":\"" + subtopicoId + "\",\"enunciado\":\"E\",\"alternativas\":" + cinco() + "}", "numero");
		campoInvalido("{\"numero\":1,\"enunciado\":\"E\",\"alternativas\":" + cinco() + "}", "subtopicoId");
		campoInvalido("{\"numero\":1,\"subtopicoId\":\"" + subtopicoId + "\",\"alternativas\":" + cinco() + "}", "enunciado");
		campoInvalido("{\"numero\":1,\"subtopicoId\":\"" + subtopicoId + "\",\"enunciado\":\"E\"}", "alternativas");
	}

	@ParameterizedTest
	@ValueSource(ints = { 0, -1, 301, 100000 })
	void deve_devolver_400_quando_o_numero_esta_fora_de_1_a_300(int numero) throws Exception {
		campoInvalido(corpo(numero), "numero");
	}

	@Test
	void deve_devolver_400_quando_o_enunciado_e_vazio_ou_passa_de_10000() throws Exception {
		campoInvalido(corpo(1, "     ", ""), "enunciado");
		campoInvalido(corpo(1, "e".repeat(10001), ""), "enunciado");
	}

	@Test
	void deve_devolver_400_quando_texto_de_apoio_ou_explicacao_passam_de_10000() throws Exception {
		campoInvalido(corpo(1, "E", "\"textoApoio\":\"" + "t".repeat(10001) + "\""), "textoApoio");
		campoInvalido(corpo(1, "E", "\"explicacao\":\"" + "x".repeat(10001) + "\""), "explicacao");
	}

	@Test
	void deve_devolver_400_quando_o_grupo_de_apoio_e_menor_que_1() throws Exception {
		campoInvalido(corpo(1, "E", "\"grupoApoio\":0"), "grupoApoio");
	}

	@Test
	void deve_devolver_400_quando_o_gabarito_nao_e_uma_letra_de_a_a_e() throws Exception {
		for (String gabarito : new String[] { "F", "a", "AB", "", "1" }) {
			campoInvalido(corpo(1, "E", "\"gabarito\":\"" + gabarito + "\""), "gabarito");
		}
	}

	@Test
	void deve_devolver_400_quando_a_dificuldade_nao_e_um_valor_do_enum() throws Exception {
		cadastrar(corpo(1, "E", "\"dificuldade\":\"IMPOSSIVEL\"")).andExpect(status().isBadRequest());
		cadastrar(corpo(1, "E", "\"dificuldade\":\"facil\"")).andExpect(status().isBadRequest());
	}

	@Test
	void deve_devolver_400_quando_nao_ha_exatamente_5_alternativas() throws Exception {
		for (String lista : new String[] { alternativas("a", "b", "c", "d"), alternativas("a", "b", "c", "d", "e", "f"), "[]" }) {
			campoInvalido("{\"numero\":1,\"subtopicoId\":\"" + subtopicoId + "\",\"enunciado\":\"E\",\"alternativas\":" + lista + "}",
					"alternativas");
		}
	}

	@Test
	void deve_devolver_400_quando_uma_letra_se_repete_ou_esta_fora_de_a_a_e() throws Exception {
		String repetida = "[{\"letra\":\"A\",\"texto\":\"1\"},{\"letra\":\"A\",\"texto\":\"2\"},{\"letra\":\"C\",\"texto\":\"3\"},"
				+ "{\"letra\":\"D\",\"texto\":\"4\"},{\"letra\":\"E\",\"texto\":\"5\"}]";
		String foraDoIntervalo = "[{\"letra\":\"A\",\"texto\":\"1\"},{\"letra\":\"B\",\"texto\":\"2\"},{\"letra\":\"C\",\"texto\":\"3\"},"
				+ "{\"letra\":\"D\",\"texto\":\"4\"},{\"letra\":\"F\",\"texto\":\"5\"}]";
		String minuscula = "[{\"letra\":\"a\",\"texto\":\"1\"},{\"letra\":\"B\",\"texto\":\"2\"},{\"letra\":\"C\",\"texto\":\"3\"},"
				+ "{\"letra\":\"D\",\"texto\":\"4\"},{\"letra\":\"E\",\"texto\":\"5\"}]";
		for (String lista : new String[] { repetida, foraDoIntervalo, minuscula }) {
			cadastrar("{\"numero\":1,\"subtopicoId\":\"" + subtopicoId + "\",\"enunciado\":\"E\",\"alternativas\":" + lista + "}")
					.andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("VALIDACAO_FALHOU"));
		}
	}

	@Test
	void deve_devolver_400_quando_o_texto_da_alternativa_e_vazio_ou_passa_de_2000() throws Exception {
		campoInvalido("{\"numero\":1,\"subtopicoId\":\"" + subtopicoId + "\",\"enunciado\":\"E\",\"alternativas\":"
				+ alternativas("   ", "b", "c", "d", "e") + "}", "alternativas[0].texto");
		campoInvalido("{\"numero\":1,\"subtopicoId\":\"" + subtopicoId + "\",\"enunciado\":\"E\",\"alternativas\":"
				+ alternativas("a", "b", "c", "d", "e".repeat(2001)) + "}", "alternativas[4].texto");
	}

	@ParameterizedTest
	@ValueSource(strings = { "<script>alert(1)</script>", "<img src=x onerror=alert(1)>", "<b class=\\\"x\\\">a</b>", "<b>aberta",
			"5 < 6", "AT&T", "<br/>", "<p>x</p>" })
	void deve_devolver_400_quando_a_marcacao_e_invalida_em_qualquer_texto(String texto) throws Exception {
		campoInvalido(corpo(1, texto, ""), "enunciado");
		campoInvalido(corpo(1, "E", "\"textoApoio\":\"" + texto + "\""), "textoApoio");
		campoInvalido(corpo(1, "E", "\"explicacao\":\"" + texto + "\""), "explicacao");
		campoInvalido("{\"numero\":1,\"subtopicoId\":\"" + subtopicoId + "\",\"enunciado\":\"E\",\"alternativas\":"
				+ alternativas("a", "b", texto, "d", "e") + "}", "alternativas[2].texto");
	}

	@ParameterizedTest
	@ValueSource(strings = { "https://atacante.invalid/a.png", "https://imagens.exemplo.invalid.atacante.invalid/a.png",
			"http://imagens.exemplo.invalid/a.png", "ftp://imagens.exemplo.invalid/a.png", "javascript:alert(1)",
			"imagens.exemplo.invalid/a.png", "https://usuario:senha@imagens.exemplo.invalid/a.png" })
	void deve_devolver_400_quando_a_imagem_nao_e_https_de_um_host_permitido(String url) throws Exception {
		campoInvalido(corpo(1, "E", "\"imagemUrl\":\"" + url + "\""), "imagemUrl");
	}

	@Test
	void deve_aceitar_imagem_de_host_permitido_inclusive_com_outra_caixa_e_localhost() throws Exception {
		cadastrar(corpo(1, "E", "\"imagemUrl\":\"https://IMAGENS.exemplo.invalid/a.png\"")).andExpect(status().isCreated());
		cadastrar(corpo(2, "E", "\"imagemUrl\":\"https://localhost:9000/a.png\"")).andExpect(status().isCreated());
	}

	@Test
	void deve_devolver_400_quando_a_imagem_passa_de_500_caracteres() throws Exception {
		campoInvalido(corpo(1, "E", "\"imagemUrl\":\"https://imagens.exemplo.invalid/" + "p".repeat(480) + "\""), "imagemUrl");
	}

	@Test
	void deve_devolver_400_corpo_ilegivel_quando_o_json_esta_quebrado_ou_com_tipo_errado() throws Exception {
		verificarErro(cadastrar("{\"numero\": "), 400, "Bad Request", CodigoErro.CORPO_ILEGIVEL);
		verificarErro(cadastrar("[]"), 400, "Bad Request", CodigoErro.CORPO_ILEGIVEL);
		verificarErro(cadastrar(corpo(1, "E", "\"numero\":\"1\"")), 400, "Bad Request", CodigoErro.CORPO_ILEGIVEL);
		verificarErro(cadastrar(corpo(1, "E", "\"numero\":1.5")), 400, "Bad Request", CodigoErro.CORPO_ILEGIVEL);
		verificarErro(cadastrar(corpo(1, "E", "\"grupoApoio\":\"2\"")), 400, "Bad Request", CodigoErro.CORPO_ILEGIVEL);
		verificarErro(cadastrar(corpo(1, "E", "\"alternativas\":\"cinco\"")), 400, "Bad Request", CodigoErro.CORPO_ILEGIVEL);
	}

	@Test
	void deve_devolver_415_quando_o_corpo_nao_e_json() throws Exception {
		verificarErro(mockMvc.perform(post("/api/v1/admin/provas/" + provaId + "/questoes").contentType(MediaType.TEXT_PLAIN)
				.content(corpo(1))), 415, "Unsupported Media Type", CodigoErro.TIPO_NAO_SUPORTADO);
	}

	@Test
	void deve_devolver_400_quando_o_id_da_prova_e_malformado_inclusive_com_grupo_curto() throws Exception {
		for (String id : new String[] { "abc", UUID_CURTO, "123" }) {
			verificarErro(mockMvc.perform(post("/api/v1/admin/provas/" + id + "/questoes").contentType(MediaType.APPLICATION_JSON)
					.content(corpo(1))), 400, "Bad Request", CodigoErro.PARAMETRO_INVALIDO);
		}
	}

	@Test
	void deve_devolver_400_antes_de_404_quando_o_corpo_e_invalido_e_a_prova_nao_existe() throws Exception {
		verificarErro(mockMvc.perform(post("/api/v1/admin/provas/" + UUID.randomUUID() + "/questoes")
				.contentType(MediaType.APPLICATION_JSON).content("{}")), 400, "Bad Request", CodigoErro.VALIDACAO_FALHOU);
	}

	// ----- GET /admin/questoes/{id} -----

	@Test
	void deve_devolver_200_com_a_questao_quando_busca_pelo_id() throws Exception {
		String id = cadastrarEObterId(5);

		mockMvc.perform(get("/api/v1/admin/questoes/" + id)).andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(id))
				.andExpect(jsonPath("$.versao").value(1))
				.andExpect(jsonPath("$.provaId").value(provaId))
				.andExpect(jsonPath("$.numero").value(5))
				.andExpect(jsonPath("$.status").value("RASCUNHO"))
				.andExpect(jsonPath("$.alternativas.length()").value(5))
				.andExpect(jsonPath("$.programa").doesNotExist())
				.andExpect(jsonPath("$.tipo").doesNotExist());
	}

	@Test
	void deve_ler_a_versao_pedida_e_devolver_404_para_versao_inexistente() throws Exception {
		String id = cadastrarEObterId(5);

		mockMvc.perform(get("/api/v1/admin/questoes/" + id).param("versao", "1")).andExpect(status().isOk())
				.andExpect(jsonPath("$.versao").value(1));
		verificarErro(mockMvc.perform(get("/api/v1/admin/questoes/" + id).param("versao", "2")), 404, "Not Found",
				CodigoErro.QUESTAO_NAO_ENCONTRADA);
	}

	@ParameterizedTest
	@ValueSource(strings = { "0", "-1", "abc", "1.5", "", " 1", "99999999999" })
	void deve_devolver_400_quando_a_versao_e_invalida(String versao) throws Exception {
		String id = cadastrarEObterId(5);

		verificarErro(mockMvc.perform(get("/api/v1/admin/questoes/" + id).param("versao", versao)), 400, "Bad Request",
				CodigoErro.PARAMETRO_INVALIDO);
	}

	@Test
	void deve_devolver_404_questao_nao_encontrada_quando_o_id_nao_existe() throws Exception {
		verificarErro(mockMvc.perform(get("/api/v1/admin/questoes/" + UUID.randomUUID())), 404, "Not Found",
				CodigoErro.QUESTAO_NAO_ENCONTRADA);
	}

	@ParameterizedTest
	@ValueSource(strings = { "abc", UUID_CURTO, "123", "zzzzzzzz-zzzz-zzzz-zzzz-zzzzzzzzzzzz" })
	void deve_devolver_400_quando_o_id_da_questao_e_malformado(String id) throws Exception {
		verificarErro(mockMvc.perform(get("/api/v1/admin/questoes/" + id)), 400, "Bad Request", CodigoErro.PARAMETRO_INVALIDO);
	}

	// ----- GET /admin/questoes (listagem) -----

	private static final String LISTA = "/api/v1/admin/questoes";

	@Test
	void deve_listar_com_o_envelope_padrao_a_mesma_questao_completa_do_get_por_id_sem_programa_nem_tipo() throws Exception {
		String id = cadastrarEObterId(1);
		String porId = mockMvc.perform(get(LISTA + "/" + id)).andReturn().getResponse().getContentAsString();

		String lista = mockMvc.perform(get(LISTA).param("provaId", provaId)).andExpect(status().isOk())
				.andExpect(jsonPath("$.conteudo.length()").value(1))
				.andExpect(jsonPath("$.pagina").value(0))
				.andExpect(jsonPath("$.tamanho").value(20))
				.andExpect(jsonPath("$.totalElementos").value(1))
				.andExpect(jsonPath("$.totalPaginas").value(1))
				.andReturn().getResponse().getContentAsString();

		assertThat(JsonPath.<Map<String, Object>>read(lista, "$.conteudo[0]")).isEqualTo(JsonPath.read(porId, "$"));
		assertThat(lista).doesNotContain("programa").doesNotContain("\"tipo\"").doesNotContain("MULTIPLA_ESCOLHA")
				.doesNotContain("hash");
	}

	@Test
	void deve_listar_em_ordem_de_numero_crescente_com_ou_sem_o_sort() throws Exception {
		for (int numero : new int[] { 7, 2, 30, 1 }) {
			cadastrarEObterId(numero);
		}

		for (String sort : new String[] { null, "numero", "numero,asc", "numero,ASC" }) {
			var requisicao = get(LISTA).param("provaId", provaId);
			if (sort != null) {
				requisicao.param("sort", sort);
			}
			mockMvc.perform(requisicao).andExpect(status().isOk())
					.andExpect(jsonPath("$.conteudo[*].numero").value(contains(1, 2, 7, 30)));
		}
	}

	@ParameterizedTest
	@ValueSource(strings = { "sort=numero,desc", "sort=numero,DESC", "sort=enunciado", "sort=id", "sort=NUMERO", "sort=versao",
			"sort=numero,cima", "sort=", "sort=numero&sort=numero", "sort=numero,asc&sort=desc" })
	void deve_devolver_400_ordenacao_invalida_para_qualquer_sort_que_nao_seja_numero_crescente(String consulta) throws Exception {
		verificarErro(mockMvc.perform(get(LISTA + "?" + consulta)), 400, "Bad Request", CodigoErro.ORDENACAO_INVALIDA);
	}

	@ParameterizedTest
	@ValueSource(strings = { "status=ARQUIVADA", "status=rascunho", "status=Publicada", "status=", "status=OUTRO",
			"provaId=abc", "provaId=" + UUID_CURTO, "subtopicoId=abc", "subtopicoId=" + UUID_CURTO, "page=-1", "page=x", "size=0",
			"size=-2", "size=abc" })
	void deve_devolver_400_parametro_invalido_para_filtro_ou_pagina_invalidos(String consulta) throws Exception {
		verificarErro(mockMvc.perform(get(LISTA + "?" + consulta)), 400, "Bad Request", CodigoErro.PARAMETRO_INVALIDO);
	}

	@Test
	void deve_filtrar_por_prova_subtopico_e_status() throws Exception {
		String outraProva = criarProva(10);
		cadastrarEObterId(1);
		cadastrarEObterId(2);
		mockMvc.perform(post("/api/v1/admin/provas/" + outraProva + "/questoes").contentType(MediaType.APPLICATION_JSON)
				.content(corpo(3))).andExpect(status().isCreated());

		mockMvc.perform(get(LISTA).param("provaId", provaId)).andExpect(jsonPath("$.totalElementos").value(2))
				.andExpect(jsonPath("$.conteudo[*].provaId").value(contains(provaId, provaId)));
		mockMvc.perform(get(LISTA).param("provaId", outraProva)).andExpect(jsonPath("$.conteudo[*].numero").value(contains(3)));
		mockMvc.perform(get(LISTA).param("subtopicoId", subtopicoId)).andExpect(jsonPath("$.totalElementos").value(3));
		mockMvc.perform(get(LISTA).param("status", "RASCUNHO")).andExpect(jsonPath("$.totalElementos").value(3));
		mockMvc.perform(get(LISTA).param("status", "PUBLICADA")).andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElementos").value(0));
		mockMvc.perform(get(LISTA).param("provaId", outraProva).param("subtopicoId", subtopicoId).param("status", "RASCUNHO"))
				.andExpect(jsonPath("$.totalElementos").value(1));
	}

	@Test
	void deve_devolver_lista_vazia_com_200_quando_a_prova_ou_o_subtopico_do_filtro_nao_existem() throws Exception {
		cadastrarEObterId(1);

		for (String filtro : new String[] { "provaId", "subtopicoId" }) {
			mockMvc.perform(get(LISTA).param(filtro, UUID.randomUUID().toString())).andExpect(status().isOk())
					.andExpect(jsonPath("$.conteudo.length()").value(0))
					.andExpect(jsonPath("$.totalElementos").value(0));
		}
	}

	@Test
	void deve_paginar_reduzir_o_size_a_50_e_devolver_vazio_alem_do_fim() throws Exception {
		for (int numero = 1; numero <= 5; numero++) {
			cadastrarEObterId(numero);
		}

		mockMvc.perform(get(LISTA).param("provaId", provaId).param("size", "2").param("page", "1"))
				.andExpect(jsonPath("$.conteudo[*].numero").value(contains(3, 4)))
				.andExpect(jsonPath("$.totalElementos").value(5))
				.andExpect(jsonPath("$.totalPaginas").value(3));
		mockMvc.perform(get(LISTA).param("size", "500")).andExpect(jsonPath("$.tamanho").value(50));
		mockMvc.perform(get(LISTA).param("page", "99")).andExpect(status().isOk())
				.andExpect(jsonPath("$.conteudo.length()").value(0));
		mockMvc.perform(get(LISTA).param("page", "99999999999999")).andExpect(status().isOk());
	}

	// ----- questoesCadastradas na prova -----

	@Test
	void deve_contar_as_questoes_cadastradas_em_cada_prova_no_get_e_na_listagem() throws Exception {
		String vazia = criarProva(10);
		cadastrarEObterId(1);
		cadastrarEObterId(2);
		cadastrar(corpo(2)).andExpect(status().isOk()); // repetir não conta de novo

		mockMvc.perform(get("/api/v1/provas/" + provaId)).andExpect(jsonPath("$.questoesCadastradas").value(2));
		mockMvc.perform(get("/api/v1/provas/" + vazia)).andExpect(jsonPath("$.questoesCadastradas").value(0));
		mockMvc.perform(get("/api/v1/provas"))
				.andExpect(jsonPath("$.conteudo[?(@.id=='" + provaId + "')].questoesCadastradas").value(contains(2)))
				.andExpect(jsonPath("$.conteudo[?(@.id=='" + vazia + "')].questoesCadastradas").value(contains(0)));
	}

	// ----- o parser de UUID estrito vale para TODAS as rotas com id (antes, o grupo curto virava 404) -----

	@Test
	void deve_devolver_400_para_o_uuid_de_grupo_curto_em_todas_as_rotas_com_id() throws Exception {
		List<ResultActions> chamadas = List.of(
				mockMvc.perform(get("/api/v1/materias/" + UUID_CURTO)),
				mockMvc.perform(put("/api/v1/materias/" + UUID_CURTO).contentType(MediaType.APPLICATION_JSON).content("{\"nome\":\"X1\"}")),
				mockMvc.perform(patch("/api/v1/materias/" + UUID_CURTO + "/disable")),
				mockMvc.perform(patch("/api/v1/materias/" + UUID_CURTO + "/enable")),
				mockMvc.perform(delete("/api/v1/materias/" + UUID_CURTO)),
				mockMvc.perform(get("/api/v1/subtopicos/" + UUID_CURTO)),
				mockMvc.perform(put("/api/v1/subtopicos/" + UUID_CURTO).contentType(MediaType.APPLICATION_JSON).content("{\"nome\":\"X1\"}")),
				mockMvc.perform(patch("/api/v1/subtopicos/" + UUID_CURTO + "/disable")),
				mockMvc.perform(delete("/api/v1/subtopicos/" + UUID_CURTO)),
				mockMvc.perform(get("/api/v1/provas/" + UUID_CURTO)),
				mockMvc.perform(get("/api/v1/admin/questoes/" + UUID_CURTO)),
				mockMvc.perform(get("/api/v1/subtopicos").param("materiaId", UUID_CURTO)));
		for (ResultActions chamada : chamadas) {
			chamada.andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("PARAMETRO_INVALIDO"));
		}
	}

	@Test
	void deve_devolver_400_quando_o_uuid_do_corpo_tem_grupo_curto() throws Exception {
		mockMvc.perform(post("/api/v1/subtopicos").contentType(MediaType.APPLICATION_JSON)
				.content("{\"materiaId\":\"" + UUID_CURTO + "\",\"nome\":\"Qualquer\"}")).andExpect(status().isBadRequest());
		cadastrar(corpo(1).replace(subtopicoId, UUID_CURTO)).andExpect(status().isBadRequest());
	}

	@Test
	void deve_continuar_aceitando_o_uuid_canonico_em_maiusculas_nas_rotas() throws Exception {
		String id = cadastrarEObterId(5);

		mockMvc.perform(get("/api/v1/admin/questoes/" + id.toUpperCase())).andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(id));
	}

}
