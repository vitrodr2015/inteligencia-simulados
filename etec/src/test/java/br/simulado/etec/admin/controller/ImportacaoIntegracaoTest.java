package br.simulado.etec.admin.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import io.restassured.response.Response;

/** A importação contra MySQL REAL, por HTTP real: atomicidade, reimportação e o fluxo completo. */
class ImportacaoIntegracaoTest extends AbstractImportacaoIntegrationTest {

	// ----- tudo ou nada -----

	/** Prova com UMA questão antiga (numero 60) e um lote de 50 em que só o item 50 (índice 49) está errado. */
	@ParameterizedTest(name = "item 50 de 50 com {0}")
	@ValueSource(strings = { "SUBTOPICO_INATIVO", "NUMERO_ACIMA_DA_PROVA", "SUBTOPICO_INEXISTENTE", "CONFLITO" })
	void deve_nao_gravar_nenhuma_linha_e_manter_as_antigas_quando_o_item_50_de_50_falha(String falha) {
		String prova = novaProva(100);
		String subtopico = criarSubtopico();
		String antiga = cadastrar(prova, corpo(subtopico, 60, "Antiga")).then().statusCode(201).extract().path("id");
		Map<String, Object> antigaAntes = buscarQuestao(antiga).then().extract().path("$");
		List<String> itens = new ArrayList<>(itens(subtopico, 1, 49, "Nova"));
		String inativo = criarSubtopico();
		requisicao().patch("/api/v1/subtopicos/" + inativo + "/disable").then().statusCode(200);
		itens.add(switch (falha) {
			case "SUBTOPICO_INATIVO" -> corpo(inativo, 50, "Nova 50");
			case "NUMERO_ACIMA_DA_PROVA" -> corpo(subtopico, 101, "Nova 101");
			case "SUBTOPICO_INEXISTENTE" -> corpo(UUID.randomUUID().toString(), 50, "Nova 50");
			default -> corpo(subtopico, 60, "Outro conteúdo no numero da antiga");
		});
		List<Integer> antes = linhasDasTresTabelas();

		Response resposta = importar(lote(prova, itens));

		switch (falha) {
			case "SUBTOPICO_INATIVO" -> {
				verificarErro(resposta, 409, "Conflict", "SUBTOPICO_INATIVO");
				resposta.then().body("detalhes.indice", equalTo(49)).body("detalhes.numero", equalTo(50));
			}
			case "NUMERO_ACIMA_DA_PROVA" -> {
				verificarErro(resposta, 400, "Bad Request", "VALIDACAO_FALHOU");
				resposta.then().body("campos.campo", contains("questoes[49].numero"));
			}
			case "SUBTOPICO_INEXISTENTE" -> {
				verificarErro(resposta, 400, "Bad Request", "VALIDACAO_FALHOU");
				resposta.then().body("campos.campo", contains("questoes[49].subtopicoId"));
			}
			default -> {
				verificarErro(resposta, 409, "Conflict", "QUESTAO_NUMERO_EM_CONFLITO");
				resposta.then().body("detalhes.numerosEmConflito", contains(60)).body("detalhes.indice", equalTo(49));
			}
		}
		assertThat(linhasDasTresTabelas()).as("questao, questao_versao e alternativa: zero linhas novas").isEqualTo(antes);
		assertThat(questoesDaProva(prova)).isEqualTo(1);
		assertThat(buscarQuestao(antiga).then().statusCode(200).extract().<Map<String, Object>>path("$")).isEqualTo(antigaAntes);
	}

	@Test
	void deve_listar_todos_os_conflitos_e_nao_gravar_os_itens_novos_do_mesmo_lote() {
		String prova = novaProva(100);
		String subtopico = criarSubtopico();
		importar(lote(prova, itens(subtopico, 1, 5, "Original"))).then().statusCode(200);
		List<String> segundo = new ArrayList<>(itens(subtopico, 6, 10, "Nova"));
		segundo.add(corpo(subtopico, 2, "Mudou 2"));
		segundo.add(corpo(subtopico, 11, "Nova 11"));
		segundo.add(corpo(subtopico, 4, "Mudou 4"));
		segundo.add(corpo(subtopico, 5, "Original 5"));
		List<Integer> antes = linhasDasTresTabelas();

		Response resposta = importar(lote(prova, segundo));

		verificarErro(resposta, 409, "Conflict", "QUESTAO_NUMERO_EM_CONFLITO");
		resposta.then().body("detalhes.numerosEmConflito", contains(2, 4)).body("detalhes.indice", equalTo(5))
				.body("detalhes.numero", equalTo(2));
		assertThat(linhasDasTresTabelas()).isEqualTo(antes);
	}

	@Test
	void deve_manter_o_409_do_cadastro_unitario_como_antes() {
		String prova = novaProva(10);
		String subtopico = criarSubtopico();
		cadastrar(prova, corpo(subtopico, 3, "Original")).then().statusCode(201);

		Response resposta = cadastrar(prova, corpo(subtopico, 3, "Outro"));

		verificarErro(resposta, 409, "Conflict", "QUESTAO_NUMERO_EM_CONFLITO");
		resposta.then().body("detalhes.numerosEmConflito", contains(3)).body("detalhes.indice", org.hamcrest.Matchers.nullValue());
	}

	// ----- reimportação -----

	@Test
	void deve_criar_zero_e_ignorar_tudo_quando_o_mesmo_lote_e_reimportado() {
		String prova = novaProva(20);
		String corpo = lote(prova, itens(criarSubtopico(), 1, 20, "Questão"));
		importar(corpo).then().statusCode(200).body("questoesCriadas", equalTo(20)).body("questoesIgnoradas", equalTo(0));
		List<Integer> antes = linhasDasTresTabelas();

		importar(corpo).then().statusCode(200).body("provaId", equalTo(prova)).body("questoesCriadas", equalTo(0))
				.body("questoesIgnoradas", equalTo(20));

		assertThat(linhasDasTresTabelas()).isEqualTo(antes);
	}

	@Test
	void deve_ignorar_tudo_na_reimportacao_e_no_unitario_quando_o_subtopico_foi_desativado_depois() {
		String prova = novaProva(10);
		String subtopico = criarSubtopico();
		String corpo = lote(prova, itens(subtopico, 1, 3, "Questão"));
		importar(corpo).then().statusCode(200);
		requisicao().patch("/api/v1/subtopicos/" + subtopico + "/disable").then().statusCode(200);

		importar(corpo).then().statusCode(200).body("questoesCriadas", equalTo(0)).body("questoesIgnoradas", equalTo(3));
		cadastrar(prova, corpo(subtopico, 2, "Questão 2")).then().statusCode(200).body("numero", equalTo(2));

		verificarErro(cadastrar(prova, corpo(subtopico, 4, "Questão 4")), 409, "Conflict", "SUBTOPICO_INATIVO");
	}

	@Test
	void deve_gravar_as_questoes_importadas_como_as_do_cadastro_unitario() {
		String prova = novaProva(10);
		String subtopico = criarSubtopico();
		importar(lote(prova, itens(subtopico, 1, 3, "Questão"))).then().statusCode(200);

		// o unitário reconhece o que o lote gravou como a MESMA questão (mesmo hash)
		cadastrar(prova, corpo(subtopico, 2, "Questão 2")).then().statusCode(200).body("status", equalTo("RASCUNHO"))
				.body("versao", equalTo(1));
		requisicao().queryParam("provaId", prova).get("/api/v1/admin/questoes").then().statusCode(200)
				.body("conteudo.numero", contains(1, 2, 3));
		requisicao().get("/api/v1/provas/" + prova).then().body("questoesCadastradas", equalTo(3));
	}

	@Test
	void deve_dar_409_prova_nao_editavel_mesmo_com_o_lote_ja_gravado() {
		String prova = novaProva(10);
		String corpo = lote(prova, itens(criarSubtopico(), 1, 2, "Questão"));
		importar(corpo).then().statusCode(200);
		jdbc.update("UPDATE prova SET status = 'PUBLICADA', publicada_em = UTC_TIMESTAMP(3) WHERE public_id = UUID_TO_BIN(?)", prova);

		verificarErro(importar(corpo), 409, "Conflict", "PROVA_NAO_EDITAVEL");
	}

	@Test
	void deve_dar_400_no_campo_provaid_quando_a_prova_nao_existe() {
		Response resposta = importar(lote(UUID.randomUUID().toString(), itens(criarSubtopico(), 1, 2, "Questão")));

		verificarErro(resposta, 400, "Bad Request", "VALIDACAO_FALHOU");
		resposta.then().body("campos.campo", contains("provaId"));
	}

	// ----- 100 itens -----

	@Test
	void deve_aceitar_100_itens_numa_chamada() {
		String prova = novaProva(100);
		String corpo = lote(prova, itens(criarSubtopico(), 1, 100, "Questão"));

		long inicio = System.nanoTime();
		Response resposta = importar(corpo);
		long criando = (System.nanoTime() - inicio) / 1_000_000;
		inicio = System.nanoTime();
		Response repetida = importar(corpo);
		long ignorando = (System.nanoTime() - inicio) / 1_000_000;
		// só informa, sem asserção de tempo
		System.out.println("TEMPO-LOTE-100: criando " + criando + " ms; reimportando (tudo ignorado) " + ignorando + " ms");

		resposta.then().statusCode(200).body("questoesCriadas", equalTo(100)).body("questoesIgnoradas", equalTo(0));
		repetida.then().statusCode(200).body("questoesCriadas", equalTo(0)).body("questoesIgnoradas", equalTo(100));
		assertThat(questoesDaProva(prova)).isEqualTo(100);
	}

}
