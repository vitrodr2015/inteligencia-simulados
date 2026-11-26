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
 * Concorrência do cadastro de questão contra o MySQL REAL, por HTTP real: todos os pedidos largam juntos (CountDownLatch). A linha da
 * prova travada (SELECT ... FOR UPDATE) serializa os cadastros, e a decisão "o numero já existe?" nunca corre com outro (D153).
 */
class QuestaoConcorrenciaIntegracaoTest extends AbstractQuestaoIntegrationTest {

	private static final int PEDIDOS = 20;

	@RepeatedTest(3)
	void deve_ter_1_criacao_e_19_respostas_200_com_o_mesmo_id_quando_20_cadastros_do_mesmo_numero_e_conteudo_chegam_juntos()
			throws Exception {
		String prova = criarProva(10);
		String corpo = corpo(criarSubtopico(), 5, "Mesmo conteúdo");

		List<Response> respostas = executarSimultaneamente(PEDIDOS, () -> cadastrar(prova, corpo));

		List<Integer> status = respostas.stream().map(Response::statusCode).toList();
		assertThat(status).as("status dos %d cadastros", PEDIDOS).filteredOn(s -> s == 201).hasSize(1);
		assertThat(status).filteredOn(s -> s == 200).hasSize(PEDIDOS - 1);
		assertThat(status).doesNotContain(500);
		assertThat(respostas.stream().map(r -> (String) r.path("id")).distinct()).as("todas devolvem a MESMA questão").hasSize(1);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM questao WHERE prova_id = ?", Integer.class, idInternoDaProva(prova)))
				.isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM questao_versao v JOIN questao q ON q.id = v.questao_id WHERE q.prova_id = ?",
				Integer.class, idInternoDaProva(prova))).isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM alternativa a JOIN questao_versao v ON v.id = a.questao_versao_id "
				+ "JOIN questao q ON q.id = v.questao_id WHERE q.prova_id = ?", Integer.class, idInternoDaProva(prova))).isEqualTo(5);
	}

	@RepeatedTest(3)
	void deve_ter_1_criacao_e_19_conflitos_409_quando_20_cadastros_do_mesmo_numero_com_conteudo_diferente_chegam_juntos()
			throws Exception {
		String prova = criarProva(10);
		String subtopico = criarSubtopico();
		java.util.concurrent.atomic.AtomicInteger contador = new java.util.concurrent.atomic.AtomicInteger();

		List<Response> respostas = executarSimultaneamente(PEDIDOS,
				() -> cadastrar(prova, corpo(subtopico, 5, "Conteúdo " + contador.incrementAndGet())));

		List<Integer> status = respostas.stream().map(Response::statusCode).toList();
		assertThat(status).as("status dos %d cadastros", PEDIDOS).filteredOn(s -> s == 201).hasSize(1);
		assertThat(status).filteredOn(s -> s == 409).hasSize(PEDIDOS - 1);
		assertThat(status).doesNotContain(500);
		respostas.stream().filter(r -> r.statusCode() == 409).forEach(r -> {
			assertThat((String) r.path("codigo")).isEqualTo("QUESTAO_NUMERO_EM_CONFLITO");
			assertThat((List<Integer>) r.path("detalhes.numerosEmConflito")).containsExactly(5);
		});
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM questao WHERE prova_id = ?", Integer.class, idInternoDaProva(prova)))
				.isEqualTo(1);
	}

	@RepeatedTest(2)
	void deve_criar_todas_quando_20_cadastros_simultaneos_sao_de_numeros_diferentes() throws Exception {
		String prova = criarProva(50);
		String subtopico = criarSubtopico();
		java.util.concurrent.atomic.AtomicInteger numero = new java.util.concurrent.atomic.AtomicInteger();

		List<Response> respostas = executarSimultaneamente(PEDIDOS,
				() -> cadastrar(prova, corpo(subtopico, numero.incrementAndGet(), "Questão")));

		assertThat(respostas.stream().map(Response::statusCode)).containsOnly(201);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM questao WHERE prova_id = ?", Integer.class, idInternoDaProva(prova)))
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
				resultados.add(futuro.get(90, TimeUnit.SECONDS));
			}
			return resultados;
		}
		finally {
			pool.shutdownNow();
		}
	}

}
