package br.simulado.etec.assinatura.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** A escolha da política: sem valor padrão, ausente ou desconhecida derruba a subida, e existe UM bean só (D142). */
class EntitlementsConfigTest {

	private final ApplicationContextRunner executor = new ApplicationContextRunner().withUserConfiguration(EntitlementsConfig.class);

	@Test
	void deve_criar_exatamente_uma_politica_o_stub_quando_a_propriedade_vale_stub() {
		executor.withPropertyValues("app.entitlements.politica=stub").run(contexto -> {
			assertThat(contexto).hasNotFailed();
			assertThat(contexto.getBeansOfType(PoliticaDeEntitlements.class)).hasSize(1);
			assertThat(contexto.getBean(PoliticaDeEntitlements.class)).isExactlyInstanceOf(PoliticaStubLiberaTudo.class);
		});
	}

	@Test
	void deve_aceitar_o_valor_em_qualquer_caixa() {
		executor.withPropertyValues("app.entitlements.politica=STUB").run(contexto -> assertThat(contexto).hasNotFailed());
	}

	@Test
	void deve_derrubar_a_subida_quando_a_propriedade_esta_ausente_sem_cair_num_libera_tudo_silencioso() {
		executor.run(contexto -> {
			assertThat(contexto).hasFailed();
			assertThat(contexto.getStartupFailure()).hasRootCauseMessage("app.entitlements.politica não está definida (valores aceitos: stub).");
		});
	}

	@Test
	void deve_derrubar_a_subida_quando_a_propriedade_esta_em_branco_ou_e_desconhecida() {
		executor.withPropertyValues("app.entitlements.politica=").run(contexto -> assertThat(contexto).hasFailed());
		executor.withPropertyValues("app.entitlements.politica=libera").run(contexto -> assertThat(contexto).hasFailed());
		executor.withPropertyValues("app.entitlements.politica=cobra-de-verdade").run(contexto -> assertThat(contexto).hasFailed());
	}

}
