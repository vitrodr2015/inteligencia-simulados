package br.simulado.etec.shared.validacao;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class UrlHttpsValidatorTest {

	private final UrlHttpsValidator validador = new UrlHttpsValidator();

	@Test
	void deve_aceitar_nulo_porque_a_obrigatoriedade_e_de_outra_anotacao() {
		assertThat(validador.isValid(null, null)).isTrue();
	}

	@ParameterizedTest
	@ValueSource(strings = { "https://exemplo.invalid", "https://exemplo.invalid/prova.pdf", "HTTPS://EXEMPLO.INVALID/P",
			"https://exemplo.invalid:8443/p?q=1#topo", "https://sub.exemplo.invalid/a/b/c", "https://127.0.0.1/p",
			"https://exemplo.invalid/p%20q" })
	void deve_aceitar_url_https_com_host(String url) {
		assertThat(validador.isValid(url, null)).as(url).isTrue();
	}

	@ParameterizedTest
	@ValueSource(strings = { "http://exemplo.invalid", "ftp://exemplo.invalid", "javascript:alert(1)", "data:text/html,x",
			"file:///etc/passwd", "exemplo.invalid/p", "//exemplo.invalid/p", "https://", "https:///p", "https:exemplo.invalid",
			"https://usuario@exemplo.invalid", "https://usuario:senha@exemplo.invalid", "https://exemplo invalido",
			"https://exemplo.invalid/p q", "", " ", "https://exemplo.invalid/\n" })
	void deve_recusar_o_que_nao_e_url_https_com_host_e_sem_credenciais(String url) {
		assertThat(validador.isValid(url, null)).as(url).isFalse();
	}

}
