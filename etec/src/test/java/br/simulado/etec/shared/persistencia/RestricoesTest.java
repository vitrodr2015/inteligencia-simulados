package br.simulado.etec.shared.persistencia;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;

import org.hibernate.exception.ConstraintViolationException;
import org.hibernate.exception.ConstraintViolationException.ConstraintKind;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

/** Classifica a violação de integridade pelo TIPO e pelo NOME da restrição, nunca pelo texto da mensagem. */
class RestricoesTest {

	private static DataIntegrityViolationException erro(ConstraintKind tipo, String nomeDaRestricao) {
		ConstraintViolationException causa = new ConstraintViolationException("violação", new SQLException("x"), tipo,
				nomeDaRestricao);
		return new DataIntegrityViolationException("falha ao gravar", causa);
	}

	@Test
	void deve_reconhecer_a_violacao_de_unicidade() {
		assertThat(Restricoes.ehUnicidade(erro(ConstraintKind.UNIQUE, "uk_subtopico_materia_nome"))).isTrue();
	}

	@Test
	void deve_nao_confundir_unicidade_com_chave_estrangeira_nem_com_outras_restricoes() {
		for (ConstraintKind tipo : new ConstraintKind[] { ConstraintKind.FOREIGN_KEY, ConstraintKind.NOT_NULL,
				ConstraintKind.CHECK, ConstraintKind.OTHER }) {
			assertThat(Restricoes.ehUnicidade(erro(tipo, "qualquer"))).as("tipo %s", tipo).isFalse();
		}
	}

	@Test
	void deve_reconhecer_a_chave_estrangeira_pelo_nome_sem_distinguir_caixa() {
		assertThat(Restricoes.ehChaveEstrangeira(erro(ConstraintKind.FOREIGN_KEY, "fk_subtopico_materia"),
				"fk_subtopico_materia")).isTrue();
		assertThat(Restricoes.ehChaveEstrangeira(erro(ConstraintKind.FOREIGN_KEY, "PUBLIC.FK_SUBTOPICO_MATERIA"),
				"fk_subtopico_materia")).isTrue();
	}

	@Test
	void deve_nao_reconhecer_a_chave_estrangeira_de_outra_restricao() {
		assertThat(Restricoes.ehChaveEstrangeira(erro(ConstraintKind.FOREIGN_KEY, "fk_questao_prova"),
				"fk_subtopico_materia")).isFalse();
	}

	@Test
	void deve_aceitar_pelo_tipo_quando_o_banco_nao_informa_o_nome_da_restricao() {
		assertThat(Restricoes.ehChaveEstrangeira(erro(ConstraintKind.FOREIGN_KEY, null), "fk_subtopico_materia")).isTrue();
	}

	@Test
	void deve_nao_tratar_outros_tipos_como_chave_estrangeira() {
		assertThat(Restricoes.ehChaveEstrangeira(erro(ConstraintKind.UNIQUE, "fk_subtopico_materia"), "fk_subtopico_materia"))
				.isFalse();
		assertThat(Restricoes.ehChaveEstrangeira(erro(ConstraintKind.NOT_NULL, null), "fk_subtopico_materia")).isFalse();
	}

	@Test
	void deve_responder_falso_quando_nao_ha_violacao_do_hibernate_na_cadeia_de_causas() {
		DataIntegrityViolationException semCausa = new DataIntegrityViolationException("sem causa conhecida");
		DataIntegrityViolationException comOutraCausa = new DataIntegrityViolationException("x", new SQLException("y"));

		assertThat(Restricoes.ehUnicidade(semCausa)).isFalse();
		assertThat(Restricoes.ehChaveEstrangeira(semCausa, "fk")).isFalse();
		assertThat(Restricoes.ehUnicidade(comOutraCausa)).isFalse();
		assertThat(Restricoes.ehUnicidade(null)).isFalse();
	}

	@Test
	void deve_achar_a_violacao_mesmo_enterrada_em_varias_causas() {
		ConstraintViolationException violacao = new ConstraintViolationException("v", new SQLException("x"),
				ConstraintKind.UNIQUE, "uk");
		Throwable embrulhado = new RuntimeException("nível 1", new IllegalStateException("nível 2", violacao));

		assertThat(Restricoes.ehUnicidade(embrulhado)).isTrue();
	}

	@Test
	void deve_reconhecer_a_unicidade_pelo_nome_da_restricao_com_ou_sem_o_prefixo_da_tabela() {
		assertThat(Restricoes.ehUnicidade(erro(ConstraintKind.UNIQUE, "materia.uk_materia_programa_nome"), "uk_materia_programa_nome")).isTrue();
		assertThat(Restricoes.ehUnicidade(erro(ConstraintKind.UNIQUE, "UK_MATERIA_PROGRAMA_NOME"), "uk_materia_programa_nome")).isTrue();
		assertThat(Restricoes.ehUnicidade(erro(ConstraintKind.UNIQUE, null), "uk_materia_programa_nome")).isTrue();
	}

	@Test
	void deve_nao_reconhecer_outra_restricao_unica_nem_outro_tipo_pelo_nome() {
		assertThat(Restricoes.ehUnicidade(erro(ConstraintKind.UNIQUE, "materia.uk_materia_nome"), "uk_materia_programa_nome")).isFalse();
		assertThat(Restricoes.ehUnicidade(erro(ConstraintKind.FOREIGN_KEY, "uk_materia_programa_nome"), "uk_materia_programa_nome")).isFalse();
		assertThat(Restricoes.ehUnicidade(erro(ConstraintKind.NOT_NULL, "programa_id"), "uk_materia_programa_nome")).isFalse();
	}

}
