package br.simulado.etec.shared.paginacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import br.simulado.etec.shared.exception.CodigoErro;
import br.simulado.etec.shared.exception.InvalidRequestException;

class FiltrosTest {

	@Test
	void deve_devolver_nulo_quando_o_parametro_booleano_esta_ausente() {
		assertThat(Filtros.booleano("ativo", null)).isNull();
	}

	@ParameterizedTest
	@ValueSource(strings = { "true", "TRUE", "True" })
	void deve_ler_true_quando_o_valor_e_true_em_qualquer_caixa(String valor) {
		assertThat(Filtros.booleano("ativo", valor)).isTrue();
	}

	@ParameterizedTest
	@ValueSource(strings = { "false", "FALSE", "False" })
	void deve_ler_false_quando_o_valor_e_false_em_qualquer_caixa(String valor) {
		assertThat(Filtros.booleano("ativo", valor)).isFalse();
	}

	@ParameterizedTest
	@ValueSource(strings = { "1", "0", "yes", "no", "on", "off", "talvez", "", " ", "truee", "t", "null" })
	void deve_recusar_com_parametro_invalido_quando_o_valor_nao_e_true_nem_false(String valor) {
		assertThatThrownBy(() -> Filtros.booleano("ativo", valor))
				.isInstanceOfSatisfying(InvalidRequestException.class,
						erro -> assertThat(erro.getCodigo()).isEqualTo(CodigoErro.PARAMETRO_INVALIDO))
				.hasMessageContaining("'ativo'").hasMessageContaining("true ou false");
	}

}
