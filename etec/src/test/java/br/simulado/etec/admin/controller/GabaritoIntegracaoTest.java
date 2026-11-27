package br.simulado.etec.admin.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import br.simulado.etec.catalogo.controller.AbstractEdicaoIntegrationTest;
import io.restassured.response.Response;

/** Gabarito em lote contra MySQL real (D174): atomicidade, a versão publicada intacta e o que é regravado. */
class GabaritoIntegracaoTest extends AbstractEdicaoIntegrationTest {

	private List<String> prepararCinco(String prova, String subtopico) {
		return IntStream.rangeClosed(1, 5).mapToObj(n -> cadastrarId(prova, subtopico, n, "Questão " + n)).toList();
	}

	/**
	 * A falha acontece no BANCO, no meio do lote: um CHECK temporário recusa a letra E só na terceira questão. Quando ela é
	 * recusada, as duas primeiras já tinham sido mandadas ao banco dentro da transação, e precisam ser desfeitas.
	 */
	@Test
	void deve_nao_gravar_nada_quando_o_banco_recusa_um_item_no_meio_do_lote() {
		String prova = novaProva(5);
		List<String> questoes = prepararCinco(prova, criarSubtopico());
		// o MySQL não aceita CHECK sobre a coluna auto-incremento: o alvo é marcado por um enunciado que só ele tem
		String marca = "Alvo " + sufixo();
		assertThat(jdbc.update("UPDATE questao_versao SET enunciado = ? WHERE questao_id = ?", marca,
				idInternoDaQuestao(questoes.get(2)))).isEqualTo(1);
		jdbc.execute("ALTER TABLE questao_versao ADD CONSTRAINT ck_teste_falha_no_meio CHECK (enunciado <> '" + marca
				+ "' OR gabarito IS NULL OR gabarito <> 'E')");
		try {
			Response resposta = gabarito(prova, item(1, "A"), item(2, "B"), item(3, "E"), item(4, "C"), item(5, "D"));

			verificarErro(resposta, 500, "Internal Server Error", "ERRO_INTERNO");
			assertThat(gabaritosDosRascunhos(prova)).containsExactly(null, null, null, null, null);
		}
		finally {
			jdbc.execute("ALTER TABLE questao_versao DROP CHECK ck_teste_falha_no_meio");
		}
		gabarito(prova, item(1, "A"), item(2, "B"), item(3, "E"), item(4, "C"), item(5, "D")).then().statusCode(200)
				.body("atualizadas", equalTo(5));
		assertThat(gabaritosDosRascunhos(prova)).containsExactly("A", "B", "E", "C", "D");
	}

	@Test
	void deve_nao_gravar_nada_quando_ha_numero_inexistente_ou_questao_sem_rascunho_no_meio_do_lote() {
		String prova = novaProva(10);
		List<String> questoes = prepararCinco(prova, criarSubtopico());
		publicarNoBanco(questoes.get(3));

		verificarErro(gabarito(prova, item(1, "A"), item(9, "B"), item(2, "C")), 400, "Bad Request", "VALIDACAO_FALHOU");
		Response conflito = gabarito(prova, item(1, "A"), item(4, "B"), item(2, "C"));
		verificarErro(conflito, 409, "Conflict", "QUESTAO_SEM_RASCUNHO");
		conflito.then().body("detalhes.numerosSemRascunho", contains(4));

		assertThat(gabaritosDosRascunhos(prova)).containsExactly(null, null, null, null);
		assertThat(jdbc.queryForObject("SELECT gabarito FROM questao_versao WHERE questao_id = ?", String.class,
				idInternoDaQuestao(questoes.get(3)))).isNull();
	}

	@Test
	void deve_gravar_no_rascunho_novo_de_uma_publicada_sem_tocar_na_versao_publicada() {
		String prova = novaProva(10);
		String questao = cadastrarId(prova, criarSubtopico(), 1, "Com versão nova");
		publicarNoBanco(questao);
		abrirRascunhoNoBanco(questao);
		List<Map<String, Object>> publicada = fotoDaVersao(questao, 1);

		gabarito(prova, item(1, "D")).then().statusCode(200).body("atualizadas", equalTo(1));

		assertThat(fotoDaVersao(questao, 1)).usingRecursiveComparison().isEqualTo(publicada);
		buscarQuestao(questao).then().body("versao", equalTo(2)).body("gabarito", equalTo("D"));
	}

	@Test
	void deve_regravar_so_as_questoes_cujo_gabarito_mudou_e_nunca_o_hash() {
		String prova = novaProva(5);
		List<String> questoes = prepararCinco(prova, criarSubtopico());
		gabarito(prova, item(1, "A"), item(2, "B")).then().statusCode(200);
		List<String> hashes = questoes.stream().map(this::hashDa).toList();
		jdbc.update("UPDATE questao_versao v JOIN questao q ON q.id = v.questao_id SET v.atualizado_em = ? WHERE q.prova_id = ?",
				MOMENTO_ANTIGO, idInternoDaProva(prova));

		gabarito(prova, item(1, "A"), item(2, "C")).then().statusCode(200).body("atualizadas", equalTo(1));

		assertThat(jdbc.queryForList("SELECT q.numero FROM questao_versao v JOIN questao q ON q.id = v.questao_id "
				+ "WHERE q.prova_id = ? AND v.atualizado_em <> ?", Integer.class, idInternoDaProva(prova), MOMENTO_ANTIGO))
				.containsExactly(2);
		assertThat(questoes.stream().map(this::hashDa).toList()).isEqualTo(hashes);
	}

}
