package br.simulado.etec.catalogo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Entrada de POST e PUT de matéria. Só {@code nome} e {@code descricao} entram: qualquer outro
 * campo enviado (id, ativo, datas) é ignorado (D27). Os espaços das pontas do nome são removidos
 * ANTES da validação, então um nome só de espaços vira vazio e dá 400.
 */
@Schema(description = "Dados de uma matéria. Campos de saída (id, ativo, datas) enviados no corpo são ignorados.")
public record MateriaRequest(

		@Schema(description = "Nome da matéria (2 a 100 caracteres, sem espaços nas pontas). Único, sem distinguir maiúsculas e acentos.")
		@NotNull(message = "O nome é obrigatório.")
		@Size(min = 2, max = 100, message = "O nome deve ter de 2 a 100 caracteres.")
		String nome,

		@Schema(description = "Descrição opcional, de até 255 caracteres.")
		@Size(max = 255, message = "A descrição deve ter no máximo 255 caracteres.")
		String descricao) {

	public MateriaRequest {
		nome = (nome == null) ? null : nome.strip();
	}

}
