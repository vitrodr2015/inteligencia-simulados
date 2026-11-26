package br.simulado.etec.shared.validacao;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import br.simulado.etec.shared.config.ImagensProperties;

class HostDeImagemPermitidoValidatorTest {

	private final HostDeImagemPermitidoValidator validador = new HostDeImagemPermitidoValidator(
			new ImagensProperties(List.of("Imagens.Exemplo.Invalid", " localhost ")));

	@Test
	void deve_aceitar_nulo() {
		assertThat(validador.isValid(null, null)).isTrue();
	}

	@ParameterizedTest
	@ValueSource(strings = { "https://imagens.exemplo.invalid/a.png", "https://IMAGENS.EXEMPLO.INVALID/a.png",
			"https://imagens.exemplo.invalid:8443/a.png?x=1", "https://localhost/a.png" })
	void deve_aceitar_o_host_da_lista_sem_distinguir_caixa_e_ignorando_a_porta(String url) {
		assertThat(validador.isValid(url, null)).as(url).isTrue();
	}

	@ParameterizedTest
	@ValueSource(strings = { "https://outro.exemplo.invalid/a.png", "https://sub.imagens.exemplo.invalid/a.png",
			"https://imagens.exemplo.invalid.atacante.invalid/a.png", "https://exemplo.invalid/a.png",
			"https://atacante.invalid/imagens.exemplo.invalid", "https://127.0.0.1/a.png" })
	void deve_recusar_host_fora_da_lista_inclusive_subdominio_e_prefixo(String url) {
		assertThat(validador.isValid(url, null)).as(url).isFalse();
	}

	@ParameterizedTest
	@ValueSource(strings = { "nao-e-url", "https://", "mailto:a@b.invalid", "https://a b/c" })
	void deve_deixar_para_o_url_https_o_que_nao_tem_host(String texto) {
		assertThat(validador.isValid(texto, null)).as(texto).isTrue();
	}

	@Test
	void deve_recusar_tudo_quando_a_lista_esta_vazia() {
		HostDeImagemPermitidoValidator vazio = new HostDeImagemPermitidoValidator(new ImagensProperties(List.of()));

		assertThat(vazio.isValid("https://imagens.exemplo.invalid/a.png", null)).isFalse();
		assertThat(new ImagensProperties(null).hostsPermitidos()).isEmpty();
	}

}
