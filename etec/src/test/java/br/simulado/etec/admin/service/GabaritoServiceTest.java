package br.simulado.etec.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import br.simulado.etec.admin.dto.GabaritoRequest;
import br.simulado.etec.admin.dto.GabaritoRequest.ItemDoGabaritoRequest;
import br.simulado.etec.catalogo.model.Prova;
import br.simulado.etec.catalogo.service.QuestaoService;
import br.simulado.etec.catalogo.service.QuestaoService.SituacaoDoGabarito;
import br.simulado.etec.shared.exception.CodigoErro;
import br.simulado.etec.shared.exception.ConflictException;
import br.simulado.etec.shared.exception.InvalidRequestException;
import br.simulado.etec.shared.exception.ResourceNotFoundException;

/** As regras do LOTE de gabarito (D174). A regra de editar uma questão é do catálogo e fica mockada aqui. */
@ExtendWith(MockitoExtension.class)
class GabaritoServiceTest {

	@Mock
	private QuestaoService questoes;

	@Mock
	private Prova prova;

	@InjectMocks
	private GabaritoService servico;

	private final UUID provaId = UUID.randomUUID();

	/** Um lote com os numeros dados, todos com o gabarito "A". */
	private static GabaritoRequest lote(Integer... numeros) {
		return new GabaritoRequest(Arrays.stream(numeros).map(n -> new ItemDoGabaritoRequest(n, "A")).toList());
	}

	private void travar() {
		when(questoes.travarProvaEditavel(provaId)).thenReturn(prova);
	}

	private void situacao(int numero, SituacaoDoGabarito situacao) {
		when(questoes.aplicarGabarito(prova, numero, "A")).thenReturn(situacao);
	}

	@Test
	void deve_travar_uma_vez_aplicar_na_ordem_do_lote_e_contar_so_as_alteradas() {
		travar();
		situacao(7, SituacaoDoGabarito.ALTERADO);
		situacao(2, SituacaoDoGabarito.SEM_MUDANCA);
		situacao(5, SituacaoDoGabarito.ALTERADO);

		assertThat(servico.aplicar(provaId, lote(7, 2, 5)).atualizadas()).isEqualTo(2);

		InOrder ordem = inOrder(questoes);
		ordem.verify(questoes).travarProvaEditavel(provaId);
		ordem.verify(questoes).aplicarGabarito(prova, 7, "A");
		ordem.verify(questoes).aplicarGabarito(prova, 2, "A");
		ordem.verify(questoes).aplicarGabarito(prova, 5, "A");
		verifyNoMoreInteractions(questoes);
	}

	@Test
	void deve_devolver_zero_quando_nada_muda() {
		travar();
		situacao(1, SituacaoDoGabarito.SEM_MUDANCA);

		assertThat(servico.aplicar(provaId, lote(1)).atualizadas()).isZero();
	}

	@Test
	void deve_apontar_cada_repeticao_sem_tocar_no_catalogo() {
		assertThatThrownBy(() -> servico.aplicar(provaId, lote(3, 4, 3, 4, 3)))
				.isInstanceOfSatisfying(InvalidRequestException.class, e -> {
					assertThat(e.getCodigo()).isEqualTo(CodigoErro.VALIDACAO_FALHOU);
					assertThat(e.getCampos()).extracting("campo")
							.containsExactly("itens[2].numero", "itens[3].numero", "itens[4].numero");
				});
		verifyNoInteractions(questoes);
	}

	@Test
	void deve_listar_todos_os_inexistentes_no_400_mesmo_havendo_questao_sem_rascunho_antes() {
		travar();
		situacao(1, SituacaoDoGabarito.SEM_RASCUNHO);
		situacao(8, SituacaoDoGabarito.QUESTAO_INEXISTENTE);
		situacao(2, SituacaoDoGabarito.ALTERADO);
		situacao(9, SituacaoDoGabarito.QUESTAO_INEXISTENTE);

		assertThatThrownBy(() -> servico.aplicar(provaId, lote(1, 8, 2, 9)))
				.isInstanceOfSatisfying(InvalidRequestException.class,
						e -> assertThat(e.getCampos()).extracting("campo").containsExactly("itens[1].numero", "itens[3].numero"));
	}

	@Test
	void deve_listar_todos_os_numeros_sem_rascunho_num_unico_409() {
		travar();
		situacao(4, SituacaoDoGabarito.SEM_RASCUNHO);
		situacao(1, SituacaoDoGabarito.ALTERADO);
		situacao(6, SituacaoDoGabarito.SEM_RASCUNHO);

		assertThatThrownBy(() -> servico.aplicar(provaId, lote(4, 1, 6)))
				.isInstanceOfSatisfying(ConflictException.class, e -> {
					assertThat(e.getCodigo()).isEqualTo(CodigoErro.QUESTAO_SEM_RASCUNHO);
					assertThat(e.getDetalhes()).containsEntry("numerosSemRascunho", List.of(4, 6)).hasSize(1);
				});
	}

	@Test
	void deve_deixar_passar_o_404_e_o_409_da_prova_sem_aplicar_nada() {
		when(questoes.travarProvaEditavel(provaId))
				.thenThrow(new ResourceNotFoundException(CodigoErro.PROVA_NAO_ENCONTRADA, "x"))
				.thenThrow(new ConflictException(CodigoErro.PROVA_NAO_EDITAVEL, "y"));

		assertThatThrownBy(() -> servico.aplicar(provaId, lote(1))).isInstanceOf(ResourceNotFoundException.class);
		assertThatThrownBy(() -> servico.aplicar(provaId, lote(1))).isInstanceOf(ConflictException.class);
		verify(questoes, never()).aplicarGabarito(any(), anyInt(), anyString());
	}

	@Test
	void deve_aceitar_100_itens() {
		travar();
		when(questoes.aplicarGabarito(any(), anyInt(), anyString())).thenReturn(SituacaoDoGabarito.ALTERADO);
		GabaritoRequest cem = new GabaritoRequest(
				IntStream.rangeClosed(1, 100).mapToObj(n -> new ItemDoGabaritoRequest(n, "B")).toList());

		assertThat(servico.aplicar(provaId, cem).atualizadas()).isEqualTo(100);
	}

}
