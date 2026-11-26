package br.simulado.etec.admin.dto;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Saída da importação: SÓ contagens. A divergência de subtópico, gabarito, dificuldade ou explicação numa questão que já estava
 * gravada (fora do hash) não é detalhada aqui: o que está gravado se vê em GET /admin/questoes.
 */
@JsonPropertyOrder({ "provaId", "questoesCriadas", "questoesIgnoradas" })
@JsonInclude(JsonInclude.Include.ALWAYS)
@Schema(description = "Resultado da importação.")
public record ImportacaoResponse(

		@Schema(description = "Id público da prova-alvo.")
		UUID provaId,

		@Schema(description = "Questões gravadas nesta chamada.")
		int questoesCriadas,

		@Schema(description = "Questões que já existiam com o mesmo conteúdo no mesmo numero: nada foi gravado nelas.")
		int questoesIgnoradas) {

}
