package br.simulado.etec.catalogo.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import io.restassured.response.Response;

/**
 * Formato da resposta do Subtópico em TEXTO EXATO, por HTTP real contra o MySQL real: ordem dos campos, ids
 * públicos, JSON compacto e Content-Type. Só o que varia de verdade (ids e datas) é trocado por marcadores.
 */
class SubtopicoFormatoIntegracaoTest extends AbstractSubtopicoIntegrationTest {

	private static final String UUID_REGEX = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

	/** Troca ids e datas pelos marcadores {@code <ID>} e {@code <DATA>}. */
	private static String mascarar(String texto) {
		return texto.replaceAll(UUID_REGEX, "<ID>").replaceAll("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}Z", "<DATA>");
	}

	private static String corpo(Response resposta) {
		return new String(resposta.asByteArray(), StandardCharsets.UTF_8);
	}

	@Test
	void deve_devolver_o_texto_exato_do_subtopico_com_o_content_type_sem_charset() {
		String nome = nomeUnico("Equações");
		Response criado = criarSubtopico(novaMateria(), nome);
		String id = criado.path("id");

		Response resposta = buscarSubtopico(id);

		assertThat(resposta.statusCode()).isEqualTo(200);
		assertThat(resposta.getHeader("Content-Type")).isEqualTo("application/json");
		assertThat(mascarar(corpo(resposta))).isEqualTo("{\"id\":\"<ID>\",\"materiaId\":\"<ID>\",\"nome\":\"" + nome
				+ "\",\"ativo\":true,\"criadoEm\":\"<DATA>\",\"atualizadoEm\":\"<DATA>\"}");
	}

	@Test
	void deve_devolver_o_mesmo_texto_no_201_no_put_no_disable_e_no_enable_com_o_ativo_correto() {
		String materiaId = novaMateria();
		String nome = nomeUnico("Probabilidade");
		Response criado = criarSubtopico(materiaId, nome);
		String id = criado.path("id");
		String modelo = "{\"id\":\"<ID>\",\"materiaId\":\"<ID>\",\"nome\":\"%s\",\"ativo\":%s,\"criadoEm\":\"<DATA>\",\"atualizadoEm\":\"<DATA>\"}";

		assertThat(criado.statusCode()).isEqualTo(201);
		assertThat(mascarar(corpo(criado))).isEqualTo(String.format(modelo, nome, "true"));
		assertThat(mascarar(corpo(atualizarSubtopico(id, materiaId, nome)))).isEqualTo(String.format(modelo, nome, "true"));
		assertThat(mascarar(corpo(desativarSubtopico(id)))).isEqualTo(String.format(modelo, nome, "false"));
		assertThat(mascarar(corpo(ativarSubtopico(id)))).isEqualTo(String.format(modelo, nome, "true"));
	}

	@Test
	void deve_devolver_o_texto_exato_do_envelope_da_listagem() {
		String materiaId = novaMateria();
		criarSubtopicoEObterId(materiaId, "Álgebra");
		criarSubtopicoEObterId(materiaId, "Biologia celular");

		Response resposta = requisicao().queryParam("materiaId", materiaId).get(BASE_SUBTOPICOS);

		assertThat(resposta.getHeader("Content-Type")).isEqualTo("application/json");
		String item = "{\"id\":\"<ID>\",\"materiaId\":\"<ID>\",\"nome\":\"%s\",\"ativo\":true,\"criadoEm\":\"<DATA>\",\"atualizadoEm\":\"<DATA>\"}";
		assertThat(mascarar(corpo(resposta))).isEqualTo("{\"conteudo\":[" + String.format(item, "Álgebra") + ","
				+ String.format(item, "Biologia celular") + "],\"pagina\":0,\"tamanho\":20,\"totalElementos\":2,\"totalPaginas\":1}");
	}

	@Test
	void deve_devolver_o_texto_exato_do_409_de_nome_duplicado() {
		String materiaId = novaMateria();
		String nome = nomeUnico("Repetido");
		criarSubtopicoEObterId(materiaId, nome);

		Response resposta = criarSubtopico(materiaId, nome);

		assertThat(resposta.statusCode()).isEqualTo(409);
		assertThat(mascarar(corpo(resposta))).isEqualTo("{\"dataHora\":\"<DATA>\",\"status\":409,\"erro\":\"Conflict\","
				+ "\"codigo\":\"SUBTOPICO_NOME_DUPLICADO\",\"mensagem\":\"Já existe um subtópico com esse nome nessa matéria.\"}");
	}

	@Test
	void deve_devolver_o_texto_exato_do_409_de_materia_inativa() {
		String materiaId = novaMateria();
		desativar(materiaId).then().statusCode(200);

		Response resposta = criarSubtopico(materiaId, nomeUnico("Bloqueado"));

		assertThat(mascarar(corpo(resposta))).isEqualTo("{\"dataHora\":\"<DATA>\",\"status\":409,\"erro\":\"Conflict\","
				+ "\"codigo\":\"MATERIA_INATIVA\",\"mensagem\":\"A matéria está desativada.\"}");
	}

	@Test
	void deve_devolver_o_texto_exato_do_409_de_materia_com_subtopicos() {
		String materiaId = novaMateria();
		criarSubtopicoEObterId(materiaId, nomeUnico("Dependente"));

		Response resposta = apagar(materiaId);

		assertThat(resposta.statusCode()).isEqualTo(409);
		assertThat(mascarar(corpo(resposta))).isEqualTo("{\"dataHora\":\"<DATA>\",\"status\":409,\"erro\":\"Conflict\","
				+ "\"codigo\":\"MATERIA_COM_SUBTOPICOS\",\"mensagem\":\"A matéria tem subtópicos e não pode ser apagada.\"}");
	}

	@Test
	void deve_devolver_o_texto_exato_do_400_de_materia_inexistente_com_campos() {
		Response resposta = criarSubtopico("11111111-2222-3333-4444-555555555555", nomeUnico("Órfão"));

		assertThat(resposta.statusCode()).isEqualTo(400);
		assertThat(mascarar(corpo(resposta))).isEqualTo("{\"dataHora\":\"<DATA>\",\"status\":400,\"erro\":\"Bad Request\","
				+ "\"codigo\":\"VALIDACAO_FALHOU\",\"mensagem\":\"Há campos inválidos na requisição.\","
				+ "\"campos\":[{\"campo\":\"materiaId\",\"mensagem\":\"A matéria informada não existe.\"}]}");
	}

	@Test
	void deve_devolver_o_texto_exato_do_400_de_campo_imutavel_e_do_404() {
		String materiaId = novaMateria();
		String id = criarSubtopicoEObterId(materiaId, nomeUnico("Fixo"));

		Response imutavel = atualizarSubtopico(id, novaMateria(), nomeUnico("Fixo 2"));
		Response naoEncontrado = buscarSubtopico("11111111-2222-3333-4444-555555555555");

		assertThat(mascarar(corpo(imutavel))).isEqualTo("{\"dataHora\":\"<DATA>\",\"status\":400,\"erro\":\"Bad Request\","
				+ "\"codigo\":\"CAMPO_IMUTAVEL\",\"mensagem\":\"A matéria de um subtópico não pode ser alterada (materiaId).\"}");
		assertThat(mascarar(corpo(naoEncontrado))).isEqualTo("{\"dataHora\":\"<DATA>\",\"status\":404,\"erro\":\"Not Found\","
				+ "\"codigo\":\"SUBTOPICO_NAO_ENCONTRADO\",\"mensagem\":\"Subtópico não encontrado.\"}");
	}

	@Test
	void deve_devolver_o_texto_exato_do_400_de_validacao_do_nome() {
		Response resposta = criarSubtopico(novaMateria(), "A");

		assertThat(mascarar(corpo(resposta))).isEqualTo("{\"dataHora\":\"<DATA>\",\"status\":400,\"erro\":\"Bad Request\","
				+ "\"codigo\":\"VALIDACAO_FALHOU\",\"mensagem\":\"Há campos inválidos na requisição.\","
				+ "\"campos\":[{\"campo\":\"nome\",\"mensagem\":\"O nome deve ter de 2 a 100 caracteres.\"}]}");
	}

	@Test
	void deve_devolver_406_em_json_com_accept_de_xml_ou_yaml_nas_rotas_de_subtopico() {
		for (String tipo : new String[] { "application/xml", "application/yaml" }) {
			Response resposta = requisicao().accept(tipo).get(BASE_SUBTOPICOS);

			assertThat(resposta.statusCode()).as(tipo).isEqualTo(406);
			assertThat(resposta.getHeader("Content-Type")).as(tipo).isEqualTo("application/json");
		}
	}

}
