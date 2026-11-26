package br.simulado.etec.admin.dto;

import java.util.List;
import java.util.UUID;

import br.simulado.etec.catalogo.dto.QuestaoRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Entrada de POST /admin/importacoes: a prova-alvo, que já existe (a importação não cria nem altera prova), e de 1 a
 * {@value #MAXIMO_DE_QUESTOES} questões, cada uma com o MESMO record do cadastro unitário, validada pelas mesmas anotações. Um
 * erro de validação aponta o item pelo caminho {@code questoes[i].campo} (índice a partir de 0). Qualquer outro campo é ignorado.
 */
@Schema(description = "Lote de questões a importar numa prova que já existe.")
public record ImportacaoRequest(

		@Schema(description = "Id público da prova-alvo (UUID). A prova precisa existir e estar em RASCUNHO.")
		@NotNull(message = "A prova é obrigatória.")
		UUID provaId,

		@Schema(description = "De 1 a 100 questões, com os mesmos campos do cadastro unitário. Números sem repetição no lote.")
		@NotNull(message = "A lista de questões é obrigatória.")
		@Size(min = 1, max = ImportacaoRequest.MAXIMO_DE_QUESTOES, message = "O lote deve ter de 1 a 100 questões.")
		List<@NotNull(message = "A questão não pode ser nula.") @Valid QuestaoRequest> questoes) {

	/** Máximo de questões por chamada (D55). */
	public static final int MAXIMO_DE_QUESTOES = 100;

}
