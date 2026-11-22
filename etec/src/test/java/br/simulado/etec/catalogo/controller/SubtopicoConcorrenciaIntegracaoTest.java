package br.simulado.etec.catalogo.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import io.restassured.response.Response;

/**
 * Concorrência do Subtópico contra o MySQL REAL, por HTTP real: todos os pedidos largam juntos (CountDownLatch) e o
 * resultado precisa ser sempre o mesmo, sem 500. O índice único (materia_id, nome) e a FK com RESTRICT é que seguram
 * as corridas; a aplicação só traduz o erro.
 */
class SubtopicoConcorrenciaIntegracaoTest extends AbstractSubtopicoIntegrationTest {

	private static final int PEDIDOS = 20;

	@Autowired
	private JdbcTemplate jdbc;

	@RepeatedTest(3)
	void deve_ter_exatamente_um_201_e_19_respostas_409_quando_20_post_com_o_mesmo_nome_chegam_ao_mesmo_tempo()
			throws Exception {
		String materiaId = novaMateria();
		String nome = nomeUnico("Concorrência no POST");

		List<Response> respostas = executarSimultaneamente(PEDIDOS, () -> criarSubtopico(materiaId, nome));

		List<Integer> status = respostas.stream().map(Response::statusCode).toList();
		assertThat(status).as("status dos %d POST simultâneos", PEDIDOS).filteredOn(codigo -> codigo == 201).hasSize(1);
		assertThat(status).filteredOn(codigo -> codigo == 409).hasSize(PEDIDOS - 1);
		assertThat(status).doesNotContain(500);
		respostas.stream().filter(resposta -> resposta.statusCode() == 409)
				.forEach(resposta -> assertThat((String) resposta.path("codigo")).isEqualTo("SUBTOPICO_NOME_DUPLICADO"));
		assertThat(listarSubtopicos(java.util.Map.of("materiaId", materiaId)).jsonPath().getInt("totalElementos"))
				.as("o índice único segurou a corrida").isEqualTo(1);
	}

	@RepeatedTest(3)
	void deve_ter_exatamente_um_204_e_19_respostas_404_quando_20_delete_do_mesmo_subtopico_chegam_ao_mesmo_tempo()
			throws Exception {
		String id = criarSubtopicoEObterId(novaMateria(), nomeUnico("Concorrência no DELETE"));

		List<Response> respostas = executarSimultaneamente(PEDIDOS, () -> apagarSubtopico(id));

		List<Integer> status = respostas.stream().map(Response::statusCode).toList();
		assertThat(status).filteredOn(codigo -> codigo == 204).hasSize(1);
		assertThat(status).filteredOn(codigo -> codigo == 404).hasSize(PEDIDOS - 1);
		assertThat(status).doesNotContain(500);
	}

	@RepeatedTest(3)
	void deve_nunca_deixar_subtopico_orfao_nem_dar_500_quando_a_materia_e_apagada_enquanto_se_criam_subtopicos()
			throws Exception {
		String materiaId = novaMateria();
		criarSubtopicoEObterId(materiaId, nomeUnico("Semente"));
		java.util.concurrent.atomic.AtomicInteger indice = new java.util.concurrent.atomic.AtomicInteger();

		// 10 criações + 10 apagamentos da matéria, todos juntos
		List<Response> respostas = executarSimultaneamente(PEDIDOS, () -> {
			int numero = indice.getAndIncrement();
			return (numero % 2 == 0) ? criarSubtopico(materiaId, nomeUnico("Corrida " + numero)) : apagar(materiaId);
		});

		List<Integer> status = respostas.stream().map(Response::statusCode).toList();
		assertThat(status).as("nenhum 500 em nenhuma das corridas").doesNotContain(500);
		// criação: 201 (entrou antes do delete) ou 400 (a matéria já tinha sido apagada); apagar: 409 (tem subtópicos) ou 404
		for (Response resposta : respostas) {
			assertThat(resposta.statusCode()).isIn(201, 400, 404, 409, 204);
		}
		// invariante do banco: não existe subtópico cuja matéria não existe
		Long orfaos = jdbc.queryForObject(
				"SELECT COUNT(*) FROM subtopico s LEFT JOIN materia m ON m.id = s.materia_id WHERE m.id IS NULL", Long.class);
		assertThat(orfaos).isZero();
	}

	/** Dispara {@code quantidade} tarefas ao mesmo tempo (todas esperam a largada) e devolve os resultados. */
	private static <T> List<T> executarSimultaneamente(int quantidade, Callable<T> tarefa) throws Exception {
		ExecutorService pool = Executors.newFixedThreadPool(quantidade);
		CountDownLatch todosProntos = new CountDownLatch(quantidade);
		CountDownLatch largada = new CountDownLatch(1);
		try {
			List<Future<T>> futuros = new ArrayList<>();
			for (int i = 0; i < quantidade; i++) {
				futuros.add(pool.submit(() -> {
					todosProntos.countDown();
					largada.await();
					return tarefa.call();
				}));
			}
			assertThat(todosProntos.await(15, TimeUnit.SECONDS)).as("todas as threads prontas").isTrue();
			largada.countDown();

			List<T> resultados = new ArrayList<>();
			for (Future<T> futuro : futuros) {
				resultados.add(futuro.get(60, TimeUnit.SECONDS));
			}
			return resultados;
		}
		finally {
			pool.shutdownNow();
		}
	}

}
