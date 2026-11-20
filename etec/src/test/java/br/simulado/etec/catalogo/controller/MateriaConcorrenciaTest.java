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

import br.simulado.etec.catalogo.repository.MateriaRepository;
import io.restassured.response.Response;

/**
 * Concorrência contra o MySQL REAL, por HTTP real (substitui a versão com H2): todos os pedidos largam
 * juntos, por um CountDownLatch, e o resultado precisa ser sempre o mesmo.
 */
class MateriaConcorrenciaTest extends AbstractMateriaIntegrationTest {

	private static final int PEDIDOS = 20;

	@Autowired
	private MateriaRepository repositorio;

	@RepeatedTest(3)
	void deve_ter_exatamente_um_204_e_19_respostas_404_quando_20_delete_chegam_ao_mesmo_tempo() throws Exception {
		String id = criarEObterId(nomeUnico("Concorrência no DELETE"));

		List<Response> respostas = executarSimultaneamente(PEDIDOS, () -> apagar(id));

		List<Integer> status = respostas.stream().map(Response::statusCode).toList();
		assertThat(status).as("status dos %d DELETE simultâneos", PEDIDOS).filteredOn(codigo -> codigo == 204).hasSize(1);
		assertThat(status).filteredOn(codigo -> codigo == 404).hasSize(PEDIDOS - 1);
		assertThat(status).doesNotContain(500);
		respostas.stream().filter(resposta -> resposta.statusCode() == 404)
				.forEach(resposta -> assertThat((String) resposta.path("codigo")).isEqualTo("MATERIA_NAO_ENCONTRADA"));
	}

	@RepeatedTest(3)
	void deve_ter_exatamente_um_201_e_19_respostas_409_quando_20_post_com_o_mesmo_nome_chegam_ao_mesmo_tempo()
			throws Exception {
		String nome = nomeUnico("Concorrência no POST");

		List<Response> respostas = executarSimultaneamente(PEDIDOS, () -> criar(nome, "mesmo nome"));

		List<Integer> status = respostas.stream().map(Response::statusCode).toList();
		assertThat(status).as("status dos %d POST simultâneos", PEDIDOS).filteredOn(codigo -> codigo == 201).hasSize(1);
		assertThat(status).filteredOn(codigo -> codigo == 409).hasSize(PEDIDOS - 1);
		assertThat(status).doesNotContain(500);
		respostas.stream().filter(resposta -> resposta.statusCode() == 409)
				.forEach(resposta -> assertThat((String) resposta.path("codigo")).isEqualTo("MATERIA_NOME_DUPLICADO"));
		// e o banco ficou com UMA só matéria com esse nome (o índice único segurou a corrida)
		assertThat(repositorio.findAll().stream().filter(materia -> materia.getNome().equals(nome))).hasSize(1);
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
