package br.simulado.etec.catalogo.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;

import br.simulado.etec.catalogo.dto.ProvaRequest;
import br.simulado.etec.catalogo.dto.ProvaResponse;
import br.simulado.etec.shared.exception.ErrorResponse;
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
 * Documentação OpenAPI da prova. Só o que o código devolve hoje: 200, 201, 400, 404 e 409 (o 401, o 403, o 500 e o 503 não são
 * repetidos por rota). As rotas são só do ADMIN (D154). O controller implementa esta interface e fica só com a lógica.
 */
@Tag(name = "Provas", description = "Cadastro e consulta de provas do catálogo (só ADMIN nesta fase).")
public interface ProvaControllerDocs {

	@Operation(summary = "Cria uma prova (só ADMIN)",
			description = "A prova nasce em RASCUNHO, antes das questões. Banca, ano e semestre são opcionais; a unicidade por banca + ano + semestre só vale com os três preenchidos. O Location aponta para o novo recurso.")
	@ApiResponse(responseCode = "201", description = "Prova criada.",
			content = @Content(schema = @Schema(implementation = ProvaResponse.class)))
	@ApiResponse(responseCode = "400", description = "Corpo inválido ou ilegível.",
			content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	@ApiResponse(responseCode = "409", description = "Já existe uma prova com essa banca, ano e semestre (PROVA_DUPLICADA).",
			content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	ResponseEntity<ProvaResponse> criar(ProvaRequest dados);

	@Operation(summary = "Altera uma prova em rascunho (só ADMIN)",
			description = "Substituição total: o campo opcional omitido (banca, ano, semestre, fonteUrl) vira nulo. Só em RASCUNHO. A quantidadeQuestoes pode mudar, mas não pode ficar abaixo do maior número de questão já cadastrado. Status e datas enviados no corpo são ignorados. Sem mudança de valor, nada é gravado.")
	@ApiResponse(responseCode = "200", description = "Prova alterada.",
			content = @Content(schema = @Schema(implementation = ProvaResponse.class)))
	@ApiResponse(responseCode = "400", description = "Corpo inválido ou ilegível, ou id malformado.",
			content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	@ApiResponse(responseCode = "404", description = "Prova não encontrada (PROVA_NAO_ENCONTRADA).",
			content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	@ApiResponse(responseCode = "409", description = "PROVA_NAO_EDITAVEL (prova que não é rascunho), PROVA_DUPLICADA ou PROVA_COM_QUESTOES_ACIMA_DA_QUANTIDADE (detalhes.maiorNumero).",
			content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	ResponseEntity<ProvaResponse> atualizar(@Parameter(description = "Id público da prova (UUID).") UUID id, ProvaRequest dados);

	@Operation(summary = "Publica a prova e as questões dela (só ADMIN)",
			description = "Sem corpo. Publica a prova e, em cascata, o rascunho de todas as questões, numa transação só. A prova precisa estar em RASCUNHO (publicar de novo dá 409) e ter as questões de 1 a quantidadeQuestoes, cada uma com rascunho aberto, gabarito, subtópico ativo (de matéria ativa) do programa da prova e imagem de um host permitido na configuração atual. Havendo qualquer problema, nada é publicado, e detalhes.questoesPendentes traz TODOS, como {numero, motivo}, em ordem de número: QUESTAO_AUSENTE, QUESTAO_SEM_RASCUNHO, SEM_GABARITO, SUBTOPICO_INATIVO, SUBTOPICO_DE_OUTRO_PROGRAMA ou IMAGEM_NAO_PERMITIDA. Publicar não tem volta: depois, a prova não aceita edição, questão nova, importação nem gabarito em lote.")
	@ApiResponse(responseCode = "200", description = "Prova publicada, com publicadaEm preenchido.",
			content = @Content(schema = @Schema(implementation = ProvaResponse.class)))
	@ApiResponse(responseCode = "400", description = "Id malformado.",
			content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	@ApiResponse(responseCode = "404", description = "Prova não encontrada (PROVA_NAO_ENCONTRADA).",
			content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	@ApiResponse(responseCode = "409", description = "PROVA_NAO_EDITAVEL (não é rascunho, inclusive a já publicada) ou PROVA_COM_QUESTOES_PENDENTES (detalhes.questoesPendentes).",
			content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	ResponseEntity<ProvaResponse> publicar(@Parameter(description = "Id público da prova (UUID).") UUID id);

	@Operation(summary = "Apaga uma prova em rascunho (só ADMIN)",
			description = "Só vale em prova em RASCUNHO. Apaga a prova e todas as questões dela, numa transação só: ou tudo, ou nada. Não tem volta. Prova publicada não se apaga. Se alguma questão já foi publicada, nada é apagado, e detalhes.numerosPublicados traz os números (a API não produz esse estado numa prova em rascunho).")
	@ApiResponse(responseCode = "204", description = "Prova apagada.")
	@ApiResponse(responseCode = "400", description = "Id malformado.",
			content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	@ApiResponse(responseCode = "404", description = "Prova não encontrada (PROVA_NAO_ENCONTRADA).",
			content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	@ApiResponse(responseCode = "409", description = "PROVA_NAO_EDITAVEL (não está em rascunho) ou PROVA_COM_QUESTOES_PUBLICADAS (detalhes.numerosPublicados).",
			content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	ResponseEntity<Void> apagar(@Parameter(description = "Id público da prova (UUID).") UUID id);

	@Operation(summary = "Lista as provas (só ADMIN)",
			description = "Lista paginada, com filtros opcionais e ordenação. Página além do fim devolve 200 com a lista vazia. Com sort por ano, as provas sem ano ficam no fim na ordem decrescente (o padrão) e no início na crescente.")
	@Parameter(name = "sort", in = ParameterIn.QUERY, description = "Ordenação no formato campo,direção (asc ou desc). Campos aceitos: ano e semestre (o nome do campo diferencia maiúsculas). Padrão: ano,desc. Sem direção vale asc (intencional, D102). Um único sort por pedido: mais de um dá 400, e qualquer outro valor também.")
	@ApiResponse(responseCode = "200", description = "Página de provas.")
	@ApiResponse(responseCode = "400", description = "Parâmetro inválido: page, size, sort, ano, semestre ou status.",
			content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	ResponseEntity<PaginaResponse<ProvaResponse>> listar(
			@Parameter(description = "Filtro por ano, de 2000 a 2100.") String ano,
			@Parameter(description = "Filtro por semestre: 1 ou 2.") String semestre,
			@Parameter(description = "Filtro por status: RASCUNHO ou PUBLICADA (maiúsculas).") String status,
			@Parameter(description = "Número da página, a partir de 0. Padrão 0. Negativo ou não numérico dá 400.") String page,
			@Parameter(description = "Itens por página. Padrão 20, máximo 50 (valor maior é reduzido a 50). Zero, negativo ou não numérico dá 400.") String size,
			@Parameter(hidden = true) HttpServletRequest requisicao);

	@Operation(summary = "Busca uma prova pelo id (só ADMIN)")
	@ApiResponse(responseCode = "200", description = "Prova encontrada.",
			content = @Content(schema = @Schema(implementation = ProvaResponse.class)))
	@ApiResponse(responseCode = "400", description = "Id malformado.",
			content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	@ApiResponse(responseCode = "404", description = "Prova não encontrada (PROVA_NAO_ENCONTRADA).",
			content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	ResponseEntity<ProvaResponse> buscar(@Parameter(description = "Id público da prova (UUID).") UUID id);

}
