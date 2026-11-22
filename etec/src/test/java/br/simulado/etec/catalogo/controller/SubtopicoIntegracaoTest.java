package br.simulado.etec.catalogo.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.restassured.response.Response;

/**
 * Fluxo completo e erros 400, 404 e 409 de cada endpoint do Subtópico, por HTTP real contra o MySQL real (migrations
 * V1 a V3 de verdade). Cada teste cria a própria matéria com nome único.
 */
class SubtopicoIntegracaoTest extends AbstractSubtopicoIntegrationTest {

	private static final String UUID_INEXISTENTE = "11111111-2222-3333-4444-555555555555";

	private static final String PADRAO_UUID = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

	// ----- fluxo completo -----

	@Test
	void deve_completar_o_fluxo_quando_cria_busca_renomeia_desativa_ativa_e_apaga() {
		String materiaId = novaMateria();
		String nome = nomeUnico("Equações");
		String novoNome = nomeUnico("Equações do 2º grau");

		Response criado = criarSubtopico(materiaId, nome);
		String id = criado.path("id");
		criado.then().statusCode(201)
				.header("Location", "/api/v1/subtopicos/" + id)
				.body("id", matchesPattern(PADRAO_UUID))
				.body("materiaId", equalTo(materiaId))
				.body("nome", equalTo(nome))
				.body("ativo", equalTo(true))
				.body("criadoEm", matchesPattern(DATA_HORA))
				.body("atualizadoEm", matchesPattern(DATA_HORA));

		buscarSubtopico(id).then().statusCode(200).body("id", equalTo(id)).body("materiaId", equalTo(materiaId))
				.body("nome", equalTo(nome));
		atualizarSubtopico(id, materiaId, novoNome).then().statusCode(200).body("nome", equalTo(novoNome))
				.body("materiaId", equalTo(materiaId)).body("ativo", equalTo(true));
		desativarSubtopico(id).then().statusCode(200).body("ativo", equalTo(false)).body("nome", equalTo(novoNome));
		buscarSubtopico(id).then().statusCode(200).body("ativo", equalTo(false));
		ativarSubtopico(id).then().statusCode(200).body("ativo", equalTo(true));
		apagarSubtopico(id).then().statusCode(204);
		verificarErro(buscarSubtopico(id), 404, "Not Found", "SUBTOPICO_NAO_ENCONTRADO");
		// apagar o subtópico não apaga a matéria
		buscar(materiaId).then().statusCode(200);
	}

	@Test
	void deve_devolver_o_location_com_o_prefixo_api_v1_e_um_id_que_o_get_encontra() {
		String materiaId = novaMateria();

		Response criado = criarSubtopico(materiaId, nomeUnico("Funções"));

		String location = criado.getHeader("Location");
		assertThat(location).startsWith("/api/v1/subtopicos/");
		requisicao().get(location).then().statusCode(200).body("id", equalTo(criado.path("id")));
	}

	@Test
	void deve_nao_expor_o_id_numerico_nem_a_entidade_nem_campos_internos() {
		String materiaId = novaMateria();

		Response criado = criarSubtopico(materiaId, nomeUnico("Geometria"));

		Map<String, Object> corpo = criado.path("$");
		assertThat(corpo.keySet()).containsExactlyInAnyOrder("id", "materiaId", "nome", "ativo", "criadoEm", "atualizadoEm");
		assertThat(corpo.get("id")).isInstanceOf(String.class);
		assertThat(corpo.get("materiaId")).isInstanceOf(String.class);
	}

	// ----- POST: corpo -----

	@Test
	void deve_devolver_400_com_campos_quando_a_materia_e_omitida_nula_ou_o_nome_e_invalido() {
		String materiaId = novaMateria();

		verificarErroComCampo(requisicao().body("{\"nome\":\"Álgebra\"}").post(BASE_SUBTOPICOS), "materiaId");
		verificarErroComCampo(requisicao().body("{\"materiaId\":null,\"nome\":\"Álgebra\"}").post(BASE_SUBTOPICOS),
				"materiaId");
		verificarErroComCampo(requisicao().body("{\"materiaId\":\"" + materiaId + "\"}").post(BASE_SUBTOPICOS), "nome");
		verificarErroComCampo(criarSubtopico(materiaId, null), "nome");
		verificarErroComCampo(criarSubtopico(materiaId, "A"), "nome");
		verificarErroComCampo(criarSubtopico(materiaId, "        "), "nome");
		verificarErroComCampo(criarSubtopico(materiaId, "x".repeat(101)), "nome");
	}

	@Test
	void deve_aceitar_nome_com_2_e_com_100_caracteres() {
		String materiaId = novaMateria();

		criarSubtopico(materiaId, "AB").then().statusCode(201);
		criarSubtopico(materiaId, "x".repeat(100)).then().statusCode(201).body("nome", equalTo("x".repeat(100)));
	}

	@Test
	void deve_devolver_400_com_campos_materiaId_quando_a_materia_nao_existe() {
		Response resposta = criarSubtopico(UUID_INEXISTENTE, nomeUnico("Trigonometria"));

		verificarErroComCampo(resposta, "materiaId");
		resposta.then().body("mensagem", equalTo("Há campos inválidos na requisição."));
	}

	@Test
	void deve_devolver_400_com_corpo_ilegivel_quando_o_materiaid_nao_e_um_uuid_ou_o_json_esta_quebrado() {
		verificarErro(requisicao().body("{\"materiaId\":\"nao-e-uuid\",\"nome\":\"Álgebra\"}").post(BASE_SUBTOPICOS), 400,
				"Bad Request", "CORPO_ILEGIVEL");
		verificarErro(requisicao().body("{\"materiaId\":123,\"nome\":\"Álgebra\"}").post(BASE_SUBTOPICOS), 400, "Bad Request",
				"CORPO_ILEGIVEL");
		verificarErro(requisicao().body("{\"materiaId\":").post(BASE_SUBTOPICOS), 400, "Bad Request", "CORPO_ILEGIVEL");
		verificarErro(requisicao().body("{\"materiaId\":\"" + novaMateria() + "\",\"nome\":{\"x\":1}}").post(BASE_SUBTOPICOS),
				400, "Bad Request", "CORPO_ILEGIVEL");
	}

	@Test
	void deve_ignorar_campos_desconhecidos_e_de_saida_no_corpo() {
		String materiaId = novaMateria();
		String nome = nomeUnico("Probabilidade");
		String corpo = "{\"materiaId\":\"" + materiaId + "\",\"nome\":\"" + nome + "\",\"id\":\"" + UUID_INEXISTENTE
				+ "\",\"ativo\":false,\"criadoEm\":\"2000-01-01T00:00:00.000Z\",\"campoQueNaoExiste\":123}";

		Response criado = requisicao().body(corpo).post(BASE_SUBTOPICOS);

		criado.then().statusCode(201).body("ativo", equalTo(true)).body("nome", equalTo(nome))
				.body("id", not(equalTo(UUID_INEXISTENTE))).body("criadoEm", not(equalTo("2000-01-01T00:00:00.000Z")))
				.body("$", not(hasKey("campoQueNaoExiste")));
	}

	@Test
	void deve_aparar_os_espacos_das_pontas_do_nome() {
		String materiaId = novaMateria();
		String nome = nomeUnico("Estatística");

		criarSubtopico(materiaId, "   " + nome + "  ").then().statusCode(201).body("nome", equalTo(nome));
	}

	@Test
	void deve_devolver_415_quando_o_corpo_nao_e_json() {
		verificarErro(requisicao().contentType("text/plain").body("materiaId=1").post(BASE_SUBTOPICOS), 415,
				"Unsupported Media Type", "TIPO_NAO_SUPORTADO");
	}

	// ----- GET por id -----

	@Test
	void deve_devolver_404_para_id_inexistente_e_400_para_id_malformado() {
		verificarErro(buscarSubtopico(UUID_INEXISTENTE), 404, "Not Found", "SUBTOPICO_NAO_ENCONTRADO");
		verificarErro(buscarSubtopico("abc"), 400, "Bad Request", "PARAMETRO_INVALIDO");
		verificarErro(buscarSubtopico(UUID.randomUUID().toString()), 404, "Not Found", "SUBTOPICO_NAO_ENCONTRADO");
	}

	// ----- PUT -----

	@Test
	void deve_trocar_so_o_nome_e_nunca_o_estado_ativo_no_put() {
		String materiaId = novaMateria();
		String id = criarSubtopicoEObterId(materiaId, nomeUnico("Logaritmos"));
		desativarSubtopico(id).then().statusCode(200);
		String novoNome = nomeUnico("Logaritmos decimais");

		atualizarSubtopico(id, null, novoNome).then().statusCode(200).body("nome", equalTo(novoNome))
				.body("ativo", equalTo(false)).body("materiaId", equalTo(materiaId));
		// "ativo" no corpo é ignorado
		requisicao().body("{\"nome\":\"" + novoNome + " 2\",\"ativo\":true}").put(BASE_SUBTOPICOS + "/" + id).then()
				.statusCode(200).body("ativo", equalTo(false));
	}

	@Test
	void deve_aceitar_o_materiaid_igual_ao_atual_e_recusar_o_diferente_com_campo_imutavel() {
		String materiaId = novaMateria();
		String outraMateria = novaMateria();
		String id = criarSubtopicoEObterId(materiaId, nomeUnico("Matrizes"));

		atualizarSubtopico(id, materiaId, nomeUnico("Matrizes 2")).then().statusCode(200);
		verificarErro(atualizarSubtopico(id, outraMateria, nomeUnico("Matrizes 3")), 400, "Bad Request", "CAMPO_IMUTAVEL");
		// o subtópico continua na matéria original
		buscarSubtopico(id).then().statusCode(200).body("materiaId", equalTo(materiaId));
	}

	@Test
	void deve_devolver_400_campo_imutavel_e_nao_alterar_o_subtopico_quando_o_materiaid_do_put_nao_existe() {
		String materiaId = novaMateria();
		String nome = nomeUnico("Funções");
		String id = criarSubtopicoEObterId(materiaId, nome);

		verificarErro(atualizarSubtopico(id, UUID.randomUUID().toString(), nomeUnico("Funções 2")), 400, "Bad Request",
				"CAMPO_IMUTAVEL");

		buscarSubtopico(id).then().statusCode(200).body("materiaId", equalTo(materiaId)).body("nome", equalTo(nome));
	}

	@Test
	void deve_devolver_400_no_put_quando_o_nome_e_invalido_ou_omitido() {
		String materiaId = novaMateria();
		String id = criarSubtopicoEObterId(materiaId, nomeUnico("Vetores"));

		verificarErroComCampo(atualizarSubtopico(id, null, null), "nome");
		verificarErroComCampo(atualizarSubtopico(id, null, "A"), "nome");
		verificarErroComCampo(atualizarSubtopico(id, null, "   "), "nome");
		verificarErroComCampo(atualizarSubtopico(id, null, "y".repeat(101)), "nome");
		verificarErro(requisicao().body("{\"materiaId\":\"nao-e-uuid\",\"nome\":\"Vetores 2\"}")
				.put(BASE_SUBTOPICOS + "/" + id), 400, "Bad Request", "CORPO_ILEGIVEL");
	}

	@Test
	void deve_devolver_404_no_put_de_subtopico_inexistente_e_400_para_id_malformado() {
		verificarErro(atualizarSubtopico(UUID_INEXISTENTE, null, "Nome qualquer"), 404, "Not Found",
				"SUBTOPICO_NAO_ENCONTRADO");
		verificarErro(atualizarSubtopico("abc", null, "Nome qualquer"), 400, "Bad Request", "PARAMETRO_INVALIDO");
	}

	@Test
	void deve_seguir_a_ordem_de_validacao_da_d79_no_put() {
		String materiaId = novaMateria();
		String outraMateria = novaMateria();
		String nomeExistente = nomeUnico("Ocupado");
		criarSubtopicoEObterId(materiaId, nomeExistente);
		String id = criarSubtopicoEObterId(materiaId, nomeUnico("Livre"));

		// 404 vem antes do CAMPO_IMUTAVEL: subtópico inexistente com materiaId "diferente" = 404
		verificarErro(atualizarSubtopico(UUID_INEXISTENTE, outraMateria, "Qualquer nome"), 404, "Not Found",
				"SUBTOPICO_NAO_ENCONTRADO");
		// 409 de nome vem antes do CAMPO_IMUTAVEL: nome repetido E materiaId diferente = 409
		verificarErro(atualizarSubtopico(id, outraMateria, nomeExistente), 409, "Conflict", "SUBTOPICO_NOME_DUPLICADO");
		// 400 de formato vem antes de tudo: nome inválido E materiaId diferente = 400 de validação
		verificarErroComCampo(atualizarSubtopico(id, outraMateria, "A"), "nome");
	}

	// ----- DELETE -----

	@Test
	void deve_devolver_204_no_delete_e_404_na_repeticao_e_400_para_id_malformado() {
		String id = criarSubtopicoEObterId(novaMateria(), nomeUnico("Para apagar"));

		apagarSubtopico(id).then().statusCode(204);
		verificarErro(apagarSubtopico(id), 404, "Not Found", "SUBTOPICO_NAO_ENCONTRADO");
		verificarErro(apagarSubtopico("abc"), 400, "Bad Request", "PARAMETRO_INVALIDO");
	}

	// ----- disable / enable: 404 e 400 -----

	@Test
	void deve_devolver_404_em_disable_e_enable_de_subtopico_inexistente_e_400_para_id_malformado() {
		verificarErro(desativarSubtopico(UUID_INEXISTENTE), 404, "Not Found", "SUBTOPICO_NAO_ENCONTRADO");
		verificarErro(ativarSubtopico(UUID_INEXISTENTE), 404, "Not Found", "SUBTOPICO_NAO_ENCONTRADO");
		verificarErro(desativarSubtopico("abc"), 400, "Bad Request", "PARAMETRO_INVALIDO");
		verificarErro(ativarSubtopico("abc"), 400, "Bad Request", "PARAMETRO_INVALIDO");
	}

	// ----- rotas e métodos -----

	@Test
	void deve_devolver_403_em_json_para_metodo_sem_regra_na_colecao() {
		// D121: DELETE e PATCH na coleção não têm regra: negados, e não 405.
		verificarErro(requisicao().delete(BASE_SUBTOPICOS), 403, "Forbidden", "ACESSO_NEGADO");
		verificarErro(requisicao().patch(BASE_SUBTOPICOS), 403, "Forbidden", "ACESSO_NEGADO");
	}

}
