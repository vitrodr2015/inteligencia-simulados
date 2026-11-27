package br.simulado.etec.admin.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;

import br.simulado.etec.admin.dto.GabaritoRequest;
import br.simulado.etec.admin.dto.GabaritoResponse;
import br.simulado.etec.shared.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

/** Documentação OpenAPI do gabarito em lote. Como nas demais, o 401, o 403, o 500 e o 503 não são repetidos por rota. */
@Tag(name = "Gabarito (admin)", description = "Gabarito em lote de uma prova. Só ADMIN.")
public interface GabaritoControllerDocs {

	@Operation(summary = "Aplica o gabarito em lote nas questões de uma prova (só ADMIN)",
			description = "Parcial: só as questões citadas mudam. De 1 a 100 itens, sem número repetido, gabarito de A a E obrigatório (não apaga gabarito). A prova precisa estar em RASCUNHO, e o gabarito vai para o rascunho aberto de cada questão, inclusive o de uma questão já publicada que ganhou versão nova. Tudo ou nada. atualizadas conta só as que mudaram: repetir o mesmo lote devolve 0. Primeiro vêm todos os números que não existem na prova (400); depois, todas as questões sem rascunho aberto (409).")
	@ApiResponse(responseCode = "200", description = "Lote aplicado.",
			content = @Content(schema = @Schema(implementation = GabaritoResponse.class)))
	@ApiResponse(responseCode = "400", description = "Corpo inválido ou ilegível, id malformado, número repetido no lote ou número que não existe na prova (campos[itens[i].numero]).",
			content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	@ApiResponse(responseCode = "404", description = "Prova não encontrada (PROVA_NAO_ENCONTRADA).",
			content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	@ApiResponse(responseCode = "409", description = "PROVA_NAO_EDITAVEL (prova que não é rascunho) ou QUESTAO_SEM_RASCUNHO (detalhes.numerosSemRascunho).",
			content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	ResponseEntity<GabaritoResponse> aplicar(@Parameter(description = "Id público da prova (UUID).") UUID id,
			GabaritoRequest lote);

}
