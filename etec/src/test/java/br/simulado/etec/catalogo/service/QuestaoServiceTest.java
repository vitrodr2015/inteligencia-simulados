package br.simulado.etec.catalogo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import br.simulado.etec.catalogo.dto.AlternativaRequest;
import br.simulado.etec.catalogo.dto.QuestaoRequest;
import br.simulado.etec.catalogo.model.Materia;
import br.simulado.etec.catalogo.model.ProgramaDeProva;
import br.simulado.etec.catalogo.model.Prova;
import br.simulado.etec.catalogo.model.Questao;
import br.simulado.etec.catalogo.model.QuestaoVersao;
import br.simulado.etec.catalogo.model.StatusProva;
import br.simulado.etec.catalogo.model.StatusQuestao;
import br.simulado.etec.catalogo.model.Subtopico;
import br.simulado.etec.catalogo.model.TipoQuestao;
import br.simulado.etec.catalogo.repository.ProvaRepository;
import br.simulado.etec.catalogo.repository.QuestaoRepository;
import br.simulado.etec.catalogo.repository.QuestaoVersaoRepository;
import br.simulado.etec.catalogo.repository.SubtopicoRepository;
import br.simulado.etec.catalogo.service.QuestaoService.ResultadoDoCadastro;
import br.simulado.etec.shared.exception.ApiException;
import br.simulado.etec.shared.exception.CodigoErro;
import br.simulado.etec.shared.exception.ConflictException;
import br.simulado.etec.shared.exception.InvalidRequestException;
import br.simulado.etec.shared.exception.ResourceNotFoundException;
import br.simulado.etec.shared.programa.Programa;

@ExtendWith(MockitoExtension.class)
class QuestaoServiceTest {

	@Mock
	private ProvaRepository provas;

	@Mock
	private QuestaoRepository questoes;

	@Mock
	private QuestaoVersaoRepository versoes;

	@Mock
	private SubtopicoRepository subtopicos;

	@InjectMocks
	private QuestaoService servico;

	private ProgramaDeProva etec;

	private Prova prova;

	private Materia materia;

	private Subtopico subtopico;

	@BeforeEach
	void montar() {
		etec = new ProgramaDeProva(Programa.ETEC, "ETEC");
		ReflectionTestUtils.setField(etec, "id", 1L);
		prova = new Prova(etec, "Prova", "Vunesp", 2026, 1, 180, 50, null);
		materia = new Materia(etec, "Matemática", null);
		subtopico = new Subtopico(materia, "Álgebra");
	}

	private static List<AlternativaRequest> cinco() {
		return List.of(new AlternativaRequest("A", "um"), new AlternativaRequest("B", "dois"), new AlternativaRequest("C", "três"),
				new AlternativaRequest("D", "quatro"), new AlternativaRequest("E", "cinco"));
	}

	private QuestaoRequest dados(int numero, String enunciado, List<AlternativaRequest> alternativas) {
		return new QuestaoRequest(numero, subtopico.getPublicId(), null, null, enunciado, null, alternativas, "C", null, null);
	}

	private QuestaoRequest dados(int numero) {
		return dados(numero, "Quanto é 1+1?", cinco());
	}

	private void prepararCaminhoFeliz() {
		when(provas.buscarComTrava(prova.getPublicId())).thenReturn(Optional.of(prova));
		when(subtopicos.findByPublicId(subtopico.getPublicId())).thenReturn(Optional.of(subtopico));
	}

	private static String codigoDe(Throwable erro) {
		return ((ApiException) erro).getCodigo().name();
	}

	// ----- caminho feliz -----

	@Test
	void deve_criar_a_questao_e_a_versao_1_em_rascunho_com_as_5_alternativas_quando_o_numero_e_novo() {
		prepararCaminhoFeliz();
		when(questoes.findByProvaAndNumero(prova, 3)).thenReturn(Optional.empty());
		when(questoes.save(any(Questao.class))).thenAnswer(c -> c.getArgument(0));
		when(versoes.saveAndFlush(any(QuestaoVersao.class))).thenAnswer(c -> c.getArgument(0));

		ResultadoDoCadastro resultado = servico.cadastrar(prova.getPublicId(), dados(3));

		assertThat(resultado.criada()).isTrue();
		QuestaoVersao versao = resultado.versao();
		assertThat(versao.getVersao()).isEqualTo(1);
		assertThat(versao.getStatus()).isEqualTo(StatusQuestao.RASCUNHO);
		assertThat(versao.getSubtopico()).isSameAs(subtopico);
		assertThat(versao.getGabarito()).isEqualTo("C");
		assertThat(versao.getAlternativas()).extracting("letra").containsExactly("A", "B", "C", "D", "E");
		assertThat(versao.getAlternativas()).extracting("texto").containsExactly("um", "dois", "três", "quatro", "cinco");
		assertThat(versao.getQuestao().getNumero()).isEqualTo(3);
		assertThat(versao.getQuestao().getProva()).isSameAs(prova);
		assertThat(versao.getQuestao().getTipo()).as("o tipo é interno e único (D145)").isEqualTo(TipoQuestao.MULTIPLA_ESCOLHA);
		assertThat(versao.getQuestao().getHashConteudo()).matches("[0-9a-f]{64}");
	}

	@Test
	void deve_gravar_as_alternativas_de_a_a_e_mesmo_quando_vem_fora_de_ordem() {
		prepararCaminhoFeliz();
		when(questoes.findByProvaAndNumero(prova, 1)).thenReturn(Optional.empty());
		when(questoes.save(any(Questao.class))).thenAnswer(c -> c.getArgument(0));
		when(versoes.saveAndFlush(any(QuestaoVersao.class))).thenAnswer(c -> c.getArgument(0));
		List<AlternativaRequest> embaralhadas = new ArrayList<>(cinco());
		java.util.Collections.reverse(embaralhadas);

		QuestaoVersao versao = servico.cadastrar(prova.getPublicId(), dados(1, "Enunciado", embaralhadas)).versao();

		assertThat(versao.getAlternativas()).extracting("letra").containsExactly("A", "B", "C", "D", "E");
	}

	@Test
	void deve_travar_a_prova_antes_de_ler_ou_gravar_qualquer_coisa() {
		prepararCaminhoFeliz();
		when(questoes.findByProvaAndNumero(prova, 1)).thenReturn(Optional.empty());
		when(questoes.save(any(Questao.class))).thenAnswer(c -> c.getArgument(0));
		when(versoes.saveAndFlush(any(QuestaoVersao.class))).thenAnswer(c -> c.getArgument(0));

		servico.cadastrar(prova.getPublicId(), dados(1));

		InOrder ordem = inOrder(provas, questoes);
		ordem.verify(provas).buscarComTrava(prova.getPublicId());
		ordem.verify(questoes).findByProvaAndNumero(prova, 1);
		ordem.verify(questoes).save(any(Questao.class));
	}

	// ----- numero -----

	@Test
	void deve_aceitar_o_numero_igual_a_quantidade_da_prova() {
		prepararCaminhoFeliz();
		when(questoes.findByProvaAndNumero(prova, 50)).thenReturn(Optional.empty());
		when(questoes.save(any(Questao.class))).thenAnswer(c -> c.getArgument(0));
		when(versoes.saveAndFlush(any(QuestaoVersao.class))).thenAnswer(c -> c.getArgument(0));

		assertThat(servico.cadastrar(prova.getPublicId(), dados(50)).criada()).isTrue();
	}

	@Test
	void deve_recusar_com_400_no_campo_numero_quando_o_numero_passa_da_quantidade_da_prova() {
		when(provas.buscarComTrava(prova.getPublicId())).thenReturn(Optional.of(prova));

		assertThatThrownBy(() -> servico.cadastrar(prova.getPublicId(), dados(51))).isInstanceOf(InvalidRequestException.class)
				.satisfies(e -> {
					assertThat(codigoDe(e)).isEqualTo("VALIDACAO_FALHOU");
					assertThat(((InvalidRequestException) e).getCampos()).singleElement().extracting("campo").isEqualTo("numero");
				});
		verify(questoes, never()).save(any());
	}

	// ----- prova -----

	@Test
	void deve_lancar_404_prova_nao_encontrada_quando_a_prova_nao_existe() {
		UUID id = UUID.randomUUID();
		when(provas.buscarComTrava(id)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> servico.cadastrar(id, dados(1))).isInstanceOf(ResourceNotFoundException.class)
				.satisfies(e -> assertThat(codigoDe(e)).isEqualTo("PROVA_NAO_ENCONTRADA"));
	}

	@Test
	void deve_recusar_com_409_prova_nao_editavel_quando_a_prova_nao_esta_em_rascunho() {
		ReflectionTestUtils.setField(prova, "status", StatusProva.PUBLICADA);
		when(provas.buscarComTrava(prova.getPublicId())).thenReturn(Optional.of(prova));

		assertThatThrownBy(() -> servico.cadastrar(prova.getPublicId(), dados(1))).isInstanceOf(ConflictException.class)
				.satisfies(e -> assertThat(codigoDe(e)).isEqualTo("PROVA_NAO_EDITAVEL"));
		verify(questoes, never()).save(any());
	}

	@Test
	void deve_dar_o_409_da_prova_antes_do_400_do_numero() {
		ReflectionTestUtils.setField(prova, "status", StatusProva.PUBLICADA);
		when(provas.buscarComTrava(prova.getPublicId())).thenReturn(Optional.of(prova));

		assertThatThrownBy(() -> servico.cadastrar(prova.getPublicId(), dados(999))).isInstanceOf(ConflictException.class);
	}

	// ----- subtópico -----

	@Test
	void deve_recusar_com_400_no_campo_subtopicoid_quando_o_subtopico_nao_existe() {
		when(provas.buscarComTrava(prova.getPublicId())).thenReturn(Optional.of(prova));
		when(subtopicos.findByPublicId(subtopico.getPublicId())).thenReturn(Optional.empty());

		assertThatThrownBy(() -> servico.cadastrar(prova.getPublicId(), dados(1))).isInstanceOf(InvalidRequestException.class)
				.satisfies(e -> {
					assertThat(codigoDe(e)).isEqualTo("VALIDACAO_FALHOU");
					assertThat(((InvalidRequestException) e).getCampos()).singleElement().extracting("campo")
							.isEqualTo("subtopicoId");
				});
	}

	@Test
	void deve_tratar_como_inexistente_o_subtopico_de_materia_de_outro_programa() {
		ProgramaDeProva enem = new ProgramaDeProva(Programa.de("ENEM"), "ENEM");
		ReflectionTestUtils.setField(enem, "id", 2L);
		Subtopico deOutro = new Subtopico(new Materia(enem, "Matemática", null), "Funções");
		when(provas.buscarComTrava(prova.getPublicId())).thenReturn(Optional.of(prova));
		when(subtopicos.findByPublicId(deOutro.getPublicId())).thenReturn(Optional.of(deOutro));
		QuestaoRequest dados = new QuestaoRequest(1, deOutro.getPublicId(), null, null, "Enunciado", null, cinco(), null, null,
				null);

		assertThatThrownBy(() -> servico.cadastrar(prova.getPublicId(), dados)).isInstanceOf(InvalidRequestException.class)
				.satisfies(e -> assertThat(((InvalidRequestException) e).getCampos()).singleElement().extracting("campo")
						.isEqualTo("subtopicoId"));
		verify(questoes, never()).save(any());
	}

	@Test
	void deve_recusar_com_409_subtopico_inativo_quando_o_subtopico_esta_desativado() {
		subtopico.desativar();
		prepararCaminhoFeliz();

		assertThatThrownBy(() -> servico.cadastrar(prova.getPublicId(), dados(1))).isInstanceOf(ConflictException.class)
				.satisfies(e -> assertThat(codigoDe(e)).isEqualTo("SUBTOPICO_INATIVO"));
		verify(questoes, never()).save(any());
	}

	@Test
	void deve_recusar_com_409_subtopico_inativo_quando_a_materia_do_subtopico_esta_desativada() {
		materia.desativar();
		prepararCaminhoFeliz();

		assertThatThrownBy(() -> servico.cadastrar(prova.getPublicId(), dados(1))).isInstanceOf(ConflictException.class)
				.satisfies(e -> assertThat(codigoDe(e)).isEqualTo("SUBTOPICO_INATIVO"));
	}

	// ----- alternativas (o service não depende de ter passado pelo HTTP) -----

	@Test
	void deve_recusar_com_400_quando_ha_menos_ou_mais_de_5_alternativas() {
		List<AlternativaRequest> quatro = cinco().subList(0, 4);
		List<AlternativaRequest> seis = new ArrayList<>(cinco());
		seis.add(new AlternativaRequest("F", "seis"));

		for (List<AlternativaRequest> invalida : List.of(quatro, seis, List.<AlternativaRequest>of())) {
			assertThatThrownBy(() -> servico.cadastrar(prova.getPublicId(), dados(1, "E", invalida)))
					.isInstanceOf(InvalidRequestException.class).satisfies(e -> assertThat(
							((InvalidRequestException) e).getCampos()).singleElement().extracting("campo").isEqualTo("alternativas"));
		}
		verify(provas, never()).buscarComTrava(any());
	}

	@Test
	void deve_recusar_com_400_quando_uma_letra_se_repete_ou_falta() {
		List<AlternativaRequest> repetida = List.of(new AlternativaRequest("A", "1"), new AlternativaRequest("A", "2"),
				new AlternativaRequest("C", "3"), new AlternativaRequest("D", "4"), new AlternativaRequest("E", "5"));
		List<AlternativaRequest> foraDoIntervalo = List.of(new AlternativaRequest("A", "1"), new AlternativaRequest("B", "2"),
				new AlternativaRequest("C", "3"), new AlternativaRequest("D", "4"), new AlternativaRequest("F", "5"));
		List<AlternativaRequest> minuscula = List.of(new AlternativaRequest("a", "1"), new AlternativaRequest("B", "2"),
				new AlternativaRequest("C", "3"), new AlternativaRequest("D", "4"), new AlternativaRequest("E", "5"));
		List<AlternativaRequest> semLetra = List.of(new AlternativaRequest(null, "1"), new AlternativaRequest("B", "2"),
				new AlternativaRequest("C", "3"), new AlternativaRequest("D", "4"), new AlternativaRequest("E", "5"));

		for (List<AlternativaRequest> invalida : List.of(repetida, foraDoIntervalo, minuscula, semLetra)) {
			assertThatThrownBy(() -> servico.cadastrar(prova.getPublicId(), dados(1, "E", invalida)))
					.isInstanceOf(InvalidRequestException.class);
		}
	}

	// ----- numero repetido (D153) -----

	@Test
	void deve_devolver_a_questao_ja_gravada_sem_criar_nada_quando_o_numero_repete_com_o_mesmo_conteudo() {
		prepararCaminhoFeliz();
		String hash = HashDaQuestao.calcular(prova.getPublicId(), 4, null, null, "Quanto é 1+1?", null,
				Map.of("A", "um", "B", "dois", "C", "três", "D", "quatro", "E", "cinco"));
		Questao existente = new Questao(prova, 4, hash);
		QuestaoVersao gravada = new QuestaoVersao(existente, subtopico, null, null, "Quanto é 1+1?", null,
				Map.of("A", "um", "B", "dois", "C", "três", "D", "quatro", "E", "cinco"), null, null, null);
		when(questoes.findByProvaAndNumero(prova, 4)).thenReturn(Optional.of(existente));
		when(versoes.findByQuestaoAndStatus(existente, StatusQuestao.RASCUNHO)).thenReturn(Optional.of(gravada));

		ResultadoDoCadastro resultado = servico.cadastrar(prova.getPublicId(), dados(4));

		assertThat(resultado.criada()).isFalse();
		assertThat(resultado.versao()).as("mostra o que está gravado").isSameAs(gravada);
		verify(questoes, never()).save(any());
		verify(versoes, never()).saveAndFlush(any());
	}

	@Test
	void deve_devolver_a_publicada_quando_nao_ha_rascunho_aberto_e_o_numero_repete_com_o_mesmo_conteudo() {
		prepararCaminhoFeliz();
		String hash = HashDaQuestao.calcular(prova.getPublicId(), 4, null, null, "Quanto é 1+1?", null,
				Map.of("A", "um", "B", "dois", "C", "três", "D", "quatro", "E", "cinco"));
		Questao existente = new Questao(prova, 4, hash);
		QuestaoVersao publicada = new QuestaoVersao(existente, subtopico, null, null, "Quanto é 1+1?", null,
				Map.of("A", "um", "B", "dois", "C", "três", "D", "quatro", "E", "cinco"), null, null, null);
		when(questoes.findByProvaAndNumero(prova, 4)).thenReturn(Optional.of(existente));
		when(versoes.findByQuestaoAndStatus(existente, StatusQuestao.RASCUNHO)).thenReturn(Optional.empty());
		when(versoes.findByQuestaoAndStatus(existente, StatusQuestao.PUBLICADA)).thenReturn(Optional.of(publicada));

		assertThat(servico.cadastrar(prova.getPublicId(), dados(4)).versao()).isSameAs(publicada);
	}

	@Test
	void deve_recusar_com_409_e_os_numeros_em_conflito_quando_o_numero_repete_com_conteudo_diferente() {
		prepararCaminhoFeliz();
		Questao existente = new Questao(prova, 4, "0".repeat(64));
		when(questoes.findByProvaAndNumero(prova, 4)).thenReturn(Optional.of(existente));

		assertThatThrownBy(() -> servico.cadastrar(prova.getPublicId(), dados(4))).isInstanceOf(ConflictException.class)
				.satisfies(e -> {
					assertThat(codigoDe(e)).isEqualTo("QUESTAO_NUMERO_EM_CONFLITO");
					assertThat(((ApiException) e).getDetalhes()).containsEntry("numerosEmConflito", List.of(4));
				});
		verify(questoes, never()).save(any());
	}

	@Test
	void deve_ignorar_subtopico_gabarito_dificuldade_e_explicacao_ao_decidir_se_e_a_mesma_questao() {
		prepararCaminhoFeliz();
		String hash = HashDaQuestao.calcular(prova.getPublicId(), 2, null, null, "Quanto é 1+1?", null,
				Map.of("A", "um", "B", "dois", "C", "três", "D", "quatro", "E", "cinco"));
		Questao existente = new Questao(prova, 2, hash);
		QuestaoVersao gravada = new QuestaoVersao(existente, subtopico, null, null, "Quanto é 1+1?", null,
				Map.of("A", "um", "B", "dois", "C", "três", "D", "quatro", "E", "cinco"), "A", null, "explicação antiga");
		when(questoes.findByProvaAndNumero(prova, 2)).thenReturn(Optional.of(existente));
		when(versoes.findByQuestaoAndStatus(existente, StatusQuestao.RASCUNHO)).thenReturn(Optional.of(gravada));
		QuestaoRequest outroGabaritoEExplicacao = new QuestaoRequest(2, subtopico.getPublicId(), null, null, "Quanto é 1+1?", null,
				cinco(), "E", br.simulado.etec.catalogo.model.Dificuldade.DIFICIL, "explicação nova");

		ResultadoDoCadastro resultado = servico.cadastrar(prova.getPublicId(), outroGabaritoEExplicacao);

		assertThat(resultado.criada()).isFalse();
		assertThat(resultado.versao().getGabarito()).as("a resposta mostra o gabarito GRAVADO").isEqualTo("A");
		assertThat(resultado.versao().getExplicacao()).isEqualTo("explicação antiga");
	}

	// ----- buscar -----

	@Test
	void deve_devolver_o_rascunho_aberto_quando_busca_sem_versao() {
		Questao questao = new Questao(prova, 1, "0".repeat(64));
		QuestaoVersao rascunho = new QuestaoVersao(questao, subtopico, null, null, "E", null,
				Map.of("A", "1", "B", "2", "C", "3", "D", "4", "E", "5"), null, null, null);
		when(questoes.findByPublicId(questao.getPublicId())).thenReturn(Optional.of(questao));
		when(versoes.findByQuestaoAndStatus(questao, StatusQuestao.RASCUNHO)).thenReturn(Optional.of(rascunho));

		assertThat(servico.buscar(questao.getPublicId(), null)).isSameAs(rascunho);
	}

	@Test
	void deve_devolver_a_versao_pedida_e_404_quando_ela_nao_existe() {
		Questao questao = new Questao(prova, 1, "0".repeat(64));
		QuestaoVersao v1 = new QuestaoVersao(questao, subtopico, null, null, "E", null,
				Map.of("A", "1", "B", "2", "C", "3", "D", "4", "E", "5"), null, null, null);
		when(questoes.findByPublicId(questao.getPublicId())).thenReturn(Optional.of(questao));
		when(versoes.findByQuestaoAndVersao(questao, 1)).thenReturn(Optional.of(v1));
		when(versoes.findByQuestaoAndVersao(questao, 9)).thenReturn(Optional.empty());

		assertThat(servico.buscar(questao.getPublicId(), 1)).isSameAs(v1);
		assertThatThrownBy(() -> servico.buscar(questao.getPublicId(), 9)).isInstanceOf(ResourceNotFoundException.class)
				.satisfies(e -> assertThat(codigoDe(e)).isEqualTo("QUESTAO_NAO_ENCONTRADA"));
	}

	@Test
	void deve_lancar_404_questao_nao_encontrada_quando_a_questao_nao_existe() {
		UUID id = UUID.randomUUID();
		when(questoes.findByPublicId(id)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> servico.buscar(id, null)).isInstanceOf(ResourceNotFoundException.class)
				.satisfies(e -> assertThat(((ApiException) e).getCodigo()).isEqualTo(CodigoErro.QUESTAO_NAO_ENCONTRADA));
	}

}
