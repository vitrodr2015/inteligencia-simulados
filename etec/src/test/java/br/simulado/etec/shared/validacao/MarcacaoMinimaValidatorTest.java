package br.simulado.etec.shared.validacao;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MarcacaoMinimaValidatorTest {

	private final MarcacaoMinimaValidator validador = new MarcacaoMinimaValidator();

	@Test
	void deve_aceitar_nulo_porque_a_obrigatoriedade_e_de_outra_anotacao() {
		assertThat(validador.isValid(null, null)).isTrue();
	}

	@ParameterizedTest
	@ValueSource(strings = { "", "texto puro", "com <b>negrito</b>", "<u>a</u> <i>b</i> <sup>2</sup> <sub>3</sub>", "linha<br>outra",
			"<b><i>aninhado</i></b>", "<b>um</b><b>dois</b>", "5 &lt; 6 &amp;&amp; 7 &gt; 6", "H<sub>2</sub>O e x<sup>2</sup>",
			"quebra\ncom nova linha", "acentuação: ção, é, ü, 😀", "<b>a</b><br><br><i>b</i>" })
	void deve_aceitar_a_marcacao_permitida_bem_formada(String texto) {
		assertThat(MarcacaoMinimaValidator.valido(texto)).as(texto).isTrue();
	}

	@ParameterizedTest
	@ValueSource(strings = { "<script>alert(1)</script>", "<script>", "<img src=x onerror=alert(1)>", "<a href=\"x\">l</a>",
			"<b class=\"x\">a</b>", "<b onclick=\"x()\">a</b>", "<b >a</b>", "<B>a</B>", "<br/>", "<br />", "</br>", "<b>aberta",
			"fechada</b>", "<b><i>cruzado</b></i>", "<b>a</i>", "<b></b></b>", "5 < 6", "7 > 6", "a < b > c", "<", ">", "<>",
			"</>", "< b>a</b>", "&", "AT&T", "&nbsp;", "&#60;", "&lt", "&amp", "&quot;", "<p>parágrafo</p>", "<style>x</style>",
			"<iframe src=x>", "<!-- comentário -->", "<![CDATA[x]]>", "<<b>a</b>", "<b>a</b>>", "<svg onload=alert(1)>",
			"javascript:alert(1)<b>", "<b" })
	void deve_recusar_o_que_nao_e_a_marcacao_minima_bem_formada(String texto) {
		if (texto.equals("javascript:alert(1)<b>")) {
			// aqui o <b> fica aberto: recusado pela pilha, não pelo texto antes dele
			assertThat(MarcacaoMinimaValidator.valido(texto)).isFalse();
			return;
		}
		assertThat(MarcacaoMinimaValidator.valido(texto)).as(texto).isFalse();
	}

}
