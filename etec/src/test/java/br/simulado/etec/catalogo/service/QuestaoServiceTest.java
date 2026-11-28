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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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
import br.simulado.etec.catalogo.service.QuestaoService.Situacao;
import br.simulado.etec.shared.exception.ApiException;
import br.simulado.etec.shared.exception.CodigoErro;
import br.simulado.etec.shared.exception.ConflictException;
import br.simulado.etec.shared.exception.InvalidRequestException;
import br.simulado.etec.shared.exception.ResourceNotFoundException;
import br.simulado.etec.shared.programa.Programa;
import java.sql.SQLException;
import java.time.Instant;
import org.hibernate.exception.ConstraintViolationException;
import org.hibernate.exception.ConstraintViolationException.ConstraintKind;
import org.springframework.dao.DataIntegrityViolationException;
import br.simulado.etec.catalogo.dto.QuestaoAtualizacaoRequest;
import br.simulado.etec.catalogo.model.Alternativa;
import br.simulado.etec.catalogo.service.QuestaoService.SituacaoDoGabarito;
import java.util.HashMap;
import br.simulado.etec.catalogo.service.QuestaoService.MotivoDePendencia;
import br.simulado.etec.catalogo.service.QuestaoService.Pendencia;

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

	@Mock
	private org.springframework.transaction.PlatformTransactionManager transacoes;

	@Mock
	private java.time.Clock relogio;

	@Mock
	private br.simulado.etec.shared.config.ImagensProperties imagens;

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

	// ----- idempotência ANTES do estado do subtópico (D169) -----

	private Questao jaGravada(int numero) {
		String hash = HashDaQuestao.calcular(prova.getPublicId(), numero, null, null, "Quanto é 1+1?", null,
				Map.of("A", "um", "B", "dois", "C", "três", "D", "quatro", "E", "cinco"));
		return new Questao(prova, numero, hash);
	}

	@Test
	void deve_devolver_ja_existia_sem_olhar_o_estado_do_subtopico_quando_a_mesma_questao_ja_esta_gravada() {
		subtopico.desativar();
		prepararCaminhoFeliz();
		Questao existente = jaGravada(4);
		QuestaoVersao gravada = new QuestaoVersao(existente, subtopico, null, null, "Quanto é 1+1?", null,
				Map.of("A", "um", "B", "dois", "C", "três", "D", "quatro", "E", "cinco"), null, null, null);
		when(questoes.findByProvaAndNumero(prova, 4)).thenReturn(Optional.of(existente));
		when(versoes.findByQuestaoAndStatus(existente, StatusQuestao.RASCUNHO)).thenReturn(Optional.of(gravada));

		ResultadoDoCadastro resultado = servico.cadastrar(prova.getPublicId(), dados(4));

		assertThat(resultado.situacao()).isEqualTo(Situacao.JA_EXISTIA);
		assertThat(resultado.versao()).isSameAs(gravada);
		verify(questoes, never()).save(any());
	}

	@Test
	void deve_dar_o_conflito_de_conteudo_antes_do_subtopico_inativo_quando_o_numero_tem_outra_questao() {
		materia.desativar();
		prepararCaminhoFeliz();
		when(questoes.findByProvaAndNumero(prova, 4)).thenReturn(Optional.of(new Questao(prova, 4, "0".repeat(64))));

		assertThatThrownBy(() -> servico.cadastrar(prova.getPublicId(), dados(4))).isInstanceOf(ConflictException.class)
				.satisfies(e -> assertThat(codigoDe(e)).isEqualTo("QUESTAO_NUMERO_EM_CONFLITO"));
	}

	@Test
	void deve_continuar_exigindo_o_subtopico_existente_e_do_programa_mesmo_quando_o_numero_ja_tem_questao() {
		when(provas.buscarComTrava(prova.getPublicId())).thenReturn(Optional.of(prova));
		when(subtopicos.findByPublicId(subtopico.getPublicId())).thenReturn(Optional.empty());

		assertThatThrownBy(() -> servico.cadastrar(prova.getPublicId(), dados(4))).isInstanceOf(InvalidRequestException.class);
		verify(questoes, never()).findByProvaAndNumero(any(), org.mockito.ArgumentMatchers.anyInt());
	}

	// ----- as duas partes usadas pelo lote -----

	@Test
	void deve_devolver_conflito_sem_lancar_nem_gravar_quando_cadastra_na_prova_travada_com_conteudo_diferente() {
		when(subtopicos.findByPublicId(subtopico.getPublicId())).thenReturn(Optional.of(subtopico));
		when(questoes.findByProvaAndNumero(prova, 4)).thenReturn(Optional.of(new Questao(prova, 4, "0".repeat(64))));

		ResultadoDoCadastro resultado = servico.cadastrarNaProva(prova, dados(4));

		assertThat(resultado.situacao()).isEqualTo(Situacao.CONFLITO);
		assertThat(resultado.versao()).isNull();
		verify(questoes, never()).save(any());
		verify(provas, never()).buscarComTrava(any());
	}

	@Test
	void deve_validar_as_alternativas_tambem_ao_cadastrar_na_prova_travada() {
		assertThatThrownBy(() -> servico.cadastrarNaProva(prova, dados(1, "E", cinco().subList(0, 4))))
				.isInstanceOf(InvalidRequestException.class);
		verify(questoes, never()).save(any());
	}

	@Test
	void deve_travar_a_prova_e_devolve_la_so_quando_esta_em_rascunho() {
		when(provas.buscarComTrava(prova.getPublicId())).thenReturn(Optional.of(prova));

		assertThat(servico.travarProvaEditavel(prova.getPublicId())).isSameAs(prova);

		ReflectionTestUtils.setField(prova, "status", StatusProva.PUBLICADA);
		assertThatThrownBy(() -> servico.travarProvaEditavel(prova.getPublicId())).isInstanceOf(ConflictException.class)
				.satisfies(e -> assertThat(codigoDe(e)).isEqualTo("PROVA_NAO_EDITAVEL"));
	}

	// ----- listar -----

	private QuestaoVersao versaoComId(long id, int numero) {
		QuestaoVersao versao = new QuestaoVersao(new Questao(prova, numero, "0".repeat(64)), subtopico, null, null, "E", null,
				Map.of("A", "1", "B", "2", "C", "3", "D", "4", "E", "5"), null, null, null);
		ReflectionTestUtils.setField(versao, "id", id);
		return versao;
	}

	@Test
	void deve_devolver_as_versoes_na_ordem_dos_ids_mesmo_que_a_carga_venha_em_outra_ordem() {
		UUID provaId = UUID.randomUUID();
		UUID subtopicoId = UUID.randomUUID();
		Pageable pagina = PageRequest.of(1, 3, Sort.by("numero", "id"));
		when(versoes.buscarIdsDasVersoesExibidas(provaId, subtopicoId, StatusQuestao.RASCUNHO, PageRequest.of(1, 3)))
				.thenReturn(new PageImpl<>(List.of(10L, 11L, 12L), PageRequest.of(1, 3), 9));
		when(versoes.carregarCompletas(List.of(10L, 11L, 12L)))
				.thenReturn(List.of(versaoComId(12, 3), versaoComId(10, 1), versaoComId(11, 2)));

		Page<QuestaoVersao> resultado = servico.listar(provaId, subtopicoId, StatusQuestao.RASCUNHO, pagina);

		assertThat(resultado.getContent()).extracting(QuestaoVersao::getId).containsExactly(10L, 11L, 12L);
		assertThat(resultado.getTotalElements()).isEqualTo(9);
		assertThat(resultado.getNumber()).isEqualTo(1);
	}

	@Test
	void deve_nao_carregar_nada_quando_a_pagina_de_ids_e_vazia() {
		when(versoes.buscarIdsDasVersoesExibidas(null, null, null, PageRequest.of(5, 20)))
				.thenReturn(new PageImpl<>(List.of(), PageRequest.of(5, 20), 3));

		Page<QuestaoVersao> resultado = servico.listar(null, null, null, PageRequest.of(5, 20));

		assertThat(resultado.getContent()).isEmpty();
		assertThat(resultado.getTotalElements()).isEqualTo(3);
		verify(versoes, never()).carregarCompletas(any());
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

	// ----- editar o rascunho (PUT, D171, D172 e D175) -----

	private static final Instant MOMENTO = Instant.parse("2026-11-27T12:00:00.000Z");

	/** A questão 3 da prova, com um rascunho completo no subtópico da prova. */
	private QuestaoVersao rascunhoExistente() {
		Questao questao = new Questao(prova, 3, "a".repeat(64));
		return new QuestaoVersao(questao, subtopico, "Apoio", 1, "Original", null,
				Map.of("A", "um", "B", "dois", "C", "três", "D", "quatro", "E", "cinco"), "C",
				br.simulado.etec.catalogo.model.Dificuldade.FACIL, "Porque sim");
	}

	private QuestaoAtualizacaoRequest edicao(UUID provaId, Integer numero, UUID subtopicoId, String enunciado) {
		return new QuestaoAtualizacaoRequest(provaId, numero, subtopicoId, null, null, enunciado, null, cinco(), null, null, null);
	}

	private QuestaoAtualizacaoRequest edicao(String enunciado) {
		return edicao(null, null, subtopico.getPublicId(), enunciado);
	}

	private void prepararEdicao(QuestaoVersao rascunho) {
		Questao questao = rascunho.getQuestao();
		when(questoes.buscarProvaDaQuestao(questao.getPublicId())).thenReturn(Optional.of(prova.getPublicId()));
		when(provas.buscarComTrava(prova.getPublicId())).thenReturn(Optional.of(prova));
		when(questoes.findByPublicId(questao.getPublicId())).thenReturn(Optional.of(questao));
		when(versoes.findByQuestaoAndStatus(questao, StatusQuestao.RASCUNHO)).thenReturn(Optional.of(rascunho));
	}

	private void prepararGravacao() {
		when(relogio.instant()).thenReturn(MOMENTO);
		when(versoes.saveAndFlush(any(QuestaoVersao.class))).thenAnswer(c -> c.getArgument(0));
	}

	private Subtopico outroSubtopico() {
		Subtopico outro = new Subtopico(materia, "Geometria");
		when(subtopicos.findByPublicId(outro.getPublicId())).thenReturn(Optional.of(outro));
		return outro;
	}

	@Test
	void deve_ler_a_prova_da_questao_antes_da_transacao_e_travar_antes_de_reler() {
		QuestaoVersao rascunho = rascunhoExistente();
		prepararEdicao(rascunho);
		prepararGravacao();

		servico.atualizar(rascunho.getQuestao().getPublicId(), edicao("Novo"));

		InOrder ordem = inOrder(questoes, transacoes, provas, versoes);
		ordem.verify(questoes).buscarProvaDaQuestao(rascunho.getQuestao().getPublicId());
		ordem.verify(transacoes).getTransaction(any());
		ordem.verify(provas).buscarComTrava(prova.getPublicId());
		ordem.verify(questoes).findByPublicId(rascunho.getQuestao().getPublicId());
		ordem.verify(versoes).findByQuestaoAndStatus(rascunho.getQuestao(), StatusQuestao.RASCUNHO);
		ordem.verify(versoes).saveAndFlush(rascunho);
		ordem.verify(transacoes).commit(any());
	}

	@Test
	void deve_substituir_o_rascunho_nas_mesmas_linhas_sem_tocar_no_numero_nem_no_hash() {
		QuestaoVersao rascunho = rascunhoExistente();
		prepararEdicao(rascunho);
		prepararGravacao();
		List<Alternativa> linhasAntes = rascunho.getAlternativas();
		List<AlternativaRequest> novas = List.of(new AlternativaRequest("E", "cinco"), new AlternativaRequest("A", "UM"),
				new AlternativaRequest("B", "dois"), new AlternativaRequest("C", "três"), new AlternativaRequest("D", "quatro"));

		QuestaoVersao salva = servico.atualizar(rascunho.getQuestao().getPublicId(), new QuestaoAtualizacaoRequest(null, 3,
				subtopico.getPublicId(), null, null, "Novo enunciado", null, novas, null, null, null));

		assertThat(salva).isSameAs(rascunho);
		assertThat(salva.getEnunciado()).isEqualTo("Novo enunciado");
		assertThat(salva.getGabarito()).as("omitir o gabarito o apaga (D111)").isNull();
		assertThat(salva.getTextoApoio()).isNull();
		assertThat(salva.getGrupoApoio()).isNull();
		assertThat(salva.getDificuldade()).isNull();
		assertThat(salva.getExplicacao()).isNull();
		assertThat(salva.getAlternativas()).extracting("texto").containsExactly("UM", "dois", "três", "quatro", "cinco");
		assertThat(salva.getAlternativas()).as("as MESMAS 5 linhas: o texto muda no lugar")
				.usingElementComparator((a, b) -> a == b ? 0 : 1).containsExactlyElementsOf(linhasAntes);
		assertThat(salva.getQuestao().getHashConteudo()).as("o hash nunca muda (D51)").isEqualTo("a".repeat(64));
		assertThat(salva.getQuestao().getNumero()).isEqualTo(3);
		assertThat(salva.getVersao()).as("o PUT não cria versão").isEqualTo(1);
		assertThat(salva.getStatus()).isEqualTo(StatusQuestao.RASCUNHO);
	}

	@Test
	void deve_anotar_o_momento_na_versao_quando_so_o_texto_de_uma_alternativa_muda() {
		QuestaoVersao rascunho = rascunhoExistente();
		prepararEdicao(rascunho);
		prepararGravacao();
		List<AlternativaRequest> soUma = new ArrayList<>(cinco());
		soUma.set(2, new AlternativaRequest("C", "outro texto"));

		servico.atualizar(rascunho.getQuestao().getPublicId(), new QuestaoAtualizacaoRequest(null, null,
				subtopico.getPublicId(), "Apoio", 1, "Original", null, soUma, "C",
				br.simulado.etec.catalogo.model.Dificuldade.FACIL, "Porque sim"));

		assertThat(ReflectionTestUtils.getField(rascunho, "atualizadoEm")).isEqualTo(MOMENTO);
	}

	@Test
	void deve_nao_anotar_momento_nenhum_quando_o_conteudo_enviado_e_igual_ao_gravado() {
		QuestaoVersao rascunho = rascunhoExistente();
		prepararEdicao(rascunho);
		prepararGravacao();

		servico.atualizar(rascunho.getQuestao().getPublicId(), new QuestaoAtualizacaoRequest(null, null,
				subtopico.getPublicId(), "Apoio", 1, "Original", null, cinco(), "C",
				br.simulado.etec.catalogo.model.Dificuldade.FACIL, "Porque sim"));

		assertThat(ReflectionTestUtils.getField(rascunho, "atualizadoEm")).isNull();
	}

	@Test
	void deve_aceitar_o_mesmo_subtopico_desativado_sem_conferir_nada_nele() {
		QuestaoVersao rascunho = rascunhoExistente();
		prepararEdicao(rascunho);
		prepararGravacao();
		subtopico.desativar();

		QuestaoVersao salva = servico.atualizar(rascunho.getQuestao().getPublicId(), edicao("Novo"));

		assertThat(salva.getSubtopico()).isSameAs(subtopico);
		verify(subtopicos, never()).findByPublicId(any());
	}

	@Test
	void deve_trocar_o_subtopico_quando_o_novo_esta_ativo() {
		QuestaoVersao rascunho = rascunhoExistente();
		prepararEdicao(rascunho);
		prepararGravacao();
		Subtopico outro = outroSubtopico();

		QuestaoVersao salva = servico.atualizar(rascunho.getQuestao().getPublicId(), edicao(null, null, outro.getPublicId(), "N"));

		assertThat(salva.getSubtopico()).isSameAs(outro);
	}

	@Test
	void deve_recusar_com_409_subtopico_inativo_antes_do_campo_imutavel_quando_o_subtopico_trocado_esta_desativado() {
		QuestaoVersao rascunho = rascunhoExistente();
		prepararEdicao(rascunho);
		Subtopico outro = outroSubtopico();
		outro.desativar();

		assertThatThrownBy(() -> servico.atualizar(rascunho.getQuestao().getPublicId(),
				edicao(UUID.randomUUID(), 9, outro.getPublicId(), "N")))
				.satisfies(e -> assertThat(codigoDe(e)).isEqualTo("SUBTOPICO_INATIVO"));
		assertThat(rascunho.getEnunciado()).isEqualTo("Original");
		verify(versoes, never()).saveAndFlush(any());
	}

	@Test
	void deve_recusar_com_400_no_campo_subtopicoid_quando_o_subtopico_trocado_nao_existe() {
		QuestaoVersao rascunho = rascunhoExistente();
		prepararEdicao(rascunho);
		UUID inexistente = UUID.randomUUID();
		when(subtopicos.findByPublicId(inexistente)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> servico.atualizar(rascunho.getQuestao().getPublicId(), edicao(null, 9, inexistente, "N")))
				.isInstanceOfSatisfying(InvalidRequestException.class, e -> {
					assertThat(e.getCodigo()).isEqualTo(CodigoErro.VALIDACAO_FALHOU);
					assertThat(e.getCampos()).extracting("campo").containsExactly("subtopicoId");
				});
	}

	@Test
	void deve_dar_409_sem_rascunho_antes_de_qualquer_outra_regra() {
		QuestaoVersao rascunho = rascunhoExistente();
		Questao questao = rascunho.getQuestao();
		when(questoes.buscarProvaDaQuestao(questao.getPublicId())).thenReturn(Optional.of(prova.getPublicId()));
		when(provas.buscarComTrava(prova.getPublicId())).thenReturn(Optional.of(prova));
		when(questoes.findByPublicId(questao.getPublicId())).thenReturn(Optional.of(questao));
		when(versoes.findByQuestaoAndStatus(questao, StatusQuestao.RASCUNHO)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> servico.atualizar(questao.getPublicId(), edicao(UUID.randomUUID(), 9, UUID.randomUUID(), "N")))
				.isInstanceOf(ConflictException.class)
				.satisfies(e -> assertThat(codigoDe(e)).isEqualTo("QUESTAO_SEM_RASCUNHO"));
		verify(subtopicos, never()).findByPublicId(any());
		verify(versoes, never()).saveAndFlush(any());
	}

	@Test
	void deve_editar_o_rascunho_mesmo_com_a_prova_publicada() {
		ReflectionTestUtils.setField(prova, "status", StatusProva.PUBLICADA);
		QuestaoVersao rascunho = rascunhoExistente();
		prepararEdicao(rascunho);
		prepararGravacao();

		assertThat(servico.atualizar(rascunho.getQuestao().getPublicId(), edicao("Novo")).getEnunciado()).isEqualTo("Novo");
		assertThat(prova.getStatus()).as("a prova não muda").isEqualTo(StatusProva.PUBLICADA);
	}

	@Test
	void deve_recusar_com_campo_imutavel_numero_ou_provaid_diferentes_e_nao_alterar_nada() {
		QuestaoVersao rascunho = rascunhoExistente();
		prepararEdicao(rascunho);
		UUID questaoId = rascunho.getQuestao().getPublicId();

		assertThatThrownBy(() -> servico.atualizar(questaoId, edicao(null, 4, subtopico.getPublicId(), "N")))
				.satisfies(e -> assertThat(codigoDe(e)).isEqualTo("CAMPO_IMUTAVEL"));
		assertThatThrownBy(() -> servico.atualizar(questaoId, edicao(UUID.randomUUID(), 3, subtopico.getPublicId(), "N")))
				.as("provaId diferente, mesmo que não exista").satisfies(e -> assertThat(codigoDe(e)).isEqualTo("CAMPO_IMUTAVEL"));

		assertThat(rascunho.getEnunciado()).isEqualTo("Original");
		verify(versoes, never()).saveAndFlush(any());
	}

	@Test
	void deve_aceitar_numero_e_provaid_iguais_aos_atuais() {
		QuestaoVersao rascunho = rascunhoExistente();
		prepararEdicao(rascunho);
		prepararGravacao();

		servico.atualizar(rascunho.getQuestao().getPublicId(), edicao(prova.getPublicId(), 3, subtopico.getPublicId(), "Igual"));

		assertThat(rascunho.getEnunciado()).isEqualTo("Igual");
	}

	@Test
	void deve_dar_404_sem_abrir_transacao_quando_a_questao_nao_existe() {
		UUID id = UUID.randomUUID();
		when(questoes.buscarProvaDaQuestao(id)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> servico.atualizar(id, edicao("N")))
				.satisfies(e -> assertThat(codigoDe(e)).isEqualTo("QUESTAO_NAO_ENCONTRADA"));
		verify(transacoes, never()).getTransaction(any());
		verify(provas, never()).buscarComTrava(any());
	}

	@Test
	void deve_dar_404_quando_a_questao_some_enquanto_espera_a_trava() {
		UUID id = UUID.randomUUID();
		when(questoes.buscarProvaDaQuestao(id)).thenReturn(Optional.of(prova.getPublicId()));
		when(provas.buscarComTrava(prova.getPublicId())).thenReturn(Optional.of(prova));
		when(questoes.findByPublicId(id)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> servico.atualizar(id, edicao("N")))
				.satisfies(e -> assertThat(codigoDe(e)).isEqualTo("QUESTAO_NAO_ENCONTRADA"));
	}

	@Test
	void deve_validar_as_alternativas_tambem_no_put_sem_ler_nada() {
		assertThatThrownBy(() -> servico.atualizar(UUID.randomUUID(), new QuestaoAtualizacaoRequest(null, null,
				subtopico.getPublicId(), null, null, "E", null, cinco().subList(0, 4), null, null, null)))
				.isInstanceOf(InvalidRequestException.class);
		verify(questoes, never()).buscarProvaDaQuestao(any());
	}

	@Test
	void deve_recusar_alteracao_na_entidade_quando_a_versao_nao_e_rascunho() {
		QuestaoVersao publicada = rascunhoExistente();
		ReflectionTestUtils.setField(publicada, "status", StatusQuestao.PUBLICADA);

		assertThatThrownBy(() -> publicada.definirGabarito("A")).isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> publicada.editarRascunho(subtopico, null, null, "X", null,
				Map.of("A", "1", "B", "2", "C", "3", "D", "4", "E", "5"), null, null, null, MOMENTO))
				.isInstanceOf(IllegalStateException.class);
		assertThat(publicada.getEnunciado()).isEqualTo("Original");
	}

	// ----- subtópico apagado no meio da gravação (D177) -----

	private static DataIntegrityViolationException chaveEstrangeira(String restricao) {
		return new DataIntegrityViolationException("erro de integridade",
				new ConstraintViolationException("erro", new SQLException("x"), "insert", ConstraintKind.FOREIGN_KEY, restricao));
	}

	@Test
	void deve_traduzir_para_400_no_subtopico_a_fk_do_subtopico_apagado_durante_o_cadastro() {
		prepararCaminhoFeliz();
		when(questoes.findByProvaAndNumero(prova, 1)).thenReturn(Optional.empty());
		when(questoes.save(any(Questao.class))).thenAnswer(c -> c.getArgument(0));
		when(versoes.saveAndFlush(any(QuestaoVersao.class))).thenThrow(chaveEstrangeira("fk_questao_versao_subtopico"));

		assertThatThrownBy(() -> servico.cadastrar(prova.getPublicId(), dados(1)))
				.isInstanceOfSatisfying(InvalidRequestException.class,
						e -> assertThat(e.getCampos()).extracting("campo").containsExactly("subtopicoId"));
	}

	@Test
	void deve_traduzir_para_400_no_subtopico_a_fk_do_subtopico_apagado_durante_o_put() {
		QuestaoVersao rascunho = rascunhoExistente();
		prepararEdicao(rascunho);
		when(relogio.instant()).thenReturn(MOMENTO);
		when(versoes.saveAndFlush(rascunho)).thenThrow(chaveEstrangeira("questao_versao.fk_questao_versao_subtopico"));

		assertThatThrownBy(() -> servico.atualizar(rascunho.getQuestao().getPublicId(), edicao("N")))
				.isInstanceOfSatisfying(InvalidRequestException.class,
						e -> assertThat(e.getCampos()).extracting("campo").containsExactly("subtopicoId"));
	}

	@Test
	void deve_deixar_subir_qualquer_outra_violacao_de_integridade_na_gravacao_da_versao() {
		prepararCaminhoFeliz();
		when(questoes.findByProvaAndNumero(prova, 1)).thenReturn(Optional.empty());
		when(questoes.save(any(Questao.class))).thenAnswer(c -> c.getArgument(0));
		DataIntegrityViolationException outra = chaveEstrangeira("fk_questao_versao_questao");
		when(versoes.saveAndFlush(any(QuestaoVersao.class))).thenThrow(outra);

		assertThatThrownBy(() -> servico.cadastrar(prova.getPublicId(), dados(1))).isSameAs(outra);
	}

	// ----- um item do gabarito (D174) -----

	@Test
	void deve_alterar_o_gabarito_do_rascunho_e_dizer_que_alterou() {
		QuestaoVersao rascunho = rascunhoExistente();
		when(questoes.findByProvaAndNumero(prova, 3)).thenReturn(Optional.of(rascunho.getQuestao()));
		when(versoes.findByQuestaoAndStatus(rascunho.getQuestao(), StatusQuestao.RASCUNHO)).thenReturn(Optional.of(rascunho));

		assertThat(servico.aplicarGabarito(prova, 3, "E")).isEqualTo(SituacaoDoGabarito.ALTERADO);
		assertThat(rascunho.getGabarito()).isEqualTo("E");
		assertThat(rascunho.getQuestao().getHashConteudo()).isEqualTo("a".repeat(64));
	}

	@Test
	void deve_dizer_que_nao_houve_mudanca_quando_o_gabarito_ja_e_o_mesmo() {
		QuestaoVersao rascunho = rascunhoExistente();
		when(questoes.findByProvaAndNumero(prova, 3)).thenReturn(Optional.of(rascunho.getQuestao()));
		when(versoes.findByQuestaoAndStatus(rascunho.getQuestao(), StatusQuestao.RASCUNHO)).thenReturn(Optional.of(rascunho));

		assertThat(servico.aplicarGabarito(prova, 3, "C")).isEqualTo(SituacaoDoGabarito.SEM_MUDANCA);
	}

	@Test
	void deve_distinguir_questao_inexistente_de_questao_sem_rascunho() {
		Questao semRascunho = new Questao(prova, 4, "b".repeat(64));
		when(questoes.findByProvaAndNumero(prova, 4)).thenReturn(Optional.of(semRascunho));
		when(versoes.findByQuestaoAndStatus(semRascunho, StatusQuestao.RASCUNHO)).thenReturn(Optional.empty());
		when(questoes.findByProvaAndNumero(prova, 5)).thenReturn(Optional.empty());

		assertThat(servico.aplicarGabarito(prova, 4, "A")).isEqualTo(SituacaoDoGabarito.SEM_RASCUNHO);
		assertThat(servico.aplicarGabarito(prova, 5, "A")).isEqualTo(SituacaoDoGabarito.QUESTAO_INEXISTENTE);
	}

	// ----- publicar as questões da prova, em cascata (D178) -----

	private Prova provaCom(int quantidade) {
		return new Prova(etec, "Para publicar", null, null, null, 180, quantidade, null);
	}

	/** Um rascunho pronto para publicar (com gabarito, no subtópico ativo), a ser estragado pelo teste. */
	private QuestaoVersao pronta(Prova daProva, int numero, Subtopico emQue, String gabarito, String imagemUrl) {
		Questao questao = new Questao(daProva, numero, "a".repeat(64));
		return new QuestaoVersao(questao, emQue, null, null, "E", imagemUrl,
				Map.of("A", "1", "B", "2", "C", "3", "D", "4", "E", "5"), gabarito, null, null);
	}

	private QuestaoVersao pronta(Prova daProva, int numero) {
		return pronta(daProva, numero, subtopico, "A", null);
	}

	private void aProvaTem(Prova daProva, QuestaoVersao... versoesDela) {
		when(questoes.findByProva(daProva))
				.thenReturn(java.util.Arrays.stream(versoesDela).map(QuestaoVersao::getQuestao).distinct().toList());
		when(versoes.buscarVivasDaProva(daProva)).thenReturn(List.of(versoesDela));
	}

	@Test
	void deve_publicar_todos_os_rascunhos_com_o_mesmo_momento_quando_nao_ha_pendencia() {
		Prova duas = provaCom(2);
		QuestaoVersao um = pronta(duas, 1);
		QuestaoVersao dois = pronta(duas, 2, subtopico, "E", "https://localhost/a.png");
		aProvaTem(duas, dois, um);
		when(imagens.permite("localhost")).thenReturn(true);

		assertThat(servico.publicarQuestoesDaProva(duas, MOMENTO)).isEmpty();

		for (QuestaoVersao versao : List.of(um, dois)) {
			assertThat(versao.getStatus()).isEqualTo(StatusQuestao.PUBLICADA);
			assertThat(versao.getPublicadaEm()).isEqualTo(MOMENTO);
		}
	}

	@Test
	void deve_juntar_todas_as_pendencias_em_ordem_de_numero_sem_publicar_nada() {
		Prova seis = provaCom(6);
		Subtopico desativado = new Subtopico(materia, "Desativado");
		desativado.desativar();
		ProgramaDeProva outroPrograma = new ProgramaDeProva(Programa.ETEC, "outro");
		ReflectionTestUtils.setField(outroPrograma, "id", 2L);
		Subtopico deOutroPrograma = new Subtopico(new Materia(outroPrograma, "De fora", null), "De fora");
		QuestaoVersao boa = pronta(seis, 1);
		QuestaoVersao semGabarito = pronta(seis, 3, subtopico, null, null);
		QuestaoVersao comTres = pronta(seis, 4, desativado, null, "https://fora.exemplo.invalid/a.png");
		QuestaoVersao deFora = pronta(seis, 5, deOutroPrograma, "A", null);
		QuestaoVersao soPublicada = pronta(seis, 6);
		ReflectionTestUtils.setField(soPublicada, "status", StatusQuestao.PUBLICADA);
		aProvaTem(seis, soPublicada, deFora, comTres, semGabarito, boa);

		List<Pendencia> pendencias = servico.publicarQuestoesDaProva(seis, MOMENTO);

		assertThat(pendencias).containsExactly(new Pendencia(2, MotivoDePendencia.QUESTAO_AUSENTE),
				new Pendencia(3, MotivoDePendencia.SEM_GABARITO), new Pendencia(4, MotivoDePendencia.SEM_GABARITO),
				new Pendencia(4, MotivoDePendencia.SUBTOPICO_INATIVO), new Pendencia(4, MotivoDePendencia.IMAGEM_NAO_PERMITIDA),
				new Pendencia(5, MotivoDePendencia.SUBTOPICO_DE_OUTRO_PROGRAMA),
				new Pendencia(6, MotivoDePendencia.QUESTAO_SEM_RASCUNHO));
		assertThat(boa.getStatus()).as("nem a que estava pronta").isEqualTo(StatusQuestao.RASCUNHO);
		assertThat(boa.getPublicadaEm()).isNull();
		verify(versoes, never()).flush();
	}

	@Test
	void deve_apontar_o_subtopico_de_materia_desativada_como_inativo() {
		Prova uma = provaCom(1);
		QuestaoVersao versao = pronta(uma, 1);
		materia.desativar();
		aProvaTem(uma, versao);

		assertThat(servico.publicarQuestoesDaProva(uma, MOMENTO))
				.containsExactly(new Pendencia(1, MotivoDePendencia.SUBTOPICO_INATIVO));
	}

	@Test
	void deve_arquivar_a_publicada_anterior_e_mandar_ao_banco_antes_de_publicar_o_rascunho() {
		Prova uma = provaCom(1);
		QuestaoVersao anterior = pronta(uma, 1);
		anterior.publicar(MOMENTO.minusSeconds(60));
		QuestaoVersao nova = new QuestaoVersao(anterior.getQuestao(), subtopico, null, null, "E2", null,
				Map.of("A", "1", "B", "2", "C", "3", "D", "4", "E", "5"), "B", null, null);
		aProvaTem(uma, anterior, nova);
		Map<String, StatusQuestao> noFlush = new HashMap<>();
		org.mockito.Mockito.doAnswer(chamada -> {
			noFlush.put("anterior", anterior.getStatus());
			noFlush.put("nova", nova.getStatus());
			return null;
		}).when(versoes).flush();

		assertThat(servico.publicarQuestoesDaProva(uma, MOMENTO)).isEmpty();

		assertThat(noFlush).as("quando o flush acontece, só a antiga mudou").containsEntry("anterior", StatusQuestao.ARQUIVADA)
				.containsEntry("nova", StatusQuestao.RASCUNHO);
		assertThat(nova.getStatus()).isEqualTo(StatusQuestao.PUBLICADA);
		assertThat(anterior.getPublicadaEm()).as("a arquivada guarda a data dela").isEqualTo(MOMENTO.minusSeconds(60));
	}

	@Test
	void deve_recusar_na_entidade_publicar_o_que_nao_e_rascunho_e_arquivar_o_que_nao_e_publicada() {
		QuestaoVersao versao = pronta(prova, 1);

		assertThatThrownBy(versao::arquivar).isInstanceOf(IllegalStateException.class);
		versao.publicar(MOMENTO);
		assertThatThrownBy(() -> versao.publicar(MOMENTO)).isInstanceOf(IllegalStateException.class);
		prova.publicar(MOMENTO);
		assertThatThrownBy(() -> prova.publicar(MOMENTO)).isInstanceOf(IllegalStateException.class);
	}

	// ----- nova versão (D181) e publicação avulsa (D182). A troca no banco, a atomicidade e a concorrência estão no MySQL real -----

	/** A questão 3 com a versão 1 já publicada. */
	private QuestaoVersao publicadaExistente() {
		QuestaoVersao publicada = rascunhoExistente();
		publicada.publicar(MOMENTO.minusSeconds(60));
		return publicada;
	}

	/** O rascunho que sucede a publicada, completo, no subtópico da prova. */
	private QuestaoVersao sucessora(QuestaoVersao publicada, String gabarito, String imagemUrl) {
		return new QuestaoVersao(publicada.getQuestao(), subtopico, null, null, "Corrigida", imagemUrl,
				Map.of("A", "1", "B", "2", "C", "3", "D", "4", "E", "5"), gabarito, null, null);
	}

	private void aQuestaoExiste(Questao questao) {
		when(questoes.buscarProvaDaQuestao(questao.getPublicId())).thenReturn(Optional.of(prova.getPublicId()));
		when(provas.buscarComTrava(prova.getPublicId())).thenReturn(Optional.of(prova));
		when(questoes.findByPublicId(questao.getPublicId())).thenReturn(Optional.of(questao));
	}

	private void aVersaoNoStatus(Questao questao, StatusQuestao status, QuestaoVersao versao) {
		when(versoes.findByQuestaoAndStatus(questao, status)).thenReturn(Optional.ofNullable(versao));
	}

	@Test
	void deve_abrir_um_rascunho_com_copia_completa_da_publicada_e_a_maior_versao_mais_1() {
		QuestaoVersao publicada = publicadaExistente();
		Questao questao = publicada.getQuestao();
		subtopico.desativar();
		aQuestaoExiste(questao);
		aVersaoNoStatus(questao, StatusQuestao.RASCUNHO, null);
		aVersaoNoStatus(questao, StatusQuestao.PUBLICADA, publicada);
		when(versoes.buscarMaiorVersao(questao)).thenReturn(4);
		when(versoes.saveAndFlush(any(QuestaoVersao.class))).thenAnswer(c -> c.getArgument(0));

		QuestaoVersao nova = servico.criarVersao(questao.getPublicId());

		assertThat(nova).isNotSameAs(publicada);
		assertThat(nova.getVersao()).isEqualTo(5);
		assertThat(nova.getStatus()).isEqualTo(StatusQuestao.RASCUNHO);
		assertThat(nova.getPublicadaEm()).isNull();
		assertThat(nova.getQuestao()).isSameAs(questao);
		assertThat(nova.getSubtopico()).as("mesmo desativado").isSameAs(subtopico);
		assertThat(List.of(nova.getTextoApoio(), nova.getGrupoApoio(), nova.getEnunciado(), nova.getGabarito(),
				nova.getDificuldade(), nova.getExplicacao()))
				.containsExactly("Apoio", 1, "Original", "C", br.simulado.etec.catalogo.model.Dificuldade.FACIL, "Porque sim");
		assertThat(nova.getImagemUrl()).isNull();
		assertThat(nova.getAlternativas()).extracting(a -> a.getLetra() + " " + a.getTexto())
				.containsExactly("A um", "B dois", "C três", "D quatro", "E cinco");
		assertThat(nova.getAlternativas()).as("alternativas novas, não as da publicada")
				.allMatch(a -> publicada.getAlternativas().stream().noneMatch(original -> original == a));
		assertThat(publicada.getStatus()).isEqualTo(StatusQuestao.PUBLICADA);
		assertThat(publicada.getAlternativas()).hasSize(5);
		InOrder ordem = inOrder(questoes, transacoes, provas, versoes);
		ordem.verify(questoes).buscarProvaDaQuestao(questao.getPublicId());
		ordem.verify(transacoes).getTransaction(any());
		ordem.verify(provas).buscarComTrava(prova.getPublicId());
		ordem.verify(questoes).findByPublicId(questao.getPublicId());
		ordem.verify(versoes).findByQuestaoAndStatus(questao, StatusQuestao.RASCUNHO);
		ordem.verify(versoes).saveAndFlush(nova);
		ordem.verify(transacoes).commit(any());
	}

	@Test
	void deve_recusar_a_nova_versao_quando_ja_ha_rascunho_sem_gravar_nada() {
		QuestaoVersao rascunho = rascunhoExistente();
		aQuestaoExiste(rascunho.getQuestao());
		aVersaoNoStatus(rascunho.getQuestao(), StatusQuestao.RASCUNHO, rascunho);

		assertThatThrownBy(() -> servico.criarVersao(rascunho.getQuestao().getPublicId()))
				.isInstanceOf(ConflictException.class)
				.satisfies(e -> assertThat(codigoDe(e)).isEqualTo("QUESTAO_COM_RASCUNHO"));

		verify(versoes, never()).saveAndFlush(any(QuestaoVersao.class));
		verify(versoes, never()).buscarMaiorVersao(any());
	}

	@Test
	void deve_dar_conflito_na_nova_versao_quando_nao_ha_publicada_para_copiar() {
		Questao questao = rascunhoExistente().getQuestao();
		aQuestaoExiste(questao);
		aVersaoNoStatus(questao, StatusQuestao.RASCUNHO, null);
		aVersaoNoStatus(questao, StatusQuestao.PUBLICADA, null);

		assertThatThrownBy(() -> servico.criarVersao(questao.getPublicId())).isInstanceOf(ConflictException.class)
				.satisfies(e -> assertThat(codigoDe(e)).isEqualTo("CONFLITO"));

		verify(versoes, never()).saveAndFlush(any(QuestaoVersao.class));
	}

	@Test
	void deve_recusar_a_publicacao_avulsa_em_prova_em_rascunho_antes_de_olhar_as_versoes() {
		Questao questao = rascunhoExistente().getQuestao();
		aQuestaoExiste(questao);

		assertThatThrownBy(() -> servico.publicar(questao.getPublicId())).isInstanceOf(ConflictException.class)
				.satisfies(e -> assertThat(codigoDe(e)).isEqualTo("PROVA_NAO_PUBLICADA"));

		verify(versoes, never()).findByQuestaoAndStatus(any(), any());
		verify(versoes, never()).flush();
	}

	@Test
	void deve_recusar_a_publicacao_avulsa_quando_nao_ha_rascunho_aberto() {
		Questao questao = rascunhoExistente().getQuestao();
		prova.publicar(MOMENTO);
		aQuestaoExiste(questao);
		aVersaoNoStatus(questao, StatusQuestao.RASCUNHO, null);

		assertThatThrownBy(() -> servico.publicar(questao.getPublicId())).isInstanceOf(ConflictException.class)
				.satisfies(e -> assertThat(codigoDe(e)).isEqualTo("QUESTAO_SEM_RASCUNHO"));

		verify(versoes, never()).flush();
	}

	@Test
	void deve_recusar_a_publicacao_avulsa_com_todas_as_pendencias_sem_arquivar_a_publicada() {
		QuestaoVersao publicada = publicadaExistente();
		QuestaoVersao rascunho = sucessora(publicada, null, "https://fora.exemplo.invalid/a.png");
		prova.publicar(MOMENTO);
		subtopico.desativar();
		aQuestaoExiste(publicada.getQuestao());
		aVersaoNoStatus(publicada.getQuestao(), StatusQuestao.RASCUNHO, rascunho);

		assertThatThrownBy(() -> servico.publicar(publicada.getQuestao().getPublicId()))
				.isInstanceOfSatisfying(ConflictException.class, e -> {
					assertThat(codigoDe(e)).isEqualTo("QUESTAO_COM_PENDENCIAS");
					assertThat(e.getDetalhes()).containsEntry("questoesPendentes",
							List.of(new Pendencia(3, MotivoDePendencia.SEM_GABARITO),
									new Pendencia(3, MotivoDePendencia.SUBTOPICO_INATIVO),
									new Pendencia(3, MotivoDePendencia.IMAGEM_NAO_PERMITIDA)))
							.hasSize(1);
				});

		assertThat(publicada.getStatus()).isEqualTo(StatusQuestao.PUBLICADA);
		assertThat(rascunho.getStatus()).isEqualTo(StatusQuestao.RASCUNHO);
		verify(versoes, never()).flush();
	}

	@Test
	void deve_arquivar_e_mandar_ao_banco_antes_de_publicar_na_publicacao_avulsa() {
		QuestaoVersao publicada = publicadaExistente();
		QuestaoVersao rascunho = sucessora(publicada, "B", null);
		Questao questao = publicada.getQuestao();
		prova.publicar(MOMENTO);
		aQuestaoExiste(questao);
		aVersaoNoStatus(questao, StatusQuestao.RASCUNHO, rascunho);
		aVersaoNoStatus(questao, StatusQuestao.PUBLICADA, publicada);
		when(relogio.instant()).thenReturn(MOMENTO.plusNanos(123_456_789));
		Map<String, StatusQuestao> noFlush = new HashMap<>();
		org.mockito.Mockito.doAnswer(chamada -> {
			noFlush.put("publicada", publicada.getStatus());
			noFlush.put("rascunho", rascunho.getStatus());
			return null;
		}).when(versoes).flush();

		assertThat(servico.publicar(questao.getPublicId())).isSameAs(rascunho);

		assertThat(noFlush).containsEntry("publicada", StatusQuestao.ARQUIVADA).containsEntry("rascunho", StatusQuestao.RASCUNHO);
		assertThat(rascunho.getStatus()).isEqualTo(StatusQuestao.PUBLICADA);
		assertThat(rascunho.getPublicadaEm()).as("em milissegundos").isEqualTo(MOMENTO.plusMillis(123));
		assertThat(publicada.getPublicadaEm()).isEqualTo(MOMENTO.minusSeconds(60));
		InOrder ordem = inOrder(questoes, transacoes, provas, versoes);
		ordem.verify(questoes).buscarProvaDaQuestao(questao.getPublicId());
		ordem.verify(transacoes).getTransaction(any());
		ordem.verify(provas).buscarComTrava(prova.getPublicId());
		ordem.verify(questoes).findByPublicId(questao.getPublicId());
		ordem.verify(versoes).flush();
		ordem.verify(transacoes).commit(any());
	}

	@Test
	void deve_dar_404_nas_rotas_de_versao_sem_abrir_transacao_quando_a_questao_nao_existe() {
		UUID id = UUID.randomUUID();
		when(questoes.buscarProvaDaQuestao(id)).thenReturn(Optional.empty());

		for (org.assertj.core.api.ThrowableAssert.ThrowingCallable chamada : List
				.<org.assertj.core.api.ThrowableAssert.ThrowingCallable>of(() -> servico.criarVersao(id), () -> servico.publicar(id))) {
			assertThatThrownBy(chamada).isInstanceOf(ResourceNotFoundException.class)
					.satisfies(e -> assertThat(codigoDe(e)).isEqualTo("QUESTAO_NAO_ENCONTRADA"));
		}

		verify(transacoes, never()).getTransaction(any());
	}

	// ----- apagar (D184 e D185) -----

	@Test
	void deve_apagar_a_questao_nunca_publicada_com_a_versao_antes_da_identidade() {
		QuestaoVersao rascunho = rascunhoExistente();
		Questao questao = rascunho.getQuestao();
		aQuestaoExiste(questao);
		aVersaoNoStatus(questao, StatusQuestao.RASCUNHO, rascunho);
		when(versoes.existsByQuestaoAndStatusNot(questao, StatusQuestao.RASCUNHO)).thenReturn(false);

		servico.apagar(questao.getPublicId());

		InOrder ordem = inOrder(questoes, transacoes, provas, versoes);
		ordem.verify(questoes).buscarProvaDaQuestao(questao.getPublicId());
		ordem.verify(transacoes).getTransaction(any());
		ordem.verify(provas).buscarComTrava(prova.getPublicId());
		ordem.verify(questoes).findByPublicId(questao.getPublicId());
		ordem.verify(versoes).delete(rascunho);
		ordem.verify(versoes).flush();
		ordem.verify(questoes).delete(questao);
		ordem.verify(transacoes).commit(any());
	}

	@Test
	void deve_apagar_so_o_rascunho_da_questao_que_ja_foi_publicada() {
		QuestaoVersao publicada = publicadaExistente();
		QuestaoVersao rascunho = sucessora(publicada, "B", null);
		Questao questao = publicada.getQuestao();
		prova.publicar(MOMENTO);
		aQuestaoExiste(questao);
		aVersaoNoStatus(questao, StatusQuestao.RASCUNHO, rascunho);
		when(versoes.existsByQuestaoAndStatusNot(questao, StatusQuestao.RASCUNHO)).thenReturn(true);

		servico.apagar(questao.getPublicId());

		verify(versoes).delete(rascunho);
		verify(versoes, never()).delete(publicada);
		verify(questoes, never()).delete(any(Questao.class));
		assertThat(publicada.getStatus()).isEqualTo(StatusQuestao.PUBLICADA);
	}

	@Test
	void deve_recusar_apagar_a_questao_ja_publicada_que_nao_tem_rascunho() {
		Questao questao = publicadaExistente().getQuestao();
		aQuestaoExiste(questao);
		aVersaoNoStatus(questao, StatusQuestao.RASCUNHO, null);
		when(versoes.existsByQuestaoAndStatusNot(questao, StatusQuestao.RASCUNHO)).thenReturn(true);

		assertThatThrownBy(() -> servico.apagar(questao.getPublicId())).isInstanceOf(ConflictException.class)
				.satisfies(e -> assertThat(codigoDe(e)).isEqualTo("QUESTAO_SEM_RASCUNHO"));

		verify(versoes, never()).delete(any(QuestaoVersao.class));
		verify(questoes, never()).delete(any(Questao.class));
	}

	@Test
	void deve_recusar_apagar_a_questao_nunca_publicada_de_uma_prova_publicada() {
		QuestaoVersao rascunho = rascunhoExistente();
		Questao questao = rascunho.getQuestao();
		prova.publicar(MOMENTO);
		aQuestaoExiste(questao);
		aVersaoNoStatus(questao, StatusQuestao.RASCUNHO, rascunho);
		when(versoes.existsByQuestaoAndStatusNot(questao, StatusQuestao.RASCUNHO)).thenReturn(false);

		assertThatThrownBy(() -> servico.apagar(questao.getPublicId())).isInstanceOf(ConflictException.class)
				.satisfies(e -> assertThat(codigoDe(e)).isEqualTo("PROVA_NAO_EDITAVEL"));

		verify(versoes, never()).delete(any(QuestaoVersao.class));
		verify(questoes, never()).delete(any(Questao.class));
	}

	@Test
	void deve_dar_404_ao_apagar_questao_que_nao_existe_sem_abrir_transacao() {
		UUID id = UUID.randomUUID();
		when(questoes.buscarProvaDaQuestao(id)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> servico.apagar(id)).isInstanceOf(ResourceNotFoundException.class)
				.satisfies(e -> assertThat(codigoDe(e)).isEqualTo("QUESTAO_NAO_ENCONTRADA"));

		verify(transacoes, never()).getTransaction(any());
	}

	@Test
	void deve_apagar_somente_os_rascunhos_e_depois_as_questoes_da_prova_com_flush_entre_eles() {
		Prova duas = provaCom(2);
		QuestaoVersao um = pronta(duas, 1);
		QuestaoVersao dois = pronta(duas, 2);
		// a conferência dos já publicados responde vazio de propósito: mesmo assim a publicada não é carregada para apagar
		QuestaoVersao publicada = pronta(duas, 2);
		publicada.publicar(MOMENTO);
		aProvaTem(duas, um, publicada, dois);
		when(versoes.buscarNumerosJaPublicados(duas)).thenReturn(List.of());

		assertThat(servico.apagarQuestoesDaProva(duas)).isEmpty();

		InOrder ordem = inOrder(versoes, questoes);
		ordem.verify(versoes).deleteAll(List.of(um, dois));
		ordem.verify(versoes).flush();
		ordem.verify(questoes).deleteAll(List.of(um.getQuestao(), publicada.getQuestao(), dois.getQuestao()));
		ordem.verify(questoes).flush();
	}

	@Test
	void deve_nao_apagar_nada_quando_alguma_questao_da_prova_ja_foi_publicada() {
		Prova duas = provaCom(2);
		when(versoes.buscarNumerosJaPublicados(duas)).thenReturn(List.of(2));

		assertThat(servico.apagarQuestoesDaProva(duas)).containsExactly(2);

		verify(versoes, never()).deleteAll(any());
		verify(questoes, never()).deleteAll(any());
	}

}
