package br.simulado.etec.catalogo.model;

/**
 * Tipo da questão (D145): campo INTERNO, invisível na API. Hoje só existe {@code MULTIPLA_ESCOLHA}. É atribuído num ponto só (o
 * construtor de {@link Questao}); não há interface nem {@code if} por tipo, porque ainda não há um segundo tipo.
 */
public enum TipoQuestao {
	MULTIPLA_ESCOLHA
}
