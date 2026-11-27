package br.simulado.etec.admin.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.AfterEach;

import br.simulado.etec.catalogo.controller.AbstractQuestaoIntegrationTest;
import io.restassured.response.Response;

/** Ajudantes da importação contra MySQL real. Tudo o que o teste importa numa prova dele é apagado no fim. */
abstract class AbstractImportacaoIntegrationTest extends AbstractQuestaoIntegrationTest {

	protected static final String ROTA = "/api/v1/admin/importacoes";

	private final List<String> provasDoTeste = new ArrayList<>();

	protected String novaProva(int quantidade) {
		String prova = criarProva(quantidade);
		provasDoTeste.add(prova);
		return prova;
	}

	@AfterEach
	void apagarOQueOTesteImportou() {
		for (String prova : provasDoTeste) {
			long id = idInternoDaProva(prova);
			jdbc.update("DELETE a FROM alternativa a JOIN questao_versao v ON v.id = a.questao_versao_id "
					+ "JOIN questao q ON q.id = v.questao_id WHERE q.prova_id = ?", id);
			jdbc.update("DELETE v FROM questao_versao v JOIN questao q ON q.id = v.questao_id WHERE q.prova_id = ?", id);
			jdbc.update("DELETE FROM questao WHERE prova_id = ?", id);
			jdbc.update("DELETE FROM prova WHERE id = ?", id);
		}
		provasDoTeste.clear();
	}

	/** Itens de numero {@code de} a {@code ate}, com o enunciado "{marca} {numero}". */
	protected static List<String> itens(String subtopico, int de, int ate, String marca) {
		return IntStream.rangeClosed(de, ate).mapToObj(n -> corpo(subtopico, n, marca + " " + n)).collect(Collectors.toList());
	}

	protected static String lote(String prova, List<String> itens) {
		return "{\"provaId\":\"" + prova + "\",\"questoes\":[" + String.join(",", itens) + "]}";
	}

	protected Response importar(String corpo) {
		return requisicao().body(corpo).post(ROTA);
	}

	/** Linhas das três tabelas de questão, no banco inteiro. */
	protected List<Integer> linhasDasTresTabelas() {
		return List.of(contar("questao"), contar("questao_versao"), contar("alternativa"));
	}

	private int contar(String tabela) {
		return jdbc.queryForObject("SELECT COUNT(*) FROM " + tabela, Integer.class);
	}

	protected int questoesDaProva(String prova) {
		return jdbc.queryForObject("SELECT COUNT(*) FROM questao WHERE prova_id = ?", Integer.class, idInternoDaProva(prova));
	}

}
