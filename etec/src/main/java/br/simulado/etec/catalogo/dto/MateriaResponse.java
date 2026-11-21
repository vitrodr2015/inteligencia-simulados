package br.simulado.etec.catalogo.dto;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Saída de matéria. O {@code id} é o UUID público; o id numérico do banco nunca sai.
 *
 * <p>Formato estável: a ordem dos campos é fixa ({@code @JsonPropertyOrder}) e um campo nulo (como
 * {@code descricao}) sai SEMPRE no JSON, como {@code null}, nunca omitido ({@code @JsonInclude(ALWAYS)}).
 * Um campo novo entra no fim da lista de ordem. As datas não usam {@code @JsonFormat}: o formato é
 * global, no {@code JacksonConfig}.
 */
@JsonPropertyOrder({ "id", "nome", "descricao", "ativo", "criadoEm", "atualizadoEm" })
@JsonInclude(JsonInclude.Include.ALWAYS)
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
