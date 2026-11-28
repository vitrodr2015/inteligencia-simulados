package br.simulado.etec.catalogo.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import io.restassured.response.Response;

/** Ajudantes da publicação (D178, D182) e das versões (D181) contra MySQL real. A limpeza das provas é a da {@link AbstractEdicaoIntegrationTest}. */
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

	/** As pendências do 409 da publicação da PROVA, cada uma como "numero MOTIVO", na ordem em que vieram. */
	protected static List<String> pendencias(Response resposta) {
		return pendencias(resposta, "PROVA_COM_QUESTOES_PENDENTES");
	}

	/** O mesmo, para o 409 da publicação avulsa de QUESTÃO (D187). */
	protected static List<String> pendenciasDaQuestao(Response resposta) {
		return pendencias(resposta, "QUESTAO_COM_PENDENCIAS");
	}

	private static List<String> pendencias(Response resposta, String codigo) {
		verificarErro(resposta, 409, "Conflict", codigo);
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

	// ----- apagar (D184 e D185) -----

	protected Response apagarQuestao(String questao) {
		return requisicao().delete("/api/v1/admin/questoes/" + questao);
	}

	protected Response apagarProva(String prova) {
		return requisicao().delete("/api/v1/admin/provas/" + prova);
	}

	// ----- versões e publicação avulsa (D181 e D182) -----

	protected Response novaVersao(String questao) {
		return requisicao().post("/api/v1/admin/questoes/" + questao + "/versoes");
	}

	protected Response publicarQuestao(String questao) {
		return requisicao().patch("/api/v1/admin/questoes/" + questao + "/publish");
	}

	protected Response lerVersao(String questao, int versao) {
		return requisicao().get("/api/v1/admin/questoes/" + questao + "?versao=" + versao);
	}

	/** Cadastra a questão com gabarito A e devolve o id. */
	protected String cadastrarPronta(String prova, String subtopico, int numero) {
		return cadastrar(prova, questaoComGabarito(subtopico, numero, "A")).then().statusCode(201).extract().path("id");
	}

	/** Uma questão com versão 1 PUBLICADA numa prova de uma questão, publicada PELA API. Devolve [prova, questão]. */
	protected List<String> questaoPublicada(String subtopico) {
		String prova = novaProva(1);
		String questao = cadastrarPronta(prova, subtopico, 1);
		publicar(prova).then().statusCode(200);
		return List.of(prova, questao);
	}

	/** Cada versão da questão como "numero STATUS", na ordem do numero da versão. */
	protected List<String> historico(String questao) {
		return jdbc.queryForList("SELECT CONCAT(versao, ' ', status) FROM questao_versao WHERE questao_id = ? ORDER BY versao",
				String.class, idInternoDaQuestao(questao));
	}

	/** O enunciado da versão que está no status dado. */
	protected String enunciadoDa(String questao, String status) {
		return jdbc.queryForObject("SELECT enunciado FROM questao_versao WHERE questao_id = ? AND status = ?", String.class,
				idInternoDaQuestao(questao), status);
	}

	/**
	 * O que não pode mudar numa versão publicada nem quando ela é arquivada: a linha dela, menos o status, o atualizado_em e a
	 * coluna gerada que depende do status, e as 5 linhas das alternativas inteiras.
	 */
	protected List<Map<String, Object>> conteudoDaVersao(String questao, int versao) {
		List<Map<String, Object>> linhas = fotoDaVersao(questao, versao);
		Map<String, Object> daVersao = new java.util.LinkedHashMap<>(linhas.get(0));
		List.of("status", "atualizado_em", "publicada_vigente").forEach(coluna -> assertThat(daVersao.keySet()).contains(coluna));
		daVersao.keySet().removeAll(List.of("status", "atualizado_em", "publicada_vigente"));
		linhas.set(0, daVersao);
		return linhas;
	}

}
