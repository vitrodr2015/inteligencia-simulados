package br.simulado.etec.catalogo.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import io.restassured.response.Response;

/**
 * Concorrência da publicação (D178) contra MySQL REAL. Nas corridas valem os dois resultados possíveis, nunca 500 nem estado
 * parcial; os determinísticos provam que a trava da prova é a primeira coisa que a publicação faz.
 */
class PublicacaoConcorrenciaIntegracaoTest extends AbstractPublicacaoIntegrationTest {

	@RepeatedTest(5)
	void deve_dar_200_a_uma_e_409_a_outra_quando_duas_publicacoes_chegam_ao_mesmo_tempo() throws Exception {
		String prova = novaProva(10);
		importarProntas(prova, criarSubtopico(), 1, 10);

		List<Response> respostas = executarSimultaneamente(List.of(() -> publicar(prova), () -> publicar(prova)));

		assertThat(respostas.stream().map(Response::statusCode)).containsExactlyInAnyOrder(200, 409);
		Response recusada = respostas.stream().filter(r -> r.statusCode() == 409).findFirst().orElseThrow();
		assertThat((String) recusada.path("codigo")).isEqualTo("PROVA_NAO_EDITAVEL");
		assertThat(statusDaProva(prova)).isEqualTo("PUBLICADA");
		exigirSemEstadoParcial(prova);
	}

	/** O cadastro completa a prova: quem pega a trava primeiro decide se a publicação passa ou acha o numero ausente. */
	@RepeatedTest(6)
	void deve_ficar_consistente_quando_a_publicacao_corre_junto_com_o_cadastro_que_completa_a_prova() throws Exception {
		String prova = novaProva(5);
		String subtopico = criarSubtopico();
		importarProntas(prova, subtopico, 1, 4);

		List<Response> respostas = executarSimultaneamente(
				List.of(() -> publicar(prova), () -> cadastrar(prova, questaoComGabarito(subtopico, 5, "B"))));

		assertThat(respostas.get(1).statusCode()).as(respostas.get(1).asString()).isEqualTo(201);
		if (respostas.get(0).statusCode() == 200) {
			assertThat(statusDaProva(prova)).isEqualTo("PUBLICADA");
		}
		else {
			assertThat(pendencias(respostas.get(0))).containsExactly("5 QUESTAO_AUSENTE");
			assertThat(statusDaProva(prova)).isEqualTo("RASCUNHO");
		}
		exigirSemEstadoParcial(prova);
	}

	@RepeatedTest(6)
	void deve_ficar_consistente_quando_a_publicacao_corre_junto_com_a_importacao_que_completa_a_prova() throws Exception {
		String prova = novaProva(20);
		String subtopico = criarSubtopico();
		importarProntas(prova, subtopico, 1, 10);
		String resto = lote(prova, java.util.stream.IntStream.rangeClosed(11, 20)
				.mapToObj(n -> questaoComGabarito(subtopico, n, "C")).toList());

		List<Response> respostas = executarSimultaneamente(List.of(() -> publicar(prova), () -> importar(resto)));

		assertThat(respostas.get(1).statusCode()).as(respostas.get(1).asString()).isEqualTo(200);
		assertThat((Integer) respostas.get(1).path("questoesCriadas")).isEqualTo(10);
		if (respostas.get(0).statusCode() == 200) {
			assertThat(statusDaProva(prova)).isEqualTo("PUBLICADA");
		}
		else {
			assertThat(pendencias(respostas.get(0))).as("o lote é tudo ou nada: faltam os 10")
					.hasSize(10).allMatch(p -> p.endsWith("QUESTAO_AUSENTE"));
			assertThat(statusDaProva(prova)).isEqualTo("RASCUNHO");
		}
		exigirSemEstadoParcial(prova);
	}

	/** Aqui nada muda no conteúdo: o reenvio idempotente vem antes (200) ou depois (409) da publicação. */
	@RepeatedTest(4)
	void deve_dar_so_200_ou_409_ao_reenvio_pelo_cadastro_e_pela_importacao_durante_a_publicacao() throws Exception {
		String prova = novaProva(5);
		String subtopico = criarSubtopico();
		importarProntas(prova, subtopico, 1, 5);

		List<Response> respostas = executarSimultaneamente(List.of(() -> publicar(prova),
				() -> cadastrar(prova, questaoComGabarito(subtopico, 3, "A")),
				() -> importar(lote(prova, List.of(questaoComGabarito(subtopico, 4, "A"))))));

		assertThat(respostas.get(0).statusCode()).as(respostas.get(0).asString()).isEqualTo(200);
		for (Response reenvio : respostas.subList(1, 3)) {
			assertThat(reenvio.statusCode()).as(reenvio.asString()).isIn(200, 409);
			if (reenvio.statusCode() == 409) {
				assertThat((String) reenvio.path("codigo")).isEqualTo("PROVA_NAO_EDITAVEL");
			}
		}
		exigirSemEstadoParcial(prova);
	}

	// ----- os dois desfechos, com a ordem na fila da trava fixada -----

	/** A escrita que completa a prova de 3 questões, pelo cadastro ou pela importação. */
	private Callable<Response> completar(String como, String prova, String subtopico) {
		String terceira = questaoComGabarito(subtopico, 3, "B");
		return como.equals("cadastro") ? () -> cadastrar(prova, terceira) : () -> importar(lote(prova, List.of(terceira)));
	}

	@ParameterizedTest
	@ValueSource(strings = { "cadastro", "importação" })
	void deve_publicar_com_a_ultima_questao_quando_a_escrita_chega_primeiro(String como) throws Exception {
		String prova = novaProva(3);
		String subtopico = criarSubtopico();
		importarProntas(prova, subtopico, 1, 2);

		List<Response> respostas = naOrdemDaFila(prova, completar(como, prova, subtopico), () -> publicar(prova));

		assertThat(respostas.get(0).statusCode()).as(respostas.get(0).asString()).isIn(200, 201);
		assertThat(respostas.get(1).statusCode()).as(respostas.get(1).asString()).isEqualTo(200);
		assertThat(versoesPorStatus(prova)).containsExactly("PUBLICADA=3");
		exigirSemEstadoParcial(prova);
	}

	@ParameterizedTest
	@ValueSource(strings = { "cadastro", "importação" })
	void deve_recusar_a_publicacao_incompleta_e_deixar_a_escrita_gravar_quando_a_publicacao_chega_primeiro(String como)
			throws Exception {
		String prova = novaProva(3);
		String subtopico = criarSubtopico();
		importarProntas(prova, subtopico, 1, 2);

		List<Response> respostas = naOrdemDaFila(prova, () -> publicar(prova), completar(como, prova, subtopico));

		assertThat(pendencias(respostas.get(0))).containsExactly("3 QUESTAO_AUSENTE");
		assertThat(respostas.get(1).statusCode()).as(respostas.get(1).asString()).isIn(200, 201);
		assertThat(statusDaProva(prova)).isEqualTo("RASCUNHO");
		assertThat(versoesPorStatus(prova)).containsExactly("RASCUNHO=3");
		exigirSemEstadoParcial(prova);
	}

	// ----- determinísticos: a trava da prova é a primeira coisa da publicação -----

	@Test
	void deve_enxergar_o_gabarito_apagado_enquanto_esperava_a_trava_e_nao_publicar_nada() throws Exception {
		String prova = novaProva(2);
		String subtopico = criarSubtopico();
		importarProntas(prova, subtopico, 1, 2);
		long segunda = jdbc.queryForObject("SELECT id FROM questao WHERE prova_id = ? AND numero = 2", Long.class,
				idInternoDaProva(prova));

		Response resposta = comAProvaTravada(prova, () -> publicar(prova),
				outra -> executar(outra, "UPDATE questao_versao SET gabarito = NULL WHERE questao_id = " + segunda));

		assertThat(pendencias(resposta)).containsExactly("2 SEM_GABARITO");
		assertThat(jdbc.queryForObject("SELECT gabarito FROM questao_versao WHERE questao_id = ?", String.class, segunda))
				.as("a mudança da outra transação não foi sobrescrita").isNull();
		exigirSemEstadoParcial(prova);
	}

	@Test
	void deve_dar_409_prova_nao_editavel_quando_a_prova_foi_publicada_enquanto_esperava_a_trava() throws Exception {
		String prova = novaProva(1);
		importarProntas(prova, criarSubtopico(), 1, 1);
		long interna = idInternoDaProva(prova);

		Response resposta = comAProvaTravada(prova, () -> publicar(prova), outra -> {
			executar(outra, "UPDATE questao_versao v JOIN questao q ON q.id = v.questao_id SET v.status = 'PUBLICADA', "
					+ "v.publicada_em = '2020-01-01 00:00:00.000' WHERE q.prova_id = " + interna);
			executar(outra, "UPDATE prova SET status = 'PUBLICADA', publicada_em = '2020-01-01 00:00:00.000' WHERE id = " + interna);
		});

		verificarErro(resposta, 409, "Conflict", "PROVA_NAO_EDITAVEL");
		assertThat(jdbc.queryForObject("SELECT CAST(publicada_em AS CHAR) FROM prova WHERE id = ?", String.class, interna))
				.as("a data da primeira publicação fica").isEqualTo("2020-01-01 00:00:00.000");
		exigirSemEstadoParcial(prova);
	}

	@Test
	void deve_publicar_quando_a_questao_que_faltava_chega_enquanto_espera_a_trava() throws Exception {
		String prova = novaProva(2);
		String subtopico = criarSubtopico();
		importarProntas(prova, subtopico, 1, 1);
		long interna = idInternoDaProva(prova);
		long subtopicoInterno = idInternoDoSubtopico(subtopico);

		Response resposta = comAProvaTravada(prova, () -> publicar(prova),
				outra -> gravarQuestaoPronta(outra, interna, 2, subtopicoInterno));

		assertThat(resposta.statusCode()).as(resposta.asString()).isEqualTo(200);
		assertThat(versoesPorStatus(prova)).containsExactly("PUBLICADA=2");
		exigirSemEstadoParcial(prova);
	}

	private static void gravarQuestaoPronta(Connection conexao, long prova, int numero, long subtopico) throws SQLException {
		executar(conexao, "INSERT INTO questao (public_id, prova_id, numero, tipo, hash_conteudo, criado_em, atualizado_em) "
				+ "VALUES (UNHEX(REPLACE(UUID(), '-', '')), " + prova + ", " + numero + ", 'MULTIPLA_ESCOLHA', '" + "d".repeat(64)
				+ "', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))");
		String daQuestao = "(SELECT id FROM questao WHERE prova_id = " + prova + " AND numero = " + numero + ")";
		executar(conexao, "INSERT INTO questao_versao (questao_id, versao, status, subtopico_id, enunciado, gabarito, criado_em, "
				+ "atualizado_em) SELECT id, 1, 'RASCUNHO', " + subtopico + ", 'Chegou depois', 'B', UTC_TIMESTAMP(3), "
				+ "UTC_TIMESTAMP(3) FROM questao WHERE prova_id = " + prova + " AND numero = " + numero);
		for (String letra : new String[] { "A", "B", "C", "D", "E" }) {
			executar(conexao, "INSERT INTO alternativa (questao_versao_id, letra, texto, criado_em, atualizado_em) "
					+ "SELECT id, '" + letra + "', 't', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3) FROM questao_versao "
					+ "WHERE questao_id = " + daQuestao);
		}
	}

}
