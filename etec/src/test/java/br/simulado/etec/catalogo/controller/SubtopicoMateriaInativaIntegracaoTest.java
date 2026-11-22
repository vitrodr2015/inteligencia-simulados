package br.simulado.etec.catalogo.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;

import org.junit.jupiter.api.Test;

import io.restassured.response.Response;

/**
 * Regras de estado com a matéria desativada (D109 e D62), no MySQL real: criar subtópico e ativar um subtópico
 * inativo dão 409 MATERIA_INATIVA; renomear, desativar e ativar um subtópico que já está ativo continuam permitidos.
 */
class SubtopicoMateriaInativaIntegracaoTest extends AbstractSubtopicoIntegrationTest {

	@Test
	void deve_devolver_409_materia_inativa_quando_cria_subtopico_em_materia_desativada() {
		String materiaId = novaMateria();
		desativar(materiaId).then().statusCode(200);

		Response resposta = criarSubtopico(materiaId, nomeUnico("Bloqueado"));

		verificarErro(resposta, 409, "Conflict", "MATERIA_INATIVA");
	}

	@Test
	void deve_voltar_a_criar_quando_a_materia_e_reativada() {
		String materiaId = novaMateria();
		desativar(materiaId).then().statusCode(200);
		String nome = nomeUnico("Depois de reativar");
		verificarErro(criarSubtopico(materiaId, nome), 409, "Conflict", "MATERIA_INATIVA");

		ativar(materiaId).then().statusCode(200);

		criarSubtopico(materiaId, nome).then().statusCode(201);
	}

	@Test
	void deve_devolver_materia_inativa_antes_de_nome_duplicado_quando_as_duas_condicoes_de_409_valem() {
		String materiaId = novaMateria();
		String nome = nomeUnico("Repetido");
		criarSubtopicoEObterId(materiaId, nome);
		desativar(materiaId).then().statusCode(200);

		verificarErro(criarSubtopico(materiaId, nome), 409, "Conflict", "MATERIA_INATIVA");
	}

	@Test
	void deve_permitir_renomear_e_desativar_o_subtopico_com_a_materia_desativada() {
		String materiaId = novaMateria();
		String id = criarSubtopicoEObterId(materiaId, nomeUnico("Em matéria que será desativada"));
		desativar(materiaId).then().statusCode(200);
		String novoNome = nomeUnico("Renomeado com a matéria inativa");

		atualizarSubtopico(id, null, novoNome).then().statusCode(200).body("nome", equalTo(novoNome))
				.body("ativo", equalTo(true));
		desativarSubtopico(id).then().statusCode(200).body("ativo", equalTo(false));
	}

	@Test
	void deve_devolver_409_materia_inativa_quando_ativa_um_subtopico_inativo_com_a_materia_desativada() {
		String materiaId = novaMateria();
		String id = criarSubtopicoEObterId(materiaId, nomeUnico("Inativo"));
		desativarSubtopico(id).then().statusCode(200);
		desativar(materiaId).then().statusCode(200);

		verificarErro(ativarSubtopico(id), 409, "Conflict", "MATERIA_INATIVA");
		// continua inativo
		buscarSubtopico(id).then().statusCode(200).body("ativo", equalTo(false));

		ativar(materiaId).then().statusCode(200);
		ativarSubtopico(id).then().statusCode(200).body("ativo", equalTo(true));
	}

	@Test
	void deve_devolver_200_sem_efeito_quando_ativa_um_subtopico_que_ja_esta_ativo_mesmo_com_a_materia_desativada() {
		String materiaId = novaMateria();
		String id = criarSubtopicoEObterId(materiaId, nomeUnico("Já ativo"));
		String atualizadoAntes = buscarSubtopico(id).path("atualizadoEm");
		desativar(materiaId).then().statusCode(200);

		Response resposta = ativarSubtopico(id);

		resposta.then().statusCode(200).body("ativo", equalTo(true));
		assertThat((String) resposta.path("atualizadoEm")).as("atualizadoEm não muda (D62)").isEqualTo(atualizadoAntes);
	}

	// ----- idempotência (D62), sem depender da matéria -----

	@Test
	void deve_repetir_disable_e_enable_com_o_mesmo_resultado_sem_alterar_atualizado_em() throws InterruptedException {
		String materiaId = novaMateria();
		String id = criarSubtopicoEObterId(materiaId, nomeUnico("Idempotência"));

		Response primeiroDisable = desativarSubtopico(id);
		primeiroDisable.then().statusCode(200).body("ativo", equalTo(false));
		String atualizadoDepoisDoDisable = primeiroDisable.path("atualizadoEm");
		Thread.sleep(30);

		Response segundoDisable = desativarSubtopico(id);
		segundoDisable.then().statusCode(200).body("ativo", equalTo(false));
		assertThat((String) segundoDisable.path("atualizadoEm")).as("disable repetido").isEqualTo(atualizadoDepoisDoDisable);

		Response primeiroEnable = ativarSubtopico(id);
		primeiroEnable.then().statusCode(200).body("ativo", equalTo(true));
		String atualizadoDepoisDoEnable = primeiroEnable.path("atualizadoEm");
		Thread.sleep(30);

		Response segundoEnable = ativarSubtopico(id);
		segundoEnable.then().statusCode(200).body("ativo", equalTo(true));
		assertThat((String) segundoEnable.path("atualizadoEm")).as("enable repetido").isEqualTo(atualizadoDepoisDoEnable);
		assertThat(atualizadoDepoisDoEnable).as("a mudança real altera atualizadoEm")
				.isNotEqualTo(atualizadoDepoisDoDisable);
	}

	@Test
	void deve_desativar_a_materia_sem_desativar_os_subtopicos_em_cascata() {
		String materiaId = novaMateria();
		String id = criarSubtopicoEObterId(materiaId, nomeUnico("Sem cascata"));

		desativar(materiaId).then().statusCode(200);

		buscarSubtopico(id).then().statusCode(200).body("ativo", equalTo(true));
	}

}
