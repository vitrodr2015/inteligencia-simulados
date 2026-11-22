package br.simulado.etec.catalogo.dto;

import java.util.UUID;

import br.simulado.etec.shared.validacao.TamanhoEmPontosDeCodigo;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * Entrada do PUT de subtópico: substituição total (D61) de um recurso que só tem o nome para trocar. O
 * {@code materiaId} é OPCIONAL: se vier, precisa ser igual ao atual (D48), e um valor diferente dá 400
 * CAMPO_IMUTAVEL (avaliado por último, D79). Os espaços das pontas do nome saem antes da validação (D57, D104).
 */
@Schema(description = "Dados para trocar o nome de um subtópico. A matéria não muda: materiaId é opcional e, se vier, tem de ser o atual.")
public record SubtopicoAtualizacaoRequest(

		@Schema(description = "Opcional. Se enviado, tem de ser igual ao id público da matéria atual; um valor diferente dá 400 (CAMPO_IMUTAVEL).")
		UUID materiaId,

		@Schema(description = "Novo nome (2 a 100 caracteres, sem espaços nas pontas). Único dentro da matéria, sem distinguir maiúsculas e acentos.")
		@NotNull(message = "O nome é obrigatório.")
		@TamanhoEmPontosDeCodigo(min = 2, max = 100, message = "O nome deve ter de 2 a 100 caracteres.")
		String nome) {

	public SubtopicoAtualizacaoRequest {
		nome = (nome == null) ? null : nome.strip();
	}

}
