package br.simulado.etec.catalogo.controller;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import io.restassured.response.Response;

/**
 * O parâmetro {@code sort} da listagem (D101): um único por pedido (mais de um = 400) e o NOME do campo
 * diferencia maiúsculas (NOME = 400); só a direção aceita qualquer caixa (D64). Estes testes tornam
 * intencional um comportamento que antes existia por acaso.
 */
class MateriaSortIntegracaoTest extends AbstractMateriaIntegrationTest {

	private static void exigirOrdenacaoInvalida(Response resposta) {
		verificarErro(resposta, 400, "Bad Request", "ORDENACAO_INVALIDA");
	}

	@Test
	void deve_devolver_400_quando_o_pedido_traz_dois_parametros_sort_com_campos_diferentes() {
		exigirOrdenacaoInvalida(requisicao().queryParam("sort", "nome", "criadoEm").get(BASE));
	}

	@Test
	void deve_devolver_400_quando_o_pedido_traz_dois_parametros_sort_com_direcao() {
		exigirOrdenacaoInvalida(requisicao().queryParam("sort", "nome,asc", "criadoEm,desc").get(BASE));
	}

	@Test
	void deve_devolver_400_quando_o_pedido_repete_o_mesmo_sort() {
		exigirOrdenacaoInvalida(requisicao().queryParam("sort", "nome", "nome").get(BASE));
	}

	@Test
	void deve_devolver_400_quando_dois_sort_se_juntariam_num_texto_valido_de_campo_e_direcao() {
		// sort=nome&sort=desc: se os dois valores fossem juntados com vírgula, virariam "nome,desc", que é válido.
		exigirOrdenacaoInvalida(requisicao().queryParam("sort", "nome", "desc").get(BASE));
	}

	@Test
	void deve_devolver_400_quando_o_primeiro_sort_e_invalido_e_o_segundo_e_valido() {
		exigirOrdenacaoInvalida(requisicao().queryParam("sort", "senha", "nome").get(BASE));
	}

	@Test
	void deve_devolver_400_quando_o_sort_leva_dois_campos_na_mesma_virgula() {
		exigirOrdenacaoInvalida(requisicao().queryParam("sort", "nome,criadoEm").get(BASE));
	}

	@ParameterizedTest
	@ValueSource(strings = { "NOME", "Nome", "nOME", "CRIADOEM", "criadoem", "CriadoEm", "NOME,asc", "Nome,desc" })
	void deve_devolver_400_quando_o_nome_do_campo_do_sort_muda_a_caixa(String sort) {
		exigirOrdenacaoInvalida(requisicao().queryParam("sort", sort).get(BASE));
	}

	@Test
	void deve_aceitar_um_unico_sort_valido_e_a_direcao_em_qualquer_caixa() {
		String sufixo = sufixo();
		criar("Alfa " + sufixo, null).then().statusCode(201);
		criar("Beta " + sufixo, null).then().statusCode(201);

		requisicao().queryParam("nome", sufixo).queryParam("sort", "nome,DESC").get(BASE).then().statusCode(200)
				.body("conteudo.nome", contains("Beta " + sufixo, "Alfa " + sufixo));
		requisicao().queryParam("nome", sufixo).queryParam("sort", "nome,Asc").get(BASE).then().statusCode(200)
				.body("conteudo.nome", contains("Alfa " + sufixo, "Beta " + sufixo));
		requisicao().queryParam("nome", sufixo).queryParam("sort", "criadoEm").get(BASE).then().statusCode(200)
				.body("conteudo.nome", contains("Alfa " + sufixo, "Beta " + sufixo));
	}

	@Test
	void deve_descrever_o_formato_aceito_na_mensagem_do_400() {
		Response resposta = requisicao().queryParam("sort", "NOME").get(BASE);

		resposta.then().statusCode(400).body("mensagem", equalTo(
				"O parâmetro 'sort' é inválido. Use campo,direção (asc ou desc) com um dos campos: criadoEm, nome."));
	}

	@Test
	void deve_devolver_o_codigo_de_erro_do_sort_sem_o_campo_campos_no_corpo() {
		Response resposta = requisicao().queryParam("sort", "nome", "criadoEm").get(BASE);

		resposta.then().statusCode(400).body("codigo", equalTo("ORDENACAO_INVALIDA")).body("$", not(hasKey("campos")));
	}

}
