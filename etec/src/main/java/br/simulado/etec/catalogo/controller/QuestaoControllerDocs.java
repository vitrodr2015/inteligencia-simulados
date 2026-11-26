package br.simulado.etec.catalogo.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;

import br.simulado.etec.catalogo.dto.QuestaoRequest;
import br.simulado.etec.catalogo.dto.QuestaoResponse;
import br.simulado.etec.shared.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

/** Documentação OpenAPI da questão (ADMIN). Só o que o código devolve hoje: 200, 201, 400, 404 e 409. */
@Tag(name = "Questões (admin)", description = "Cadastro unitário e leitura de questão. Só ADMIN.")
public interface QuestaoControllerDocs {

	@Operation(summary = "Cadastra uma questão numa prova (só ADMIN)",
			description = "A questão nasce em RASCUNHO, na versão 1. A prova precisa estar em RASCUNHO. O número vai de 1 até a quantidadeQuestoes da prova. Se o número já tem a MESMA questão (mesmo conteúdo do enunciado, apoio, imagem e alternativas), responde 200 com a questão já gravada (idempotente); com conteúdo diferente, 409 QUESTAO_NUMERO_EM_CONFLITO com detalhes.numerosEmConflito. Subtópico, gabarito, dificuldade e explicação não entram na comparação: se divergem, a resposta mostra o que está gravado.")
	@ApiResponse(responseCode = "201", description = "Questão criada. O Location aponta para GET /admin/questoes/{id}.",
			content = @Content(schema = @Schema(implementation = QuestaoResponse.class)))
	@ApiResponse(responseCode = "200", description = "O número já tinha a mesma questão: devolve a que está gravada.",
			content = @Content(schema = @Schema(implementation = QuestaoResponse.class)))
	@ApiResponse(responseCode = "400", description = "Corpo inválido ou ilegível, id malformado, número acima da quantidade da prova ou subtópico inexistente (campos[subtopicoId]).",
			content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	@ApiResponse(responseCode = "404", description = "Prova não encontrada (PROVA_NAO_ENCONTRADA).",
			content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	@ApiResponse(responseCode = "409", description = "PROVA_NAO_EDITAVEL (prova que não é rascunho), SUBTOPICO_INATIVO ou QUESTAO_NUMERO_EM_CONFLITO.",
			content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	ResponseEntity<QuestaoResponse> cadastrar(@Parameter(description = "Id público da prova (UUID).") UUID id,
			QuestaoRequest dados);

	@Operation(summary = "Busca uma questão pelo id (só ADMIN)",
			description = "Sem versao, devolve o rascunho aberto, se houver, senão a última publicada. Com ?versao=n, devolve essa versão, inclusive uma ARQUIVADA.")
	@ApiResponse(responseCode = "200", description = "Questão encontrada.",
			content = @Content(schema = @Schema(implementation = QuestaoResponse.class)))
	@ApiResponse(responseCode = "400", description = "Id malformado ou versao inválida.",
			content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	@ApiResponse(responseCode = "404", description = "Questão ou versão inexistente (QUESTAO_NAO_ENCONTRADA).",
			content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	ResponseEntity<QuestaoResponse> buscar(@Parameter(description = "Id público da questão (UUID).") UUID id,
			@Parameter(description = "Número da versão, a partir de 1. Ausente: o rascunho aberto ou a última publicada.") String versao);

}
