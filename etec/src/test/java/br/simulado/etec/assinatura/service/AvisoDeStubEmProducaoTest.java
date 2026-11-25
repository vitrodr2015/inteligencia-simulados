package br.simulado.etec.assinatura.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.env.MockEnvironment;

import br.simulado.etec.assinatura.model.Decisao;
import br.simulado.etec.shared.config.Perfis;

@ExtendWith(OutputCaptureExtension.class)
class AvisoDeStubEmProducaoTest {

	private static MockEnvironment ambiente(String... perfis) {
		MockEnvironment ambiente = new MockEnvironment();
		ambiente.setActiveProfiles(perfis);
		return ambiente;
	}

	@Test
	void deve_avisar_uma_vez_em_producao_com_o_stub_sem_derrubar_a_subida(CapturedOutput saida) {
		new AvisoDeStubEmProducao(new PoliticaStubLiberaTudo(), ambiente(Perfis.PRODUCAO)).afterSingletonsInstantiated();

		assertThat(saida.getAll()).contains("Entitlements: a política STUB está ativa em produção");
		assertThat(saida.getAll().split("política STUB está ativa em produção", -1)).hasSize(2);
	}

	@Test
	void deve_ficar_quieto_fora_de_producao(CapturedOutput saida) {
		new AvisoDeStubEmProducao(new PoliticaStubLiberaTudo(), ambiente("local")).afterSingletonsInstantiated();

		assertThat(saida.getAll()).doesNotContain("Entitlements:");
	}

	@Test
	void deve_ficar_quieto_em_producao_quando_a_politica_nao_e_o_stub(CapturedOutput saida) {
		new AvisoDeStubEmProducao((quem, recurso, programa) -> Decisao.liberada(), ambiente(Perfis.PRODUCAO))
				.afterSingletonsInstantiated();

		assertThat(saida.getAll()).doesNotContain("Entitlements:");
	}

}
