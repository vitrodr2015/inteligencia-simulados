package br.simulado.etec.shared.security.limite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import br.simulado.etec.shared.security.limite.RateLimitProperties.Regra;

class RateLimitPropertiesTest {

	private static final Regra OK = new Regra(5, Duration.ofMinutes(15));

	@ParameterizedTest
	@ValueSource(ints = { 0, -1, RateLimitProperties.LIMITE_MAXIMO + 1 })
	void deve_recusar_limite_fora_de_1_ate_o_maximo(int limite) {
		assertThatThrownBy(() -> new Regra(limite, Duration.ofMinutes(1))).isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("limite");
	}

	@Test
	void deve_recusar_janela_nula_zero_negativa_ou_acima_de_1_hora() {
		assertThatThrownBy(() -> new Regra(5, null)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new Regra(5, Duration.ZERO)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new Regra(5, Duration.ofSeconds(-1))).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new Regra(5, Duration.ofHours(1).plusMillis(1)))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("janela");
		assertThat(new Regra(1, Duration.ofSeconds(1)).janela()).isEqualTo(Duration.ofSeconds(1));
		assertThat(new Regra(5, Duration.ofHours(1)).janela()).isEqualTo(Duration.ofHours(1));
	}

	@Test
	void deve_recusar_regra_ausente() {
		assertThatThrownBy(() -> new RateLimitProperties(null, OK, OK, true)).hasMessageContaining("auth-por-origem");
		assertThatThrownBy(() -> new RateLimitProperties(OK, null, OK, true)).hasMessageContaining("signin-email-origem");
		assertThatThrownBy(() -> new RateLimitProperties(OK, OK, null, true)).hasMessageContaining("signin-origem");
	}

	@Test
	void deve_ligar_pelas_chaves_do_yml_e_ter_a_limpeza_ligada_por_padrao() {
		var fonte = new MapConfigurationPropertySource(java.util.Map.of(
				"app.rate-limit.auth-por-origem.limite", "120", "app.rate-limit.auth-por-origem.janela", "1m",
				"app.rate-limit.signin-email-origem.limite", "5", "app.rate-limit.signin-email-origem.janela", "15m",
				"app.rate-limit.signin-origem.limite", "50", "app.rate-limit.signin-origem.janela", "15m"));

		RateLimitProperties p = new Binder(fonte).bind("app.rate-limit", RateLimitProperties.class).get();

		assertThat(p.authPorOrigem()).isEqualTo(new Regra(120, Duration.ofMinutes(1)));
		assertThat(p.signinEmailOrigem()).isEqualTo(new Regra(5, Duration.ofMinutes(15)));
		assertThat(p.signinOrigem()).isEqualTo(new Regra(50, Duration.ofMinutes(15)));
		assertThat(p.limpezaAtiva()).as("padrão").isTrue();
	}

	@Test
	void deve_falhar_a_ligacao_quando_o_valor_e_invalido() {
		var fonte = new MapConfigurationPropertySource(java.util.Map.of(
				"app.rate-limit.auth-por-origem.limite", "0", "app.rate-limit.auth-por-origem.janela", "1m",
				"app.rate-limit.signin-email-origem.limite", "5", "app.rate-limit.signin-email-origem.janela", "15m",
				"app.rate-limit.signin-origem.limite", "50", "app.rate-limit.signin-origem.janela", "15m"));

		assertThatThrownBy(() -> new Binder(fonte).bind("app.rate-limit", RateLimitProperties.class))
				.hasRootCauseInstanceOf(IllegalArgumentException.class);
	}

}
