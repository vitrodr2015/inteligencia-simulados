package br.simulado.etec.shared.validacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Validações do cadastro: senha em bytes, e-mail ASCII (D119) e data de nascimento (D106). */
class ValidacoesDeCadastroTest {

	// ----- TamanhoEmBytes -----

	record Senha(@TamanhoEmBytes(min = 8, max = 72) String valor) {
	}

	record Incoerente(@TamanhoEmBytes(min = 10, max = 5) String valor) {
	}

	private static final jakarta.validation.Validator VALIDADOR = jakarta.validation.Validation
			.buildDefaultValidatorFactory().getValidator();

	private static boolean senhaValida(String valor) {
		return VALIDADOR.validate(new Senha(valor)).isEmpty();
	}

	@Test
	void deve_contar_bytes_utf8_com_limites_inclusivos() {
		assertThat(senhaValida("1234567")).isFalse();
		assertThat(senhaValida("12345678")).isTrue();
		assertThat(senhaValida("a".repeat(72))).isTrue();
		assertThat(senhaValida("a".repeat(73))).isFalse();
		assertThat(senhaValida(null)).isTrue();
	}

	@Test
	void deve_contar_acentos_e_emojis_como_varios_bytes() {
		assertThat(senhaValida("é".repeat(36))).isTrue(); // 72 bytes
		assertThat(senhaValida("é".repeat(37))).isFalse(); // 74 bytes
		assertThat(senhaValida("😀".repeat(18))).isTrue(); // 72 bytes
		assertThat(senhaValida("😀".repeat(19))).isFalse(); // 76 bytes
		assertThat(senhaValida("éé")).isFalse(); // 4 bytes, abaixo do mínimo
		assertThat(senhaValida("éééé")).isTrue(); // 8 bytes
	}

	@Test
	void deve_recusar_limites_incoerentes() {
		assertThatThrownBy(() -> VALIDADOR.validate(new Incoerente("x"))).isInstanceOf(RuntimeException.class);
	}

	// ----- EmailAscii -----

	private final EmailAsciiValidator emailValidator = new EmailAsciiValidator();

	@ParameterizedTest
	@ValueSource(strings = { "ana@exemplo.com", "ana.silva@exemplo.com.br", "a+b@exemplo.com", "a_b-c@sub.exemplo.org",
			"x@y.zz", "um.dois.tres@a-b.com", "1234@5678.io", "!#$%&'*+/=?^_`{|}~-@exemplo.com" })
	void deve_aceitar_emails_ascii_bem_formados(String email) {
		assertThat(emailValidator.isValid(email, null)).as(email).isTrue();
	}

	@ParameterizedTest
	@ValueSource(strings = { "", "ana", "@exemplo.com", "ana@", "ana@exemplo", "ana@@exemplo.com", "a na@exemplo.com",
			"ana@exe mplo.com", ".ana@exemplo.com", "ana.@exemplo.com", "a..na@exemplo.com", "ana@-exemplo.com",
			"ana@exemplo-.com", "ana@exemplo..com", "ana@exemplo.com.", "<ana>@exemplo.com", "ana@[127.0.0.1]",
			"\"ana\"@exemplo.com", "ana@exemplo.com\n", "ana(c)@exemplo.com" })
	void deve_recusar_emails_malformados(String email) {
		assertThat(emailValidator.isValid(email, null)).as(email).isFalse();
	}

	@ParameterizedTest
	@ValueSource(strings = { "josé@exemplo.com", "ana@exemplô.com", "ａna@exemplo.com", "ana@exemplo.中国",
			"ana@ехample.com", "ana​@exemplo.com", "ana@exemplo.com " })
	void deve_recusar_qualquer_caractere_fora_do_ascii(String email) {
		assertThat(emailValidator.isValid(email, null)).as(email).isFalse();
	}

	@Test
	void deve_limitar_a_parte_local_a_64_caracteres() {
		assertThat(emailValidator.isValid("a".repeat(64) + "@exemplo.com", null)).isTrue();
		assertThat(emailValidator.isValid("a".repeat(65) + "@exemplo.com", null)).isFalse();
		assertThat(emailValidator.isValid(null, null)).isTrue();
	}

	// ----- DataDeNascimento -----

	private static DataDeNascimentoValidator validadorDeData(String instante) {
		return new DataDeNascimentoValidator(Clock.fixed(Instant.parse(instante), ZoneOffset.UTC));
	}

	@Test
	void deve_aceitar_ontem_e_recusar_hoje_e_amanha() {
		DataDeNascimentoValidator validador = validadorDeData("2026-11-23T12:00:00Z");

		assertThat(validador.isValid(LocalDate.of(2026, 11, 22), null)).isTrue();
		assertThat(validador.isValid(LocalDate.of(2026, 11, 23), null)).isFalse();
		assertThat(validador.isValid(LocalDate.of(2026, 11, 24), null)).isFalse();
		assertThat(validador.isValid(LocalDate.of(9999, 1, 1), null)).isFalse();
	}

	@Test
	void deve_aceitar_a_partir_de_1900_01_01() {
		DataDeNascimentoValidator validador = validadorDeData("2026-11-23T12:00:00Z");

		assertThat(validador.isValid(LocalDate.of(1900, 1, 1), null)).isTrue();
		assertThat(validador.isValid(LocalDate.of(1899, 12, 31), null)).isFalse();
		assertThat(validador.isValid(LocalDate.of(1, 1, 1), null)).isFalse();
		assertThat(validador.isValid(null, null)).isTrue();
	}

	@Test
	void deve_usar_utc_perto_da_meia_noite() {
		// 00:30 UTC de 23/11: "hoje" é 23/11 em UTC (ainda é 22/11 em São Paulo).
		DataDeNascimentoValidator validador = validadorDeData("2026-11-23T00:30:00Z");

		assertThat(validador.isValid(LocalDate.of(2026, 11, 22), null)).isTrue();
		assertThat(validador.isValid(LocalDate.of(2026, 11, 23), null)).isFalse();
	}

}
