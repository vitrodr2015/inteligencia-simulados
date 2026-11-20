package br.simulado.etec.catalogo.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.restassured.response.Response;

/**
 * Fluxo completo e erros 400, 404 e 409 de cada endpoint da Matéria, por HTTP real contra o MySQL real
 * (migrations V1 e V2 de verdade). Cada teste cria os próprios dados com nomes únicos.
 */
class MateriaIntegracaoTest extends AbstractMateriaIntegrationTest {

	private static final String UUID_INEXISTENTE = "11111111-2222-3333-4444-555555555555";

	// ----- fluxo completo -----

	@Test
	void deve_completar_o_fluxo_quando_cria_busca_atualiza_desativa_ativa_e_apaga() {
		String nome = nomeUnico("Biologia");
		String novoNome = nomeUnico("Biologia Marinha");

		Response criada = criar(nome, "Seres vivos");
		String id = criada.path("id");
		criada.then().statusCode(201)
				.header("Location", "/api/v1/materias/" + id)
				.body("nome", equalTo(nome))
				.body("descricao", equalTo("Seres vivos"))
				.body("ativo", equalTo(true))
				.body("id", matchesPattern("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"));

		buscar(id).then().statusCode(200).body("id", equalTo(id)).body("nome", equalTo(nome));
		atualizar(id, novoNome, "Oceanos").then().statusCode(200)
				.body("nome", equalTo(novoNome)).body("descricao", equalTo("Oceanos")).body("ativo", equalTo(true));
		desativar(id).then().statusCode(200).body("ativo", equalTo(false));
		buscar(id).then().statusCode(200).body("ativo", equalTo(false));
		ativar(id).then().statusCode(200).body("ativo", equalTo(true));
		apagar(id).then().statusCode(204);
		verificarErro(buscar(id), 404, "Not Found", "MATERIA_NAO_ENCONTRADA");
	}

	@Test
	void deve_gerar_o_id_e_manter_ativo_true_quando_o_corpo_traz_id_e_ativo_false() {
		String enviado = "{\"nome\":\"" + nomeUnico("Mass Assignment") + "\",\"id\":\"11111111-1111-1111-1111-111111111111\","
				+ "\"ativo\":false,\"criadoEm\":\"2000-01-01T00:00:00.000Z\",\"atualizadoEm\":\"2000-01-01T00:00:00.000Z\"}";

		Response resposta = requisicao().body(enviado).post(BASE);

		resposta.then().statusCode(201)
				.body("id", not(equalTo("11111111-1111-1111-1111-111111111111")))
				.body("ativo", equalTo(true))
				.body("criadoEm", not(equalTo("2000-01-01T00:00:00.000Z")))
				.body("atualizadoEm", not(equalTo("2000-01-01T00:00:00.000Z")));
		String id = resposta.path("id");
		buscar(id).then().statusCode(200).body("ativo", equalTo(true));
	}

	@Test
	void deve_remover_os_espacos_das_pontas_do_nome_quando_cria() {
		String nome = nomeUnico("Sociologia");

		criar("   " + nome + "   ", null).then().statusCode(201).body("nome", equalTo(nome));
	}

	@Test
	void deve_nunca_alterar_o_ativo_quando_o_put_envia_ativo_no_corpo() {
		String nome = nomeUnico("História");
		String id = criarEObterId(nome);
		desativar(id).then().statusCode(200);

		requisicao().body("{\"nome\":\"" + nome + "\",\"descricao\":\"x\",\"ativo\":true}").put(BASE + "/" + id)
				.then().statusCode(200).body("ativo", equalTo(false));
	}

	@Test
	void deve_zerar_a_descricao_quando_o_put_a_omite() {
		String nome = nomeUnico("Geografia");
		String id = criarEObterId(nome);

		requisicao().body("{\"nome\":\"" + nome + "\"}").put(BASE + "/" + id)
				.then().statusCode(200).body("descricao", nullValue());
	}

	@Test
	void deve_devolver_o_mesmo_resultado_quando_repete_disable_e_enable() {
		String id = criarEObterId(nomeUnico("Idempotência"));

		String primeiroDisable = desativar(id).then().statusCode(200).extract().asString();
		String segundoDisable = desativar(id).then().statusCode(200).extract().asString();
		String primeiroEnable = ativar(id).then().statusCode(200).extract().asString();
		String segundoEnable = ativar(id).then().statusCode(200).extract().asString();

		assertThat(segundoDisable).isEqualTo(primeiroDisable);
		assertThat(segundoEnable).isEqualTo(primeiroEnable);
	}

	// ----- POST: 400, 409, 415 -----

	@Test
	void deve_devolver_400_com_campos_quando_o_nome_so_tem_espacos() {
		Response resposta = criar("     ", null);

		verificarErro(resposta, 400, "Bad Request", "VALIDACAO_FALHOU");
		resposta.then().body("campos.size()", equalTo(1)).body("campos[0].campo", equalTo("nome"))
				.body("campos[0].mensagem", equalTo("O nome deve ter de 2 a 100 caracteres."));
	}

	@Test
	void deve_devolver_400_quando_o_nome_tem_um_caractere_ou_passa_de_cem() {
		verificarErro(criar("A", null), 400, "Bad Request", "VALIDACAO_FALHOU");
		verificarErro(criar("x".repeat(101), null), 400, "Bad Request", "VALIDACAO_FALHOU");
	}

	@Test
	void deve_devolver_400_quando_falta_o_nome() {
		Response resposta = requisicao().body("{\"descricao\":\"sem nome\"}").post(BASE);

		verificarErro(resposta, 400, "Bad Request", "VALIDACAO_FALHOU");
		resposta.then().body("campos[0].campo", equalTo("nome")).body("campos[0].mensagem", equalTo("O nome é obrigatório."));
	}

	@Test
	void deve_devolver_400_quando_a_descricao_passa_de_255_caracteres() {
		Response resposta = criar(nomeUnico("Descrição longa"), "d".repeat(256));

		verificarErro(resposta, 400, "Bad Request", "VALIDACAO_FALHOU");
		resposta.then().body("campos[0].campo", equalTo("descricao"));
	}

	@Test
	void deve_devolver_400_sem_vazar_o_corpo_quando_o_json_esta_ilegivel() {
		Response resposta = requisicao().body("{\"nome\": \"SEGREDO-NO-CORPO\", ").post(BASE);

		verificarErro(resposta, 400, "Bad Request", "CORPO_ILEGIVEL");
		assertThat(resposta.asString()).doesNotContain("SEGREDO-NO-CORPO");
	}

	@Test
	void deve_devolver_415_quando_o_tipo_de_conteudo_nao_e_suportado() {
		verificarErro(requisicao().contentType("text/plain").body("texto").post(BASE), 415, "Unsupported Media Type",
				"TIPO_NAO_SUPORTADO");
	}

	@Test
	void deve_devolver_409_quando_o_nome_ja_existe() {
		String nome = nomeUnico("Duplicada");
		criar(nome, null).then().statusCode(201);

		Response resposta = criar(nome, null);

		verificarErro(resposta, 409, "Conflict", "MATERIA_NOME_DUPLICADO");
		resposta.then().body("campos", nullValue()).body("detalhes", nullValue());
	}

	// ----- GET -----

	@Test
	void deve_devolver_404_quando_busca_um_id_inexistente() {
		verificarErro(buscar(UUID_INEXISTENTE), 404, "Not Found", "MATERIA_NAO_ENCONTRADA");
	}

	@Test
	void deve_devolver_400_sem_vazar_o_valor_quando_o_id_esta_malformado() {
		Response resposta = buscar("isto-nao-e-um-uuid");

		verificarErro(resposta, 400, "Bad Request", "PARAMETRO_INVALIDO");
		assertThat(resposta.asString()).doesNotContain("isto-nao-e-um-uuid");
	}

	// ----- PUT -----

	@Test
	void deve_permitir_o_put_com_o_proprio_nome_e_devolver_409_com_o_nome_de_outra_materia() {
		String nomeA = nomeUnico("Música");
		String nomeB = nomeUnico("Artes");
		String idA = criarEObterId(nomeA);
		criarEObterId(nomeB);

		atualizar(idA, nomeA, "nova descrição").then().statusCode(200).body("descricao", equalTo("nova descrição"));
		verificarErro(atualizar(idA, nomeB, null), 409, "Conflict", "MATERIA_NOME_DUPLICADO");
	}

	@Test
	void deve_devolver_404_quando_o_put_usa_um_id_inexistente() {
		verificarErro(atualizar(UUID_INEXISTENTE, nomeUnico("Qualquer"), null), 404, "Not Found",
				"MATERIA_NAO_ENCONTRADA");
	}

	@Test
	void deve_devolver_400_quando_o_put_tem_corpo_invalido_ou_id_malformado() {
		String id = criarEObterId(nomeUnico("Corpo inválido"));

		verificarErro(atualizar(id, " ", null), 400, "Bad Request", "VALIDACAO_FALHOU");
		verificarErro(atualizar("xyz", nomeUnico("Qualquer"), null), 400, "Bad Request", "PARAMETRO_INVALIDO");
	}

	// ----- PATCH -----

	@Test
	void deve_devolver_404_e_400_quando_disable_e_enable_usam_ids_ruins() {
		verificarErro(desativar(UUID_INEXISTENTE), 404, "Not Found", "MATERIA_NAO_ENCONTRADA");
		verificarErro(ativar(UUID_INEXISTENTE), 404, "Not Found", "MATERIA_NAO_ENCONTRADA");
		verificarErro(desativar("nao-e-uuid"), 400, "Bad Request", "PARAMETRO_INVALIDO");
		verificarErro(ativar("nao-e-uuid"), 400, "Bad Request", "PARAMETRO_INVALIDO");
	}

	// ----- DELETE -----

	@Test
	void deve_devolver_404_quando_apaga_duas_vezes_e_400_quando_o_id_esta_malformado() {
		String id = criarEObterId(nomeUnico("Latim"));

		apagar(id).then().statusCode(204);
		verificarErro(apagar(id), 404, "Not Found", "MATERIA_NAO_ENCONTRADA");
		verificarErro(apagar("isto-nao-e-um-uuid"), 400, "Bad Request", "PARAMETRO_INVALIDO");
	}

	// ----- outros -----

	@Test
	void deve_devolver_404_em_json_quando_a_rota_nao_existe_em_qualquer_metodo() {
		verificarErro(requisicao().get("/api/v1/" + UUID.randomUUID()), 404, "Not Found", "RECURSO_NAO_ENCONTRADO");
		verificarErro(requisicao().body("{}").post("/api/v1/nao-existe"), 404, "Not Found", "RECURSO_NAO_ENCONTRADO");
	}

	@Test
	void deve_devolver_405_quando_chama_a_listagem_que_ainda_nao_existe() {
		Response resposta = requisicao().get(BASE);

		verificarErro(resposta, 405, "Method Not Allowed", "METODO_NAO_PERMITIDO");
		assertThat(resposta.header("Allow")).contains("POST");
	}

}
