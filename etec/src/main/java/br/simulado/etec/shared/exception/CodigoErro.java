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
	ERRO_INTERNO,
	ORDENACAO_INVALIDA,

	// Campo de entrada que não muda depois de criado, enviado com valor diferente do atual (D48)
	CAMPO_IMUTAVEL,

	// Matéria
	MATERIA_NOME_DUPLICADO,
	MATERIA_NAO_ENCONTRADA,
	MATERIA_COM_SUBTOPICOS,
	MATERIA_INATIVA,

	// Subtópico
	SUBTOPICO_NOME_DUPLICADO,
	SUBTOPICO_NAO_ENCONTRADO,
	// Autenticação (Módulo 10; D120 para o 401 e o 403 da cadeia de segurança)
	NAO_AUTENTICADO,
	CREDENCIAIS_INVALIDAS,
	TOKEN_INVALIDO,
	ACESSO_NEGADO,
	EMAIL_DUPLICADO,
	// Firewall do Spring Security: URL malformada ou perigosa (D124)
	REQUISICAO_INVALIDA,
	// Limite de tentativas (Módulo 13A, D129)
	LIMITE_EXCEDIDO
}
