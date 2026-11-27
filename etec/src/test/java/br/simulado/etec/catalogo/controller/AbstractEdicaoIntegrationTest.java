package br.simulado.etec.catalogo.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;

import io.restassured.response.Response;

/**
 * Ajudantes da edição (fatia 4, passada 1) contra MySQL real. As provas criadas por {@link #novaProva(int)}, com tudo o que há
 * nelas (inclusive as versões publicadas montadas por JDBC, que a API ainda não sabe produzir), são apagadas no fim de cada
 * teste.
 */
public abstract class AbstractEdicaoIntegrationTest extends AbstractQuestaoIntegrationTest {

	/** Um momento antigo, gravado de propósito: se o atualizado_em continuar nele, nada foi regravado. */
	protected static final String MOMENTO_ANTIGO = "2000-01-01 00:00:00.000";

	private final List<String> provasDoTeste = new ArrayList<>();

	protected String novaProva(int quantidade) {
		String prova = criarProva(quantidade);
		provasDoTeste.add(prova);
		return prova;
	}

	@AfterEach
	void apagarOQueOTesteCriou() {
		for (String prova : provasDoTeste) {
			List<Long> ids = jdbc.queryForList("SELECT id FROM prova WHERE public_id = UUID_TO_BIN(?)", Long.class, prova);
			for (Long id : ids) {
				jdbc.update("DELETE a FROM alternativa a JOIN questao_versao v ON v.id = a.questao_versao_id "
						+ "JOIN questao q ON q.id = v.questao_id WHERE q.prova_id = ?", id);
				jdbc.update("DELETE v FROM questao_versao v JOIN questao q ON q.id = v.questao_id WHERE q.prova_id = ?", id);
				jdbc.update("DELETE FROM questao WHERE prova_id = ?", id);
				jdbc.update("DELETE FROM prova WHERE id = ?", id);
			}
		}
		provasDoTeste.clear();
	}

	// ----- HTTP -----

	protected String cadastrarId(String prova, String subtopico, int numero, String enunciado) {
		return cadastrar(prova, corpo(subtopico, numero, enunciado)).then().statusCode(201).extract().path("id");
	}

	/** Corpo de PUT de questão com as alternativas padrão do cadastro ({@code corpo}) e um extra opcional. */
	protected static String corpoDoPut(String subtopico, String enunciado, String extra) {
		return "{\"subtopicoId\":\"" + subtopico + "\",\"enunciado\":\"" + enunciado + "\",\"alternativas\":"
				+ alternativas("um", "dois", "três", "quatro", "cinco") + (extra.isEmpty() ? "" : "," + extra) + "}";
	}

	protected Response editarQuestao(String questao, String corpo) {
		return requisicao().body(corpo).put("/api/v1/admin/questoes/" + questao);
	}

	protected static String corpoDaProva(String titulo, String banca, Integer ano, Integer semestre, int quantidade) {
		return "{\"titulo\":\"" + titulo + "\",\"banca\":" + (banca == null ? "null" : "\"" + banca + "\"") + ",\"ano\":" + ano
				+ ",\"semestre\":" + semestre + ",\"duracaoMinutos\":180,\"quantidadeQuestoes\":" + quantidade + "}";
	}

	protected Response editarProva(String prova, String corpo) {
		return requisicao().body(corpo).put("/api/v1/admin/provas/" + prova);
	}

	protected static String item(int numero, String letra) {
		return "{\"numero\":" + numero + ",\"gabarito\":\"" + letra + "\"}";
	}

	protected Response gabarito(String prova, String... itens) {
		return requisicao().body("{\"itens\":[" + String.join(",", itens) + "]}")
				.put("/api/v1/admin/provas/" + prova + "/gabarito");
	}

	// ----- banco direto -----

	/** Publica o rascunho direto no banco: a API ainda não publica (passada 2). */
	protected void publicarNoBanco(String questao) {
		assertThat(jdbc.update("UPDATE questao_versao SET status = 'PUBLICADA', publicada_em = UTC_TIMESTAMP(3) "
				+ "WHERE questao_id = ? AND status = 'RASCUNHO'", idInternoDaQuestao(questao))).isEqualTo(1);
	}

	/** Abre a versão 2 em RASCUNHO, cópia da publicada (o que o POST /versoes fará na passada 2). */
	protected void abrirRascunhoNoBanco(String questao) {
		long id = idInternoDaQuestao(questao);
		jdbc.update("INSERT INTO questao_versao (questao_id, versao, status, subtopico_id, texto_apoio, grupo_apoio, enunciado, "
				+ "imagem_url, gabarito, dificuldade, explicacao, criado_em, atualizado_em) SELECT questao_id, versao + 1, "
				+ "'RASCUNHO', subtopico_id, texto_apoio, grupo_apoio, enunciado, imagem_url, gabarito, dificuldade, explicacao, "
				+ "UTC_TIMESTAMP(3), UTC_TIMESTAMP(3) FROM questao_versao WHERE questao_id = ? AND status = 'PUBLICADA'", id);
		jdbc.update("INSERT INTO alternativa (questao_versao_id, letra, texto, criado_em, atualizado_em) SELECT n.id, a.letra, "
				+ "a.texto, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3) FROM alternativa a JOIN questao_versao p ON p.id = a.questao_versao_id "
				+ "JOIN questao_versao n ON n.questao_id = p.questao_id AND n.status = 'RASCUNHO' "
				+ "WHERE p.questao_id = ? AND p.status = 'PUBLICADA'", id);
	}

	/** Tudo o que há numa versão (linha dela e as das alternativas), para provar que não mudou. */
	protected List<Map<String, Object>> fotoDaVersao(String questao, int versao) {
		long id = idInternoDaQuestao(questao);
		List<Map<String, Object>> linhas = new ArrayList<>(jdbc.queryForList(
				"SELECT * FROM questao_versao WHERE questao_id = ? AND versao = ?", id, versao));
		linhas.addAll(jdbc.queryForList("SELECT a.* FROM alternativa a JOIN questao_versao v ON v.id = a.questao_versao_id "
				+ "WHERE v.questao_id = ? AND v.versao = ? ORDER BY a.letra", id, versao));
		return linhas;
	}

	/** O gabarito do rascunho de cada questão da prova, na ordem do numero. */
	protected List<String> gabaritosDosRascunhos(String prova) {
		return jdbc.queryForList("SELECT v.gabarito FROM questao_versao v JOIN questao q ON q.id = v.questao_id "
				+ "WHERE q.prova_id = ? AND v.status = 'RASCUNHO' ORDER BY q.numero", String.class, idInternoDaProva(prova));
	}

	protected String hashDa(String questao) {
		return jdbc.queryForObject("SELECT hash_conteudo FROM questao WHERE id = ?", String.class, idInternoDaQuestao(questao));
	}

	// ----- conexões de fora da aplicação -----

	/** Uma conexão direta, fora do pool da aplicação, sem autocommit. */
	protected static Connection outraTransacao() throws SQLException {
		Connection conexao = DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
		conexao.setAutoCommit(false);
		return conexao;
	}

	protected static void executar(Connection conexao, String sql) throws SQLException {
		try (Statement comando = conexao.createStatement()) {
			comando.execute(sql);
		}
	}

	protected void travarProva(Connection conexao, String prova) throws SQLException {
		executar(conexao, "SELECT id FROM prova WHERE id = " + idInternoDaProva(prova) + " FOR UPDATE");
	}

	/** Espera o MySQL mostrar uma transação esperando por trava (consultado como root, que enxerga o innodb_trx). */
	protected static void esperarAlguemNaFilaDaTrava() throws SQLException, InterruptedException {
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
		throw new AssertionError("a requisição não chegou a esperar por uma trava");
	}

	/** Dispara as tarefas ao mesmo tempo (todas esperam a largada) e devolve os resultados na ordem das tarefas. */
	protected static <T> List<T> executarSimultaneamente(List<Callable<T>> tarefas) throws Exception {
		ExecutorService pool = Executors.newFixedThreadPool(tarefas.size());
		CountDownLatch todosProntos = new CountDownLatch(tarefas.size());
		CountDownLatch largada = new CountDownLatch(1);
		try {
			List<Future<T>> futuros = tarefas.stream().map(tarefa -> pool.submit(() -> {
				todosProntos.countDown();
				largada.await();
				return tarefa.call();
			})).collect(Collectors.toList());
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
