package br.simulado.etec.catalogo.controller;

import static org.hamcrest.Matchers.equalTo;

import org.junit.jupiter.api.Test;

/**
 * Unicidade do nome SEM distinguir maiúsculas e acentos (contrato 4.1 e D29), provada no MySQL real:
 * a collation utf8mb4_unicode_ci é quem decide, no índice único e na checagem prévia. O H2 não prova
 * isso, porque compara com distinção de caixa e de acento.
 */
class MateriaUnicidadeIntegracaoTest extends AbstractMateriaIntegrationTest {

	@Test
	void deve_devolver_409_quando_o_post_repete_o_nome_so_com_outra_caixa_ou_sem_acento() {
		String sufixo = sufixo();
		criar("Matemática " + sufixo, null).then().statusCode(201);

		verificarErro(criar("matematica " + sufixo, null), 409, "Conflict", "MATERIA_NOME_DUPLICADO");
		verificarErro(criar("MATEMÁTICA " + sufixo.toUpperCase(), null), 409, "Conflict", "MATERIA_NOME_DUPLICADO");
		verificarErro(criar("MATEMATICA " + sufixo, null), 409, "Conflict", "MATERIA_NOME_DUPLICADO");
	}

	@Test
	void deve_devolver_409_quando_o_put_de_outra_materia_leva_a_um_nome_que_so_difere_por_caixa_ou_acento() {
		String sufixo = sufixo();
		criar("Matemática " + sufixo, null).then().statusCode(201);
		String idOutra = criarEObterId("Física " + sufixo);

		verificarErro(atualizar(idOutra, "matematica " + sufixo, null), 409, "Conflict", "MATERIA_NOME_DUPLICADO");
		verificarErro(atualizar(idOutra, "MATEMÁTICA " + sufixo.toUpperCase(), null), 409, "Conflict",
				"MATERIA_NOME_DUPLICADO");
		buscar(idOutra).then().statusCode(200).body("nome", equalTo("Física " + sufixo));
	}

	@Test
	void deve_permitir_o_put_da_mesma_materia_trocando_so_a_caixa_do_proprio_nome() {
		String sufixo = sufixo();
		String id = criarEObterId("Química " + sufixo);

		atualizar(id, "QUÍMICA " + sufixo.toUpperCase(), "caixa alta").then().statusCode(200)
				.body("nome", equalTo("QUÍMICA " + sufixo.toUpperCase()));
		atualizar(id, "quimica " + sufixo, "sem acento").then().statusCode(200)
				.body("nome", equalTo("quimica " + sufixo));
	}

}
