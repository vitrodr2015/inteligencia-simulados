package br.simulado.etec.assinatura.model;

/** Por que a política negou. Serve só para log e teste: NUNCA vai para o cliente (a resposta é sempre a mesma, D141). */
public enum MotivoDaNegacao {

	/** O papel não tem acesso a nada (GUARDIAN na fase 1, D122). */
	PAPEL_SEM_ACESSO,

	/** O programa não é conhecido da política (ENEM e OAB, até existir {@code exam_program}). */
	PROGRAMA_DESCONHECIDO,

	/** O recurso não está na lista da política: negar por padrão. */
	RECURSO_FORA_DA_LISTA

}
