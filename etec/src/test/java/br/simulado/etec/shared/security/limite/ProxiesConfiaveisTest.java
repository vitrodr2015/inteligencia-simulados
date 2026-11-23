package br.simulado.etec.shared.security.limite;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class ProxiesConfiaveisTest {

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = { "   ", ".*", ".+", "^.*$", "(.*)", ".*?", "0.0.0.0/0", "::/0", "10\\.0\\.0\\.1|.*",
			"10\\.0\\.0\\.1|", "|10\\.0\\.0\\.1" })
	void deve_considerar_permissiva_a_lista_vazia_ou_com_qualquer_alternativa_que_casa_tudo(String lista) {
		assertThat(ProxiesConfiaveis.permissiva(lista)).isTrue();
	}

	@ParameterizedTest
	@ValueSource(strings = { "127\\.0\\.0\\.1", "10\\.0\\.0\\.1|10\\.0\\.0\\.2", "10\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}",
			"0:0:0:0:0:0:0:1|::1" })
	void deve_aceitar_a_lista_explicita(String lista) {
		assertThat(ProxiesConfiaveis.permissiva(lista)).isFalse();
	}

}
