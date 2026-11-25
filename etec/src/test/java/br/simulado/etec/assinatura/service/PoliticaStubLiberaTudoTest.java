package br.simulado.etec.assinatura.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.EnumSet;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import br.simulado.etec.assinatura.model.Decisao;
import br.simulado.etec.assinatura.model.MotivoDaNegacao;
import br.simulado.etec.shared.programa.Programa;
import br.simulado.etec.assinatura.model.Recurso;
import br.simulado.etec.shared.security.UsuarioAutenticado;
import br.simulado.etec.usuarios.model.Papel;

/** O stub é uma política EXPLÍCITA: lista de recursos, lista de programas e papel (D140). O que não está na lista é negado. */
class PoliticaStubLiberaTudoTest {

	private final PoliticaStubLiberaTudo stub = new PoliticaStubLiberaTudo();

	private static UsuarioAutenticado como(Papel papel) {
		return new UsuarioAutenticado(UUID.randomUUID(), papel);
	}

	@ParameterizedTest
	@EnumSource(value = Papel.class, names = { "STUDENT", "ADMIN" })
	void deve_liberar_iniciar_tentativa_no_programa_etec_para_student_e_admin(Papel papel) {
		assertThat(stub.decidir(como(papel), Recurso.INICIAR_TENTATIVA, Programa.ETEC)).isEqualTo(Decisao.liberada());
	}

	@Test
	void deve_negar_o_guardian_em_qualquer_recurso_e_programa() {
		for (Recurso recurso : Recurso.values()) {
			assertThat(stub.decidir(como(Papel.GUARDIAN), recurso, Programa.ETEC))
					.isEqualTo(Decisao.negada(MotivoDaNegacao.PAPEL_SEM_ACESSO));
		}
	}

	@Test
	void deve_negar_programa_desconhecido_ate_existir_exam_program() {
		assertThat(stub.decidir(como(Papel.STUDENT), Recurso.INICIAR_TENTATIVA, Programa.de("ENEM")))
				.isEqualTo(Decisao.negada(MotivoDaNegacao.PROGRAMA_DESCONHECIDO));
		assertThat(stub.decidir(como(Papel.ADMIN), Recurso.INICIAR_TENTATIVA, Programa.de("OAB")))
				.isEqualTo(Decisao.negada(MotivoDaNegacao.PROGRAMA_DESCONHECIDO));
	}

	@Test
	void deve_negar_o_recurso_que_nao_esta_na_lista_do_stub() {
		PoliticaStubLiberaTudo semNada = new PoliticaStubLiberaTudo(EnumSet.noneOf(Recurso.class));

		assertThat(semNada.decidir(como(Papel.STUDENT), Recurso.INICIAR_TENTATIVA, Programa.ETEC))
				.isEqualTo(Decisao.negada(MotivoDaNegacao.RECURSO_FORA_DA_LISTA));
	}

	@Test
	void deve_exigir_uma_decisao_explicita_para_todo_recurso_liberado_ou_negado_de_proposito_e_nunca_nos_dois() {
		for (Recurso recurso : Recurso.values()) {
			boolean liberado = PoliticaStubLiberaTudo.LIBERADOS.contains(recurso);
			boolean negado = PoliticaStubLiberaTudo.NEGADOS_DE_PROPOSITO.contains(recurso);
			assertThat(liberado ^ negado)
					.as("o recurso %s precisa estar em LIBERADOS ou em NEGADOS_DE_PROPOSITO (e só em um)", recurso).isTrue();
		}
	}

	@Test
	void deve_negar_o_que_o_stub_nega_de_proposito() {
		for (Recurso recurso : PoliticaStubLiberaTudo.NEGADOS_DE_PROPOSITO) {
			assertThat(stub.decidir(como(Papel.ADMIN), recurso, Programa.ETEC).permitida()).isFalse();
		}
	}

	@Test
	void deve_recusar_argumento_nulo_como_erro_de_programacao_e_nunca_liberar() {
		assertThatThrownBy(() -> stub.decidir(null, Recurso.INICIAR_TENTATIVA, Programa.ETEC))
				.isExactlyInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> stub.decidir(como(Papel.STUDENT), null, Programa.ETEC))
				.isExactlyInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> stub.decidir(como(Papel.STUDENT), Recurso.INICIAR_TENTATIVA, null))
				.isExactlyInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> stub.decidir(new UsuarioAutenticado(UUID.randomUUID(), null), Recurso.INICIAR_TENTATIVA,
				Programa.ETEC)).isExactlyInstanceOf(IllegalArgumentException.class);
	}

}
