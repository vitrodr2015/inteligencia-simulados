package br.simulado.etec.admin.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

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
 * Contrato HTTP da importação (H2, sem filtros de segurança: o 401 e o 403 são da matriz, contra MySQL real). A atomicidade de
 * verdade, a concorrência e o 413 por HTTP real estão nos testes de integração.
 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ComPrincipal(Papel.ADMIN)
class ImportacaoControllerTest {

	private static final String ROTA = "/api/v1/admin/importacoes";

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
		provaId = criar("/api/v1/admin/provas", "{\"titulo\":\"Prova\",\"duracaoMinutos\":180,\"quantidadeQuestoes\":150}");
		String materia = criar("/api/v1/materias", "{\"nome\":\"Matemática\"}");
		subtopicoId = criar("/api/v1/subtopicos", "{\"materiaId\":\"" + materia + "\",\"nome\":\"Álgebra\"}");
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

	private String criar(String rota, String corpo) throws Exception {
		return JsonPath.read(mockMvc.perform(post(rota).contentType(MediaType.APPLICATION_JSON).content(corpo))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
	}

	private String item(int numero, String enunciado, String extra) {
		return "{\"numero\":" + numero + ",\"subtopicoId\":\"" + subtopicoId + "\",\"enunciado\":\"" + enunciado
				+ "\",\"alternativas\":[{\"letra\":\"A\",\"texto\":\"a\"},{\"letra\":\"B\",\"texto\":\"b\"},"
				+ "{\"letra\":\"C\",\"texto\":\"c\"},{\"letra\":\"D\",\"texto\":\"d\"},{\"letra\":\"E\",\"texto\":\"e\"}]"
				+ (extra.isEmpty() ? "" : "," + extra) + "}";
	}

	private String item(int numero) {
		return item(numero, "Enunciado " + numero, "");
	}

	private static String lote(String prova, String... itens) {
		return "{\"provaId\":\"" + prova + "\",\"questoes\":[" + String.join(",", itens) + "]}";
	}

	private String loteDe(int quantidade) {
		return lote(provaId, IntStream.rangeClosed(1, quantidade).mapToObj(this::item).collect(Collectors.joining(",")));
	}

	private ResultActions importar(String corpo) throws Exception {
		return mockMvc.perform(post(ROTA).contentType(MediaType.APPLICATION_JSON).content(corpo));
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

	private ResultActions campos(String corpo, String... esperados) throws Exception {
		return verificarErro(importar(corpo), 400, "Bad Request", CodigoErro.VALIDACAO_FALHOU)
				.andExpect(jsonPath("$.campos[*].campo").value(contains(esperados)));
	}

	private int questoesGravadas() {
		return jdbc.queryForObject("SELECT COUNT(*) FROM questao", Integer.class);
	}

	// ----- sucesso -----

	@Test
	void deve_devolver_200_com_prova_criadas_e_ignoradas_nessa_ordem_sem_location() throws Exception {
		String resposta = importar(lote(provaId, item(1), item(2), item(3))).andExpect(status().isOk())
				.andExpect(header().doesNotExist("Location"))
				.andExpect(jsonPath("$.provaId").value(provaId))
				.andExpect(jsonPath("$.questoesCriadas").value(3))
				.andExpect(jsonPath("$.questoesIgnoradas").value(0))
				.andReturn().getResponse().getContentAsString();

		assertThat(JsonPath.<Map<String, Object>>read(resposta, "$").keySet()).containsExactly("provaId", "questoesCriadas",
				"questoesIgnoradas");
		assertThat(questoesGravadas()).isEqualTo(3);
	}

	@Test
	void deve_devolver_200_com_tudo_ignorado_quando_o_mesmo_lote_e_reimportado() throws Exception {
		String corpo = lote(provaId, item(1), item(2));
		importar(corpo).andExpect(status().isOk());

		importar(corpo).andExpect(status().isOk()).andExpect(jsonPath("$.questoesCriadas").value(0))
				.andExpect(jsonPath("$.questoesIgnoradas").value(2));
		assertThat(questoesGravadas()).isEqualTo(2);
	}

	@Test
	void deve_contar_criadas_e_ignoradas_e_ignorar_gabarito_diferente_sem_altera_lo() throws Exception {
		importar(lote(provaId, item(1, "Um", "\"gabarito\":\"A\""))).andExpect(status().isOk());

		importar(lote(provaId, item(1, "Um", "\"gabarito\":\"E\""), item(2, "Dois", "\"gabarito\":\"D\"")))
				.andExpect(status().isOk()).andExpect(jsonPath("$.questoesCriadas").value(1))
				.andExpect(jsonPath("$.questoesIgnoradas").value(1));
		assertThat(jdbc.queryForList("SELECT v.gabarito FROM questao_versao v JOIN questao q ON q.id = v.questao_id ORDER BY q.numero",
				String.class)).containsExactly("A", "D");
	}

	@Test
	void deve_aceitar_100_itens_e_ignorar_campos_desconhecidos_no_lote_e_nos_itens() throws Exception {
		importar(loteDe(100)).andExpect(status().isOk()).andExpect(jsonPath("$.questoesCriadas").value(100));

		importar("{\"provaId\":\"" + provaId + "\",\"prova\":{\"titulo\":\"outro\"},\"questoes\":[" + item(101, "E",
				"\"status\":\"PUBLICADA\",\"tipo\":\"X\"") + "]}").andExpect(status().isOk());
		assertThat(jdbc.queryForObject("SELECT titulo FROM prova", String.class)).as("a importação não altera a prova")
				.isEqualTo("Prova");
	}

	// ----- 400 de formato: quantidade e campos -----

	@Test
	void deve_devolver_400_quando_o_lote_tem_zero_ou_101_itens() throws Exception {
		campos("{\"provaId\":\"" + provaId + "\",\"questoes\":[]}", "questoes");
		campos(loteDe(101), "questoes").andExpect(jsonPath("$.campos[0].mensagem").value("O lote deve ter de 1 a 100 questões."));
		assertThat(questoesGravadas()).isZero();
	}

	@Test
	void deve_devolver_400_quando_falta_a_prova_ou_a_lista() throws Exception {
		campos("{\"questoes\":[" + item(1) + "]}", "provaId");
		campos("{\"provaId\":\"" + provaId + "\"}", "questoes");
		campos("{}", "provaId", "questoes");
		campos("{\"provaId\":null,\"questoes\":null}", "provaId", "questoes");
	}

	@Test
	void deve_apontar_o_caminho_com_indice_de_todos_os_itens_invalidos_juntos() throws Exception {
		String semEnunciado = "{\"numero\":2,\"subtopicoId\":\"" + subtopicoId + "\",\"alternativas\":[]}";
		String marcacaoNaAlternativa = item(4).replace("{\"letra\":\"C\",\"texto\":\"c\"}",
				"{\"letra\":\"C\",\"texto\":\"<script>x</script>\"}");

		campos(lote(provaId, item(1), semEnunciado, item(3, "E", "\"gabarito\":\"F\""), marcacaoNaAlternativa),
				"questoes[1].alternativas", "questoes[1].enunciado", "questoes[2].gabarito",
				"questoes[3].alternativas[2].texto");
		assertThat(questoesGravadas()).isZero();
	}

	@Test
	void deve_apontar_o_item_nulo_pelo_indice() throws Exception {
		campos("{\"provaId\":\"" + provaId + "\",\"questoes\":[" + item(1) + ",null]}", "questoes[1]");
	}

	@ParameterizedTest
	@ValueSource(strings = { UUID_CURTO, "abc", "", "Ej5FZ+ibEtOkVkJmFBdAAA==" })
	void deve_devolver_400_corpo_ilegivel_quando_o_provaid_nao_e_um_uuid_canonico(String id) throws Exception {
		verificarErro(importar(lote(id, item(1))), 400, "Bad Request", CodigoErro.CORPO_ILEGIVEL);
	}

	@Test
	void deve_devolver_400_corpo_ilegivel_sem_indice_para_enum_invalido_tipo_errado_ou_json_quebrado() throws Exception {
		verificarErro(importar(lote(provaId, item(1), item(2, "E", "\"dificuldade\":\"IMPOSSIVEL\""))), 400, "Bad Request",
				CodigoErro.CORPO_ILEGIVEL).andExpect(jsonPath("$.campos").doesNotExist());
		verificarErro(importar(lote(provaId, item(1, "E", "\"grupoApoio\":\"2\""))), 400, "Bad Request", CodigoErro.CORPO_ILEGIVEL);
		verificarErro(importar(lote(provaId, item(1).replace(subtopicoId, UUID_CURTO))), 400, "Bad Request",
				CodigoErro.CORPO_ILEGIVEL);
		verificarErro(importar("{\"provaId\": "), 400, "Bad Request", CodigoErro.CORPO_ILEGIVEL);
		verificarErro(importar("[]"), 400, "Bad Request", CodigoErro.CORPO_ILEGIVEL);
		assertThat(questoesGravadas()).isZero();
	}

	@Test
	void deve_devolver_415_quando_o_corpo_nao_e_json() throws Exception {
		verificarErro(mockMvc.perform(post(ROTA).contentType(MediaType.TEXT_PLAIN).content(loteDe(1))), 415,
				"Unsupported Media Type", CodigoErro.TIPO_NAO_SUPORTADO);
	}

	@Test
	void deve_devolver_413_corpo_muito_grande_com_a_mensagem_de_dividir_o_lote() throws Exception {
		verificarErro(importar(loteDe(1) + " ".repeat(LimiteDeCorpoDaImportacao.LIMITE_EM_BYTES)), 413, "Content Too Large",
				CodigoErro.CORPO_MUITO_GRANDE).andExpect(jsonPath("$.mensagem", org.hamcrest.Matchers.containsString("Divida o lote")));
		assertThat(questoesGravadas()).isZero();
	}

	@Test
	void deve_dar_415_antes_do_413_quando_o_corpo_grande_nao_e_json() throws Exception {
		verificarErro(mockMvc.perform(post(ROTA).contentType(MediaType.TEXT_PLAIN)
				.content(" ".repeat(LimiteDeCorpoDaImportacao.LIMITE_EM_BYTES + 1))), 415, "Unsupported Media Type",
				CodigoErro.TIPO_NAO_SUPORTADO);
	}

	@Test
	void deve_ter_o_limite_de_exatamente_2_mib_em_bytes() {
		assertThat(LimiteDeCorpoDaImportacao.LIMITE_EM_BYTES).isEqualTo(2_097_152);
	}

	// ----- numero repetido no lote -----

	@Test
	void deve_devolver_400_apontando_as_repeticoes_do_numero_antes_de_olhar_a_prova() throws Exception {
		// a prova nem existe: o numero repetido é erro de FORMATO e vem antes
		campos(lote(UUID.randomUUID().toString(), item(1), item(2), item(1), item(2)), "questoes[2].numero", "questoes[3].numero");
		assertThat(questoesGravadas()).isZero();
	}

	// ----- prova -----

	@Test
	void deve_devolver_400_no_campo_provaid_quando_a_prova_nao_existe() throws Exception {
		campos(lote(UUID.randomUUID().toString(), item(1)), "provaId");
	}

	@Test
	void deve_devolver_409_prova_nao_editavel_mesmo_com_o_lote_ja_gravado() throws Exception {
		String corpo = lote(provaId, item(1));
		importar(corpo).andExpect(status().isOk());
		jdbc.update("UPDATE prova SET status = 'PUBLICADA', publicada_em = CURRENT_TIMESTAMP");

		verificarErro(importar(corpo), 409, "Conflict", CodigoErro.PROVA_NAO_EDITAVEL)
				.andExpect(jsonPath("$.detalhes").doesNotExist());
	}

	// ----- itens recusados pelo serviço: identificação e tudo ou nada -----

	@Test
	void deve_prefixar_o_indice_no_numero_acima_da_prova_e_no_subtopico_inexistente() throws Exception {
		campos(lote(provaId, item(1), item(2), item(151)), "questoes[2].numero");
		campos(lote(provaId, item(1), item(2).replace(subtopicoId, UUID.randomUUID().toString())), "questoes[1].subtopicoId");
		assertThat(questoesGravadas()).as("tudo ou nada").isZero();
	}

	@Test
	void deve_devolver_409_subtopico_inativo_com_indice_e_numero_e_nao_gravar_nada() throws Exception {
		String inativo = criar("/api/v1/subtopicos",
				"{\"materiaId\":\"" + jdbcMateria() + "\",\"nome\":\"Inativo\"}");
		mockMvc.perform(patch("/api/v1/subtopicos/" + inativo + "/disable")).andExpect(status().isOk());

		verificarErro(importar(lote(provaId, item(1), item(7).replace(subtopicoId, inativo), item(8))), 409, "Conflict",
				CodigoErro.SUBTOPICO_INATIVO)
				.andExpect(jsonPath("$.detalhes.indice").value(1))
				.andExpect(jsonPath("$.detalhes.numero").value(7));
		assertThat(questoesGravadas()).isZero();
	}

	private String jdbcMateria() throws Exception {
		return JsonPath.read(mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
				.get("/api/v1/subtopicos/" + subtopicoId)).andReturn().getResponse().getContentAsString(), "$.materiaId");
	}

	@Test
	void deve_listar_todos_os_numeros_em_conflito_num_unico_409_e_nao_gravar_nada() throws Exception {
		importar(lote(provaId, item(2), item(4))).andExpect(status().isOk());

		verificarErro(importar(lote(provaId, item(1), item(2, "Outro", ""), item(3), item(4, "Outro", ""))), 409, "Conflict",
				CodigoErro.QUESTAO_NUMERO_EM_CONFLITO)
				.andExpect(jsonPath("$.detalhes.numerosEmConflito").value(contains(2, 4)))
				.andExpect(jsonPath("$.detalhes.indice").value(1))
				.andExpect(jsonPath("$.detalhes.numero").value(2));
		assertThat(questoesGravadas()).isEqualTo(2);
	}

	@Test
	void deve_devolver_o_400_do_item_posterior_quando_ha_um_conflito_antes() throws Exception {
		importar(lote(provaId, item(1))).andExpect(status().isOk());

		campos(lote(provaId, item(1, "Outro", ""), item(151)), "questoes[1].numero");
	}

	// ----- reimportação com subtópico desativado (D169) -----

	@Test
	void deve_ignorar_a_questao_ja_gravada_mesmo_com_o_subtopico_desativado_no_lote_e_no_unitario() throws Exception {
		String corpo = lote(provaId, item(1), item(2));
		importar(corpo).andExpect(status().isOk());
		mockMvc.perform(patch("/api/v1/subtopicos/" + subtopicoId + "/disable")).andExpect(status().isOk());

		importar(corpo).andExpect(status().isOk()).andExpect(jsonPath("$.questoesCriadas").value(0))
				.andExpect(jsonPath("$.questoesIgnoradas").value(2));
		mockMvc.perform(post("/api/v1/admin/provas/" + provaId + "/questoes").contentType(MediaType.APPLICATION_JSON)
				.content(item(1))).andExpect(status().isOk()).andExpect(jsonPath("$.numero").value(1));
		// o que seria gravado continua exigindo o subtópico ativo
		verificarErro(importar(lote(provaId, item(1), item(3))), 409, "Conflict", CodigoErro.SUBTOPICO_INATIVO)
				.andExpect(jsonPath("$.detalhes.indice").value(1));
	}

}
