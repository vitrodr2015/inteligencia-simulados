package br.simulado.etec.catalogo.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;

import br.simulado.etec.catalogo.dto.QuestaoAtualizacaoRequest;
import br.simulado.etec.catalogo.dto.QuestaoRequest;
import br.simulado.etec.catalogo.dto.QuestaoResponse;
import br.simulado.etec.shared.exception.ErrorResponse;
import br.simulado.etec.shared.paginacao.PaginaResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import jakarta.servlet.http.HttpServletRequest;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

/** Documentação OpenAPI da questão (ADMIN). Só o que o código devolve hoje: 200, 201, 400, 404 e 409. */
@Tag(name = "Questões (admin)", description = "Cadastro unitário, edição do rascunho e leitura de questão. Só ADMIN.")
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

	@Operation(summary = "Altera o rascunho de uma questão (só ADMIN)",
			description = "ATENÇÃO: é substituição total. Um PUT que omite o gabarito APAGA o gabarito gravado, e o mesmo vale para explicação, texto de apoio, grupo, imagem e dificuldade. Só altera o rascunho aberto: questão sem rascunho dá 409 (é preciso criar uma nova versão), e o status da prova não importa; editar a questão não altera a prova. numero e provaId são opcionais e não mudam. O subtópico só precisa estar ativo quando é trocado. A identidade da questão não muda: reenviar o conteúdo original pelo cadastro continua respondendo que ela já existe. Sem mudança de valor, nada é gravado.")
	@ApiResponse(responseCode = "200", description = "Rascunho alterado.",
			content = @Content(schema = @Schema(implementation = QuestaoResponse.class)))
	@ApiResponse(responseCode = "400", description = "Corpo inválido ou ilegível, id malformado, subtópico inexistente ou de outro programa (campos[subtopicoId]), ou CAMPO_IMUTAVEL (numero ou provaId diferentes do atual).",
			content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	@ApiResponse(responseCode = "404", description = "Questão não encontrada (QUESTAO_NAO_ENCONTRADA).",
			content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	@ApiResponse(responseCode = "409", description = "QUESTAO_SEM_RASCUNHO ou SUBTOPICO_INATIVO (só quando o subtópico é trocado).",
			content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	ResponseEntity<QuestaoResponse> atualizar(@Parameter(description = "Id público da questão (UUID).") UUID id,
			QuestaoAtualizacaoRequest dados);

	@Operation(summary = "Lista as questões (só ADMIN)",
			description = "Lista paginada. De cada questão vem só a versão mais recente: o rascunho aberto, se houver, senão a última publicada. Os filtros são opcionais e agem sobre essa versão. Um provaId ou subtopicoId que não existe não é erro: devolve a lista vazia. Ordem fixa por numero crescente. Página além do fim devolve 200 com a lista vazia.")
	@Parameter(name = "sort", in = ParameterIn.QUERY, description = "Só numero ou numero,asc (o padrão). Qualquer outro valor, inclusive numero,desc, ou mais de um sort, dá 400.")
	@ApiResponse(responseCode = "200", description = "Página de questões, com a mesma questão completa do GET por id.")
	@ApiResponse(responseCode = "400", description = "Parâmetro inválido: page, size, sort, provaId, subtopicoId ou status (ARQUIVADA inclusive).",
			content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	ResponseEntity<PaginaResponse<QuestaoResponse>> listar(
			@Parameter(description = "Filtro pelo id público da prova (UUID).") UUID provaId,
			@Parameter(description = "Filtro pelo id público do subtópico da versão exibida (UUID).") UUID subtopicoId,
			@Parameter(description = "Filtro pelo status da versão exibida: RASCUNHO ou PUBLICADA. A ARQUIVADA se lê por ?versao=n no GET por id.") String status,
			@Parameter(description = "Número da página, a partir de 0. Padrão 0. Negativo ou não numérico dá 400.") String page,
			@Parameter(description = "Itens por página. Padrão 20, máximo 50 (valor maior é reduzido a 50). Zero, negativo ou não numérico dá 400.") String size,
			@Parameter(hidden = true) HttpServletRequest requisicao);

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
