package br.simulado.etec.shared.persistencia;

import org.hibernate.exception.ConstraintViolationException;
import org.hibernate.exception.ConstraintViolationException.ConstraintKind;

/**
 * Diz QUAL restrição do banco falhou numa {@code DataIntegrityViolationException}, para o service traduzir cada
 * uma no erro certo (409 de nome duplicado, 409 de matéria com subtópicos...) e deixar as demais subirem como
 * 500, em vez de um {@code catch} genérico que trataria QUALQUER violação como a esperada.
 *
 * <p>Usa o tipo e o nome da restrição que o Hibernate extrai do erro do banco (no MySQL, os códigos 1062, 1451 e
 * 1452), e não o texto da mensagem.
 */
public final class Restricoes {

	private Restricoes() {
	}

	/** Violação de índice ou restrição ÚNICA. */
	public static boolean ehUnicidade(Throwable erro) {
		ConstraintViolationException violacao = procurar(erro);
		return violacao != null && violacao.getKind() == ConstraintKind.UNIQUE;
	}

	/**
	 * Violação de chave estrangeira da restrição de nome {@code nomeEsperado} (sem distinguir caixa). Se o banco
	 * não informar o nome da restrição, vale o tipo: a restrição é de chave estrangeira.
	 */
	public static boolean ehChaveEstrangeira(Throwable erro, String nomeEsperado) {
		ConstraintViolationException violacao = procurar(erro);
		if (violacao == null || violacao.getKind() != ConstraintKind.FOREIGN_KEY) {
			return false;
		}
		String nome = violacao.getConstraintName();
		return nome == null || nome.toLowerCase().contains(nomeEsperado.toLowerCase());
	}

	private static ConstraintViolationException procurar(Throwable erro) {
		for (Throwable atual = erro; atual != null; atual = atual.getCause()) {
			if (atual instanceof ConstraintViolationException violacao) {
				return violacao;
			}
			if (atual.getCause() == atual) {
				break;
			}
		}
		return null;
	}

}
