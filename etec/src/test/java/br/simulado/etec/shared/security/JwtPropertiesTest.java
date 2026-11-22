package br.simulado.etec.shared.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.Test;

class JwtPropertiesTest {

	private static final Duration ACCESS = Duration.ofMinutes(15);

	private static final Duration REFRESH = Duration.ofHours(24);

	@Test
	void deve_aceitar_a_configuracao_valida() {
		JwtProperties propriedades = new JwtProperties(SegredoDeTeste.gerar(), "etec", ACCESS, REFRESH);

		assertThat(propriedades.issuer()).isEqualTo("etec");
	}

	@Test
	void deve_recusar_segredo_nulo_ou_com_menos_de_32_bytes() {
		assertThatThrownBy(() -> new JwtProperties(null, "etec", ACCESS, REFRESH))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("32 bytes");
		assertThatThrownBy(() -> new JwtProperties("x".repeat(31), "etec", ACCESS, REFRESH))
				.isInstanceOf(IllegalArgumentException.class);
		assertThat(new JwtProperties("x".repeat(32), "etec", ACCESS, REFRESH)).isNotNull();
	}

	@Test
	void deve_contar_o_segredo_em_bytes_utf8_e_nao_em_caracteres() {
		// 16 caracteres de 2 bytes cada = 32 bytes: vale; 15 = 30 bytes: não vale.
		assertThat(new JwtProperties("é".repeat(16), "etec", ACCESS, REFRESH)).isNotNull();
		assertThatThrownBy(() -> new JwtProperties("é".repeat(15), "etec", ACCESS, REFRESH))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void deve_nunca_mostrar_o_segredo_no_texto_nem_na_mensagem_de_erro() {
		String segredo = SegredoDeTeste.gerar();

		assertThat(new JwtProperties(segredo, "etec", ACCESS, REFRESH).toString()).doesNotContain(segredo);
		String curto = "curto-demais-segredo";
		assertThatThrownBy(() -> new JwtProperties(curto, "etec", ACCESS, REFRESH))
				.hasMessageNotContaining(curto);
	}

	@Test
	void deve_recusar_emissor_em_branco_e_prazos_invalidos() {
		String segredo = SegredoDeTeste.gerar();

		assertThatThrownBy(() -> new JwtProperties(segredo, " ", ACCESS, REFRESH))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new JwtProperties(segredo, "etec", Duration.ZERO, REFRESH))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new JwtProperties(segredo, "etec", ACCESS, Duration.ofSeconds(-1)))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new JwtProperties(segredo, "etec", ACCESS, ACCESS))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("maior");
	}

}
