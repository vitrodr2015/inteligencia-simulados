package br.simulado.etec.catalogo.controller;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import io.restassured.response.Response;

/**
 * O defeito do milissegundo achado na Tarefa 05: o MySQL ARREDONDA ao gravar em DATETIME(3) e a API
 * TRUNCA ao serializar, então o POST devolvia {@code .850} e o GET seguinte {@code .851}. O H2 não pega
 * isso (o esquema dele vem do Hibernate, com precisão maior). Repetido várias vezes, porque o resto do
 * milissegundo é aleatório e uma única rodada poderia passar por sorte.
 */
class MateriaDatasIntegracaoTest extends AbstractMateriaIntegrationTest {

	private static final int REPETICOES = 40;

	@Test
	void deve_devolver_o_mesmo_criadoEm_e_atualizadoEm_no_post_e_no_get_seguinte() {
		for (int i = 0; i < REPETICOES; i++) {
			Response post = criar(nomeUnico("Milissegundo"), null);
			post.then().statusCode(201);
			String id = post.path("id");

			Response get = buscar(id);

			String criadoNoPost = post.path("criadoEm");
			String criadoNoGet = get.path("criadoEm");
			String atualizadoNoPost = post.path("atualizadoEm");
			String atualizadoNoGet = get.path("atualizadoEm");
			assertThat(criadoNoGet).as("criadoEm do GET na repetição %d", i).isEqualTo(criadoNoPost);
			assertThat(atualizadoNoGet).as("atualizadoEm do GET na repetição %d", i).isEqualTo(atualizadoNoPost);
			assertThat(criadoNoPost).matches(DATA_HORA);
		}
	}

	@Test
	void deve_devolver_o_mesmo_atualizadoEm_no_put_e_no_get_seguinte() {
		for (int i = 0; i < REPETICOES; i++) {
			String id = criarEObterId(nomeUnico("Milissegundo no PUT"));

			Response put = atualizar(id, nomeUnico("Renomeada"), "nova");
			put.then().statusCode(200);
			Response get = buscar(id);

			String atualizadoNoPut = put.path("atualizadoEm");
			String atualizadoNoGet = get.path("atualizadoEm");
			assertThat(atualizadoNoGet).as("atualizadoEm do GET na repetição %d", i).isEqualTo(atualizadoNoPut);
			assertThat(atualizadoNoPut).matches(DATA_HORA);
		}
	}

}
