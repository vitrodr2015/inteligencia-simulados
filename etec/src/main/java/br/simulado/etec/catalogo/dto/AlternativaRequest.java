package br.simulado.etec.catalogo.dto;

import br.simulado.etec.catalogo.model.Alternativa;
import br.simulado.etec.shared.validacao.MarcacaoMinima;
import br.simulado.etec.shared.validacao.TamanhoEmPontosDeCodigo;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** Uma alternativa na entrada: a letra (A a E) e o texto (1 a 2000 caracteres, D155, com a marcação mínima, D12). */
@Schema(description = "Alternativa da questão.")
public record AlternativaRequest(

		@Schema(description = "Letra da alternativa: A, B, C, D ou E (maiúscula).")
		@NotNull(message = "A letra é obrigatória.")
		@Pattern(regexp = "[A-E]", message = "A letra deve ser A, B, C, D ou E.")
		String letra,

		@Schema(description = "Texto da alternativa, de 1 a 2000 caracteres, sem espaços nas pontas. Só as marcações b, u, i, sup, sub e br.")
		@NotNull(message = "O texto da alternativa é obrigatório.")
		@TamanhoEmPontosDeCodigo(min = 1, max = Alternativa.TEXTO_MAXIMO, message = "O texto da alternativa deve ter de 1 a 2000 caracteres.")
		@MarcacaoMinima(message = "O texto da alternativa só pode usar as marcações b, u, i, sup, sub e br.")
		String texto) {

	public AlternativaRequest {
		texto = (texto == null) ? null : texto.strip();
	}

}
