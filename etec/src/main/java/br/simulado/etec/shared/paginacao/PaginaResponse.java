package br.simulado.etec.shared.paginacao;

import java.util.List;
import java.util.function.Function;

import org.springframework.data.domain.Page;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Envelope de toda lista paginada da API (contrato, seção 1). A ordem dos campos no JSON é fixa.
 *
 * @param conteudo itens da página
 * @param pagina número da página pedida, a partir de 0
 * @param tamanho tamanho de página aplicado (já reduzido ao máximo, se for o caso), não a quantidade de itens
 * @param totalElementos total de itens que casam com os filtros, em todas as páginas
 * @param totalPaginas total de páginas para esse tamanho
 */
@JsonPropertyOrder({ "conteudo", "pagina", "tamanho", "totalElementos", "totalPaginas" })
@Schema(description = "Página de resultados.")
public record PaginaResponse<T>(
		@Schema(description = "Itens da página.") List<T> conteudo,
		@Schema(description = "Número da página, a partir de 0.") int pagina,
		@Schema(description = "Tamanho de página aplicado (no máximo 50).") int tamanho,
		@Schema(description = "Total de itens que casam com os filtros, em todas as páginas.") long totalElementos,
		@Schema(description = "Total de páginas para o tamanho aplicado.") int totalPaginas) {

	/** Converte uma página do Spring Data, mapeando cada entidade para o DTO de saída. */
	public static <E, T> PaginaResponse<T> de(Page<E> pagina, Function<E, T> conversor) {
		return new PaginaResponse<>(
				pagina.getContent().stream().map(conversor).toList(),
				pagina.getNumber(),
				pagina.getSize(),
				pagina.getTotalElements(),
				pagina.getTotalPages());
	}

}
