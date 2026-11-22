package br.simulado.etec.shared.security;

import br.simulado.etec.shared.exception.AcessoNegadoException;
import br.simulado.etec.shared.exception.CodigoErro;
import br.simulado.etec.shared.exception.InvalidRequestException;
import br.simulado.etec.usuarios.model.Papel;

/**
 * O que o chamador pode VER numa leitura do catálogo (D28, D47, D103): ADMIN vê tudo, STUDENT só o que está ativo
 * (subtópico, só o de matéria ativa). É a ÚNICA ponte entre o papel e a camada de serviço: o resolvedor do controller
 * calcula o escopo a partir do usuário do banco e o service o recebe por parâmetro, sem ler o contexto de segurança e
 * sem checagem de papel espalhada. Qualquer outro papel (GUARDIAN) falha FECHADO: a cadeia de segurança já o nega
 * antes, e aqui ele nunca recebe um escopo.
 */
public enum EscopoDeLeitura {

	TUDO, SO_ATIVOS;

	/** @throws AcessoNegadoException para um papel sem permissão de leitura (D122) */
	public static EscopoDeLeitura de(Papel papel) {
		return switch (papel) {
			case ADMIN -> TUDO;
			case STUDENT -> SO_ATIVOS;
			case GUARDIAN -> throw new AcessoNegadoException();
		};
	}

	/**
	 * O STUDENT que envia {@code ativo=false} recebe 400 PARAMETRO_INVALIDO (D28, D47): ele nunca vê inativos. Vale para
	 * qualquer listagem do catálogo.
	 */
	public void validarFiltroAtivo(Boolean ativo) {
		if (!veInativos() && Boolean.FALSE.equals(ativo)) {
			throw new InvalidRequestException(CodigoErro.PARAMETRO_INVALIDO,
					"O filtro ativo=false não está disponível para o seu perfil.");
		}
	}

	/** {@code true} se o chamador enxerga registros inativos. */
	public boolean veInativos() {
		return this == TUDO;
	}

}
