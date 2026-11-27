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

	// Prova (Sprint 2, fatia 2a; D149, D150)
	PROVA_NAO_ENCONTRADA,
	PROVA_DUPLICADA,
	// Prova que não é rascunho não aceita questões novas (Sprint 2, fatia 2b-i)
	PROVA_NAO_EDITAVEL,
	// Edição (fatia 4, passada 1; D170): a quantidadeQuestoes não pode ficar abaixo do maior numero já cadastrado
	PROVA_COM_QUESTOES_ACIMA_DA_QUANTIDADE,
	// Edição (D171, D174): só o rascunho aberto se altera; sem ele, é preciso criar uma nova versão
	QUESTAO_SEM_RASCUNHO,
	// Trava não obtida a tempo ou deadlock no banco (503 com Retry-After, D176)
	RECURSO_OCUPADO,

	// Questão (Sprint 2, fatia 2b-i; D153, D156, D146)
	QUESTAO_NAO_ENCONTRADA,
	QUESTAO_NUMERO_EM_CONFLITO,
	SUBTOPICO_INATIVO,
	SUBTOPICO_COM_QUESTOES,
	// Importação em lote: o corpo passa do limite de bytes (413)
	CORPO_MUITO_GRANDE,

	// Autenticação (Módulo 10; D120 para o 401 e o 403 da cadeia de segurança)
	NAO_AUTENTICADO,
	CREDENCIAIS_INVALIDAS,
	TOKEN_INVALIDO,
	ACESSO_NEGADO,
	EMAIL_DUPLICADO,
	// Firewall do Spring Security: URL malformada ou perigosa (D124)
	REQUISICAO_INVALIDA,
	// Limite de tentativas (Módulo 13A, D129)
	LIMITE_EXCEDIDO,
	// Troca de senha (Módulo 13B, D139): a senha atual não confere
	SENHA_ATUAL_INCORRETA,
	// Entitlements (Tarefa 12, D141): o recurso não está no plano do usuário
	ENTITLEMENT_NEGADO
}
