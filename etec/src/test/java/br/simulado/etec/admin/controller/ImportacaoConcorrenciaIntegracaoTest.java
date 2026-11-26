package br.simulado.etec.admin.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import br.simulado.etec.catalogo.service.HashDaQuestao;
import io.restassured.response.Response;

/** Importações simultâneas na mesma prova, contra MySQL REAL: a trava da linha da prova é quem serializa. */
class ImportacaoConcorrenciaIntegracaoTest extends AbstractImportacaoIntegrationTest {

	private static final int SIMULTANEAS = 6;

	private static final int ITENS = 30;

	@RepeatedTest(3)
	void deve_criar_uma_vez_e_ignorar_tudo_nas_outras_quando_o_mesmo_lote_chega_ao_mesmo_tempo() throws Exception {
		String prova = novaProva(ITENS);
		String corpo = lote(prova, itens(criarSubtopico(), 1, ITENS, "Mesmo"));
		List<Callable<Response>> tarefas = new ArrayList<>();
		for (int i = 0; i < SIMULTANEAS; i++) {
			tarefas.add(() -> importar(corpo));
		}

		List<Response> respostas = executarSimultaneamente(tarefas);

		assertThat(respostas.stream().map(Response::statusCode)).as("nenhum 500").containsOnly(200);
		assertThat(respostas.stream().map(r -> (Integer) r.path("questoesCriadas"))).filteredOn(c -> c == ITENS).hasSize(1);
		assertThat(respostas.stream().map(r -> (Integer) r.path("questoesIgnoradas"))).filteredOn(i -> i == ITENS)
				.hasSize(SIMULTANEAS - 1);
		assertThat(questoesDaProva(prova)).isEqualTo(ITENS);
	}

	@RepeatedTest(3)
	void deve_gravar_so_um_lote_e_dar_409_ao_outro_quando_dois_lotes_diferentes_chegam_ao_mesmo_tempo() throws Exception {
		String prova = novaProva(ITENS);
		String subtopico = criarSubtopico();
		String loteA = lote(prova, itens(subtopico, 1, ITENS, "Lote A"));
		String loteB = lote(prova, itens(subtopico, 1, ITENS, "Lote B"));

		List<Response> respostas = executarSimultaneamente(List.of(() -> importar(loteA), () -> importar(loteB)));

		assertThat(respostas.stream().map(Response::statusCode)).containsExactlyInAnyOrder(200, 409);
		Response recusada = respostas.stream().filter(r -> r.statusCode() == 409).findFirst().orElseThrow();
		assertThat((String) recusada.path("codigo")).isEqualTo("QUESTAO_NUMERO_EM_CONFLITO");
		assertThat(recusada.<List<Integer>>path("detalhes.numerosEmConflito")).hasSize(ITENS);
		assertThat(questoesDaProva(prova)).isEqualTo(ITENS);
		String vencedor = respostas.get(0).statusCode() == 200 ? "Lote A" : "Lote B";
		assertThat(jdbc.queryForList("SELECT v.enunciado FROM questao_versao v JOIN questao q ON q.id = v.questao_id "
				+ "WHERE q.prova_id = ?", String.class, idInternoDaProva(prova)))
				.as("só o conteúdo de UM lote, sem mistura").allMatch(e -> e.startsWith(vencedor));
	}

	@RepeatedTest(3)
	void deve_nao_duplicar_nem_dar_500_quando_a_importacao_e_cadastros_unitarios_iguais_chegam_juntos() throws Exception {
		String prova = novaProva(ITENS);
		String subtopico = criarSubtopico();
		String corpoDoLote = lote(prova, itens(subtopico, 1, ITENS, "Igual"));
		List<Callable<Response>> tarefas = new ArrayList<>();
		tarefas.add(() -> importar(corpoDoLote));
		for (int numero = 1; numero <= 8; numero++) {
			int n = numero;
			tarefas.add(() -> cadastrar(prova, corpo(subtopico, n, "Igual " + n)));
		}

		List<Response> respostas = executarSimultaneamente(tarefas);

		assertThat(respostas.stream().map(Response::statusCode)).as("nenhum 500 nem 409").isSubsetOf(200, 201);
		long criadasPeloUnitario = respostas.stream().skip(1).filter(r -> r.statusCode() == 201).count();
		int criadasPeloLote = respostas.get(0).path("questoesCriadas");
		assertThat(criadasPeloLote + criadasPeloUnitario).isEqualTo(ITENS);
		assertThat(questoesDaProva(prova)).isEqualTo(ITENS);
	}

	@RepeatedTest(3)
	void deve_ter_um_so_vencedor_quando_a_importacao_e_um_unitario_diferente_disputam_o_mesmo_numero() throws Exception {
		String prova = novaProva(ITENS);
		String subtopico = criarSubtopico();
		String corpoDoLote = lote(prova, itens(subtopico, 1, ITENS, "Do lote"));

		List<Response> respostas = executarSimultaneamente(
				List.of(() -> importar(corpoDoLote), () -> cadastrar(prova, corpo(subtopico, ITENS, "Do unitário"))));

		Response doLote = respostas.get(0);
		Response doUnitario = respostas.get(1);
		if (doLote.statusCode() == 200) {
			assertThat(doUnitario.statusCode()).isEqualTo(409);
			assertThat(questoesDaProva(prova)).isEqualTo(ITENS);
		}
		else {
			assertThat(doLote.statusCode()).isEqualTo(409);
			assertThat(doUnitario.statusCode()).isEqualTo(201);
			assertThat(questoesDaProva(prova)).as("o lote perdeu: não gravou NADA").isEqualTo(1);
		}
	}

	/**
	 * Determinístico: outra transação (JDBC direto) segura a trava da prova, a importação fica esperando por ela, e só então a
	 * outra grava a questão 1 (a mesma do lote) e confirma. A importação precisa ENXERGAR essa questão ao acordar: se tivesse lido
	 * qualquer coisa antes da trava, o snapshot do REPEATABLE READ a esconderia e o insert bateria no índice único (500).
	 */
	@Test
	void deve_enxergar_o_que_a_transacao_anterior_gravou_quando_acorda_da_espera_pela_trava() throws Exception {
		String prova = novaProva(10);
		String subtopico = criarSubtopico();
		long provaInterna = idInternoDaProva(prova);
		long subtopicoInterno = idInternoDoSubtopico(subtopico);
		String corpoDoLote = lote(prova, itens(subtopico, 1, 3, "Disputada"));
		String hashDaPrimeira = HashDaQuestao.calcular(UUID.fromString(prova), 1, null, null, "Disputada 1", null,
				Map.of("A", "um", "B", "dois", "C", "três", "D", "quatro", "E", "cinco"));
		ExecutorService pool = Executors.newSingleThreadExecutor();
		try (Connection outra = DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())) {
			outra.setAutoCommit(false);
			try (Statement s = outra.createStatement()) {
				s.executeQuery("SELECT id FROM prova WHERE id = " + provaInterna + " FOR UPDATE").close();
			}

			Future<Response> importacao = pool.submit(() -> importar(corpoDoLote));
			esperarAlguemNaFilaDaTrava();
			gravarQuestaoPor(outra, provaInterna, subtopicoInterno, hashDaPrimeira);
			outra.commit();

			Response resposta = importacao.get(60, TimeUnit.SECONDS);
			assertThat(resposta.statusCode()).as(resposta.asString()).isEqualTo(200);
			assertThat((Integer) resposta.path("questoesCriadas")).isEqualTo(2);
			assertThat((Integer) resposta.path("questoesIgnoradas")).isEqualTo(1);
		}
		finally {
			pool.shutdownNow();
		}
		assertThat(questoesDaProva(prova)).isEqualTo(3);
	}

	/** Espera o MySQL mostrar uma transação esperando por trava (consultado como root, que enxerga o innodb_trx). */
	private static void esperarAlguemNaFilaDaTrava() throws SQLException, InterruptedException {
		long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
		try (Connection root = DriverManager.getConnection(MYSQL.getJdbcUrl(), "root", MYSQL.getPassword());
				Statement s = root.createStatement()) {
			while (System.nanoTime() < limite) {
				try (ResultSet r = s.executeQuery("SELECT COUNT(*) FROM information_schema.innodb_trx WHERE trx_state = 'LOCK WAIT'")) {
					r.next();
					if (r.getInt(1) > 0) {
						return;
					}
				}
				Thread.sleep(100);
			}
		}
		throw new AssertionError("a importação não chegou a esperar pela trava da prova");
	}

	private static void gravarQuestaoPor(Connection conexao, long prova, long subtopico, String hash) throws SQLException {
		long questao = inserirDevolvendoId(conexao, "INSERT INTO questao (public_id, prova_id, numero, tipo, hash_conteudo, criado_em, "
				+ "atualizado_em) VALUES (UNHEX(REPLACE(UUID(), '-', '')), ?, 1, 'MULTIPLA_ESCOLHA', ?, UTC_TIMESTAMP(3), "
				+ "UTC_TIMESTAMP(3))", prova, hash);
		long versao = inserirDevolvendoId(conexao, "INSERT INTO questao_versao (questao_id, versao, status, subtopico_id, enunciado, "
				+ "criado_em, atualizado_em) VALUES (?, 1, 'RASCUNHO', ?, 'Disputada 1', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))",
				questao, subtopico);
		String[][] alternativas = { { "A", "um" }, { "B", "dois" }, { "C", "três" }, { "D", "quatro" }, { "E", "cinco" } };
		for (String[] alternativa : alternativas) {
			inserirDevolvendoId(conexao, "INSERT INTO alternativa (questao_versao_id, letra, texto, criado_em, atualizado_em) "
					+ "VALUES (?, ?, ?, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))", versao, alternativa[0], alternativa[1]);
		}
	}

	private static long inserirDevolvendoId(Connection conexao, String sql, Object... parametros) throws SQLException {
		try (PreparedStatement comando = conexao.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
			for (int i = 0; i < parametros.length; i++) {
				comando.setObject(i + 1, parametros[i]);
			}
			comando.executeUpdate();
			try (ResultSet chaves = comando.getGeneratedKeys()) {
				chaves.next();
				return chaves.getLong(1);
			}
		}
	}

	/** Dispara as tarefas ao mesmo tempo (todas esperam a largada) e devolve os resultados na ordem das tarefas. */
	private static <T> List<T> executarSimultaneamente(List<Callable<T>> tarefas) throws Exception {
		ExecutorService pool = Executors.newFixedThreadPool(tarefas.size());
		CountDownLatch todosProntos = new CountDownLatch(tarefas.size());
		CountDownLatch largada = new CountDownLatch(1);
		try {
			List<Future<T>> futuros = new ArrayList<>();
			for (Callable<T> tarefa : tarefas) {
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
				resultados.add(futuro.get(120, TimeUnit.SECONDS));
			}
			return resultados;
		}
		finally {
			pool.shutdownNow();
		}
	}

}
