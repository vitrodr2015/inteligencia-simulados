package br.simulado.etec.catalogo.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import io.restassured.response.Response;

/**
 * Formato da resposta em TEXTO EXATO, por HTTP real contra o MySQL real. Só o que varia de verdade (id e
 * datas) é trocado por marcadores; todo o resto é comparado literalmente, inclusive a ordem dos campos, os
 * nulos e a ausência de espaços.
 */
class MateriaFormatoIntegracaoTest extends AbstractMateriaIntegrationTest {

	private static final String UUID_REGEX = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

	/** Troca ids e datas pelos marcadores {@code <ID>} e {@code <DATA>}. */
	private static String mascarar(String texto) {
		return texto.replaceAll(UUID_REGEX, "<ID>").replaceAll("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}Z",
				"<DATA>");
	}

	private static String corpo(Response resposta) {
		return new String(resposta.asByteArray(), StandardCharsets.UTF_8);
	}

	@Test
	void deve_devolver_o_texto_exato_da_materia_com_descricao_e_o_content_type_sem_charset() {
		String nome = nomeUnico("Matemática");
		Response criada = criar(nome, "Álgebra e geometria");
		String id = criada.path("id");

		Response resposta = buscar(id);

		assertThat(resposta.statusCode()).isEqualTo(200);
		assertThat(resposta.getHeader("Content-Type")).isEqualTo("application/json");
		assertThat(mascarar(corpo(resposta))).isEqualTo("{\"id\":\"<ID>\",\"nome\":\"" + nome
				+ "\",\"descricao\":\"Álgebra e geometria\",\"ativo\":true,\"criadoEm\":\"<DATA>\",\"atualizadoEm\":\"<DATA>\"}");
	}

	@Test
	void deve_devolver_descricao_null_no_texto_exato_quando_a_descricao_e_nula() {
		String nome = nomeUnico("História");
		String id = criar(nome, null).path("id");

		String texto = mascarar(corpo(buscar(id)));

		assertThat(texto).isEqualTo("{\"id\":\"<ID>\",\"nome\":\"" + nome
				+ "\",\"descricao\":null,\"ativo\":true,\"criadoEm\":\"<DATA>\",\"atualizadoEm\":\"<DATA>\"}");
	}

	@Test
	void deve_devolver_o_texto_exato_do_envelope_quando_lista_filtrando_pelo_sufixo_unico() {
		String sufixo = sufixo();
		String primeira = "Álgebra " + sufixo;
		String segunda = "Biologia " + sufixo;
		criar(segunda, null).then().statusCode(201);
		criar(primeira, "Equações").then().statusCode(201);

		Response resposta = requisicao().queryParam("nome", sufixo).get(BASE);

		assertThat(resposta.statusCode()).isEqualTo(200);
		assertThat(resposta.getHeader("Content-Type")).isEqualTo("application/json");
		assertThat(mascarar(corpo(resposta))).isEqualTo("{\"conteudo\":["
				+ "{\"id\":\"<ID>\",\"nome\":\"" + primeira
				+ "\",\"descricao\":\"Equações\",\"ativo\":true,\"criadoEm\":\"<DATA>\",\"atualizadoEm\":\"<DATA>\"},"
				+ "{\"id\":\"<ID>\",\"nome\":\"" + segunda
				+ "\",\"descricao\":null,\"ativo\":true,\"criadoEm\":\"<DATA>\",\"atualizadoEm\":\"<DATA>\"}"
				+ "],\"pagina\":0,\"tamanho\":20,\"totalElementos\":2,\"totalPaginas\":1}");
	}

	@Test
	void deve_devolver_o_texto_exato_do_409_sem_detalhes_quando_o_nome_ja_existe() {
		String nome = nomeUnico("Física");
		criar(nome, null).then().statusCode(201);

		Response resposta = criar(nome, null);

		assertThat(resposta.statusCode()).isEqualTo(409);
		assertThat(resposta.getHeader("Content-Type")).isEqualTo("application/json");
		assertThat(mascarar(corpo(resposta))).isEqualTo("{\"dataHora\":\"<DATA>\",\"status\":409,"
				+ "\"erro\":\"Conflict\",\"codigo\":\"MATERIA_NOME_DUPLICADO\","
				+ "\"mensagem\":\"Já existe uma matéria com esse nome.\"}");
	}

	@Test
	void deve_devolver_o_texto_exato_do_400_com_campos_quando_o_nome_e_invalido() {
		Response resposta = requisicao().body("{\"nome\":\"\",\"descricao\":null}").post(BASE);

		assertThat(resposta.statusCode()).isEqualTo(400);
		assertThat(mascarar(corpo(resposta))).isEqualTo("{\"dataHora\":\"<DATA>\",\"status\":400,"
				+ "\"erro\":\"Bad Request\",\"codigo\":\"VALIDACAO_FALHOU\","
				+ "\"mensagem\":\"Há campos inválidos na requisição.\","
				+ "\"campos\":[{\"campo\":\"nome\",\"mensagem\":\"O nome deve ter de 2 a 100 caracteres.\"}]}");
	}

	@Test
	void deve_devolver_o_texto_exato_do_406_em_json_quando_o_accept_pede_xml_ou_yaml() {
		for (String tipo : new String[] { "application/xml", "application/yaml" }) {
			Response resposta = requisicao().accept(tipo).get(BASE);

			assertThat(resposta.statusCode()).as(tipo).isEqualTo(406);
			assertThat(resposta.getHeader("Content-Type")).as(tipo).isEqualTo("application/json");
			assertThat(mascarar(corpo(resposta))).as(tipo).isEqualTo("{\"dataHora\":\"<DATA>\",\"status\":406,"
					+ "\"erro\":\"Not Acceptable\",\"codigo\":\"FORMATO_NAO_ACEITO\","
					+ "\"mensagem\":\"Formato de resposta não aceito. Esta API responde apenas em JSON.\"}");
		}
	}


	// ----- 403 (rota ou método sem regra, D121) e 415 também saem em JSON, no formato padrão, mesmo com Accept de XML ou YAML -----

	/** D121: rota ou método SEM regra é negado (403 a quem está logado), no mesmo formato de erro. */
	private static final String JSON_DO_403 = "{\"dataHora\":\"<DATA>\",\"status\":403,\"erro\":\"Forbidden\","
			+ "\"codigo\":\"ACESSO_NEGADO\",\"mensagem\":\"Você não tem permissão para acessar este recurso.\"}";

	private static final String JSON_DO_415 = "{\"dataHora\":\"<DATA>\",\"status\":415,"
			+ "\"erro\":\"Unsupported Media Type\",\"codigo\":\"TIPO_NAO_SUPORTADO\","
			+ "\"mensagem\":\"Tipo de conteúdo não suportado. Envie o corpo em application/json.\"}";

	@Test
	void deve_devolver_403_em_json_no_formato_padrao_para_rota_inexistente_mesmo_com_accept_de_xml_ou_yaml() {
		for (String tipo : new String[] { "application/xml", "application/yaml", "text/html" }) {
			Response resposta = requisicao().accept(tipo).get("/api/v1/nao-existe");

			assertThat(resposta.statusCode()).as(tipo).isEqualTo(403);
			assertThat(resposta.getHeader("Content-Type")).as(tipo).isEqualTo("application/json");
			assertThat(mascarar(corpo(resposta))).as(tipo).isEqualTo(JSON_DO_403);
		}
	}

	@Test
	void deve_devolver_403_em_json_no_formato_padrao_para_metodo_sem_regra_mesmo_com_accept_de_xml_ou_yaml() {
		for (String tipo : new String[] { "application/xml", "application/yaml" }) {
			// DELETE na coleção não tem regra (só /{id}): negado, e não 405 (D121).
			Response resposta = requisicao().accept(tipo).delete(BASE);

			assertThat(resposta.statusCode()).as(tipo).isEqualTo(403);
			assertThat(resposta.getHeader("Content-Type")).as(tipo).isEqualTo("application/json");
			assertThat(mascarar(corpo(resposta))).as(tipo).isEqualTo(JSON_DO_403);
		}
	}

	@Test
	void deve_devolver_415_em_json_no_formato_padrao_para_tipo_de_corpo_nao_suportado_mesmo_com_accept_de_xml_ou_yaml() {
		for (String tipo : new String[] { "application/xml", "application/yaml" }) {
			Response resposta = requisicao().contentType("text/plain").accept(tipo).body("nome=Biologia").post(BASE);

			assertThat(resposta.statusCode()).as(tipo).isEqualTo(415);
			assertThat(mascarar(corpo(resposta))).as(tipo).isEqualTo(JSON_DO_415);
		}
	}


	@Test
	void deve_devolver_403_em_json_no_formato_padrao_para_rota_inexistente_fora_de_api_v1() {
		for (String tipo : new String[] { "application/json", "application/xml", "application/yaml" }) {
			Response resposta = requisicao().accept(tipo).get("/nao-existe");

			assertThat(resposta.statusCode()).as(tipo).isEqualTo(403);
			assertThat(resposta.getHeader("Content-Type")).as(tipo).isEqualTo("application/json");
			assertThat(mascarar(corpo(resposta))).as(tipo).isEqualTo(JSON_DO_403);
		}
	}

}
