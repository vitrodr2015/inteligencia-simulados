package br.simulado.etec.shared.paginacao;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.query.EscapeCharacter;

import br.simulado.etec.shared.exception.CodigoErro;
import br.simulado.etec.shared.exception.InvalidRequestException;

/**
 * Filtros reutilizáveis para as listagens. Cada um devolve uma {@link Specification}; o recurso
 * combina as que precisa com {@link Specification#allOf(Specification[])}, e um filtro ausente
 * não restringe nada. É o molde para os filtros dos próximos recursos: um filtro novo é um método
 * novo aqui, e não uma explosão de métodos derivados no repositório (um por combinação de filtros).
 *
 * <p>Nunca há SQL montado por concatenação: o valor sempre vai como parâmetro da consulta.
 */
public final class Filtros {

	private Filtros() {
	}

	/** Não restringe nada (filtro ausente). */
	public static <T> Specification<T> semFiltro() {
		return Specification.unrestricted();
	}

	/**
	 * Texto contido no campo. Os caracteres {@code %}, {@code _} e a barra invertida digitados valem
	 * como texto LITERAL (escapados), nunca como curinga. A comparação respeita a collation da
	 * coluna: no MySQL com utf8mb4_unicode_ci ignora maiúsculas e acentos. Texto vazio ou em branco
	 * equivale a filtro ausente.
	 */
	public static <T> Specification<T> contem(String campo, String texto) {
		if (texto == null || texto.isBlank()) {
			return semFiltro();
		}
		EscapeCharacter escape = EscapeCharacter.DEFAULT;
		String padrao = "%" + escape.escape(texto.strip()) + "%";
		return (raiz, consulta, construtor) -> construtor.like(raiz.get(campo), padrao, escape.getEscapeCharacter());
	}

	/** Igualdade com o valor; {@code null} equivale a filtro ausente. */
	public static <T> Specification<T> igual(String campo, Object valor) {
		if (valor == null) {
			return semFiltro();
		}
		return (raiz, consulta, construtor) -> construtor.equal(raiz.get(campo), valor);
	}

	/** Lê um booleano estrito: só {@code true} ou {@code false}; ausente = {@code null}; outro valor = 400. */
	public static Boolean booleano(String parametro, String valor) {
		if (valor == null) {
			return null;
		}
		if ("true".equalsIgnoreCase(valor)) {
			return Boolean.TRUE;
		}
		if ("false".equalsIgnoreCase(valor)) {
			return Boolean.FALSE;
		}
		throw new InvalidRequestException(CodigoErro.PARAMETRO_INVALIDO,
				"O parâmetro '" + parametro + "' deve ser true ou false.");
	}

}
