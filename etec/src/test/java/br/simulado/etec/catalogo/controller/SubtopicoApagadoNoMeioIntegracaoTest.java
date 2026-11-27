package br.simulado.etec.catalogo.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.equalTo;

import java.sql.Connection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import br.simulado.etec.shared.persistencia.Restricoes;
import io.restassured.response.Response;
import jakarta.persistence.EntityManager;

/**
 * A trava da prova não cobre o subtópico (D177): se ele for apagado enquanto uma questão é gravada nele, a chave estrangeira de
 * {@code questao_versao.subtopico_id} recusa, e isso é o mesmo 400 do subtópico inexistente, nunca 500.
 */
class SubtopicoApagadoNoMeioIntegracaoTest extends AbstractEdicaoIntegrationTest {

	@Autowired
	private EntityManager entidades;

	@Autowired
	private PlatformTransactionManager transacoes;

	/**
	 * Determinístico: outra transação apaga o subtópico e ainda não confirma, então a requisição o enxerga, grava e para na
	 * conferência da chave estrangeira. Quando a outra confirma, a linha some e o banco recusa.
	 */
	private Response enquantoOSubtopicoEApagado(String subtopico, Callable<Response> requisicao) throws Exception {
		long interno = idInternoDoSubtopico(subtopico);
		ExecutorService pool = Executors.newSingleThreadExecutor();
		try (Connection outra = outraTransacao()) {
			executar(outra, "DELETE FROM subtopico WHERE id = " + interno);
			Future<Response> resposta = pool.submit(requisicao);
			esperarAlguemNaFilaDaTrava();
			outra.commit();
			return resposta.get(60, TimeUnit.SECONDS);
		}
		finally {
			pool.shutdownNow();
		}
	}

	private static void verificarCampo(Response resposta, String campo) {
		verificarErro(resposta, 400, "Bad Request", "VALIDACAO_FALHOU");
		resposta.then().body("campos[0].campo", equalTo(campo));
	}

	private int questoesDa(String prova) {
		return jdbc.queryForObject("SELECT COUNT(*) FROM questao WHERE prova_id = ?", Integer.class, idInternoDaProva(prova));
	}

	@Test
	void deve_dar_400_no_subtopico_quando_ele_e_apagado_durante_o_cadastro() throws Exception {
		String prova = novaProva(10);
		String subtopico = criarSubtopico();

		Response resposta = enquantoOSubtopicoEApagado(subtopico, () -> cadastrar(prova, corpo(subtopico, 1, "Órfã")));

		verificarCampo(resposta, "subtopicoId");
		assertThat(questoesDa(prova)).as("nem a identidade fica").isZero();
	}

	@Test
	void deve_dar_400_no_item_quando_o_subtopico_e_apagado_durante_a_importacao() throws Exception {
		String prova = novaProva(10);
		String bom = criarSubtopico();
		String apagado = criarSubtopico();
		String lote = "{\"provaId\":\"" + prova + "\",\"questoes\":[" + corpo(bom, 1, "Fica de fora também") + ","
				+ corpo(apagado, 2, "Órfã") + "]}";

		Response resposta = enquantoOSubtopicoEApagado(apagado,
				() -> requisicao().body(lote).post("/api/v1/admin/importacoes"));

		verificarCampo(resposta, "questoes[1].subtopicoId");
		assertThat(questoesDa(prova)).as("tudo ou nada").isZero();
	}

	@Test
	void deve_dar_400_no_subtopico_quando_o_novo_e_apagado_durante_o_put() throws Exception {
		String prova = novaProva(10);
		String atual = criarSubtopico();
		String novo = criarSubtopico();
		String questao = cadastrarId(prova, atual, 1, "Fica como está");

		Response resposta = enquantoOSubtopicoEApagado(novo, () -> editarQuestao(questao, corpoDoPut(novo, "Mudaria", "")));

		verificarCampo(resposta, "subtopicoId");
		buscarQuestao(questao).then().body("enunciado", equalTo("Fica como está")).body("subtopicoId", equalTo(atual));
	}

	@RepeatedTest(5)
	void deve_nunca_dar_500_quando_o_delete_do_subtopico_corre_junto_com_o_cadastro() throws Exception {
		String prova = novaProva(10);
		String subtopico = criarSubtopico();

		List<Response> respostas = executarSimultaneamente(List.of(() -> cadastrar(prova, corpo(subtopico, 1, "Corrida")),
				() -> requisicao().delete("/api/v1/subtopicos/" + subtopico)));

		Response cadastro = respostas.get(0);
		Response apagar = respostas.get(1);
		if (cadastro.statusCode() == 201) {
			verificarErro(apagar, 409, "Conflict", "SUBTOPICO_COM_QUESTOES");
		}
		else {
			verificarCampo(cadastro, "subtopicoId");
			assertThat(apagar.statusCode()).as(apagar.asString()).isEqualTo(204);
		}
	}

	/** "Só essa restrição" depende de o Hibernate informar o NOME da chave no MySQL real: sem o nome, qualquer FK passaria. */
	@Test
	void deve_receber_do_mysql_o_nome_da_chave_para_nao_traduzir_a_de_outra_coluna() {
		long subtopico = idInternoDoSubtopico(criarSubtopico());
		TransactionTemplate transacao = new TransactionTemplate(transacoes);

		assertThatThrownBy(() -> transacao.execute(estado -> entidades
				.createNativeQuery("INSERT INTO questao_versao (questao_id, versao, status, subtopico_id, enunciado, criado_em, "
						+ "atualizado_em) VALUES (-1, 1, 'RASCUNHO', " + subtopico + ", 'x', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))")
				.executeUpdate())).satisfies(erro -> {
					assertThat(Restricoes.ehChaveEstrangeira(erro, "fk_questao_versao_questao")).isTrue();
					assertThat(Restricoes.ehChaveEstrangeira(erro, "fk_questao_versao_subtopico"))
							.as("a de outra coluna não é a do subtópico").isFalse();
				});
	}

}
