package br.simulado.etec.catalogo.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Normalização da entrada (D57, D104): espaços das pontas aparados nos dois textos; descrição em branco vira nula. */
class MateriaRequestTest {

	@Test
	void deve_aparar_os_espacos_das_pontas_do_nome_e_da_descricao() {
		MateriaRequest dados = new MateriaRequest("  Matemática  ", "\t Álgebra e geometria \n");

		assertThat(dados.nome()).isEqualTo("Matemática");
		assertThat(dados.descricao()).isEqualTo("Álgebra e geometria");
	}

	@Test
	void deve_preservar_os_espacos_internos() {
		MateriaRequest dados = new MateriaRequest("Língua  Portuguesa", "uma   descrição  longa");

		assertThat(dados.nome()).isEqualTo("Língua  Portuguesa");
		assertThat(dados.descricao()).isEqualTo("uma   descrição  longa");
	}

	@ParameterizedTest
	@ValueSource(strings = { "", " ", "   ", "\t", "\n", " \t\r\n " })
	void deve_transformar_a_descricao_em_branco_em_nula(String descricao) {
		assertThat(new MateriaRequest("Matemática", descricao).descricao()).isNull();
	}

	@Test
	void deve_manter_nula_a_descricao_omitida() {
		assertThat(new MateriaRequest("Matemática", null).descricao()).isNull();
	}

	@Test
	void deve_deixar_o_nome_so_de_espacos_vazio_para_a_validacao_recusar() {
		assertThat(new MateriaRequest("   ", null).nome()).isEmpty();
	}

	@Test
	void deve_manter_nulo_o_nome_omitido_para_o_not_null_recusar() {
		assertThat(new MateriaRequest(null, null).nome()).isNull();
	}

}
