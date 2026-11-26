package br.simulado.etec.catalogo.dto;

import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import br.simulado.etec.catalogo.model.Dificuldade;
import br.simulado.etec.catalogo.model.StatusQuestao;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Saída de questão para o ADMIN (contrato, seção 5), na ordem do contrato. O {@code id} é o UUID público da questão (estável entre
 * versões); o id numérico, o programa e o tipo (internos) nunca saem. Campo nulo sai sempre como {@code null} (D73).
 */
@JsonPropertyOrder({ "id", "versao", "provaId", "numero", "subtopicoId", "textoApoio", "grupoApoio", "enunciado", "imagemUrl",
		"alternativas", "gabarito", "dificuldade", "explicacao", "status" })
@JsonInclude(JsonInclude.Include.ALWAYS)
@Schema(description = "Questão, na visão do ADMIN.")
public record QuestaoResponse(

		@Schema(description = "Identificador público da questão (UUID), igual em todas as versões.")
		UUID id,

		@Schema(description = "Número da versão: 1, 2, ...")
		int versao,

		@Schema(description = "Id público da prova.")
		UUID provaId,

		@Schema(description = "Número da questão na prova.")
		int numero,

		@Schema(description = "Id público do subtópico (da versão).")
		UUID subtopicoId,

		@Schema(description = "Texto de apoio, ou nulo.")
		String textoApoio,

		@Schema(description = "Grupo do texto de apoio, ou nulo.")
		Integer grupoApoio,

		@Schema(description = "Enunciado.")
		String enunciado,

		@Schema(description = "Endereço da imagem, ou nulo.")
		String imagemUrl,

		@Schema(description = "As 5 alternativas, de A a E.")
		List<AlternativaResponse> alternativas,

		@Schema(description = "Letra do gabarito, ou nulo se ainda não aplicado.")
		String gabarito,

		@Schema(description = "Dificuldade, ou nulo.")
		Dificuldade dificuldade,

		@Schema(description = "Explicação, ou nula.")
		String explicacao,

		@Schema(description = "RASCUNHO, PUBLICADA ou ARQUIVADA. Toda questão nasce em RASCUNHO.")
		StatusQuestao status) {

	/** Uma alternativa na saída. */
	@JsonPropertyOrder({ "letra", "texto" })
	@Schema(description = "Alternativa.")
	public record AlternativaResponse(String letra, String texto) {
	}

}
