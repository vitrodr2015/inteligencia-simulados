package br.simulado.etec.assinatura.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import br.simulado.etec.assinatura.model.Decisao;
import br.simulado.etec.assinatura.model.MotivoDaNegacao;
import br.simulado.etec.shared.programa.Programa;
import br.simulado.etec.assinatura.model.Recurso;
import br.simulado.etec.shared.exception.CodigoErro;
import br.simulado.etec.shared.exception.EntitlementNegadoException;
import br.simulado.etec.shared.security.UsuarioAutenticado;
import br.simulado.etec.usuarios.model.Papel;

/** O ponto único: o chamador usa só o serviço, e trocar a política não muda o chamador (D140). */
class EntitlementsServiceTest {

	private static final PoliticaDeEntitlements NEGA_TUDO = (quem, recurso, programa) -> Decisao.negada(MotivoDaNegacao.RECURSO_FORA_DA_LISTA);

	private static final PoliticaDeEntitlements LIBERA_TUDO = (quem, recurso, programa) -> Decisao.liberada();

	private final UsuarioAutenticado aluno = new UsuarioAutenticado(UUID.randomUUID(), Papel.STUDENT);

	/** Um "chamador" qualquer: só conhece o serviço, nunca a política. */
	private static boolean chamadorPodeIniciar(EntitlementsService servico, UsuarioAutenticado quem) {
		try {
			servico.exigir(quem, Recurso.INICIAR_TENTATIVA, Programa.ETEC);
			return true;
		}
		catch (EntitlementNegadoException e) {
			return false;
		}
	}

	@Test
	void deve_mudar_a_resposta_do_mesmo_chamador_so_trocando_a_politica() {
		assertThat(chamadorPodeIniciar(new EntitlementsService(LIBERA_TUDO), aluno)).isTrue();
		assertThat(chamadorPodeIniciar(new EntitlementsService(NEGA_TUDO), aluno)).isFalse();
	}

	@Test
	void deve_lancar_o_403_com_codigo_mensagem_fixa_e_detalhes_so_de_recurso_e_programa_quando_a_politica_nega() {
		EntitlementsService servico = new EntitlementsService(
				(quem, recurso, programa) -> Decisao.negada(MotivoDaNegacao.PAPEL_SEM_ACESSO));

		assertThatThrownBy(() -> servico.exigir(aluno, Recurso.INICIAR_TENTATIVA, Programa.de("ENEM")))
				.isExactlyInstanceOf(EntitlementNegadoException.class).hasMessage("Este recurso não está disponível no seu plano.")
				.satisfies(e -> {
					EntitlementNegadoException ex = (EntitlementNegadoException) e;
					assertThat(ex.getCodigo()).isEqualTo(CodigoErro.ENTITLEMENT_NEGADO);
					assertThat(ex.getDetalhes()).containsOnlyKeys("recurso", "programa")
							.containsEntry("recurso", "INICIAR_TENTATIVA").containsEntry("programa", "ENEM");
					assertThat(ex.toString()).as("o motivo não vai para o cliente").doesNotContain("PAPEL_SEM_ACESSO");
				});
	}

	@Test
	void deve_dar_a_mesma_resposta_qualquer_que_seja_o_motivo_da_negacao() {
		for (MotivoDaNegacao motivo : MotivoDaNegacao.values()) {
			EntitlementsService servico = new EntitlementsService((quem, recurso, programa) -> Decisao.negada(motivo));

			assertThatThrownBy(() -> servico.exigir(aluno, Recurso.INICIAR_TENTATIVA, Programa.ETEC))
					.hasMessage(EntitlementNegadoException.MENSAGEM);
		}
	}

	@Test
	void deve_nao_lancar_quando_a_politica_libera_e_devolver_a_decisao_em_decidir() {
		EntitlementsService servico = new EntitlementsService(LIBERA_TUDO);

		assertThatCode(() -> servico.exigir(aluno, Recurso.INICIAR_TENTATIVA, Programa.ETEC)).doesNotThrowAnyException();
		assertThat(servico.decidir(aluno, Recurso.INICIAR_TENTATIVA, Programa.ETEC)).isEqualTo(Decisao.liberada());
	}

	@Test
	void deve_recusar_argumento_nulo_como_erro_de_programacao_e_nunca_liberar() {
		EntitlementsService servico = new EntitlementsService(LIBERA_TUDO);

		assertThatThrownBy(() -> servico.exigir(null, Recurso.INICIAR_TENTATIVA, Programa.ETEC)).isExactlyInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> servico.exigir(aluno, null, Programa.ETEC)).isExactlyInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> servico.exigir(aluno, Recurso.INICIAR_TENTATIVA, null)).isExactlyInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> servico.decidir(null, Recurso.INICIAR_TENTATIVA, Programa.ETEC)).isExactlyInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void deve_tratar_decisao_nula_da_politica_como_erro_e_nunca_como_liberado() {
		EntitlementsService servico = new EntitlementsService((quem, recurso, programa) -> null);

		assertThatThrownBy(() -> servico.exigir(aluno, Recurso.INICIAR_TENTATIVA, Programa.ETEC)).isExactlyInstanceOf(IllegalStateException.class);
	}

}
