package br.simulado.etec.catalogo.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import br.simulado.etec.catalogo.repository.SubtopicoRepository;
import br.simulado.etec.shared.exception.CodigoErro;

/**
 * Teste web de ponta a ponta do Subtópico (H2): contrato HTTP, códigos de status e formato de erro. A unicidade sem
 * distinguir caixa e acento e a FK real do MySQL NÃO são provadas aqui (o H2 compara com distinção); isso é das classes
 * de integração, contra o MySQL real.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SubtopicoControllerTest {

	private static final String BASE = "/api/v1/subtopicos";

	private static final String MATERIAS = "/api/v1/materias";

	private static final String DATA_HORA = "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}Z";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private SubtopicoRepository subtopicos;

	@Autowired
	private MateriaRepository materias;

	@BeforeEach
	@AfterEach
	void limpar() {
		// subtópicos primeiro: a FK com RESTRICT não deixa apagar a matéria antes
		subtopicos.deleteAll();
		materias.deleteAll();
	}

	// ----- apoio -----

	private String criarMateria(String nome) throws Exception {
		String resposta = mockMvc.perform(post(MATERIAS).contentType(MediaType.APPLICATION_JSON)
				.content("{\"nome\":\"" + nome + "\"}")).andExpect(status().isCreated()).andReturn().getResponse()
				.getContentAsString();
		return JsonPath.read(resposta, "$.id");
	}

	private static String corpo(String materiaId, String nome) {
		return "{\"materiaId\":\"" + materiaId + "\",\"nome\":\"" + nome + "\"}";
	}

	private ResultActions postar(String json) throws Exception {
		return mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(json));
	}

	private String criar(String materiaId, String nome) throws Exception {
		String resposta = postar(corpo(materiaId, nome)).andExpect(status().isCreated()).andReturn().getResponse()
				.getContentAsString();
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
	void deve_devolver_201_com_location_com_prefixo_e_corpo_quando_cria_um_subtopico() throws Exception {
		String materiaId = criarMateria("Matemática");

		ResultActions resposta = postar(corpo(materiaId, "Álgebra"));

		String id = JsonPath.read(resposta.andReturn().getResponse().getContentAsString(), "$.id");
		resposta.andExpect(status().isCreated())
				.andExpect(header().string("Location", "/api/v1/subtopicos/" + id))
				.andExpect(jsonPath("$.materiaId").value(materiaId))
				.andExpect(jsonPath("$.nome").value("Álgebra"))
				.andExpect(jsonPath("$.ativo").value(true))
				.andExpect(jsonPath("$.criadoEm", matchesPattern(DATA_HORA)))
				.andExpect(jsonPath("$.atualizadoEm", matchesPattern(DATA_HORA)))
				.andExpect(jsonPath("$.publicId").doesNotExist());
	}

	@Test
	void deve_devolver_400_com_campos_quando_a_materia_e_o_nome_faltam_ou_o_nome_e_curto() throws Exception {
		String materiaId = criarMateria("Física");

		verificarErro(postar("{\"nome\":\"Cinemática\"}"), 400, "Bad Request", CodigoErro.VALIDACAO_FALHOU)
				.andExpect(jsonPath("$.campos[*].campo", hasItem("materiaId")));
		verificarErro(postar("{\"materiaId\":\"" + materiaId + "\"}"), 400, "Bad Request", CodigoErro.VALIDACAO_FALHOU)
				.andExpect(jsonPath("$.campos[*].campo", hasItem("nome")));
		verificarErro(postar(corpo(materiaId, "A")), 400, "Bad Request", CodigoErro.VALIDACAO_FALHOU)
				.andExpect(jsonPath("$.campos[*].campo", hasItem("nome")));
		verificarErro(postar(corpo(materiaId, "   ")), 400, "Bad Request", CodigoErro.VALIDACAO_FALHOU);
	}

	@Test
	void deve_devolver_400_validacao_com_campo_materia_id_quando_a_materia_nao_existe() throws Exception {
		verificarErro(postar(corpo("11111111-2222-3333-4444-555555555555", "Órfão")), 400, "Bad Request",
				CodigoErro.VALIDACAO_FALHOU).andExpect(jsonPath("$.campos[0].campo").value("materiaId"));
	}

	@Test
	void deve_devolver_400_corpo_ilegivel_quando_o_materia_id_nao_e_um_uuid() throws Exception {
		verificarErro(postar("{\"materiaId\":\"abc\",\"nome\":\"Teste\"}"), 400, "Bad Request", CodigoErro.CORPO_ILEGIVEL);
		verificarErro(postar("{\"materiaId\":"), 400, "Bad Request", CodigoErro.CORPO_ILEGIVEL);
	}

	@Test
	void deve_devolver_409_materia_inativa_quando_a_materia_esta_desativada() throws Exception {
		String materiaId = criarMateria("Química");
		mockMvc.perform(patch(MATERIAS + "/" + materiaId + "/disable")).andExpect(status().isOk());

		verificarErro(postar(corpo(materiaId, "Orgânica")), 409, "Conflict", CodigoErro.MATERIA_INATIVA);
	}

	@Test
	void deve_devolver_409_quando_o_nome_se_repete_na_mesma_materia_e_201_em_outra_materia() throws Exception {
		String quimica = criarMateria("Química");
		String biologia = criarMateria("Biologia");
		criar(quimica, "Introdução");

		verificarErro(postar(corpo(quimica, "Introdução")), 409, "Conflict", CodigoErro.SUBTOPICO_NOME_DUPLICADO);
		postar(corpo(biologia, "Introdução")).andExpect(status().isCreated());
	}

	@Test
	void deve_aparar_os_espacos_das_pontas_do_nome() throws Exception {
		String materiaId = criarMateria("História");

		postar(corpo(materiaId, "   Brasil Colônia  ")).andExpect(status().isCreated())
				.andExpect(jsonPath("$.nome").value("Brasil Colônia"));
	}

	@Test
	void deve_ignorar_campos_desconhecidos_e_de_saida_no_corpo() throws Exception {
		String materiaId = criarMateria("Geografia");

		postar("{\"materiaId\":\"" + materiaId + "\",\"nome\":\"Relevo\",\"ativo\":false,\"id\":\"x\",\"campoNovo\":1}")
				.andExpect(status().isCreated()).andExpect(jsonPath("$.ativo").value(true))
				.andExpect(jsonPath("$.campoNovo").doesNotExist());
	}

	// ----- GET -----

	@Test
	void deve_buscar_pelo_id_e_devolver_404_para_inexistente_e_400_para_malformado() throws Exception {
		String id = criar(criarMateria("Língua Portuguesa"), "Sintaxe");

		mockMvc.perform(get(BASE + "/" + id)).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id))
				.andExpect(jsonPath("$.nome").value("Sintaxe"));
		verificarErro(mockMvc.perform(get(BASE + "/11111111-2222-3333-4444-555555555555")), 404, "Not Found",
				CodigoErro.SUBTOPICO_NAO_ENCONTRADO);
		verificarErro(mockMvc.perform(get(BASE + "/abc")), 400, "Bad Request", CodigoErro.PARAMETRO_INVALIDO);
	}

	@Test
	void deve_listar_com_envelope_filtros_e_ordenacao() throws Exception {
		String materiaId = criarMateria("Inglês");
		criar(materiaId, "Verbos");
		criar(materiaId, "Adjetivos");
		criar(criarMateria("Espanhol"), "Verbos");

		mockMvc.perform(get(BASE).param("materiaId", materiaId)).andExpect(status().isOk())
				.andExpect(jsonPath("$.conteudo.length()").value(2)).andExpect(jsonPath("$.conteudo[0].nome").value("Adjetivos"))
				.andExpect(jsonPath("$.conteudo[1].nome").value("Verbos")).andExpect(jsonPath("$.pagina").value(0))
				.andExpect(jsonPath("$.tamanho").value(20)).andExpect(jsonPath("$.totalElementos").value(2))
				.andExpect(jsonPath("$.totalPaginas").value(1));
		mockMvc.perform(get(BASE).param("materiaId", materiaId).param("sort", "nome,desc")).andExpect(status().isOk())
				.andExpect(jsonPath("$.conteudo[0].nome").value("Verbos"));
		mockMvc.perform(get(BASE).param("materiaId", materiaId).param("nome", "VERB")).andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElementos").value(0)); // o H2 distingue caixa: o MySQL real é provado à parte
	}

	@Test
	void deve_devolver_400_na_listagem_para_parametros_invalidos_e_mais_de_um_sort() throws Exception {
		verificarErro(mockMvc.perform(get(BASE).param("materiaId", "abc")), 400, "Bad Request", CodigoErro.PARAMETRO_INVALIDO);
		verificarErro(mockMvc.perform(get(BASE).param("ativo", "talvez")), 400, "Bad Request", CodigoErro.PARAMETRO_INVALIDO);
		verificarErro(mockMvc.perform(get(BASE).param("size", "0")), 400, "Bad Request", CodigoErro.PARAMETRO_INVALIDO);
		verificarErro(mockMvc.perform(get(BASE).param("page", "-1")), 400, "Bad Request", CodigoErro.PARAMETRO_INVALIDO);
		verificarErro(mockMvc.perform(get(BASE).param("sort", "materiaId")), 400, "Bad Request", CodigoErro.ORDENACAO_INVALIDA);
		verificarErro(mockMvc.perform(get(BASE).param("sort", "nome", "desc")), 400, "Bad Request",
				CodigoErro.ORDENACAO_INVALIDA);
		verificarErro(mockMvc.perform(get(BASE).param("sort", "NOME")), 400, "Bad Request", CodigoErro.ORDENACAO_INVALIDA);
	}

	@Test
	void deve_devolver_lista_vazia_para_pagina_alem_do_fim_e_para_materia_inexistente() throws Exception {
		String materiaId = criarMateria("Artes");
		criar(materiaId, "Cores");

		mockMvc.perform(get(BASE).param("materiaId", materiaId).param("page", "9")).andExpect(status().isOk())
				.andExpect(jsonPath("$.conteudo.length()").value(0)).andExpect(jsonPath("$.pagina").value(9))
				.andExpect(jsonPath("$.totalElementos").value(1));
		mockMvc.perform(get(BASE).param("materiaId", "11111111-2222-3333-4444-555555555555")).andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElementos").value(0));
	}

	// ----- PUT -----

	@Test
	void deve_trocar_o_nome_e_aceitar_o_materia_id_igual_e_recusar_o_diferente() throws Exception {
		String materiaId = criarMateria("Sociologia");
		String outraMateria = criarMateria("Filosofia");
		String id = criar(materiaId, "Durkheim");

		mockMvc.perform(put(BASE + "/" + id).contentType(MediaType.APPLICATION_JSON).content(corpo(materiaId, "Émile Durkheim")))
				.andExpect(status().isOk()).andExpect(jsonPath("$.nome").value("Émile Durkheim"))
				.andExpect(jsonPath("$.materiaId").value(materiaId));
		mockMvc.perform(put(BASE + "/" + id).contentType(MediaType.APPLICATION_JSON).content("{\"nome\":\"Sem materiaId\"}"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.nome").value("Sem materiaId"));
		verificarErro(mockMvc.perform(put(BASE + "/" + id).contentType(MediaType.APPLICATION_JSON)
				.content(corpo(outraMateria, "Outro"))), 400, "Bad Request", CodigoErro.CAMPO_IMUTAVEL);
	}

	@Test
	void deve_devolver_404_no_put_de_inexistente_antes_do_campo_imutavel_e_409_antes_dele_no_nome_repetido()
			throws Exception {
		String materiaId = criarMateria("Teatro");
		String outraMateria = criarMateria("Dança");
		criar(materiaId, "Ocupado");
		String id = criar(materiaId, "Livre");

		verificarErro(mockMvc.perform(put(BASE + "/11111111-2222-3333-4444-555555555555")
				.contentType(MediaType.APPLICATION_JSON).content(corpo(outraMateria, "Qualquer"))), 404, "Not Found",
				CodigoErro.SUBTOPICO_NAO_ENCONTRADO);
		verificarErro(mockMvc.perform(put(BASE + "/" + id).contentType(MediaType.APPLICATION_JSON)
				.content(corpo(outraMateria, "Ocupado"))), 409, "Conflict", CodigoErro.SUBTOPICO_NOME_DUPLICADO);
	}

	@Test
	void deve_devolver_400_no_put_quando_o_nome_e_omitido_ou_invalido() throws Exception {
		String id = criar(criarMateria("Educação Física"), "Atletismo");

		verificarErro(mockMvc.perform(put(BASE + "/" + id).contentType(MediaType.APPLICATION_JSON).content("{}")), 400,
				"Bad Request", CodigoErro.VALIDACAO_FALHOU).andExpect(jsonPath("$.campos[*].campo", hasItem("nome")));
		verificarErro(mockMvc.perform(put(BASE + "/" + id).contentType(MediaType.APPLICATION_JSON).content("{\"nome\":\"A\"}")),
				400, "Bad Request", CodigoErro.VALIDACAO_FALHOU);
	}

	// ----- disable / enable -----

	@Test
	void deve_desativar_e_ativar_com_200_e_o_recurso_completo() throws Exception {
		String materiaId = criarMateria("Ciências");
		String id = criar(materiaId, "Ecologia");

		mockMvc.perform(patch(BASE + "/" + id + "/disable")).andExpect(status().isOk()).andExpect(jsonPath("$.ativo").value(false))
				.andExpect(jsonPath("$.nome").value("Ecologia")).andExpect(jsonPath("$.materiaId").value(materiaId));
		mockMvc.perform(patch(BASE + "/" + id + "/disable")).andExpect(status().isOk()).andExpect(jsonPath("$.ativo").value(false));
		mockMvc.perform(patch(BASE + "/" + id + "/enable")).andExpect(status().isOk()).andExpect(jsonPath("$.ativo").value(true));
		mockMvc.perform(patch(BASE + "/" + id + "/enable")).andExpect(status().isOk()).andExpect(jsonPath("$.ativo").value(true));
	}

	@Test
	void deve_dar_409_ao_ativar_subtopico_inativo_de_materia_desativada_e_200_ao_ativar_o_ja_ativo() throws Exception {
		String materiaId = criarMateria("Astronomia");
		String inativo = criar(materiaId, "Planetas");
		String ativo = criar(materiaId, "Estrelas");
		mockMvc.perform(patch(BASE + "/" + inativo + "/disable")).andExpect(status().isOk());
		mockMvc.perform(patch(MATERIAS + "/" + materiaId + "/disable")).andExpect(status().isOk());

		verificarErro(mockMvc.perform(patch(BASE + "/" + inativo + "/enable")), 409, "Conflict", CodigoErro.MATERIA_INATIVA);
		mockMvc.perform(patch(BASE + "/" + ativo + "/enable")).andExpect(status().isOk()).andExpect(jsonPath("$.ativo").value(true));
		// renomear e desativar continuam permitidos com a matéria desativada
		mockMvc.perform(put(BASE + "/" + ativo).contentType(MediaType.APPLICATION_JSON).content("{\"nome\":\"Estrelas 2\"}"))
				.andExpect(status().isOk());
		mockMvc.perform(patch(BASE + "/" + ativo + "/disable")).andExpect(status().isOk());
	}

	// ----- DELETE do subtópico e da matéria -----

	@Test
	void deve_apagar_o_subtopico_com_204_e_dar_404_na_repeticao() throws Exception {
		String id = criar(criarMateria("Robótica"), "Sensores");

		mockMvc.perform(delete(BASE + "/" + id)).andExpect(status().isNoContent()).andExpect(content().string(""));
		verificarErro(mockMvc.perform(delete(BASE + "/" + id)), 404, "Not Found", CodigoErro.SUBTOPICO_NAO_ENCONTRADO);
	}

	@Test
	void deve_devolver_409_materia_com_subtopicos_ao_apagar_a_materia_e_204_depois_de_apagar_o_subtopico() throws Exception {
		String materiaId = criarMateria("Informática");
		String id = criar(materiaId, "Redes");

		verificarErro(mockMvc.perform(delete(MATERIAS + "/" + materiaId)), 409, "Conflict", CodigoErro.MATERIA_COM_SUBTOPICOS);
		assertThat(subtopicos.count()).isEqualTo(1);
		mockMvc.perform(delete(BASE + "/" + id)).andExpect(status().isNoContent());
		mockMvc.perform(delete(MATERIAS + "/" + materiaId)).andExpect(status().isNoContent());
	}

	@Test
	void deve_apagar_a_materia_sem_subtopicos_como_antes() throws Exception {
		String materiaId = criarMateria("Empreendedorismo");

		mockMvc.perform(delete(MATERIAS + "/" + materiaId)).andExpect(status().isNoContent());
	}

}
