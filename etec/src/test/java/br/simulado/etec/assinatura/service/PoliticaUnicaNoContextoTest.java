package br.simulado.etec.assinatura.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * No contexto REAL da aplicação existe EXATAMENTE uma política de entitlements e ela é o stub (D142). Também prova que o
 * controller de teste NÃO vaza para os contextos normais.
 */
@SpringBootTest
class PoliticaUnicaNoContextoTest {

	@Autowired
	private List<PoliticaDeEntitlements> politicas;

	@Autowired
	private org.springframework.context.ApplicationContext contexto;

	@Test
	void deve_ter_exatamente_uma_politica_e_ela_e_o_stub() {
		assertThat(politicas).hasSize(1).first().isExactlyInstanceOf(PoliticaStubLiberaTudo.class);
	}

	@Test
	void deve_nao_ter_o_controller_de_teste_nos_contextos_normais() {
		assertThat(contexto.getBeansOfType(ControllerPremiumDeTeste.class)).isEmpty();
	}

}
