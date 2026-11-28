package br.simulado.etec.catalogo.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * O que a leitura de prova devolve: a visão completa do ADMIN ({@link ProvaResponse}) ou o resumo do aluno
 * ({@link ProvaDoAlunoResponse}), conforme o papel gravado no banco (D189, D190). São dois DTOs separados de propósito: campo que o
 * papel não pode ver não existe no JSON (D75).
 */
@Schema(description = "Prova: a visão do ADMIN (ProvaResponse) ou a do aluno (ProvaDoAlunoResponse), conforme o papel.",
		oneOf = { ProvaResponse.class, ProvaDoAlunoResponse.class })
public sealed interface ProvaDeLeitura permits ProvaResponse, ProvaDoAlunoResponse {

}
