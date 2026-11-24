package br.simulado.etec.assinatura.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class ModeloDeEntitlementsTest {

	@Test
	void deve_ter_o_programa_etec_e_aceitar_codigos_no_formato() {
		assertThat(Programa.ETEC.codigo()).isEqualTo("ETEC");
		assertThat(Programa.de("ENEM")).isEqualTo(Programa.de("ENEM")).isNotEqualTo(Programa.ETEC);
		assertThat(Programa.de("ENEM_2027").codigo()).isEqualTo("ENEM_2027");
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = { "etec", "E", "1ETEC", "ENEM 2", "ENEM-2", "AAAAAAAAAAAAAAAAAAAAA", "ÉTEC", " ETEC" })
	void deve_recusar_codigo_de_programa_fora_do_formato(String codigo) {
		assertThatThrownBy(() -> Programa.de(codigo)).isExactlyInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void deve_ter_decisao_liberada_sem_motivo_e_negada_com_motivo() {
		assertThat(Decisao.liberada().permitida()).isTrue();
		assertThat(Decisao.liberada().motivo()).isNull();
		assertThat(Decisao.negada(MotivoDaNegacao.PAPEL_SEM_ACESSO).permitida()).isFalse();
		assertThat(Decisao.negada(MotivoDaNegacao.PAPEL_SEM_ACESSO).motivo()).isEqualTo(MotivoDaNegacao.PAPEL_SEM_ACESSO);
	}

	@Test
	void deve_recusar_decisao_incoerente() {
		assertThatThrownBy(() -> new Decisao(true, MotivoDaNegacao.PAPEL_SEM_ACESSO)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new Decisao(false, null)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> Decisao.negada(null)).isInstanceOf(IllegalArgumentException.class);
	}

}
