package br.simulado.etec.catalogo.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import br.simulado.etec.shared.programa.Programa;

/**
 * O programa de prova (D143) no MySQL REAL, com as migrations de verdade: a tabela tem exatamente a linha ETEC, igual ao
 * {@link Programa#ETEC}, e a matéria criada pela API nasce nesse programa. O programa é interno: a API não o mostra.
 */
class ProgramaIntegracaoTest extends AbstractMateriaIntegrationTest {

	@Test
	void deve_ter_na_tabela_programa_exatamente_o_codigo_etec_igual_ao_do_codigo_java() {
		List<String> codigos = jdbc.queryForList("SELECT codigo FROM programa", String.class);

		assertThat(codigos).containsExactly(Programa.ETEC.codigo());
		assertThat(Programa.de(codigos.get(0))).isEqualTo(Programa.ETEC);
	}

	@Test
	void deve_criar_a_materia_no_programa_etec_sem_mostrar_o_programa_na_resposta() {
		String nome = "Programa " + sufixo();

		String corpo = criar(nome, null).then().statusCode(201).extract().asString();
		String id = criar("Outra " + sufixo(), null).then().statusCode(201).extract().path("id");

		assertThat(corpo).as("o programa é interno (D143)").doesNotContain("programa").doesNotContain("ETEC");
		String codigo = jdbc.queryForObject("SELECT p.codigo FROM materia m JOIN programa p ON p.id = m.programa_id "
				+ "WHERE m.public_id = UNHEX(REPLACE(?, '-', ''))", String.class, id);
		assertThat(codigo).isEqualTo(Programa.ETEC.codigo());
	}

}
