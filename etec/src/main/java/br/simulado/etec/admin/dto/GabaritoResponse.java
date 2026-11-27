package br.simulado.etec.admin.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import io.swagger.v3.oas.annotations.media.Schema;

/** Saída do gabarito em lote: quantas questões MUDARAM. Repetir o mesmo lote devolve 0 (D174). */
@JsonPropertyOrder({ "atualizadas" })
@JsonInclude(JsonInclude.Include.ALWAYS)
@Schema(description = "Resultado do gabarito em lote.")
public record GabaritoResponse(

		@Schema(description = "Quantas questões tiveram o gabarito realmente alterado. As que já tinham a mesma letra não contam.")
		int atualizadas) {

}
