package br.simulado.etec.catalogo.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;

import br.simulado.etec.catalogo.dto.MateriaRequest;
import br.simulado.etec.catalogo.dto.MateriaResponse;
import br.simulado.etec.shared.exception.ErrorResponse;
import br.simulado.etec.shared.paginacao.PaginaResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Documentação OpenAPI da matéria. Só o que o código devolve hoje: 200, 201, 204, 400, 404 e 409.
 * O controller implementa esta interface e fica só com a lógica.
 */
@Tag(name = "Matérias", description = "Cadastro de matérias do catálogo.")
public interface MateriaControllerDocs {

	@Operation(summary = "Cria uma matéria", description = "Nasce ativa. O Location aponta para o novo recurso.")
	@ApiResponse(responseCode = "201", description = "Matéria criada.",
			content = @Content(schema = @Schema(implementation = MateriaResponse.class)))
	@ApiResponse(responseCode = "400", description = "Corpo inválido ou ilegível.",
			content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	@ApiResponse(responseCode = "409", description = "Já existe uma matéria com esse nome (MATERIA_NOME_DUPLICADO).",
			content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	ResponseEntity<MateriaResponse> criar(MateriaRequest dados);

	@Operation(summary = "Lista as matérias",
			description = "Lista paginada, com filtros opcionais e ordenação. Página além do fim devolve 200 com a lista vazia.")
	@ApiResponse(responseCode = "200", description = "Página de matérias.")
	@ApiResponse(responseCode = "400", description = "Parâmetro inválido: page, size, sort ou ativo.",
			content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	ResponseEntity<PaginaResponse<MateriaResponse>> listar(
			@Parameter(description = "Filtro por nome: contém o texto, sem distinguir maiúsculas e acentos. Os caracteres % e _ valem como texto.") String nome,
			@Parameter(description = "Filtro por estado: true ou false. Ausente devolve todas.") String ativo,
			@Parameter(description = "Número da página, a partir de 0. Padrão 0. Negativo ou não numérico dá 400.") String page,
			@Parameter(description = "Itens por página. Padrão 20, máximo 50 (valor maior é reduzido a 50). Zero, negativo ou não numérico dá 400.") String size,
			@Parameter(description = "Ordenação no formato campo,direção (asc ou desc). Campos aceitos: nome e criadoEm. Padrão: nome,asc. Qualquer outro valor dá 400.") String sort);

	@Operation(summary = "Busca uma matéria pelo id")
	@ApiResponse(responseCode = "200", description = "Matéria encontrada.",
			content = @Content(schema = @Schema(implementation = MateriaResponse.class)))
	@ApiResponse(responseCode = "400", description = "Id malformado.",
			content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	@ApiResponse(responseCode = "404", description = "Matéria não encontrada (MATERIA_NAO_ENCONTRADA).",
			content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	ResponseEntity<MateriaResponse> buscar(@Parameter(description = "Id público da matéria (UUID).") UUID id);

	@Operation(summary = "Troca nome e descrição", description = "Nunca altera o estado ativo.")
	@ApiResponse(responseCode = "200", description = "Matéria atualizada.",
			content = @Content(schema = @Schema(implementation = MateriaResponse.class)))
	@ApiResponse(responseCode = "400", description = "Id malformado, corpo inválido ou ilegível.",
			content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	@ApiResponse(responseCode = "404", description = "Matéria não encontrada (MATERIA_NAO_ENCONTRADA).",
			content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	@ApiResponse(responseCode = "409", description = "Outra matéria já tem esse nome (MATERIA_NOME_DUPLICADO).",
			content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	ResponseEntity<MateriaResponse> atualizar(@Parameter(description = "Id público da matéria (UUID).") UUID id,
			MateriaRequest dados);

	@Operation(summary = "Desativa a matéria", description = "Idempotente: repetir devolve o mesmo resultado.")
	@ApiResponse(responseCode = "200", description = "Matéria desativada.",
			content = @Content(schema = @Schema(implementation = MateriaResponse.class)))
	@ApiResponse(responseCode = "400", description = "Id malformado.",
			content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	@ApiResponse(responseCode = "404", description = "Matéria não encontrada (MATERIA_NAO_ENCONTRADA).",
			content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	ResponseEntity<MateriaResponse> desativar(@Parameter(description = "Id público da matéria (UUID).") UUID id);

	@Operation(summary = "Ativa a matéria", description = "Idempotente: repetir devolve o mesmo resultado.")
	@ApiResponse(responseCode = "200", description = "Matéria ativada.",
			content = @Content(schema = @Schema(implementation = MateriaResponse.class)))
	@ApiResponse(responseCode = "400", description = "Id malformado.",
			content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	@ApiResponse(responseCode = "404", description = "Matéria não encontrada (MATERIA_NAO_ENCONTRADA).",
			content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	ResponseEntity<MateriaResponse> ativar(@Parameter(description = "Id público da matéria (UUID).") UUID id);

	@Operation(summary = "Apaga a matéria", description = "Atômico: uma instrução só.")
	@ApiResponse(responseCode = "204", description = "Matéria apagada.")
	@ApiResponse(responseCode = "400", description = "Id malformado.",
			content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	@ApiResponse(responseCode = "404", description = "Matéria não encontrada (MATERIA_NAO_ENCONTRADA).",
			content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	ResponseEntity<Void> apagar(@Parameter(description = "Id público da matéria (UUID).") UUID id);

}
