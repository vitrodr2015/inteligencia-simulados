package br.simulado.etec.admin.dto;

import java.util.List;

import br.simulado.etec.catalogo.model.Prova;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Entrada de PUT /admin/provas/{id}/gabarito (D174): de 1 a {@value #MAXIMO_DE_ITENS} itens, sem numero repetido, cada um com
 * o gabarito de A a E. O lote é PARCIAL (as questões que não vêm ficam como estão) e não apaga gabarito.
 */
@Schema(description = "Gabarito em lote: só as questões citadas mudam.")
public record GabaritoRequest(

		@Schema(description = "De 1 a 100 itens, sem número repetido.")
		@NotNull(message = "A lista de itens é obrigatória.")
		@Size(min = 1, max = GabaritoRequest.MAXIMO_DE_ITENS, message = "O lote deve ter de 1 a 100 itens.")
		List<@NotNull(message = "O item não pode ser nulo.") @Valid ItemDoGabaritoRequest> itens) {

	/** Máximo de itens por chamada (D174). */
	public static final int MAXIMO_DE_ITENS = 100;

	/** Um item do lote. */
	@Schema(description = "O gabarito de uma questão, identificada pelo número dela na prova.")
	public record ItemDoGabaritoRequest(

			@Schema(description = "Número da questão na prova.")
			@NotNull(message = "O número é obrigatório.")
			@jakarta.validation.constraints.Min(value = 1, message = "O número deve ser de 1 até a quantidade de questões da prova.")
			@jakarta.validation.constraints.Max(value = Prova.QUANTIDADE_MAXIMA_DE_QUESTOES,
					message = "O número deve ser de 1 até a quantidade de questões da prova.")
			Integer numero,

			@Schema(description = "Letra do gabarito: A, B, C, D ou E. Obrigatória: este lote não apaga gabarito.")
			@NotNull(message = "O gabarito é obrigatório.")
			@jakarta.validation.constraints.Pattern(regexp = "[A-E]", message = "O gabarito deve ser A, B, C, D ou E.")
			String gabarito) {
	}

}
