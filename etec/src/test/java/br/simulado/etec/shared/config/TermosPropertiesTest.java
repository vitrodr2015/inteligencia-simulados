package br.simulado.etec.shared.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class TermosPropertiesTest {

	@Test
	void deve_aceitar_versao_de_ate_20_caracteres_e_url() {
		assertThat(new TermosProperties("x".repeat(20), "https://exemplo.invalid/termos").versao()).hasSize(20);
	}

	@Test
	void deve_recusar_versao_ausente_em_branco_ou_com_mais_de_20_caracteres() {
		assertThatThrownBy(() -> new TermosProperties(null, "https://exemplo.invalid/t"))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("app.termos.versao");
		assertThatThrownBy(() -> new TermosProperties("  ", "https://exemplo.invalid/t"))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new TermosProperties("x".repeat(21), "https://exemplo.invalid/t"))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("20");
	}

	@Test
	void deve_recusar_url_ausente_ou_em_branco() {
		assertThatThrownBy(() -> new TermosProperties("v1", null)).isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("app.termos.url");
		assertThatThrownBy(() -> new TermosProperties("v1", " ")).isInstanceOf(IllegalArgumentException.class);
	}

}
