package br.simulado.etec.catalogo.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;

import br.simulado.etec.catalogo.model.ProgramaDeProva;
import br.simulado.etec.catalogo.repository.ProgramaRepository;
import br.simulado.etec.catalogo.repository.ProvaRepository;
import br.simulado.etec.shared.exception.CodigoErro;
import br.simulado.etec.shared.programa.Programa;
import br.simulado.etec.shared.security.ComPrincipal;
import br.simulado.etec.usuarios.model.Papel;

/**
 * Contrato HTTP da prova (H2, sem filtros de segurança: o 401 e o 403 são da matriz de autorização, contra MySQL real). O que
 * depende do MySQL (collation, CHECKs, ordem dos nulos, concorrência) está em {@code ProvaIntegracaoTest}.
 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ComPrincipal(Papel.ADMIN)
class ProvaControllerTest {

	private static final String CRIAR = "/api/v1/admin/provas";

	private static final String BASE = "/api/v1/provas";

	private static final String DATA_HORA = "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}Z";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ProvaRepository repositorio;

	@Autowired
	private ProgramaRepository programas;

	@BeforeEach
	@AfterEach
	void limpar() {
		repositorio.deleteAll();
		// A ETEC vem da migration no MySQL; no H2 (create-drop, sem Flyway) cada teste garante a linha.
		if (programas.findByCodigo("ETEC").isEmpty()) {
			programas.save(new ProgramaDeProva(Programa.ETEC, "ETEC"));
		}
	}

	// ----- apoio -----

	private static String corpo(String titulo, String banca, Object ano, Object semestre, Object duracao, Object quantidade,
			String fonteUrl) {
		return "{\"titulo\":" + texto(titulo) + ",\"banca\":" + texto(banca) + ",\"ano\":" + ano + ",\"semestre\":" + semestre
				+ ",\"duracaoMinutos\":" + duracao + ",\"quantidadeQuestoes\":" + quantidade + ",\"fonteUrl\":" + texto(fonteUrl)
				+ "}";
	}

	private static String texto(String valor) {
		return (valor == null) ? "null" : "\"" + valor.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
	}

	/** Corpo válido mínimo, para mudar um campo por vez. */
	private static String valido() {
		return corpo("Vestibulinho", "Vunesp", 2026, 1, 180, 50, "https://exemplo.invalid/prova");
	}

	private ResultActions postar(String json) throws Exception {
		return mockMvc.perform(post(CRIAR).contentType(MediaType.APPLICATION_JSON).content(json));
	}

	private String criar(String json) throws Exception {
		String resposta = postar(json).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		return JsonPath.read(resposta, "$.id");
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

	private static ResultActions campoInvalido(ResultActions resposta, String campo) throws Exception {
		return resposta.andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("VALIDACAO_FALHOU"))
				.andExpect(jsonPath("$.campos[0].campo").value(campo));
	}

	// ----- POST: sucesso -----

	@Test
	void deve_devolver_201_com_location_e_a_prova_completa_em_rascunho_quando_cria() throws Exception {
		ResultActions resposta = postar(valido());

		String id = JsonPath.read(resposta.andReturn().getResponse().getContentAsString(), "$.id");
		resposta.andExpect(status().isCreated())
				.andExpect(header().string("Location", "/api/v1/provas/" + id))
				.andExpect(jsonPath("$.id", matchesPattern("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")))
				.andExpect(jsonPath("$.titulo").value("Vestibulinho"))
				.andExpect(jsonPath("$.banca").value("Vunesp"))
				.andExpect(jsonPath("$.ano").value(2026))
				.andExpect(jsonPath("$.semestre").value(1))
				.andExpect(jsonPath("$.duracaoMinutos").value(180))
				.andExpect(jsonPath("$.quantidadeQuestoes").value(50))
				.andExpect(jsonPath("$.fonteUrl").value("https://exemplo.invalid/prova"))
				.andExpect(jsonPath("$.status").value("RASCUNHO"))
				.andExpect(jsonPath("$.publicadaEm").value((Object) null))
				.andExpect(jsonPath("$.criadoEm", matchesPattern(DATA_HORA)))
				.andExpect(jsonPath("$.questoesCadastradas").value(0));
		assertThat(UUID.fromString(id)).isNotNull();
	}

	@Test
	void deve_nao_expor_o_programa_nem_o_id_numerico_nem_campos_internos_quando_cria() throws Exception {
		String json = postar(valido()).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();

		assertThat(json).doesNotContain("programa").doesNotContain("publicId").doesNotContain("atualizadoEm");
		assertThat(JsonPath.<java.util.Map<String, Object>>read(json, "$").keySet()).containsExactly("id", "titulo", "banca",
				"ano", "semestre", "duracaoMinutos", "quantidadeQuestoes", "fonteUrl", "status", "publicadaEm", "criadoEm",
				"questoesCadastradas");
	}

	@Test
	void deve_devolver_campos_nulos_como_null_quando_banca_ano_semestre_e_fonte_sao_omitidos() throws Exception {
		postar("{\"titulo\":\"Só o essencial\",\"duracaoMinutos\":90,\"quantidadeQuestoes\":30}")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.banca").value((Object) null))
				.andExpect(jsonPath("$.ano").value((Object) null))
				.andExpect(jsonPath("$.semestre").value((Object) null))
				.andExpect(jsonPath("$.fonteUrl").value((Object) null));
	}

	@Test
	void deve_ignorar_id_status_e_datas_enviados_no_corpo_quando_cria() throws Exception {
		String enviado = "{\"titulo\":\"Ignora\",\"duracaoMinutos\":60,\"quantidadeQuestoes\":10,"
				+ "\"id\":\"11111111-1111-1111-1111-111111111111\",\"status\":\"PUBLICADA\","
				+ "\"publicadaEm\":\"2000-01-01T00:00:00.000Z\",\"criadoEm\":\"2000-01-01T00:00:00.000Z\","
				+ "\"programa\":\"ENEM\",\"programaId\":99}";

		postar(enviado).andExpect(status().isCreated())
				.andExpect(jsonPath("$.id", org.hamcrest.Matchers.not("11111111-1111-1111-1111-111111111111")))
				.andExpect(jsonPath("$.status").value("RASCUNHO"))
				.andExpect(jsonPath("$.publicadaEm").value((Object) null))
				.andExpect(jsonPath("$.criadoEm", org.hamcrest.Matchers.not("2000-01-01T00:00:00.000Z")));
	}

	@Test
	void deve_aparar_os_textos_e_transformar_banca_e_fonte_em_branco_em_nulas_quando_cria() throws Exception {
		postar(corpo("   Prova aparada   ", "     ", null, null, 60, 10, "    ")).andExpect(status().isCreated())
				.andExpect(jsonPath("$.titulo").value("Prova aparada"))
				.andExpect(jsonPath("$.banca").value((Object) null))
				.andExpect(jsonPath("$.fonteUrl").value((Object) null));
		postar(corpo("Outra", "  Fuvest  ", null, null, 60, 10, null)).andExpect(status().isCreated())
				.andExpect(jsonPath("$.banca").value("Fuvest"));
	}

	@Test
	void deve_aceitar_os_limites_de_cada_campo_quando_cria() throws Exception {
		postar(corpo("t".repeat(150), "b".repeat(80), 2000, 2, 1, 1, "https://exemplo.invalid/" + "p".repeat(475)))
				.andExpect(status().isCreated());
		postar(corpo("Limite superior", "Banca", 2100, 1, 600, 300, null)).andExpect(status().isCreated());
	}

	// ----- POST: 400 de validação -----

	@Test
	void deve_devolver_400_quando_falta_o_titulo() throws Exception {
		campoInvalido(postar("{\"duracaoMinutos\":60,\"quantidadeQuestoes\":10}"), "titulo")
				.andExpect(jsonPath("$.campos[0].mensagem").value("O título é obrigatório."));
	}

	@Test
	void deve_devolver_400_quando_o_titulo_so_tem_espacos_ou_passa_de_150() throws Exception {
		campoInvalido(postar(corpo("     ", null, null, null, 60, 10, null)), "titulo");
		campoInvalido(postar(corpo("t".repeat(151), null, null, null, 60, 10, null)), "titulo");
	}

	@Test
	void deve_devolver_400_quando_a_banca_passa_de_80() throws Exception {
		campoInvalido(postar(corpo("T", "b".repeat(81), null, null, 60, 10, null)), "banca");
	}

	@ParameterizedTest
	@ValueSource(ints = { 1999, 2101, 0, -1 })
	void deve_devolver_400_quando_o_ano_esta_fora_de_2000_a_2100(int ano) throws Exception {
		campoInvalido(postar(corpo("T", null, ano, null, 60, 10, null)), "ano");
	}

	@ParameterizedTest
	@ValueSource(ints = { 0, 3, -1 })
	void deve_devolver_400_quando_o_semestre_nao_e_1_nem_2(int semestre) throws Exception {
		campoInvalido(postar(corpo("T", null, null, semestre, 60, 10, null)), "semestre");
	}

	@ParameterizedTest
	@ValueSource(ints = { 0, 601, -5 })
	void deve_devolver_400_quando_a_duracao_esta_fora_de_1_a_600(int duracao) throws Exception {
		campoInvalido(postar(corpo("T", null, null, null, duracao, 10, null)), "duracaoMinutos");
	}

	@Test
	void deve_devolver_400_quando_falta_a_duracao_ou_a_quantidade() throws Exception {
		campoInvalido(postar("{\"titulo\":\"T\",\"quantidadeQuestoes\":10}"), "duracaoMinutos");
		campoInvalido(postar("{\"titulo\":\"T\",\"duracaoMinutos\":60}"), "quantidadeQuestoes");
	}

	@ParameterizedTest
	@ValueSource(ints = { 0, 301, 1000, -1 })
	void deve_devolver_400_quando_a_quantidade_de_questoes_esta_fora_de_1_a_300(int quantidade) throws Exception {
		campoInvalido(postar(corpo("T", null, null, null, 60, quantidade, null)), "quantidadeQuestoes");
	}

	static Stream<Arguments> urlsInvalidas() {
		return Stream.of(Arguments.of("http://exemplo.invalid/p"), Arguments.of("ftp://exemplo.invalid/p"),
				Arguments.of("javascript:alert(1)"), Arguments.of("exemplo.invalid/p"), Arguments.of("https://"),
				Arguments.of("https:///sem-host"), Arguments.of("https://usuario:senha@exemplo.invalid/p"),
				Arguments.of("https://exemplo invalido/p"), Arguments.of("//exemplo.invalid/p"));
	}

	@ParameterizedTest
	@MethodSource("urlsInvalidas")
	void deve_devolver_400_quando_a_fonte_nao_e_https_valida(String url) throws Exception {
		campoInvalido(postar(corpo("T", null, null, null, 60, 10, url)), "fonteUrl");
	}

	@Test
	void deve_devolver_400_quando_a_fonte_passa_de_500_caracteres() throws Exception {
		campoInvalido(postar(corpo("T", null, null, null, 60, 10, "https://exemplo.invalid/" + "p".repeat(477))), "fonteUrl");
	}

	@Test
	void deve_listar_todos_os_campos_invalidos_quando_varios_falham() throws Exception {
		postar(corpo("", null, 1900, 5, 0, 0, "http://x")).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.campos.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(6)));
	}

	// ----- POST: 400 de formato e 415 -----

	@Test
	void deve_devolver_400_corpo_ilegivel_quando_o_json_esta_quebrado() throws Exception {
		verificarErro(postar("{\"titulo\": "), 400, "Bad Request", CodigoErro.CORPO_ILEGIVEL);
	}

	@Test
	void deve_devolver_400_corpo_ilegivel_quando_o_corpo_e_um_array_ou_vazio() throws Exception {
		verificarErro(postar("[]"), 400, "Bad Request", CodigoErro.CORPO_ILEGIVEL);
		verificarErro(postar(""), 400, "Bad Request", CodigoErro.CORPO_ILEGIVEL);
	}

	@ParameterizedTest
	@ValueSource(strings = { "\"ano\":\"abc\"", "\"ano\":true", "\"semestre\":[1]", "\"duracaoMinutos\":{}",
			"\"quantidadeQuestoes\":\"muitas\"", "\"titulo\":123", "\"banca\":false", "\"fonteUrl\":5" })
	void deve_devolver_400_corpo_ilegivel_quando_um_campo_tem_tipo_errado(String campoErrado) throws Exception {
		String json = "{\"titulo\":\"T\",\"duracaoMinutos\":60,\"quantidadeQuestoes\":10," + campoErrado + "}";
		// o campo repetido no JSON vale o último: o de tipo errado vence os válidos
		verificarErro(postar(json), 400, "Bad Request", CodigoErro.CORPO_ILEGIVEL);
	}

	@ParameterizedTest
	@ValueSource(strings = { "\"ano\":\"2024\"", "\"semestre\":\"1\"", "\"duracaoMinutos\":\"60\"", "\"quantidadeQuestoes\":\"10\"",
			"\"ano\":2024.5", "\"quantidadeQuestoes\":10.9" })
	void deve_recusar_numero_em_texto_ou_decimal_em_vez_de_corrigir_em_silencio(String campoErrado) throws Exception {
		String json = "{\"titulo\":\"T\",\"duracaoMinutos\":60,\"quantidadeQuestoes\":10," + campoErrado + "}";
		verificarErro(postar(json), 400, "Bad Request", CodigoErro.CORPO_ILEGIVEL);
	}

	@Test
	void deve_devolver_415_quando_o_corpo_nao_e_json() throws Exception {
		verificarErro(mockMvc.perform(post(CRIAR).contentType(MediaType.TEXT_PLAIN).content(valido())), 415,
				"Unsupported Media Type", CodigoErro.TIPO_NAO_SUPORTADO);
	}

	// ----- GET /provas/{id} -----

	@Test
	void deve_devolver_200_com_a_prova_quando_busca_pelo_id() throws Exception {
		String id = criar(valido());

		mockMvc.perform(get(BASE + "/" + id)).andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(id))
				.andExpect(jsonPath("$.titulo").value("Vestibulinho"))
				.andExpect(jsonPath("$.status").value("RASCUNHO"))
				.andExpect(jsonPath("$.programa").doesNotExist());
	}

	@Test
	void deve_devolver_404_prova_nao_encontrada_quando_o_id_nao_existe() throws Exception {
		verificarErro(mockMvc.perform(get(BASE + "/" + UUID.randomUUID())), 404, "Not Found", CodigoErro.PROVA_NAO_ENCONTRADA);
	}

	@ParameterizedTest
	@ValueSource(strings = { "abc", "123", "zzzzzzzz-zzzz-zzzz-zzzz-zzzzzzzzzzzz", "11111111-1111-1111-1111-1111111111111111" })
	void deve_devolver_400_quando_o_id_e_malformado(String id) throws Exception {
		verificarErro(mockMvc.perform(get(BASE + "/" + id)), 400, "Bad Request", CodigoErro.PARAMETRO_INVALIDO);
	}

	// ----- GET /provas: parâmetros -----

	@Test
	void deve_listar_com_o_envelope_padrao_e_a_prova_completa() throws Exception {
		criar(valido());

		mockMvc.perform(get(BASE)).andExpect(status().isOk())
				.andExpect(jsonPath("$.conteudo.length()").value(1))
				.andExpect(jsonPath("$.conteudo[0].titulo").value("Vestibulinho"))
				.andExpect(jsonPath("$.conteudo[0].programa").doesNotExist())
				.andExpect(jsonPath("$.pagina").value(0))
				.andExpect(jsonPath("$.tamanho").value(20))
				.andExpect(jsonPath("$.totalElementos").value(1))
				.andExpect(jsonPath("$.totalPaginas").value(1));
	}

	@Test
	void deve_filtrar_por_ano_semestre_e_status() throws Exception {
		criar(corpo("A", "Banca A", 2025, 1, 60, 10, null));
		criar(corpo("B", "Banca B", 2025, 2, 60, 10, null));
		criar(corpo("C", "Banca C", 2026, 1, 60, 10, null));

		mockMvc.perform(get(BASE).param("ano", "2025")).andExpect(jsonPath("$.totalElementos").value(2));
		mockMvc.perform(get(BASE).param("semestre", "1")).andExpect(jsonPath("$.totalElementos").value(2));
		mockMvc.perform(get(BASE).param("ano", "2025").param("semestre", "2"))
				.andExpect(jsonPath("$.totalElementos").value(1)).andExpect(jsonPath("$.conteudo[0].titulo").value("B"));
		mockMvc.perform(get(BASE).param("status", "RASCUNHO")).andExpect(jsonPath("$.totalElementos").value(3));
		mockMvc.perform(get(BASE).param("status", "PUBLICADA")).andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElementos").value(0));
	}

	@ParameterizedTest
	@ValueSource(strings = { "ano=abc", "ano=1999", "ano=2101", "ano=-1", "ano=", "ano=20.5", "ano=%202025", "ano=99999999999",
			"semestre=0", "semestre=3", "semestre=x", "status=rascunho", "status=ARQUIVADA", "status=", "status=OUTRO",
			"page=-1", "page=x", "size=0", "size=-3", "size=abc" })
	void deve_devolver_400_parametro_invalido_quando_o_filtro_ou_a_pagina_e_invalido(String consulta) throws Exception {
		verificarErro(mockMvc.perform(get(BASE + "?" + consulta)), 400, "Bad Request", CodigoErro.PARAMETRO_INVALIDO);
	}

	@ParameterizedTest
	@ValueSource(strings = { "sort=titulo", "sort=banca", "sort=id", "sort=ANO", "sort=ano,cima", "sort=ano,asc,desc",
			"sort=ano&sort=semestre", "sort=ano&sort=desc", "sort=", "sort=status" })
	void deve_devolver_400_ordenacao_invalida_quando_o_sort_nao_e_ano_ou_semestre_com_direcao_valida(String consulta)
			throws Exception {
		verificarErro(mockMvc.perform(get(BASE + "?" + consulta)), 400, "Bad Request", CodigoErro.ORDENACAO_INVALIDA);
	}

	@ParameterizedTest
	@ValueSource(strings = { "sort=ano", "sort=ano,asc", "sort=ano,desc", "sort=semestre", "sort=semestre,DESC", "sort=ano,ASC" })
	void deve_aceitar_o_sort_por_ano_ou_semestre(String consulta) throws Exception {
		mockMvc.perform(get(BASE + "?" + consulta)).andExpect(status().isOk());
	}

	@Test
	void deve_reduzir_o_size_ao_maximo_de_50_e_devolver_lista_vazia_alem_do_fim() throws Exception {
		mockMvc.perform(get(BASE).param("size", "500")).andExpect(status().isOk()).andExpect(jsonPath("$.tamanho").value(50));
		mockMvc.perform(get(BASE).param("page", "9999")).andExpect(status().isOk())
				.andExpect(jsonPath("$.conteudo.length()").value(0));
	}

	@Test
	void deve_ordenar_por_ano_decrescente_por_padrao_e_crescente_quando_o_sort_nao_tem_direcao() throws Exception {
		criar(corpo("Velha", "Banca", 2020, 1, 60, 10, null));
		criar(corpo("Nova", "Banca", 2026, 1, 60, 10, null));
		criar(corpo("Meio", "Banca", 2023, 1, 60, 10, null));

		mockMvc.perform(get(BASE)).andExpect(jsonPath("$.conteudo[*].titulo").value(org.hamcrest.Matchers.contains("Nova", "Meio", "Velha")));
		mockMvc.perform(get(BASE).param("sort", "ano"))
				.andExpect(jsonPath("$.conteudo[*].titulo").value(org.hamcrest.Matchers.contains("Velha", "Meio", "Nova")));
		mockMvc.perform(get(BASE).param("sort", "ano,desc"))
				.andExpect(jsonPath("$.conteudo[*].titulo").value(org.hamcrest.Matchers.contains("Nova", "Meio", "Velha")));
	}

}
