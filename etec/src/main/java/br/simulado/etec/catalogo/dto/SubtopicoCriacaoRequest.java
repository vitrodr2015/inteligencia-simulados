package br.simulado.etec.catalogo.dto;

import java.util.UUID;

import br.simulado.etec.shared.validacao.TamanhoEmPontosDeCodigo;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * Entrada do POST de subtópico (contrato, seção 4.2). Só {@code materiaId} e {@code nome} entram; qualquer
 * outro campo (id, ativo, datas) é ignorado (D27, D86). Os espaços das pontas do nome saem ANTES da validação
 * (D57, D104), então um nome só de espaços vira vazio e dá 400.
 */
@Schema(description = "Dados para criar um subtópico. Campos de saída (id, ativo, datas) enviados no corpo são ignorados.")
public record SubtopicoCriacaoRequest(

		@Schema(description = "Id público (UUID) da matéria do subtópico. Obrigatório; a matéria precisa existir e estar ativa.")
		@NotNull(message = "A matéria é obrigatória.")
		UUID materiaId,

		@Schema(description = "Nome do subtópico (2 a 100 caracteres, sem espaços nas pontas). Único dentro da matéria, sem distinguir maiúsculas e acentos.")
		@NotNull(message = "O nome é obrigatório.")
		@TamanhoEmPontosDeCodigo(min = 2, max = 100, message = "O nome deve ter de 2 a 100 caracteres.")
		String nome) {

	public SubtopicoCriacaoRequest {
		nome = (nome == null) ? null : nome.strip();
	}

}
