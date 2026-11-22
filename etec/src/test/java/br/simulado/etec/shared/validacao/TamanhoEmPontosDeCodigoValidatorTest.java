package br.simulado.etec.shared.validacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.constraints.Size;

class TamanhoEmPontosDeCodigoValidatorTest {

	/** U+1F600: UM ponto de código, DUAS unidades UTF-16. */
	private static final String EMOJI = "😀";

	private static ValidatorFactory fabrica;

	private static Validator validador;

	@BeforeAll
	static void abrir() {
		fabrica = Validation.buildDefaultValidatorFactory();
		validador = fabrica.getValidator();
	}

	@AfterAll
	static void fechar() {
		fabrica.close();
	}

	private record Texto(@TamanhoEmPontosDeCodigo(min = 2, max = 5, message = "tamanho") String valor) {
	}

	private record TextoComSize(@Size(max = 5) String valor) {
	}

	private record TextoSemLimiteMinimo(@TamanhoEmPontosDeCodigo(max = 3) String valor) {
	}

	private static boolean valido(String valor) {
		return validador.validate(new Texto(valor)).isEmpty();
	}

	@Test
	void deve_aceitar_nulo_porque_a_obrigatoriedade_e_de_outra_anotacao() {
		assertThat(valido(null)).isTrue();
	}

	@Test
	void deve_aceitar_o_minimo_e_o_maximo_exatos_e_recusar_um_ponto_de_codigo_fora() {
		assertThat(valido("a")).isFalse();
		assertThat(valido("ab")).isTrue();
		assertThat(valido("abcde")).isTrue();
		assertThat(valido("abcdef")).isFalse();
	}

	@Test
	void deve_recusar_o_texto_vazio_quando_o_minimo_e_maior_que_zero() {
		assertThat(valido("")).isFalse();
	}

	@Test
	void deve_aceitar_vazio_quando_nao_ha_minimo() {
		assertThat(validador.validate(new TextoSemLimiteMinimo(""))).isEmpty();
	}

	@Test
	void deve_contar_um_emoji_como_um_ponto_de_codigo_e_nao_como_duas_unidades_utf16() {
		String cinco = EMOJI.repeat(5);
		String seis = EMOJI.repeat(6);

		assertThat(cinco.length()).isEqualTo(10);
		assertThat(cinco.codePointCount(0, cinco.length())).isEqualTo(5);
		assertThat(valido(cinco)).as("5 emojis = 5 pontos de código, dentro do limite").isTrue();
		assertThat(valido(seis)).as("6 emojis = 6 pontos de código, um a mais que o limite").isFalse();
	}

	@Test
	void deve_mostrar_que_o_size_do_bean_validation_recusaria_o_que_o_contrato_manda_aceitar() {
		String cinco = EMOJI.repeat(5);

		Set<ConstraintViolation<TextoComSize>> violacoes = validador.validate(new TextoComSize(cinco));

		assertThat(violacoes).as("o @Size conta unidades UTF-16 (10) e recusaria 5 emojis").hasSize(1);
		assertThat(valido(cinco)).isTrue();
	}

	@Test
	void deve_contar_texto_misto_de_letras_acentuadas_e_emojis() {
		assertThat(valido("á" + EMOJI + "é" + EMOJI + "ü")).as("5 pontos de código").isTrue();
		assertThat(valido("á" + EMOJI + "é" + EMOJI + "ü" + EMOJI)).as("6 pontos de código").isFalse();
	}

	@Test
	void deve_usar_a_mensagem_declarada_na_anotacao() {
		Set<ConstraintViolation<Texto>> violacoes = validador.validate(new Texto("a"));

		assertThat(violacoes).singleElement().extracting(ConstraintViolation::getMessage).isEqualTo("tamanho");
	}

	private record LimitesInvalidos(@TamanhoEmPontosDeCodigo(min = 5, max = 2) String valor) {
	}

	@Test
	void deve_falhar_cedo_quando_os_limites_da_anotacao_sao_incoerentes() {
		assertThatThrownBy(() -> validador.validate(new LimitesInvalidos("abc"))).hasStackTraceContaining(
				"Limites inválidos");
	}

}
