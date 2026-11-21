package br.simulado.etec.shared.paginacao;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import tools.jackson.databind.json.JsonMapper;

class PaginaResponseTest {

	private final JsonMapper json = JsonMapper.builder().build();

	@Test
	void deve_serializar_os_campos_na_ordem_do_contrato() {
		PaginaResponse<String> pagina = new PaginaResponse<>(List.of("a", "b"), 1, 20, 135, 7);

		assertThat(json.writeValueAsString(pagina))
				.isEqualTo("{\"conteudo\":[\"a\",\"b\"],\"pagina\":1,\"tamanho\":20,\"totalElementos\":135,\"totalPaginas\":7}");
	}

	@Test
	void deve_converter_a_pagina_do_spring_data_mapeando_cada_item() {
		PageImpl<Integer> origem = new PageImpl<>(List.of(1, 2, 3), PageRequest.of(2, 3), 10);

		PaginaResponse<String> resposta = PaginaResponse.de(origem, numero -> "item " + numero);

		assertThat(resposta.conteudo()).containsExactly("item 1", "item 2", "item 3");
		assertThat(resposta.pagina()).isEqualTo(2);
		assertThat(resposta.tamanho()).isEqualTo(3);
		assertThat(resposta.totalElementos()).isEqualTo(10);
		assertThat(resposta.totalPaginas()).isEqualTo(4);
	}

	@Test
	void deve_devolver_conteudo_vazio_e_totais_corretos_quando_a_pagina_esta_alem_do_fim() {
		PageImpl<Integer> alemDoFim = new PageImpl<>(List.of(), PageRequest.of(99, 20), 5);

		PaginaResponse<String> resposta = PaginaResponse.de(alemDoFim, String::valueOf);

		assertThat(resposta.conteudo()).isEmpty();
		assertThat(resposta.pagina()).isEqualTo(99);
		assertThat(resposta.totalElementos()).isEqualTo(5);
		assertThat(resposta.totalPaginas()).isEqualTo(1);
	}

}
