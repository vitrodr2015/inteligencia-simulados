package br.simulado.etec.shared.security.limite;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import br.simulado.etec.shared.security.JwtProperties;

class ChavesDoLimiteTest {

	private static final String SEGREDO = "segredo-de-teste-falso-nao-e-real-0123456789";

	private static ChavesDoLimite com(String segredo) {
		return new ChavesDoLimite(new JwtProperties(segredo, "emissor", Duration.ofMinutes(15), Duration.ofHours(24)));
	}

	@Test
	void deve_gerar_64_caracteres_hexadecimais_e_ser_estavel() {
		String chave = com(SEGREDO).de(Escopo.SIGNIN_ORIGEM, "v4:10.0.0.1");

		assertThat(chave).matches("[0-9a-f]{64}");
		assertThat(com(SEGREDO).de(Escopo.SIGNIN_ORIGEM, "v4:10.0.0.1")).isEqualTo(chave);
	}

	@Test
	void deve_separar_por_escopo_e_por_valor() {
		ChavesDoLimite chaves = com(SEGREDO);

		assertThat(chaves.de(Escopo.SIGNIN_ORIGEM, "x")).isNotEqualTo(chaves.de(Escopo.AUTH_ORIGEM, "x"))
				.isNotEqualTo(chaves.de(Escopo.SIGNIN_ORIGEM, "y"));
	}

	@Test
	void deve_zerar_os_contadores_quando_o_segredo_muda() {
		assertThat(com(SEGREDO).de(Escopo.SIGNIN_ORIGEM, "v4:10.0.0.1"))
				.isNotEqualTo(com(SEGREDO + "-outro").de(Escopo.SIGNIN_ORIGEM, "v4:10.0.0.1"));
	}

	@Test
	void deve_nao_conter_o_valor_nem_o_segredo_na_chave_nem_no_toString() {
		ChavesDoLimite chaves = com(SEGREDO);
		String chave = chaves.de(Escopo.SIGNIN_EMAIL_ORIGEM, "v4:10.0.0.1|ana@exemplo.invalid");

		assertThat(chave).doesNotContain("ana").doesNotContain("10.0.0.1").doesNotContain(SEGREDO);
		assertThat(chaves.toString()).doesNotContain(SEGREDO).contains("***");
	}

	@Test
	void deve_cortar_o_valor_em_255_caracteres_antes_do_hmac() {
		ChavesDoLimite chaves = com(SEGREDO);
		String base = "a".repeat(255);

		assertThat(chaves.de(Escopo.SIGNIN_ORIGEM, base + "XYZ")).isEqualTo(chaves.de(Escopo.SIGNIN_ORIGEM, base));
	}

}
