package br.simulado.etec.shared.paginacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import br.simulado.etec.shared.exception.CodigoErro;
import br.simulado.etec.shared.exception.InvalidRequestException;

class FiltrosTest {

	enum Cor {
		AZUL, VERDE
	}

	@Test
	void deve_ler_o_inteiro_dentro_da_faixa_e_devolver_nulo_quando_ausente() {
		assertThat(Filtros.inteiro("ano", null, 2000, 2100)).isNull();
		assertThat(Filtros.inteiro("ano", "2000", 2000, 2100)).isEqualTo(2000);
		assertThat(Filtros.inteiro("ano", "2100", 2000, 2100)).isEqualTo(2100);
		assertThat(Filtros.inteiro("ano", "2026", 2000, 2100)).isEqualTo(2026);
	}

	@ParameterizedTest
	@ValueSource(strings = { "1999", "2101", "-1", "+2026", "20.5", "", " ", " 2026", "2026 ", "abc", "٢٠٢٦", "99999999999", "0x7E9" })
	void deve_recusar_o_inteiro_fora_da_faixa_ou_mal_formado(String valor) {
		assertThatThrownBy(() -> Filtros.inteiro("ano", valor, 2000, 2100))
				.isInstanceOfSatisfying(InvalidRequestException.class,
						erro -> assertThat(erro.getCodigo()).isEqualTo(CodigoErro.PARAMETRO_INVALIDO))
				.hasMessageContaining("'ano'").hasMessageContaining("2000 a 2100");
	}

	@Test
	void deve_ler_o_enum_pelo_nome_exato_e_devolver_nulo_quando_ausente() {
		assertThat(Filtros.enumerado("cor", null, Cor.class)).isNull();
		assertThat(Filtros.enumerado("cor", "AZUL", Cor.class)).isEqualTo(Cor.AZUL);
		assertThat(Filtros.enumerado("cor", "VERDE", Cor.class)).isEqualTo(Cor.VERDE);
	}

	@ParameterizedTest
	@ValueSource(strings = { "azul", "Azul", "", " ", "AZUL ", "ROXO", "0" })
	void deve_recusar_o_enum_que_nao_e_um_nome_exato(String valor) {
		assertThatThrownBy(() -> Filtros.enumerado("cor", valor, Cor.class))
				.isInstanceOfSatisfying(InvalidRequestException.class,
						erro -> assertThat(erro.getCodigo()).isEqualTo(CodigoErro.PARAMETRO_INVALIDO))
				.hasMessageContaining("'cor'").hasMessageContaining("AZUL, VERDE");
	}

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
