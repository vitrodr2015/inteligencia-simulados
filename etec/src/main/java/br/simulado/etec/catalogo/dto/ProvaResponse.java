package br.simulado.etec.catalogo.dto;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import br.simulado.etec.catalogo.model.StatusProva;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Saída de prova. O {@code id} é o UUID público; o id numérico do banco nunca sai, e o programa (interno, D143) também não.
 * A ordem dos campos é a do contrato (seção 4.3) e um campo nulo sai SEMPRE como {@code null} (D72, D73).
 */
@JsonPropertyOrder({ "id", "titulo", "banca", "ano", "semestre", "duracaoMinutos", "quantidadeQuestoes", "fonteUrl", "status",
		"publicadaEm", "criadoEm" })
@JsonInclude(JsonInclude.Include.ALWAYS)
@Schema(description = "Prova.")
public record ProvaResponse(

		@Schema(description = "Identificador público (UUID).")
		UUID id,

		@Schema(description = "Título da prova.")
		String titulo,

		@Schema(description = "Banca, ou nulo se não informada.")
		String banca,

		@Schema(description = "Ano, ou nulo se não informado.")
		Integer ano,

		@Schema(description = "Semestre (1 ou 2), ou nulo se não informado.")
		Integer semestre,

		@Schema(description = "Duração em minutos.")
		int duracaoMinutos,

		@Schema(description = "Quantidade de questões declarada na criação.")
		int quantidadeQuestoes,

		@Schema(description = "Endereço da fonte, ou nulo se não informado.")
		String fonteUrl,

		@Schema(description = "RASCUNHO ou PUBLICADA. Toda prova nasce em RASCUNHO.")
		StatusProva status,

		@Schema(description = "Publicação, em UTC com três casas de milissegundos; nulo enquanto for RASCUNHO.")
		Instant publicadaEm,

		@Schema(description = "Criação, em UTC com três casas de milissegundos.")
		Instant criadoEm) {

}
