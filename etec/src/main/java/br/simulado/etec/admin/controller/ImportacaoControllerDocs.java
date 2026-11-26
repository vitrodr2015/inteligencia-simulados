package br.simulado.etec.admin.controller;

import org.springframework.http.ResponseEntity;

import br.simulado.etec.admin.dto.ImportacaoRequest;
import br.simulado.etec.admin.dto.ImportacaoResponse;
import br.simulado.etec.shared.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

/** Documentação OpenAPI da importação. Só o que o código devolve hoje: 200, 400, 409 e 413. */
@Tag(name = "Importação (admin)", description = "Importação de questões em lote. Só ADMIN.")
public interface ImportacaoControllerDocs {

	@Operation(summary = "Importa questões em lote numa prova que já existe (só ADMIN)",
			description = "Tudo ou nada: se um item falha, nada é gravado. A importação não cria nem altera a prova, que precisa estar em RASCUNHO. Cada item tem os mesmos campos e regras do cadastro unitário. De 1 a 100 questões e corpo de até 2 MiB (2.097.152 bytes). Questão que já existe no numero com o mesmo conteúdo é ignorada e contada, mesmo que subtópico, gabarito, dificuldade ou explicação venham diferentes (o que está gravado se vê em GET /admin/questoes) e mesmo que o subtópico tenha sido desativado. Vale o primeiro item que falha, na ordem do lote, exceto os conflitos de conteúdo no numero, que saem todos num único 409 no fim.")
	@ApiResponse(responseCode = "200", description = "Lote processado: quantas foram criadas e quantas ignoradas (inclusive quando tudo já existia).",
			content = @Content(schema = @Schema(implementation = ImportacaoResponse.class)))
	@ApiResponse(responseCode = "400", description = "Corpo inválido ou ilegível (erro de tipo ou enum inválido não diz o item); campos em questoes[i].campo (índice a partir de 0); numero repetido no lote; provaId inexistente (campos[provaId]); numero acima da quantidade da prova ou subtópico inexistente.",
			content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	@ApiResponse(responseCode = "409", description = "PROVA_NAO_EDITAVEL; SUBTOPICO_INATIVO (detalhes.indice e detalhes.numero); QUESTAO_NUMERO_EM_CONFLITO (detalhes.numerosEmConflito com todos, e detalhes.indice e detalhes.numero do primeiro).",
			content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	@ApiResponse(responseCode = "413", description = "Corpo acima de 2 MiB (CORPO_MUITO_GRANDE): divida o lote.",
			content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	ResponseEntity<ImportacaoResponse> importar(ImportacaoRequest lote);

}
