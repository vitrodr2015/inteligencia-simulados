package br.simulado.etec.shared.exception;

/**
 * Limite de tentativas excedido (429 LIMITE_EXCEDIDO, D129). A mensagem é fixa e igual para qualquer escopo, e nunca diz
 * qual contador estourou nem se o e-mail existe; {@code retryAfterSegundos} vira o cabeçalho Retry-After.
 */
public class LimiteExcedidoException extends ApiException {

	public static final String MENSAGEM = "Muitas tentativas. Tente novamente mais tarde.";

	private final long retryAfterSegundos;

	public LimiteExcedidoException(long retryAfterSegundos) {
		super(CodigoErro.LIMITE_EXCEDIDO, MENSAGEM, null);
		this.retryAfterSegundos = Math.max(1, retryAfterSegundos);
	}

	/** Segundos até o contador expirar, arredondado para cima e nunca menor que 1. */
	public long getRetryAfterSegundos() {
		return retryAfterSegundos;
	}

}
