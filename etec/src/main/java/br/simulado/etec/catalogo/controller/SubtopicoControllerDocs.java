package br.simulado.etec.catalogo.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;

import br.simulado.etec.catalogo.dto.SubtopicoAtualizacaoRequest;
import br.simulado.etec.catalogo.dto.SubtopicoCriacaoRequest;
import br.simulado.etec.catalogo.dto.SubtopicoResponse;
import br.simulado.etec.shared.exception.ErrorResponse;
import br.simulado.etec.shared.security.EscopoDeLeitura;
import br.simulado.etec.shared.paginacao.PaginaResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Documentação OpenAPI do subtópico. Só o que o código devolve hoje: 200, 201, 204, 400, 404 e 409 (sem 401 nem 403,
 * porque ainda não há autenticação). O controller implementa esta interface e fica só com a lógica.
 */
@Tag(name = "Subtópicos", description = "Cadastro de subtópicos do catálogo. Cada subtópico pertence a uma matéria.")
public interface SubtopicoControllerDocs {

	@Operation(summary = "Cria um subtópico",
			description = "Nasce ativo. A matéria (materiaId) é obrigatória, precisa existir (senão 400) e estar ativa (senão 409 MATERIA_INATIVA). "
					+ "O nome é único dentro da matéria. O Location aponta para o novo recurso.")
	@ApiResponse(responseCode = "201", description = "Subtópico criado.",
			content = @Content(schema = @Schema(implementation = SubtopicoResponse.class)))
	@ApiResponse(responseCode = "400", description = "Corpo inválido ou ilegível, ou matéria inexistente (materiaId).",
			content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	@ApiResponse(responseCode = "409",
			description = "Já existe um subtópico com esse nome na matéria (SUBTOPICO_NOME_DUPLICADO) ou a matéria está desativada (MATERIA_INATIVA).",
			content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	ResponseEntity<SubtopicoResponse> criar(SubtopicoCriacaoRequest dados);

	@Operation(summary = "Lista os subtópicos",
			description = "Lista paginada, com filtros opcionais e ordenação. Página além do fim devolve 200 com a lista vazia. O STUDENT só vê subtópicos ativos de matéria ativa e recebe 400 (PARAMETRO_INVALIDO) se enviar ativo=false; o ADMIN vê todos.")
	@Parameter(name = "sort", in = ParameterIn.QUERY, description = "Ordenação no formato campo,direção (asc ou desc). Campos aceitos: nome e criadoEm (o nome do campo diferencia maiúsculas). Padrão: nome,asc. Um único sort por pedido: mais de um dá 400, e qualquer outro valor também.")
	@ApiResponse(responseCode = "200", description = "Página de subtópicos.")
	@ApiResponse(responseCode = "400", description = "Parâmetro inválido: materiaId, ativo, page, size ou sort.",
			content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	ResponseEntity<PaginaResponse<SubtopicoResponse>> listar(
			@Parameter(description = "Filtro por matéria: id público (UUID). Um id sem matéria correspondente devolve lista vazia; malformado dá 400.") UUID materiaId,
			@Parameter(description = "Filtro por nome: contém o texto, sem distinguir maiúsculas e acentos. Os caracteres % e _ valem como texto. Em branco equivale a filtro ausente.") String nome,
			@Parameter(description = "Filtro por estado: true ou false, em qualquer caixa. Ausente devolve todos.") String ativo,
			@Parameter(description = "Número da página, a partir de 0. Padrão 0. Negativo ou não numérico dá 400.") String page,
			@Parameter(description = "Itens por página. Padrão 20, máximo 50 (valor maior é reduzido a 50). Zero, negativo ou não numérico dá 400.") String size,
			@Parameter(hidden = true) HttpServletRequest requisicao, @Parameter(hidden = true) EscopoDeLeitura escopo);

	@Operation(summary = "Busca um subtópico pelo id", description = "Para o STUDENT, subtópico inativo ou de matéria inativa dá o mesmo 404 do inexistente.")
	@ApiResponse(responseCode = "200", description = "Subtópico encontrado.",
			content = @Content(schema = @Schema(implementation = SubtopicoResponse.class)))
	@ApiResponse(responseCode = "400", description = "Id malformado.",
			content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	@ApiResponse(responseCode = "404", description = "Subtópico não encontrado (SUBTOPICO_NAO_ENCONTRADO).",
			content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	ResponseEntity<SubtopicoResponse> buscar(@Parameter(description = "Id público do subtópico (UUID).") UUID id,
			@Parameter(hidden = true) EscopoDeLeitura escopo);

	@Operation(summary = "Troca o nome do subtópico",
			description = "Substituição total. Permitido com a matéria desativada. A matéria não muda: materiaId é opcional e, se vier, "
					+ "tem de ser o atual (senão 400 CAMPO_IMUTAVEL, avaliado depois do 404 e do 409). Nunca altera o estado ativo.")
	@ApiResponse(responseCode = "200", description = "Subtópico atualizado.",
			content = @Content(schema = @Schema(implementation = SubtopicoResponse.class)))
	@ApiResponse(responseCode = "400",
			description = "Id malformado, corpo inválido ou ilegível, ou materiaId diferente do atual (CAMPO_IMUTAVEL).",
			content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	@ApiResponse(responseCode = "404", description = "Subtópico não encontrado (SUBTOPICO_NAO_ENCONTRADO).",
			content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	@ApiResponse(responseCode = "409", description = "Outro subtópico da mesma matéria já tem esse nome (SUBTOPICO_NOME_DUPLICADO).",
			content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	ResponseEntity<SubtopicoResponse> atualizar(@Parameter(description = "Id público do subtópico (UUID).") UUID id,
			SubtopicoAtualizacaoRequest dados);

	@Operation(summary = "Desativa o subtópico",
			description = "Idempotente: repetir devolve o mesmo resultado e não altera atualizadoEm. Permitido com a matéria desativada.")
	@ApiResponse(responseCode = "200", description = "Subtópico desativado.",
			content = @Content(schema = @Schema(implementation = SubtopicoResponse.class)))
	@ApiResponse(responseCode = "400", description = "Id malformado.",
			content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	@ApiResponse(responseCode = "404", description = "Subtópico não encontrado (SUBTOPICO_NAO_ENCONTRADO).",
			content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	ResponseEntity<SubtopicoResponse> desativar(@Parameter(description = "Id público do subtópico (UUID).") UUID id);

	@Operation(summary = "Ativa o subtópico",
			description = "Idempotente: um subtópico que já está ativo responde 200 sem efeito, mesmo com a matéria desativada. "
					+ "Ativar um subtópico inativo de matéria desativada dá 409 MATERIA_INATIVA.")
	@ApiResponse(responseCode = "200", description = "Subtópico ativo.",
			content = @Content(schema = @Schema(implementation = SubtopicoResponse.class)))
	@ApiResponse(responseCode = "400", description = "Id malformado.",
			content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	@ApiResponse(responseCode = "404", description = "Subtópico não encontrado (SUBTOPICO_NAO_ENCONTRADO).",
			content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	@ApiResponse(responseCode = "409", description = "A ativação de fato ativaria um subtópico inativo e a matéria está desativada (MATERIA_INATIVA).",
			content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	ResponseEntity<SubtopicoResponse> ativar(@Parameter(description = "Id público do subtópico (UUID).") UUID id);

	@Operation(summary = "Apaga o subtópico", description = "Atômico: uma instrução só. Um subtópico que tem questão, em qualquer estado, não pode ser apagado.")
	@ApiResponse(responseCode = "409", description = "O subtópico tem questões, em qualquer estado (SUBTOPICO_COM_QUESTOES).",
			content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	@ApiResponse(responseCode = "204", description = "Subtópico apagado.")
	@ApiResponse(responseCode = "400", description = "Id malformado.",
			content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	@ApiResponse(responseCode = "404", description = "Subtópico não encontrado (SUBTOPICO_NAO_ENCONTRADO).",
			content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	ResponseEntity<Void> apagar(@Parameter(description = "Id público do subtópico (UUID).") UUID id);

}
