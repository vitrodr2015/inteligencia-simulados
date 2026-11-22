package br.simulado.etec.catalogo.dto;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Saída de subtópico (contrato, seção 4.2). {@code id} e {@code materiaId} são UUIDs públicos; os ids numéricos
 * do banco nunca saem. A ordem dos campos é fixa e um campo novo entra no fim (D72); nulos saem sempre (D73).
 */
@JsonPropertyOrder({ "id", "materiaId", "nome", "ativo", "criadoEm", "atualizadoEm" })
@JsonInclude(JsonInclude.Include.ALWAYS)
@Schema(description = "Subtópico.")
public record SubtopicoResponse(

		@Schema(description = "Identificador público (UUID).")
		UUID id,

		@Schema(description = "Identificador público (UUID) da matéria do subtópico.")
		UUID materiaId,

		@Schema(description = "Nome do subtópico.")
		String nome,

		@Schema(description = "Nasce true; só muda por disable/enable.")
		boolean ativo,

		@Schema(description = "Criação, em UTC com três casas de milissegundos.")
		Instant criadoEm,

		@Schema(description = "Última alteração, em UTC com três casas de milissegundos.")
		Instant atualizadoEm) {

}
