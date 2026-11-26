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

import io.restassured.response.Response;

/**
 * Concorrência da criação de prova contra o MySQL REAL, por HTTP real: todos os pedidos largam juntos (CountDownLatch) e o
 * índice único segura a corrida (D150).
 */
class ProvaConcorrenciaIntegracaoTest extends AbstractProvaIntegrationTest {

	private static final int PEDIDOS = 20;

	@RepeatedTest(3)
	void deve_ter_exatamente_um_201_e_19_respostas_409_quando_20_post_com_a_mesma_banca_ano_e_semestre_chegam_juntos()
			throws Exception {
		String banca = bancaUnica();
		String corpo = corpo("Concorrência no POST", banca, 2090, 1);

		List<Response> respostas = executarSimultaneamente(PEDIDOS, () -> criar(corpo));

		List<Integer> status = respostas.stream().map(Response::statusCode).toList();
		assertThat(status).as("status dos %d POST simultâneos", PEDIDOS).filteredOn(codigo -> codigo == 201).hasSize(1);
		assertThat(status).filteredOn(codigo -> codigo == 409).hasSize(PEDIDOS - 1);
		assertThat(status).doesNotContain(500);
		respostas.stream().filter(resposta -> resposta.statusCode() == 409)
				.forEach(resposta -> assertThat((String) resposta.path("codigo")).isEqualTo("PROVA_DUPLICADA"));
		// e o banco ficou com UMA só prova com essa tripla (o índice único segurou a corrida)
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM prova WHERE banca = ? AND ano = 2090 AND semestre = 1",
				Integer.class, banca)).isEqualTo(1);
	}

	@RepeatedTest(2)
	void deve_criar_as_20_quando_20_post_simultaneos_nao_tem_banca_ano_e_semestre() throws Exception {
		String titulo = "Sem identidade " + sufixo();
		String corpo = corpo(titulo, null, null, null);

		List<Response> respostas = executarSimultaneamente(PEDIDOS, () -> criar(corpo));

		assertThat(respostas.stream().map(Response::statusCode)).containsOnly(201);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM prova WHERE titulo = ?", Integer.class, titulo))
				.isEqualTo(PEDIDOS);
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
