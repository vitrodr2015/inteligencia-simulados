package br.simulado.etec.assinatura.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import br.simulado.etec.assinatura.model.Decisao;
import br.simulado.etec.assinatura.model.Programa;
import br.simulado.etec.assinatura.model.Recurso;
import br.simulado.etec.shared.exception.EntitlementNegadoException;
import br.simulado.etec.shared.security.UsuarioAutenticado;

/**
 * O ponto ÚNICO de consulta de entitlements (D140). Todo endpoint premium passa por aqui, pelo serviço do próprio domínio
 * que o implementa: {@link #exigir} lança o 403 {@code ENTITLEMENT_NEGADO} (D141), e {@link #decidir} devolve a
 * {@link Decisao} para quem precisa decidir sem erro. Quem chama NUNCA conhece a política: ela é a peça substituível.
 *
 * <p>O log só diz o recurso, o programa e o motivo, nunca o usuário. Argumento nulo é erro de programação
 * ({@link IllegalArgumentException}) e nunca vira "permitido".
 */
@Service
public class EntitlementsService {

	private static final Logger LOG = LoggerFactory.getLogger(EntitlementsService.class);

	private final PoliticaDeEntitlements politica;

	public EntitlementsService(PoliticaDeEntitlements politica) {
		this.politica = politica;
	}

	/** A decisão da política para este usuário, recurso e programa. */
	public Decisao decidir(UsuarioAutenticado quem, Recurso recurso, Programa programa) {
		if (quem == null || recurso == null || programa == null) {
			throw new IllegalArgumentException("Usuário, recurso e programa são obrigatórios na consulta de entitlements.");
		}
		Decisao decisao = politica.decidir(quem, recurso, programa);
		if (decisao == null) {
			throw new IllegalStateException("A política de entitlements devolveu uma decisão nula.");
		}
		if (!decisao.permitida()) {
			LOG.debug("Entitlement negado: recurso {}, programa {}, motivo {}.", recurso, programa.codigo(), decisao.motivo());
		}
		return decisao;
	}

	/**
	 * @throws EntitlementNegadoException 403, quando a política nega (a resposta é a mesma para qualquer motivo)
	 */
	public void exigir(UsuarioAutenticado quem, Recurso recurso, Programa programa) {
		if (!decidir(quem, recurso, programa).permitida()) {
			throw new EntitlementNegadoException(recurso.name(), programa.codigo());
		}
	}

}
