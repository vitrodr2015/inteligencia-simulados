package br.simulado.etec.assinatura.model;

/**
 * A resposta da política: liberada ou negada com um motivo. É um OBJETO (e não um booleano) para a porta ficar aberta: cota
 * restante e outros dados entram aqui sem mudar quem chama (D140).
 */
public record Decisao(boolean permitida, MotivoDaNegacao motivo) {

	public Decisao {
		if (permitida == (motivo != null)) {
			throw new IllegalArgumentException("Decisão liberada não tem motivo, e negada precisa de um.");
		}
	}

	public static Decisao liberada() {
		return new Decisao(true, null);
	}

	public static Decisao negada(MotivoDaNegacao motivo) {
		return new Decisao(false, motivo);
	}

}
