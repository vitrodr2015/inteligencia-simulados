package br.simulado.etec.catalogo.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import io.restassured.response.Response;

/**
 * Concorrência da edição contra MySQL REAL. Os testes determinísticos usam outra transação (JDBC direto) que segura a trava da
 * prova, espera a requisição entrar na fila e só então muda o que ela vai ler: a requisição precisa ENXERGAR a mudança ao
 * acordar (se tivesse lido qualquer coisa antes da trava, o snapshot do REPEATABLE READ a esconderia).
 */
class EdicaoConcorrenciaIntegracaoTest extends AbstractEdicaoIntegrationTest {

	// ----- determinísticos: a trava da prova é a primeira coisa, inclusive nas rotas por id de questão -----

	@Test
	void deve_dar_409_ao_put_que_esperou_a_trava_enquanto_a_questao_era_publicada() throws Exception {
		String prova = novaProva(10);
		String subtopico = criarSubtopico();
		String questao = cadastrarId(prova, subtopico, 1, "Antes");
		long interna = idInternoDaQuestao(questao);

		Response resposta = comAProvaTravada(prova, () -> editarQuestao(questao, corpoDoPut(subtopico, "Depois", "")),
				outra -> executar(outra, "UPDATE questao_versao SET status = 'PUBLICADA', publicada_em = UTC_TIMESTAMP(3) "
						+ "WHERE questao_id = " + interna));

		verificarErro(resposta, 409, "Conflict", "QUESTAO_SEM_RASCUNHO");
		assertThat(jdbc.queryForList("SELECT CONCAT(status, ' ', enunciado) FROM questao_versao WHERE questao_id = ?",
				String.class, interna)).as("a publicação não foi desfeita nem sobrescrita").containsExactly("PUBLICADA Antes");
	}

	@Test
	void deve_dar_404_ao_put_que_esperou_a_trava_enquanto_a_questao_era_apagada() throws Exception {
		String prova = novaProva(10);
		String subtopico = criarSubtopico();
		String questao = cadastrarId(prova, subtopico, 1, "Vai sumir");
		long interna = idInternoDaQuestao(questao);

		Response resposta = comAProvaTravada(prova, () -> editarQuestao(questao, corpoDoPut(subtopico, "Depois", "")), outra -> {
			executar(outra, "DELETE a FROM alternativa a JOIN questao_versao v ON v.id = a.questao_versao_id WHERE v.questao_id = "
					+ interna);
			executar(outra, "DELETE FROM questao_versao WHERE questao_id = " + interna);
			executar(outra, "DELETE FROM questao WHERE id = " + interna);
		});

		verificarErro(resposta, 404, "Not Found", "QUESTAO_NAO_ENCONTRADA");
	}

	@Test
	void deve_enxergar_a_publicacao_no_gabarito_que_esperou_a_trava_e_nao_gravar_nada() throws Exception {
		String prova = novaProva(10);
		String subtopico = criarSubtopico();
		cadastrarId(prova, subtopico, 1, "Um");
		String dois = cadastrarId(prova, subtopico, 2, "Dois");
		long interna = idInternoDaQuestao(dois);

		Response resposta = comAProvaTravada(prova, () -> gabarito(prova, item(1, "A"), item(2, "B")),
				outra -> executar(outra, "UPDATE questao_versao SET status = 'PUBLICADA', publicada_em = UTC_TIMESTAMP(3) "
						+ "WHERE questao_id = " + interna));

		verificarErro(resposta, 409, "Conflict", "QUESTAO_SEM_RASCUNHO");
		assertThat(jdbc.queryForList("SELECT v.gabarito FROM questao_versao v JOIN questao q ON q.id = v.questao_id "
				+ "WHERE q.prova_id = ? ORDER BY q.numero", String.class, idInternoDaProva(prova))).containsExactly(null, null);
	}

	@Test
	void deve_enxergar_a_questao_nova_no_put_da_prova_que_esperou_a_trava() throws Exception {
		String prova = novaProva(20);
		cadastrarId(prova, criarSubtopico(), 1, "Um");
		long provaInterna = idInternoDaProva(prova);

		Response resposta = comAProvaTravada(prova, () -> editarProva(prova, corpoDaProva("Menor", null, null, null, 5)),
				outra -> executar(outra, "INSERT INTO questao (public_id, prova_id, numero, tipo, hash_conteudo, criado_em, "
						+ "atualizado_em) VALUES (UNHEX(REPLACE(UUID(), '-', '')), " + provaInterna + ", 10, 'MULTIPLA_ESCOLHA', '"
						+ "c".repeat(64) + "', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))"));

		verificarErro(resposta, 409, "Conflict", "PROVA_COM_QUESTOES_ACIMA_DA_QUANTIDADE");
		assertThat((Integer) resposta.path("detalhes.maiorNumero")).isEqualTo(10);
		assertThat(jdbc.queryForObject("SELECT quantidade_questoes FROM prova WHERE id = ?", Integer.class, provaInterna))
				.isEqualTo(20);
	}

	// ----- corridas -----

	@RepeatedTest(6)
	void deve_ficar_consistente_quando_a_quantidade_e_reduzida_junto_com_o_cadastro_de_um_numero_acima_dela() throws Exception {
		String prova = novaProva(10);
		String subtopico = criarSubtopico();

		List<Response> respostas = executarSimultaneamente(List.of(
				() -> editarProva(prova, corpoDaProva("Menor", null, null, null, 5)),
				() -> cadastrar(prova, corpo(subtopico, 7, "Número sete"))));

		Response reducao = respostas.get(0);
		Response cadastro = respostas.get(1);
		if (cadastro.statusCode() == 201) {
			verificarErro(reducao, 409, "Conflict", "PROVA_COM_QUESTOES_ACIMA_DA_QUANTIDADE");
		}
		else {
			verificarErro(cadastro, 400, "Bad Request", "VALIDACAO_FALHOU");
			assertThat(reducao.statusCode()).as(reducao.asString()).isEqualTo(200);
		}
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM questao q JOIN prova p ON p.id = q.prova_id "
				+ "WHERE p.id = ? AND q.numero > p.quantidade_questoes", Integer.class, idInternoDaProva(prova)))
				.as("nenhuma questão acima da quantidade").isZero();
	}

	@RepeatedTest(3)
	void deve_nao_dar_500_quando_o_put_de_questao_concorre_com_cadastros_e_importacao_na_mesma_prova() throws Exception {
		String prova = novaProva(30);
		String subtopico = criarSubtopico();
		String primeira = cadastrarId(prova, subtopico, 1, "Original 1");
		String lote = "{\"provaId\":\"" + prova + "\",\"questoes\":["
				+ IntStream.rangeClosed(7, 16).mapToObj(n -> corpo(subtopico, n, "Lote " + n)).collect(Collectors.joining(","))
				+ "," + corpo(subtopico, 1, "Original 1") + "]}";
		List<Callable<Response>> tarefas = new ArrayList<>();
		tarefas.add(() -> editarQuestao(primeira, corpoDoPut(subtopico, "Editada", "\"gabarito\":\"D\"")));
		tarefas.add(() -> requisicao().body(lote).post("/api/v1/admin/importacoes"));
		for (int numero = 2; numero <= 6; numero++) {
			int n = numero;
			tarefas.add(() -> cadastrar(prova, corpo(subtopico, n, "Unitário " + n)));
		}
		tarefas.add(() -> cadastrar(prova, corpo(subtopico, 1, "Original 1")));

		List<Response> respostas = executarSimultaneamente(tarefas);

		assertThat(respostas.stream().map(Response::statusCode)).as("nenhum 409, 500 nem 503").isSubsetOf(200, 201);
		assertThat((Integer) respostas.get(1).path("questoesCriadas")).isEqualTo(10);
		assertThat((Integer) respostas.get(1).path("questoesIgnoradas")).as("o original é a mesma questão antes ou depois do PUT")
				.isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM questao WHERE prova_id = ?", Integer.class, idInternoDaProva(prova)))
				.isEqualTo(16);
		buscarQuestao(primeira).then().body("enunciado", org.hamcrest.Matchers.equalTo("Editada"))
				.body("gabarito", org.hamcrest.Matchers.equalTo("D"));
	}

	/**
	 * Mais pedidos ao mesmo tempo que conexões no pool (10). Se a leitura feita fora da transação segurasse a conexão dela, cada
	 * pedido precisaria de duas, e o pool se esgotaria.
	 */
	@RepeatedTest(2)
	void deve_atender_muitos_puts_simultaneos_da_mesma_questao_sem_esgotar_o_pool() throws Exception {
		String prova = novaProva(5);
		String subtopico = criarSubtopico();
		String questao = cadastrarId(prova, subtopico, 1, "Disputada");
		List<Callable<Response>> tarefas = new ArrayList<>();
		for (int i = 0; i < 30; i++) {
			int n = i;
			tarefas.add(() -> editarQuestao(questao, corpoDoPut(subtopico, "Versão do pedido " + n, "")));
		}

		List<Response> respostas = executarSimultaneamente(tarefas);

		assertThat(respostas.stream().map(Response::statusCode)).containsOnly(200);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM questao_versao WHERE questao_id = ?", Integer.class,
				idInternoDaQuestao(questao))).isEqualTo(1);
	}

}
