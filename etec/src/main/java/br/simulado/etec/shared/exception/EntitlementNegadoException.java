package br.simulado.etec.shared.exception;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * O usuário não tem este recurso no plano (403 ENTITLEMENT_NEGADO, D141). A mensagem é fixa e igual para qualquer motivo
 * (o motivo fica só no log e no teste). {@code detalhes} leva o recurso e o programa, para o front mostrar a oferta (D44).
 * Fica em {@code shared.exception} só porque o tratador global está aqui: guarda só textos, nunca os tipos de
 * {@code assinatura}, então o {@code shared} não passa a depender desse domínio.
 */
public class EntitlementNegadoException extends ApiException {

	public static final String MENSAGEM = "Este recurso não está disponível no seu plano.";

	public EntitlementNegadoException(String recurso, String programa) {
		super(CodigoErro.ENTITLEMENT_NEGADO, MENSAGEM, detalhes(recurso, programa));
	}

	private static Map<String, Object> detalhes(String recurso, String programa) {
		Map<String, Object> detalhes = new LinkedHashMap<>();
		detalhes.put("recurso", recurso);
		detalhes.put("programa", programa);
		return detalhes;
	}

}
