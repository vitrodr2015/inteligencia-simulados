package br.simulado.etec.assinatura.service;

import java.util.EnumSet;
import java.util.Set;

import br.simulado.etec.assinatura.model.Decisao;
import br.simulado.etec.assinatura.model.MotivoDaNegacao;
import br.simulado.etec.shared.programa.Programa;
import br.simulado.etec.assinatura.model.Recurso;
import br.simulado.etec.shared.security.UsuarioAutenticado;
import br.simulado.etec.usuarios.model.Papel;

/**
 * O STUB da fase 1 (D140): libera os recursos que ELE CONHECE, no programa ETEC, para STUDENT e ADMIN. "Libera tudo" é
 * uma política EXPLÍCITA, com lista, e não a ausência de uma checagem: o que não está na lista é negado (recurso novo sem
 * decisão, programa que não é o ETEC) e o GUARDIAN não pode nada (D122). ANTES DE COBRAR, esta classe tem de ser trocada
 * por regras reais (assinatura, plano, cota): é uma troca de implementação, sem mudar quem chama.
 */
public class PoliticaStubLiberaTudo implements PoliticaDeEntitlements {

	/** Recursos que o stub libera. */
	static final Set<Recurso> LIBERADOS = EnumSet.of(Recurso.INICIAR_TENTATIVA);

	/** Recursos que o stub nega DE PROPÓSITO. Todo {@link Recurso} precisa estar numa das duas listas (um teste confere). */
	static final Set<Recurso> NEGADOS_DE_PROPOSITO = EnumSet.noneOf(Recurso.class);

	/** Os únicos programas que o stub conhece, até existir {@code exam_program}. */
	static final Set<Programa> PROGRAMAS_CONHECIDOS = Set.of(Programa.ETEC);

	private final Set<Recurso> liberados;

	public PoliticaStubLiberaTudo() {
		this(LIBERADOS);
	}

	/** A lista é injetável só para o teste provar que o que NÃO está nela é negado (hoje só existe um recurso). */
	PoliticaStubLiberaTudo(Set<Recurso> liberados) {
		this.liberados = EnumSet.copyOf(liberados.isEmpty() ? EnumSet.noneOf(Recurso.class) : liberados);
	}

	@Override
	public Decisao decidir(UsuarioAutenticado quem, Recurso recurso, Programa programa) {
		if (quem == null || quem.papel() == null || recurso == null || programa == null) {
			throw new IllegalArgumentException("Usuário, recurso e programa são obrigatórios na consulta de entitlements.");
		}
		if (quem.papel() == Papel.GUARDIAN) {
			return Decisao.negada(MotivoDaNegacao.PAPEL_SEM_ACESSO);
		}
		if (!PROGRAMAS_CONHECIDOS.contains(programa)) {
			return Decisao.negada(MotivoDaNegacao.PROGRAMA_DESCONHECIDO);
		}
		if (!liberados.contains(recurso)) {
			return Decisao.negada(MotivoDaNegacao.RECURSO_FORA_DA_LISTA);
		}
		return Decisao.liberada();
	}

}
