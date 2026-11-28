package br.simulado.etec.catalogo.dto;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A prova como o ALUNO a vê (D190). DTO próprio, e não o do ADMIN com campos escondidos: um campo novo na visão do ADMIN não chega
 * ao aluno sem que alguém o escreva aqui. Sem status, criadoEm, atualizadoEm nem questoesCadastradas. A ordem dos campos é a do
 * contrato (seção 4.3) e um campo nulo sai SEMPRE como {@code null} (D72, D73).
 */
@JsonPropertyOrder({ "id", "titulo", "banca", "ano", "semestre", "duracaoMinutos", "quantidadeQuestoes", "fonteUrl",
		"publicadaEm" })
@JsonInclude(JsonInclude.Include.ALWAYS)
@Schema(description = "Prova publicada, na visão do aluno.")
public record ProvaDoAlunoResponse(

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

		@Schema(description = "Quantidade de questões da prova.")
		int quantidadeQuestoes,

		@Schema(description = "Endereço da fonte, ou nulo se não informado.")
		String fonteUrl,

		@Schema(description = "Publicação, em UTC com três casas de milissegundos.")
		Instant publicadaEm) implements ProvaDeLeitura {

}
