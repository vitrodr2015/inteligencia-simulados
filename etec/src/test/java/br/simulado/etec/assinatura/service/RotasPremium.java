package br.simulado.etec.assinatura.service;

import java.util.List;

/**
 * O REGISTRO das rotas premium (D140). Todo endpoint que passa a depender de um recurso do serviço de entitlements entra aqui,
 * no próprio PR, e o teste {@link RotasPremiumNegamTudoTest} troca a política por uma que nega tudo e exige 403
 * {@code ENTITLEMENT_NEGADO} em CADA rota registrada: um chamador que esquece a consulta responde 200 e quebra o build.
 *
 * <p>Hoje só há a rota de teste (nenhum endpoint premium existe no código de produção). A Tentativa (POST /tentativas)
 * acrescenta a linha dela quando passar a chamar {@code exigir(INICIAR_TENTATIVA, ...)}.
 */
final class RotasPremium {

	/** Uma rota premium: método, caminho e corpo (JSON), todos de teste. */
	record Rota(String metodo, String caminho, String corpo) {

		@Override
		public String toString() {
			return metodo + " " + caminho;
		}

	}

	static final List<Rota> TODAS = List.of(new Rota("POST", "/api/v1/teste/premium/consultando", "{}"));

	private RotasPremium() {
	}

}
