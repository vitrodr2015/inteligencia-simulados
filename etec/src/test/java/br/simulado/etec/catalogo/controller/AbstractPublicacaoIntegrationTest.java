package br.simulado.etec.catalogo.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import io.restassured.response.Response;

/** Ajudantes da publicação da prova (D178) contra MySQL real. A limpeza das provas é a da {@link AbstractEdicaoIntegrationTest}. */
abstract class AbstractPublicacaoIntegrationTest extends AbstractEdicaoIntegrationTest {

	protected Response publicar(String prova) {
		return requisicao().patch("/api/v1/provas/" + prova + "/publish");
	}

	/** Um item de lote, ou um corpo de cadastro, com o gabarito dado (nulo = sem). */
	protected static String questaoComGabarito(String subtopico, int numero, String letra) {
		String corpo = corpo(subtopico, numero, "Questão " + numero);
		return letra == null ? corpo : corpo.substring(0, corpo.length() - 1) + ",\"gabarito\":\"" + letra + "\"}";
	}

	protected static String lote(String prova, List<String> itens) {
		return "{\"provaId\":\"" + prova + "\",\"questoes\":[" + String.join(",", itens) + "]}";
	}

	protected Response importar(String corpo) {
		return requisicao().body(corpo).post("/api/v1/admin/importacoes");
	}

	/** Importa as questões de {@code de} a {@code ate}, todas com gabarito A, no subtópico dado. */
	protected void importarProntas(String prova, String subtopico, int de, int ate) {
		importar(lote(prova, IntStream.rangeClosed(de, ate).mapToObj(n -> questaoComGabarito(subtopico, n, "A"))
				.collect(Collectors.toList()))).then().statusCode(200);
	}

	/** As pendências do 409, cada uma como "numero MOTIVO", na ordem em que vieram. */
	protected static List<String> pendencias(Response resposta) {
		verificarErro(resposta, 409, "Conflict", "PROVA_COM_QUESTOES_PENDENTES");
		List<Map<String, Object>> lista = resposta.path("detalhes.questoesPendentes");
		return lista.stream().map(p -> p.get("numero") + " " + p.get("motivo")).collect(Collectors.toList());
	}

	/** Todas as linhas da prova e do que há nela, nas quatro tabelas. */
	protected List<Object> fotoDaProva(String prova) {
		long id = idInternoDaProva(prova);
		List<Object> foto = new ArrayList<>(jdbc.queryForList("SELECT * FROM prova WHERE id = ?", id));
		foto.addAll(jdbc.queryForList("SELECT * FROM questao WHERE prova_id = ? ORDER BY id", id));
		foto.addAll(jdbc.queryForList("SELECT v.* FROM questao_versao v JOIN questao q ON q.id = v.questao_id "
				+ "WHERE q.prova_id = ? ORDER BY v.id", id));
		foto.addAll(jdbc.queryForList("SELECT a.* FROM alternativa a JOIN questao_versao v ON v.id = a.questao_versao_id "
				+ "JOIN questao q ON q.id = v.questao_id WHERE q.prova_id = ? ORDER BY a.id", id));
		return foto;
	}

	/** Quantas versões da prova estão em cada status, no formato "STATUS=quantidade". */
	protected List<String> versoesPorStatus(String prova) {
		return jdbc.queryForList("SELECT CONCAT(v.status, '=', COUNT(*)) FROM questao_versao v JOIN questao q "
				+ "ON q.id = v.questao_id WHERE q.prova_id = ? GROUP BY v.status ORDER BY v.status", String.class,
				idInternoDaProva(prova));
	}

	protected String statusDaProva(String prova) {
		return jdbc.queryForObject("SELECT status FROM prova WHERE id = ?", String.class, idInternoDaProva(prova));
	}

	/**
	 * O estado de uma publicação é sempre inteiro: prova publicada com exatamente uma versão PUBLICADA por numero de 1 a N, todas
	 * com o publicada_em dela; ou prova em rascunho sem nenhuma versão publicada nem datada.
	 */
	protected void exigirSemEstadoParcial(String prova) {
		long id = idInternoDaProva(prova);
		if ("PUBLICADA".equals(statusDaProva(prova))) {
			int quantidade = jdbc.queryForObject("SELECT quantidade_questoes FROM prova WHERE id = ?", Integer.class, id);
			assertThat(jdbc.queryForList("SELECT q.numero FROM questao_versao v JOIN questao q ON q.id = v.questao_id "
					+ "JOIN prova p ON p.id = q.prova_id WHERE p.id = ? AND v.status = 'PUBLICADA' "
					+ "AND v.publicada_em = p.publicada_em ORDER BY q.numero", Integer.class, id))
					.containsExactlyElementsOf(IntStream.rangeClosed(1, quantidade).boxed().toList());
			assertThat(versoesPorStatus(prova)).noneMatch(v -> v.startsWith("RASCUNHO"));
		}
		else {
			assertThat(jdbc.queryForObject("SELECT publicada_em FROM prova WHERE id = ?", java.sql.Timestamp.class, id)).isNull();
			assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM questao_versao v JOIN questao q ON q.id = v.questao_id "
					+ "WHERE q.prova_id = ? AND (v.status <> 'RASCUNHO' OR v.publicada_em IS NOT NULL)", Integer.class, id))
					.as("nada publicado").isZero();
		}
	}

}
