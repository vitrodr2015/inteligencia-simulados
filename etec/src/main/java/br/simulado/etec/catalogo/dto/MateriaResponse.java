package br.simulado.etec.catalogo.dto;

import java.time.Instant;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

/** Saída de matéria. O {@code id} é o UUID público; o id numérico do banco nunca sai. */
@Schema(description = "Matéria.")
public record MateriaResponse(

		@Schema(description = "Identificador público (UUID).")
		UUID id,

		@Schema(description = "Nome da matéria.")
		String nome,

		@Schema(description = "Descrição, ou nulo se não informada.")
		String descricao,

		@Schema(description = "Nasce true; só muda por disable/enable.")
		boolean ativo,

		@Schema(description = "Criação, em UTC com três casas de milissegundos.")
		Instant criadoEm,

		@Schema(description = "Última alteração, em UTC com três casas de milissegundos.")
		Instant atualizadoEm) {

}
