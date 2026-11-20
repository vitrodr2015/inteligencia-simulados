package br.simulado.etec.shared.exception;

/**
 * Códigos estáveis de erro (contrato, D33). O front decide pelo código, nunca pelo texto da
 * mensagem. Os códigos de regras de negócio (por exemplo MATERIA_NOME_DUPLICADO) são
 * acrescentados junto com cada recurso.
 */
public enum CodigoErro {
	VALIDACAO_FALHOU,
	CORPO_ILEGIVEL,
	PARAMETRO_AUSENTE,
	PARAMETRO_INVALIDO,
	RECURSO_NAO_ENCONTRADO,
	METODO_NAO_PERMITIDO,
	FORMATO_NAO_ACEITO,
	TIPO_NAO_SUPORTADO,
	CONFLITO,
	ERRO_INTERNO
}
