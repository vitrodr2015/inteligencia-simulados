package br.simulado.etec.assinatura.model;

/**
 * Os recursos que passam pelo serviço de entitlements (D140). Só entra aqui o recurso que já tem um chamador real: cota de
 * simulados curtos, explicação da questão e histórico entram com o primeiro endpoint que os usa. Todo valor novo PRECISA de
 * uma decisão explícita na política atual (o teste que percorre {@code Recurso.values()} quebra o build se faltar): recurso
 * sem decisão é NEGADO.
 */
public enum Recurso {

	/** Começar uma tentativa de prova (POST /tentativas, D35). */
	INICIAR_TENTATIVA

}
