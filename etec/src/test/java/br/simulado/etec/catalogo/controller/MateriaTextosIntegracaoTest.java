package br.simulado.etec.catalogo.controller;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.nullValue;

import org.junit.jupiter.api.Test;

import io.restassured.response.Response;

/**
 * Textos curtos da Matéria no MySQL real (D57, D98, D104): espaços das pontas aparados nos DOIS campos,
 * descrição em branco vira nula, e os limites contam PONTOS DE CÓDIGO (um emoji conta 1, mas ocupa 2
 * unidades UTF-16 e 4 bytes em utf8mb4). Prova também que a coluna VARCHAR(n) em utf8mb4 comporta o limite.
 */
class MateriaTextosIntegracaoTest extends AbstractMateriaIntegrationTest {

	/** U+1F600: um ponto de código, duas unidades UTF-16. */
	private static final String EMOJI = "😀";

	private static void exigirCampoInvalido(Response resposta, String campo) {
		verificarErro(resposta, 400, "Bad Request", "VALIDACAO_FALHOU");
		resposta.then().body("campos.campo", hasItem(campo));
	}

	// ----- trim -----

	@Test
	void deve_aparar_nome_e_descricao_no_post_e_devolver_o_texto_aparado() {
		String nome = nomeUnico("Biologia");

		Response criada = criar("   " + nome + "  ", "\t  Seres vivos \n");

		criada.then().statusCode(201).body("nome", equalTo(nome)).body("descricao", equalTo("Seres vivos"));
		buscar(criada.path("id")).then().statusCode(200).body("nome", equalTo(nome)).body("descricao",
				equalTo("Seres vivos"));
	}

	@Test
	void deve_aparar_nome_e_descricao_no_put() {
		String id = criarEObterId(nomeUnico("Geografia"));
		String novoNome = nomeUnico("Geografia Física");

		atualizar(id, "  " + novoNome + " ", "  Relevo e clima  ").then().statusCode(200)
				.body("nome", equalTo(novoNome)).body("descricao", equalTo("Relevo e clima"));
	}

	@Test
	void deve_preservar_os_espacos_internos_da_descricao() {
		Response criada = criar(nomeUnico("Redação"), "  duas   palavras  ");

		criada.then().statusCode(201).body("descricao", equalTo("duas   palavras"));
	}

	@Test
	void deve_devolver_descricao_nula_no_post_quando_a_descricao_e_so_espacos() {
		criar(nomeUnico("Artes"), "     ").then().statusCode(201).body("descricao", nullValue());
	}

	@Test
	void deve_devolver_descricao_nula_no_put_quando_a_descricao_e_so_espacos() {
		String id = criar(nomeUnico("Música"), "tem descrição").path("id");

		atualizar(id, nomeUnico("Música Popular"), " \t ").then().statusCode(200).body("descricao", nullValue());
		buscar(id).then().statusCode(200).body("descricao", nullValue());
	}

	@Test
	void deve_recusar_com_400_o_nome_so_de_espacos() {
		exigirCampoInvalido(criar("      ", null), "nome");
	}

	// ----- pontos de código -----

	@Test
	void deve_aceitar_nome_de_100_pontos_de_codigo_que_ocupa_192_unidades_utf16() {
		String nome = sufixo() + EMOJI.repeat(92);

		Response criada = criar(nome, null);

		criada.then().statusCode(201).body("nome", equalTo(nome));
		buscar(criada.path("id")).then().statusCode(200).body("nome", equalTo(nome));
	}

	@Test
	void deve_recusar_nome_de_101_pontos_de_codigo() {
		String nome = sufixo() + EMOJI.repeat(93);

		exigirCampoInvalido(criar(nome, null), "nome");
	}

	@Test
	void deve_recusar_nome_de_um_unico_emoji_porque_tem_1_ponto_de_codigo_mesmo_com_2_unidades_utf16() {
		exigirCampoInvalido(criar(EMOJI, null), "nome");
	}

	@Test
	void deve_aceitar_nome_de_2_pontos_de_codigo_com_emoji() {
		// "z" + emoji: 2 pontos de código (o mínimo). Nome fixo e único no teste: no MySQL com utf8mb4_unicode_ci
		// todos os emojis valem o mesmo na comparação, então outro nome "z" + emoji colidiria (D104).
		Response criada = criar("z" + EMOJI, null);

		criada.then().statusCode(201).body("nome", equalTo("z" + EMOJI));
	}

	@Test
	void deve_aceitar_descricao_de_255_pontos_de_codigo_e_devolve_la_inteira() {
		String descricao = EMOJI.repeat(255);

		Response criada = criar(nomeUnico("Filosofia"), descricao);

		criada.then().statusCode(201).body("descricao", equalTo(descricao));
		buscar(criada.path("id")).then().statusCode(200).body("descricao", equalTo(descricao));
	}

	@Test
	void deve_recusar_descricao_de_256_pontos_de_codigo() {
		exigirCampoInvalido(criar(nomeUnico("Sociologia"), EMOJI.repeat(256)), "descricao");
	}

	@Test
	void deve_aceitar_descricao_de_255_pontos_de_codigo_no_put_e_recusar_256() {
		String id = criarEObterId(nomeUnico("Ensino Religioso"));
		String descricao = "á" + EMOJI.repeat(254);

		atualizar(id, nomeUnico("Ensino Religioso 2"), descricao).then().statusCode(200).body("descricao",
				equalTo(descricao));
		exigirCampoInvalido(atualizar(id, nomeUnico("Ensino Religioso 3"), descricao + EMOJI), "descricao");
	}

}
