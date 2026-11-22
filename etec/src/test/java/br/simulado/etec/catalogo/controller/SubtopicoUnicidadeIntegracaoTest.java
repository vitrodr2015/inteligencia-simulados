package br.simulado.etec.catalogo.controller;

import static org.hamcrest.Matchers.equalTo;

import org.junit.jupiter.api.Test;

/**
 * Unicidade do nome do subtópico DENTRO da matéria, sem distinguir caixa nem acento (contrato 4.2 e D104),
 * provada no MySQL real: a collation utf8mb4_unicode_ci decide, no índice único (materia_id, nome) e na checagem
 * prévia. O H2 não prova isso.
 */
class SubtopicoUnicidadeIntegracaoTest extends AbstractSubtopicoIntegrationTest {

	@Test
	void deve_devolver_409_quando_o_post_repete_o_nome_so_com_outra_caixa_ou_sem_acento() {
		String materiaId = novaMateria();
		String sufixo = sufixo();
		criarSubtopico(materiaId, "Álgebra " + sufixo).then().statusCode(201);

		verificarErro(criarSubtopico(materiaId, "algebra " + sufixo), 409, "Conflict", "SUBTOPICO_NOME_DUPLICADO");
		verificarErro(criarSubtopico(materiaId, "ÁLGEBRA " + sufixo.toUpperCase()), 409, "Conflict",
				"SUBTOPICO_NOME_DUPLICADO");
		verificarErro(criarSubtopico(materiaId, "ALGEBRA " + sufixo), 409, "Conflict", "SUBTOPICO_NOME_DUPLICADO");
	}

	@Test
	void deve_devolver_409_quando_o_nome_so_difere_pelos_espacos_das_pontas() {
		String materiaId = novaMateria();
		String nome = nomeUnico("Cálculo");
		criarSubtopico(materiaId, nome).then().statusCode(201);

		verificarErro(criarSubtopico(materiaId, "   " + nome + "   "), 409, "Conflict", "SUBTOPICO_NOME_DUPLICADO");
	}

	@Test
	void deve_tratar_como_nomes_diferentes_os_que_diferem_nos_espacos_internos() {
		String materiaId = novaMateria();
		String sufixo = sufixo();

		criarSubtopico(materiaId, "Álgebra Linear " + sufixo).then().statusCode(201);
		// dois espaços no meio contam (D104: espaços internos contam; sem normalização própria)
		criarSubtopico(materiaId, "Álgebra  Linear " + sufixo).then().statusCode(201);
	}

	@Test
	void deve_permitir_o_mesmo_nome_em_materias_diferentes() {
		String nome = nomeUnico("Introdução");
		String primeiraMateria = novaMateria();
		String segundaMateria = novaMateria();

		criarSubtopico(primeiraMateria, nome).then().statusCode(201).body("materiaId", equalTo(primeiraMateria));
		criarSubtopico(segundaMateria, nome).then().statusCode(201).body("materiaId", equalTo(segundaMateria));
		// e dentro de cada uma continua único, até com outra caixa
		verificarErro(criarSubtopico(primeiraMateria, nome.toUpperCase()), 409, "Conflict", "SUBTOPICO_NOME_DUPLICADO");
	}

	@Test
	void deve_devolver_409_quando_o_put_leva_a_um_nome_que_so_difere_por_caixa_ou_acento_de_outro_da_mesma_materia() {
		String materiaId = novaMateria();
		String sufixo = sufixo();
		criarSubtopico(materiaId, "Função " + sufixo).then().statusCode(201);
		String idOutro = criarSubtopicoEObterId(materiaId, nomeUnico("Outro"));

		verificarErro(atualizarSubtopico(idOutro, null, "funcao " + sufixo), 409, "Conflict", "SUBTOPICO_NOME_DUPLICADO");
		verificarErro(atualizarSubtopico(idOutro, null, "FUNÇÃO " + sufixo.toUpperCase()), 409, "Conflict",
				"SUBTOPICO_NOME_DUPLICADO");
		buscarSubtopico(idOutro).then().statusCode(200).body("nome", org.hamcrest.Matchers.startsWith("Outro"));
	}

	@Test
	void deve_permitir_o_put_com_o_mesmo_nome_do_proprio_subtopico_trocando_so_a_caixa() {
		String materiaId = novaMateria();
		String sufixo = sufixo();
		String id = criarSubtopicoEObterId(materiaId, "Química " + sufixo);

		atualizarSubtopico(id, null, "QUÍMICA " + sufixo.toUpperCase()).then().statusCode(200).body("nome",
				equalTo("QUÍMICA " + sufixo.toUpperCase()));
		atualizarSubtopico(id, null, "quimica " + sufixo).then().statusCode(200).body("nome", equalTo("quimica " + sufixo));
		// o mesmo nome, sem mudança nenhuma, também é aceito
		atualizarSubtopico(id, null, "quimica " + sufixo).then().statusCode(200);
	}

	@Test
	void deve_permitir_reaproveitar_o_nome_de_um_subtopico_apagado() {
		String materiaId = novaMateria();
		String nome = nomeUnico("Reaproveitado");
		String id = criarSubtopicoEObterId(materiaId, nome);

		apagarSubtopico(id).then().statusCode(204);
		criarSubtopico(materiaId, nome).then().statusCode(201);
	}

}
