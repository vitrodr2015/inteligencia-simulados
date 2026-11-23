package br.simulado.etec.shared.security.limite;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;

class OrigemDaRequisicaoTest {

	private final OrigemDaRequisicao origens = new OrigemDaRequisicao();

	private String de(String enderecoDoSocket) {
		MockHttpServletRequest requisicao = new MockHttpServletRequest();
		requisicao.setRemoteAddr(enderecoDoSocket);
		return origens.de(requisicao);
	}

	@Test
	void deve_usar_o_endereco_ipv4_do_socket() {
		assertThat(de("203.0.113.7")).isEqualTo("v4:203.0.113.7");
	}

	@Test
	void deve_ignorar_o_x_forwarded_for_porque_so_o_socket_vale() {
		MockHttpServletRequest requisicao = new MockHttpServletRequest();
		requisicao.setRemoteAddr("203.0.113.7");
		requisicao.addHeader("X-Forwarded-For", "9.9.9.9");
		requisicao.addHeader("Forwarded", "for=8.8.8.8");
		requisicao.addHeader("X-Real-IP", "7.7.7.7");

		assertThat(origens.de(requisicao)).isEqualTo("v4:203.0.113.7");
	}

	@Test
	void deve_tratar_ipv4_mapeado_em_ipv6_como_o_ipv4() {
		assertThat(de("::ffff:203.0.113.7")).isEqualTo(de("203.0.113.7"));
		assertThat(de("0:0:0:0:0:ffff:cb00:7107")).isEqualTo("v4:203.0.113.7");
	}

	@Test
	void deve_agrupar_o_ipv6_pelo_prefixo_64() {
		String a = de("2001:db8:abcd:12::1");
		String b = de("2001:db8:abcd:12:ffff:ffff:ffff:ffff");

		assertThat(a).isEqualTo(b).isEqualTo("v6:20010db8abcd0012");
		assertThat(de("2001:db8:abcd:13::1")).as("outro /64").isNotEqualTo(a);
	}

	@Test
	void deve_ignorar_a_zona_do_ipv6() {
		assertThat(de("fe80::1%eth0")).isEqualTo(de("fe80::1"));
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = { "   ", "localhost", "a.b", "exemplo.com", "999.1.1.1.1", "1:2:3:4:5:6:7:8:9", "zzz:1" })
	void deve_devolver_desconhecida_sem_consultar_dns_para_o_que_nao_e_literal_de_ip(String endereco) {
		assertThat(de(endereco)).isEqualTo(OrigemDaRequisicao.DESCONHECIDA);
	}

}
