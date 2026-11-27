package br.simulado.etec.catalogo.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.zaxxer.hikari.HikariDataSource;

import io.restassured.response.Response;

/**
 * O 503 RECURSO_OCUPADO (D176) com o MySQL REAL: estouro de espera pela trava (erro 1205) e deadlock (erro 1213). Para não
 * esperar os 50 s do padrão, o prazo é baixado nas SESSÕES do pool da aplicação (todas são pegas de uma vez, ajustadas e
 * devolvidas) e restaurado no fim; o contexto compartilhado não muda.
 */
class RecursoOcupadoIntegracaoTest extends AbstractEdicaoIntegrationTest {

	@Autowired
	private DataSource fonte;

	private void ajustarEsperaNasSessoesDoPool(String valor) throws SQLException {
		int maximo = ((HikariDataSource) fonte).getMaximumPoolSize();
		List<Connection> todas = new ArrayList<>();
		try {
			for (int i = 0; i < maximo; i++) {
				Connection conexao = fonte.getConnection();
				todas.add(conexao);
				executar(conexao, "SET SESSION innodb_lock_wait_timeout = " + valor);
			}
		}
		finally {
			for (Connection conexao : todas) {
				conexao.close();
			}
		}
	}

	@AfterEach
	void restaurarAEspera() throws SQLException {
		ajustarEsperaNasSessoesDoPool("DEFAULT");
		assertThat(jdbc.queryForObject("SELECT @@session.innodb_lock_wait_timeout", Integer.class)).isEqualTo(50);
	}

	private static void verificarOcupado(Response resposta) {
		verificarErro(resposta, 503, "Service Unavailable", "RECURSO_OCUPADO");
		assertThat(resposta.header("Retry-After")).isEqualTo("5");
		assertThat(resposta.asString()).doesNotContainIgnoringCase("lock").doesNotContain("br.simulado")
				.doesNotContain("prova where");
	}

	@Test
	void deve_responder_503_com_retry_after_em_toda_escrita_quando_a_espera_pela_trava_estoura() throws Exception {
		String prova = novaProva(10);
		String subtopico = criarSubtopico();
		String questao = cadastrarId(prova, subtopico, 1, "Presa");
		String corpoDaEdicao = corpoDoPut(subtopico, "Depois", "");
		ajustarEsperaNasSessoesDoPool("1");
		assertThat(jdbc.queryForObject("SELECT @@session.innodb_lock_wait_timeout", Integer.class)).isEqualTo(1);

		try (Connection outra = outraTransacao()) {
			travarProva(outra, prova);

			verificarOcupado(editarQuestao(questao, corpoDaEdicao));
			verificarOcupado(editarProva(prova, corpoDaProva("Nova", null, null, null, 10)));
			verificarOcupado(gabarito(prova, item(1, "A")));
			verificarOcupado(cadastrar(prova, corpo(subtopico, 2, "Unitário")));
			verificarOcupado(requisicao().body("{\"provaId\":\"" + prova + "\",\"questoes\":[" + corpo(subtopico, 3, "Lote") + "]}")
					.post("/api/v1/admin/importacoes"));

			outra.rollback();
		}

		assertThat(jdbc.queryForList("SELECT CONCAT(q.numero, ' ', v.enunciado, ' ', COALESCE(v.gabarito, '-')) "
				+ "FROM questao_versao v JOIN questao q ON q.id = v.questao_id WHERE q.prova_id = ?", String.class,
				idInternoDaProva(prova))).as("nada foi gravado pelas tentativas recusadas").containsExactly("1 Presa -");
		editarQuestao(questao, corpoDaEdicao).then().statusCode(200);
	}

	/**
	 * Deadlock de verdade: a outra transação segura a linha da VERSÃO; o PUT trava a prova e fica esperando por essa linha; então a
	 * outra pede a prova. O MySQL desfaz a transação mais leve, e por isso a outra primeiro altera muitas linhas (que são
	 * desfeitas no fim): a vítima é o PUT.
	 */
	@Test
	void deve_responder_503_com_retry_after_quando_o_put_e_a_vitima_de_um_deadlock() throws Exception {
		String prova = novaProva(10);
		String subtopico = criarSubtopico();
		String questao = cadastrarId(prova, subtopico, 1, "Antes do deadlock");
		long interna = idInternoDaQuestao(questao);
		String marca = "peso-" + sufixo();
		ExecutorService pool = Executors.newSingleThreadExecutor();
		try (Connection outra = outraTransacao()) {
			try (Statement comando = outra.createStatement()) {
				for (int i = 0; i < 300; i++) {
					comando.executeUpdate("INSERT INTO materia (public_id, programa_id, nome, ativo, criado_em, atualizado_em) "
							+ "SELECT UNHEX(REPLACE(UUID(), '-', '')), id, '" + marca + "-" + i
							+ "', TRUE, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3) FROM programa WHERE codigo = 'ETEC'");
				}
			}
			executar(outra, "SELECT id FROM questao_versao WHERE questao_id = " + interna + " FOR UPDATE");

			Future<Response> edicao = pool.submit(() -> editarQuestao(questao, corpoDoPut(subtopico, "Depois", "")));
			esperarAlguemNaFilaDaTrava();
			travarProva(outra, prova); // fecha o ciclo: o MySQL detecta o deadlock na hora

			verificarOcupado(edicao.get(30, TimeUnit.SECONDS));
			outra.rollback();
		}
		finally {
			pool.shutdownNow();
		}
		assertThat(jdbc.queryForObject("SELECT enunciado FROM questao_versao WHERE questao_id = ?", String.class, interna))
				.isEqualTo("Antes do deadlock");
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM materia WHERE nome LIKE ?", Integer.class, marca + "%")).isZero();
	}

}
