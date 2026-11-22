package br.simulado.etec.catalogo.controller;

import static org.assertj.core.api.Assertions.assertThat;
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

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;

import br.simulado.etec.catalogo.repository.MateriaRepository;
import br.simulado.etec.shared.exception.CodigoErro;

/**
 * Teste web de ponta a ponta (H2). A unicidade sem distinguir maiúsculas e acentos NÃO é provada
 * aqui (o H2 compara com distinção); isso é do Módulo 7, contra MySQL real.
 */
@SpringBootTest
// SEM filtros de segurança (addFilters = false): este teste cobre o CONTRATO da rota e NÃO testa segurança.
// O 401 e o 403 da cadeia são provados nos testes de integração, com os filtros ligados.
@AutoConfigureMockMvc(addFilters = false)
class MateriaControllerTest {

	private static final String BASE = "/api/v1/materias";

	private static final String DATA_HORA = "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}Z";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private MateriaRepository repositorio;

	@BeforeEach
	@AfterEach
	void limpar() {
		repositorio.deleteAll();
	}

	// ----- apoio -----

	private static String corpo(String nome, String descricao) {
		String desc = (descricao == null) ? "null" : "\"" + descricao + "\"";
		return "{\"nome\":\"" + nome + "\",\"descricao\":" + desc + "}";
	}

	private ResultActions postar(String json) throws Exception {
		return mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(json));
	}

	private String criar(String nome) throws Exception {
		String resposta = postar(corpo(nome, "Descrição de " + nome)).andExpect(status().isCreated()).andReturn()
				.getResponse().getContentAsString();
		return JsonPath.read(resposta, "$.id");
	}

	private static ResultActions verificarErro(ResultActions resposta, int status, String erro, CodigoErro codigo)
			throws Exception {
		return resposta
				.andExpect(status().is(status))
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.dataHora", matchesPattern(DATA_HORA)))
				.andExpect(jsonPath("$.status").value(status))
				.andExpect(jsonPath("$.erro").value(erro))
				.andExpect(jsonPath("$.codigo").value(codigo.name()))
				.andExpect(jsonPath("$.mensagem").isNotEmpty());
	}

	// ----- POST -----

	@Test
	void deve_devolver_201_com_location_e_corpo_quando_cria_uma_materia() throws Exception {
		ResultActions resposta = postar(corpo("Matemática", "Álgebra e geometria"));

		String id = JsonPath.read(resposta.andReturn().getResponse().getContentAsString(), "$.id");
		resposta.andExpect(status().isCreated())
				.andExpect(header().string("Location", "/api/v1/materias/" + id))
				.andExpect(jsonPath("$.nome").value("Matemática"))
				.andExpect(jsonPath("$.descricao").value("Álgebra e geometria"))
				.andExpect(jsonPath("$.ativo").value(true))
				.andExpect(jsonPath("$.criadoEm", matchesPattern(DATA_HORA)))
				.andExpect(jsonPath("$.atualizadoEm", matchesPattern(DATA_HORA)))
				.andExpect(jsonPath("$.id", matchesPattern("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")));
		assertThat(UUID.fromString(id)).isNotNull();
	}

	@Test
	void deve_nao_expor_o_id_numerico_nem_campos_internos_quando_cria() throws Exception {
		postar(corpo("História", null)).andExpect(status().isCreated())
				.andExpect(jsonPath("$.publicId").doesNotExist())
				.andExpect(jsonPath("$.senha").doesNotExist())
				.andExpect(jsonPath("$.descricao").value((Object) null));
	}

	@Test
	void deve_ignorar_id_ativo_e_datas_enviados_no_corpo_quando_cria() throws Exception {
		String enviado = "{\"nome\":\"Física\",\"id\":\"11111111-1111-1111-1111-111111111111\",\"ativo\":false,"
				+ "\"criadoEm\":\"2000-01-01T00:00:00.000Z\",\"atualizadoEm\":\"2000-01-01T00:00:00.000Z\"}";

		postar(enviado).andExpect(status().isCreated())
				.andExpect(jsonPath("$.id", not("11111111-1111-1111-1111-111111111111")))
				.andExpect(jsonPath("$.ativo").value(true))
				.andExpect(jsonPath("$.criadoEm", not("2000-01-01T00:00:00.000Z")))
				.andExpect(jsonPath("$.atualizadoEm", not("2000-01-01T00:00:00.000Z")));
	}

	@Test
	void deve_remover_os_espacos_das_pontas_do_nome_quando_cria() throws Exception {
		postar(corpo("   Química   ", null)).andExpect(status().isCreated())
				.andExpect(jsonPath("$.nome").value("Química"));
	}

	@Test
	void deve_devolver_400_com_campos_quando_o_nome_so_tem_espacos() throws Exception {
		verificarErro(postar(corpo("     ", null)), 400, "Bad Request", CodigoErro.VALIDACAO_FALHOU)
				.andExpect(jsonPath("$.campos[0].campo").value("nome"))
				.andExpect(jsonPath("$.campos[0].mensagem").value("O nome deve ter de 2 a 100 caracteres."));
	}

	@Test
	void deve_devolver_400_quando_o_nome_tem_um_caractere() throws Exception {
		verificarErro(postar(corpo("A", null)), 400, "Bad Request", CodigoErro.VALIDACAO_FALHOU)
				.andExpect(jsonPath("$.campos[0].campo").value("nome"));
	}

	@Test
	void deve_devolver_400_quando_o_nome_passa_de_cem_caracteres() throws Exception {
		verificarErro(postar(corpo("x".repeat(101), null)), 400, "Bad Request", CodigoErro.VALIDACAO_FALHOU)
				.andExpect(jsonPath("$.campos[0].campo").value("nome"));
	}

	@Test
	void deve_aceitar_nome_de_cem_caracteres_e_descricao_de_255() throws Exception {
		postar(corpo("x".repeat(100), "d".repeat(255))).andExpect(status().isCreated());
	}

	@Test
	void deve_devolver_400_quando_falta_o_nome() throws Exception {
		verificarErro(postar("{\"descricao\":\"sem nome\"}"), 400, "Bad Request", CodigoErro.VALIDACAO_FALHOU)
				.andExpect(jsonPath("$.campos[0].campo").value("nome"))
				.andExpect(jsonPath("$.campos[0].mensagem").value("O nome é obrigatório."));
	}

	@Test
	void deve_devolver_400_quando_a_descricao_passa_de_255_caracteres() throws Exception {
		verificarErro(postar(corpo("Geografia", "d".repeat(256))), 400, "Bad Request", CodigoErro.VALIDACAO_FALHOU)
				.andExpect(jsonPath("$.campos[0].campo").value("descricao"))
				.andExpect(jsonPath("$.campos[0].mensagem").value("A descrição deve ter no máximo 255 caracteres."));
	}

	@Test
	void deve_devolver_400_quando_o_corpo_esta_ilegivel() throws Exception {
		verificarErro(postar("{\"nome\": "), 400, "Bad Request", CodigoErro.CORPO_ILEGIVEL);
	}

	@Test
	void deve_devolver_409_quando_o_nome_ja_existe() throws Exception {
		criar("Biologia");

		verificarErro(postar(corpo("Biologia", null)), 409, "Conflict", CodigoErro.MATERIA_NOME_DUPLICADO)
				.andExpect(jsonPath("$.mensagem").value("Já existe uma matéria com esse nome."))
				.andExpect(jsonPath("$.campos").doesNotExist())
				.andExpect(jsonPath("$.detalhes").doesNotExist());
	}

	// ----- GET -----

	@Test
	void deve_devolver_200_quando_busca_uma_materia_existente() throws Exception {
		String id = criar("Filosofia");

		mockMvc.perform(get(BASE + "/" + id)).andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(id))
				.andExpect(jsonPath("$.nome").value("Filosofia"))
				.andExpect(jsonPath("$.ativo").value(true));
	}

	@Test
	void deve_devolver_404_quando_busca_um_id_inexistente() throws Exception {
		verificarErro(mockMvc.perform(get(BASE + "/" + UUID.randomUUID())), 404, "Not Found",
				CodigoErro.MATERIA_NAO_ENCONTRADA)
				.andExpect(jsonPath("$.mensagem").value("Matéria não encontrada."));
	}

	@Test
	void deve_devolver_400_quando_o_id_esta_malformado_no_get() throws Exception {
		verificarErro(mockMvc.perform(get(BASE + "/isto-nao-e-um-uuid")), 400, "Bad Request",
				CodigoErro.PARAMETRO_INVALIDO);
	}

	// ----- PUT -----

	@Test
	void deve_devolver_200_e_trocar_nome_e_descricao_quando_atualiza() throws Exception {
		String id = criar("Fisica");

		mockMvc.perform(put(BASE + "/" + id).contentType(MediaType.APPLICATION_JSON).content(corpo("Física", "Mecânica")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(id))
				.andExpect(jsonPath("$.nome").value("Física"))
				.andExpect(jsonPath("$.descricao").value("Mecânica"));
	}

	@Test
	void deve_nunca_alterar_o_ativo_quando_o_put_envia_ativo_no_corpo() throws Exception {
		String id = criar("Sociologia");
		mockMvc.perform(patch(BASE + "/" + id + "/disable")).andExpect(status().isOk());

		mockMvc.perform(put(BASE + "/" + id).contentType(MediaType.APPLICATION_JSON)
				.content("{\"nome\":\"Sociologia\",\"descricao\":\"x\",\"ativo\":true}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.ativo").value(false));
	}

	@Test
	void deve_permitir_o_proprio_nome_quando_o_put_nao_muda_o_nome() throws Exception {
		String id = criar("Inglês");

		mockMvc.perform(put(BASE + "/" + id).contentType(MediaType.APPLICATION_JSON).content(corpo("Inglês", "nova")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.nome").value("Inglês"))
				.andExpect(jsonPath("$.descricao").value("nova"));
	}

	@Test
	void deve_devolver_409_quando_o_put_usa_o_nome_de_outra_materia() throws Exception {
		criar("Artes");
		String id = criar("Música");

		verificarErro(mockMvc.perform(put(BASE + "/" + id).contentType(MediaType.APPLICATION_JSON)
				.content(corpo("Artes", null))), 409, "Conflict", CodigoErro.MATERIA_NOME_DUPLICADO);
	}

	@Test
	void deve_devolver_404_quando_o_put_usa_um_id_inexistente() throws Exception {
		verificarErro(mockMvc.perform(put(BASE + "/" + UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON)
				.content(corpo("Qualquer", null))), 404, "Not Found", CodigoErro.MATERIA_NAO_ENCONTRADA);
	}

	@Test
	void deve_devolver_400_quando_o_put_tem_corpo_invalido() throws Exception {
		String id = criar("Educação Física");

		verificarErro(mockMvc.perform(put(BASE + "/" + id).contentType(MediaType.APPLICATION_JSON)
				.content(corpo(" ", null))), 400, "Bad Request", CodigoErro.VALIDACAO_FALHOU)
				.andExpect(jsonPath("$.campos[0].campo").value("nome"));
	}

	@Test
	void deve_devolver_400_quando_o_id_esta_malformado_no_put() throws Exception {
		verificarErro(mockMvc.perform(put(BASE + "/xyz").contentType(MediaType.APPLICATION_JSON)
				.content(corpo("Qualquer", null))), 400, "Bad Request", CodigoErro.PARAMETRO_INVALIDO);
	}

	// ----- PATCH disable / enable -----

	@Test
	void deve_desativar_e_ativar_quando_chama_disable_e_enable() throws Exception {
		String id = criar("Redação");

		mockMvc.perform(patch(BASE + "/" + id + "/disable")).andExpect(status().isOk())
				.andExpect(jsonPath("$.ativo").value(false));
		mockMvc.perform(get(BASE + "/" + id)).andExpect(jsonPath("$.ativo").value(false));
		mockMvc.perform(patch(BASE + "/" + id + "/enable")).andExpect(status().isOk())
				.andExpect(jsonPath("$.ativo").value(true));
	}

	@Test
	void deve_devolver_o_mesmo_resultado_quando_repete_o_disable() throws Exception {
		String id = criar("Espanhol");

		String primeira = mockMvc.perform(patch(BASE + "/" + id + "/disable")).andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		String segunda = mockMvc.perform(patch(BASE + "/" + id + "/disable")).andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();

		assertThat(segunda).isEqualTo(primeira);
	}

	@Test
	void deve_devolver_o_mesmo_resultado_quando_repete_o_enable() throws Exception {
		String id = criar("Francês");

		String primeira = mockMvc.perform(patch(BASE + "/" + id + "/enable")).andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		String segunda = mockMvc.perform(patch(BASE + "/" + id + "/enable")).andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();

		assertThat(segunda).isEqualTo(primeira);
		assertThat(segunda).contains("\"ativo\":true");
	}

	@Test
	void deve_devolver_404_quando_disable_ou_enable_usam_um_id_inexistente() throws Exception {
		verificarErro(mockMvc.perform(patch(BASE + "/" + UUID.randomUUID() + "/disable")), 404, "Not Found",
				CodigoErro.MATERIA_NAO_ENCONTRADA);
		verificarErro(mockMvc.perform(patch(BASE + "/" + UUID.randomUUID() + "/enable")), 404, "Not Found",
				CodigoErro.MATERIA_NAO_ENCONTRADA);
	}

	@Test
	void deve_devolver_400_quando_o_id_esta_malformado_no_disable_e_no_enable() throws Exception {
		verificarErro(mockMvc.perform(patch(BASE + "/nao-e-uuid/disable")), 400, "Bad Request",
				CodigoErro.PARAMETRO_INVALIDO);
		verificarErro(mockMvc.perform(patch(BASE + "/nao-e-uuid/enable")), 400, "Bad Request",
				CodigoErro.PARAMETRO_INVALIDO);
	}

	// ----- DELETE -----

	@Test
	void deve_devolver_204_e_depois_404_quando_apaga_uma_materia() throws Exception {
		String id = criar("Latim");

		mockMvc.perform(delete(BASE + "/" + id)).andExpect(status().isNoContent()).andExpect(content().string(""));
		verificarErro(mockMvc.perform(get(BASE + "/" + id)), 404, "Not Found", CodigoErro.MATERIA_NAO_ENCONTRADA);
	}

	@Test
	void deve_devolver_404_quando_apaga_a_mesma_materia_duas_vezes() throws Exception {
		String id = criar("Grego");

		mockMvc.perform(delete(BASE + "/" + id)).andExpect(status().isNoContent());
		verificarErro(mockMvc.perform(delete(BASE + "/" + id)), 404, "Not Found", CodigoErro.MATERIA_NAO_ENCONTRADA);
	}

	@Test
	void deve_devolver_400_quando_o_id_esta_malformado_no_delete() throws Exception {
		verificarErro(mockMvc.perform(delete(BASE + "/xyz")), 400, "Bad Request", CodigoErro.PARAMETRO_INVALIDO);
	}

	// ----- Location e formato -----

	@Test
	void deve_apontar_o_location_para_um_recurso_que_existe() throws Exception {
		String location = postar(corpo("Estatística", null)).andExpect(status().isCreated())
				.andExpect(header().exists("Location"))
				.andReturn().getResponse().getHeader("Location");

		mockMvc.perform(get(location)).andExpect(status().isOk()).andExpect(jsonPath("$.nome").value("Estatística"));
	}

}
