package br.simulado.etec.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import br.simulado.etec.admin.dto.ImportacaoRequest;
import br.simulado.etec.admin.dto.ImportacaoResponse;
import br.simulado.etec.catalogo.dto.QuestaoRequest;
import br.simulado.etec.catalogo.model.ProgramaDeProva;
import br.simulado.etec.catalogo.model.Prova;
import br.simulado.etec.catalogo.service.QuestaoService;
import br.simulado.etec.catalogo.service.QuestaoService.ResultadoDoCadastro;
import br.simulado.etec.catalogo.service.QuestaoService.Situacao;
import br.simulado.etec.shared.exception.ApiException;
import br.simulado.etec.shared.exception.CodigoErro;
import br.simulado.etec.shared.exception.ConflictException;
import br.simulado.etec.shared.exception.InvalidRequestException;
import br.simulado.etec.shared.exception.ResourceNotFoundException;
import br.simulado.etec.shared.programa.Programa;

@ExtendWith(MockitoExtension.class)
class ImportacaoServiceTest {

	@Mock
	private QuestaoService questoes;

	@InjectMocks
	private ImportacaoService servico;

	private final Prova prova = new Prova(new ProgramaDeProva(Programa.ETEC, "ETEC"), "P", null, null, null, 180, 100, null);

	private final UUID provaId = prova.getPublicId();

	private static QuestaoRequest item(int numero) {
		return new QuestaoRequest(numero, UUID.randomUUID(), null, null, "Enunciado " + numero, null, List.of(), null, null, null);
	}

	private ImportacaoRequest lote(QuestaoRequest... itens) {
		return new ImportacaoRequest(provaId, List.of(itens));
	}

	private static ResultadoDoCadastro resultado(Situacao situacao) {
		return new ResultadoDoCadastro(situacao, null);
	}

	private void provaTravada() {
		when(questoes.travarProvaEditavel(provaId)).thenReturn(prova);
	}

	private static Map<String, Object> detalhes(Throwable erro) {
		return ((ApiException) erro).getDetalhes();
	}

	@Test
	void deve_travar_a_prova_uma_unica_vez_e_cadastrar_os_itens_na_ordem_do_lote() {
		provaTravada();
		QuestaoRequest a = item(3);
		QuestaoRequest b = item(1);
		QuestaoRequest c = item(2);
		when(questoes.cadastrarNaProva(any(), any())).thenReturn(resultado(Situacao.CRIADA));

		servico.importar(lote(a, b, c));

		InOrder ordem = inOrder(questoes);
		ordem.verify(questoes).travarProvaEditavel(provaId);
		ordem.verify(questoes).cadastrarNaProva(prova, a);
		ordem.verify(questoes).cadastrarNaProva(prova, b);
		ordem.verify(questoes).cadastrarNaProva(prova, c);
		verify(questoes, times(1)).travarProvaEditavel(any());
		verify(questoes, never()).cadastrar(any(), any());
	}

	@Test
	void deve_contar_separadamente_as_criadas_e_as_que_ja_existiam() {
		provaTravada();
		when(questoes.cadastrarNaProva(any(), any())).thenReturn(resultado(Situacao.CRIADA), resultado(Situacao.JA_EXISTIA),
				resultado(Situacao.CRIADA), resultado(Situacao.JA_EXISTIA), resultado(Situacao.JA_EXISTIA));

		ImportacaoResponse resposta = servico.importar(lote(item(1), item(2), item(3), item(4), item(5)));

		assertThat(resposta).isEqualTo(new ImportacaoResponse(provaId, 2, 3));
	}

	@Test
	void deve_devolver_so_ignoradas_quando_tudo_ja_existia() {
		provaTravada();
		when(questoes.cadastrarNaProva(any(), any())).thenReturn(resultado(Situacao.JA_EXISTIA));

		assertThat(servico.importar(lote(item(1), item(2)))).isEqualTo(new ImportacaoResponse(provaId, 0, 2));
	}

	@Test
	void deve_recusar_numero_repetido_no_lote_apontando_cada_repeticao_sem_nenhuma_chamada_ao_cadastro() {
		assertThatThrownBy(() -> servico.importar(lote(item(1), item(2), item(1), item(3), item(2), item(1))))
				.isInstanceOfSatisfying(InvalidRequestException.class, erro -> assertThat(erro.getCampos()).extracting("campo")
						.containsExactly("questoes[2].numero", "questoes[4].numero", "questoes[5].numero"));
		verifyNoInteractions(questoes);
	}

	@Test
	void deve_dar_400_no_campo_provaid_quando_a_prova_nao_existe() {
		when(questoes.travarProvaEditavel(provaId))
				.thenThrow(new ResourceNotFoundException(CodigoErro.PROVA_NAO_ENCONTRADA, "Prova não encontrada."));

		assertThatThrownBy(() -> servico.importar(lote(item(1))))
				.isInstanceOfSatisfying(InvalidRequestException.class, erro -> {
					assertThat(erro.getCodigo()).isEqualTo(CodigoErro.VALIDACAO_FALHOU);
					assertThat(erro.getCampos()).singleElement().extracting("campo").isEqualTo("provaId");
				});
		verify(questoes, never()).cadastrarNaProva(any(), any());
	}

	@Test
	void deve_deixar_passar_o_409_da_prova_nao_editavel_sem_identificar_item() {
		ConflictException naoEditavel = new ConflictException(CodigoErro.PROVA_NAO_EDITAVEL, "não");
		when(questoes.travarProvaEditavel(provaId)).thenThrow(naoEditavel);

		assertThatThrownBy(() -> servico.importar(lote(item(1)))).isSameAs(naoEditavel);
		verify(questoes, never()).cadastrarNaProva(any(), any());
	}

	@Test
	void deve_prefixar_o_campo_com_o_indice_e_parar_na_hora_quando_um_item_e_invalido() {
		provaTravada();
		QuestaoRequest ruim = item(2);
		QuestaoRequest depois = item(3);
		when(questoes.cadastrarNaProva(any(), any())).thenReturn(resultado(Situacao.CRIADA));
		when(questoes.cadastrarNaProva(prova, ruim))
				.thenThrow(InvalidRequestException.campoInvalido("subtopicoId", "O subtópico informado não existe."));

		assertThatThrownBy(() -> servico.importar(lote(item(1), ruim, depois)))
				.isInstanceOfSatisfying(InvalidRequestException.class, erro -> assertThat(erro.getCampos()).singleElement()
						.extracting("campo").isEqualTo("questoes[1].subtopicoId"));
		verify(questoes, never()).cadastrarNaProva(prova, depois);
	}

	@Test
	void deve_acrescentar_indice_e_numero_ao_409_de_um_item_e_parar_na_hora() {
		provaTravada();
		QuestaoRequest inativo = item(9);
		QuestaoRequest depois = item(10);
		when(questoes.cadastrarNaProva(any(), any())).thenReturn(resultado(Situacao.CRIADA));
		when(questoes.cadastrarNaProva(prova, inativo))
				.thenThrow(new ConflictException(CodigoErro.SUBTOPICO_INATIVO, "O subtópico está desativado."));

		assertThatThrownBy(() -> servico.importar(lote(item(8), inativo, depois))).isInstanceOf(ConflictException.class)
				.satisfies(erro -> {
					assertThat(((ApiException) erro).getCodigo()).isEqualTo(CodigoErro.SUBTOPICO_INATIVO);
					assertThat(detalhes(erro)).containsEntry("indice", 1).containsEntry("numero", 9).hasSize(2);
				});
		verify(questoes, never()).cadastrarNaProva(prova, depois);
	}

	@Test
	void deve_juntar_todos_os_conflitos_de_conteudo_num_unico_409_no_fim() {
		provaTravada();
		when(questoes.cadastrarNaProva(any(), any())).thenReturn(resultado(Situacao.CRIADA), resultado(Situacao.CONFLITO),
				resultado(Situacao.JA_EXISTIA), resultado(Situacao.CONFLITO), resultado(Situacao.CRIADA));

		assertThatThrownBy(() -> servico.importar(lote(item(10), item(20), item(30), item(40), item(50))))
				.isInstanceOf(ConflictException.class).satisfies(erro -> {
					assertThat(((ApiException) erro).getCodigo()).isEqualTo(CodigoErro.QUESTAO_NUMERO_EM_CONFLITO);
					assertThat(detalhes(erro)).containsEntry("numerosEmConflito", List.of(20, 40))
							.containsEntry("indice", 1).containsEntry("numero", 20);
				});
		verify(questoes, times(5)).cadastrarNaProva(any(), any());
	}

	@Test
	void deve_interromper_com_o_erro_do_item_posterior_mesmo_havendo_conflito_antes() {
		provaTravada();
		QuestaoRequest ruim = item(2);
		when(questoes.cadastrarNaProva(any(), any())).thenReturn(resultado(Situacao.CONFLITO));
		when(questoes.cadastrarNaProva(prova, ruim))
				.thenThrow(InvalidRequestException.campoInvalido("numero", "acima da prova"));

		assertThatThrownBy(() -> servico.importar(lote(item(1), ruim)))
				.isInstanceOfSatisfying(InvalidRequestException.class, erro -> assertThat(erro.getCampos()).singleElement()
						.extracting("campo").isEqualTo("questoes[1].numero"));
	}

	@Test
	void deve_deixar_subir_um_erro_inesperado_sem_disfarcar() {
		provaTravada();
		IllegalStateException inesperado = new IllegalStateException("banco");
		when(questoes.cadastrarNaProva(any(), any())).thenThrow(inesperado);

		assertThatThrownBy(() -> servico.importar(lote(item(1), item(2)))).isSameAs(inesperado);
		verify(questoes, times(1)).cadastrarNaProva(any(), any());
	}

	@Test
	void deve_aceitar_100_itens() {
		provaTravada();
		when(questoes.cadastrarNaProva(any(), any())).thenReturn(resultado(Situacao.CRIADA));
		List<QuestaoRequest> cem = new ArrayList<>();
		for (int numero = 1; numero <= 100; numero++) {
			cem.add(item(numero));
		}

		assertThat(servico.importar(new ImportacaoRequest(provaId, cem)).questoesCriadas()).isEqualTo(100);
	}

}
