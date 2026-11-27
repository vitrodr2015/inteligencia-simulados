package br.simulado.etec.catalogo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

import org.hibernate.exception.ConstraintViolationException;
import org.hibernate.exception.ConstraintViolationException.ConstraintKind;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import br.simulado.etec.catalogo.dto.ProvaRequest;
import br.simulado.etec.catalogo.model.ProgramaDeProva;
import br.simulado.etec.catalogo.model.Prova;
import br.simulado.etec.catalogo.model.StatusProva;
import br.simulado.etec.catalogo.repository.ProgramaRepository;
import br.simulado.etec.catalogo.repository.ProvaRepository;
import br.simulado.etec.shared.exception.CodigoErro;
import br.simulado.etec.shared.exception.ConflictException;
import br.simulado.etec.shared.exception.ResourceNotFoundException;
import br.simulado.etec.shared.programa.Programa;
import org.mockito.InOrder;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.Instant;
import br.simulado.etec.catalogo.service.QuestaoService.MotivoDePendencia;
import br.simulado.etec.catalogo.service.QuestaoService.Pendencia;

@ExtendWith(MockitoExtension.class)
class ProvaServiceTest {

	@Mock
	private ProvaRepository repositorio;

	@Mock
	private ProgramaRepository programas;

	@Mock
	private br.simulado.etec.catalogo.repository.QuestaoRepository questoes;

	@Mock
	private QuestaoService servicoDeQuestoes;

	@Mock
	private java.time.Clock relogio;

	@InjectMocks
	private ProvaService servico;

	private final ProgramaDeProva etec = new ProgramaDeProva(Programa.ETEC, "ETEC");

	private static ProvaRequest completa() {
		return new ProvaRequest("Vestibulinho 2026", "Vunesp", 2026, 1, 180, 50, "https://exemplo.invalid/prova");
	}

	private static ProvaRequest semIdentidade() {
		return new ProvaRequest("Simulado solto", null, null, null, 120, 40, null);
	}

	private static DataIntegrityViolationException violacao(ConstraintKind tipo, String restricao) {
		return new DataIntegrityViolationException("erro de integridade",
				new ConstraintViolationException("erro", new SQLException("x"), "insert", tipo, restricao));
	}

	// ----- criar -----

	@Test
	void deve_criar_a_prova_em_rascunho_no_programa_etec_quando_os_dados_sao_validos() {
		when(programas.findByCodigo("ETEC")).thenReturn(Optional.of(etec));
		when(repositorio.existsByProgramaAndBancaAndAnoAndSemestre(etec, "Vunesp", 2026, 1)).thenReturn(false);
		when(repositorio.saveAndFlush(any(Prova.class))).thenAnswer(chamada -> chamada.getArgument(0));

		Prova criada = servico.criar(completa());

		assertThat(criada.getTitulo()).isEqualTo("Vestibulinho 2026");
		assertThat(criada.getBanca()).isEqualTo("Vunesp");
		assertThat(criada.getAno()).isEqualTo(2026);
		assertThat(criada.getSemestre()).isEqualTo(1);
		assertThat(criada.getDuracaoMinutos()).isEqualTo(180);
		assertThat(criada.getQuantidadeQuestoes()).isEqualTo(50);
		assertThat(criada.getStatus()).as("toda prova nasce em RASCUNHO (D149)").isEqualTo(StatusProva.RASCUNHO);
		assertThat(criada.getPublicadaEm()).isNull();
		assertThat(criada.getPublicId()).isNotNull();
		assertThat(criada.getPrograma()).as("a prova nasce no programa ETEC (D143)").isSameAs(etec);
	}

	@Test
	void deve_recusar_com_409_prova_duplicada_quando_os_tres_campos_ja_existem_na_checagem_previa() {
		when(programas.findByCodigo("ETEC")).thenReturn(Optional.of(etec));
		when(repositorio.existsByProgramaAndBancaAndAnoAndSemestre(etec, "Vunesp", 2026, 1)).thenReturn(true);

		assertThatThrownBy(() -> servico.criar(completa())).isInstanceOf(ConflictException.class)
				.satisfies(e -> assertThat(((ConflictException) e).getCodigo()).isEqualTo(CodigoErro.PROVA_DUPLICADA));
		verify(repositorio, never()).saveAndFlush(any());
	}

	@Test
	void deve_nao_consultar_a_unicidade_quando_banca_ano_e_semestre_nao_estao_todos_preenchidos() {
		when(programas.findByCodigo("ETEC")).thenReturn(Optional.of(etec));
		when(repositorio.saveAndFlush(any(Prova.class))).thenAnswer(chamada -> chamada.getArgument(0));

		servico.criar(semIdentidade());
		servico.criar(new ProvaRequest("Só banca", "Vunesp", null, null, 90, 30, null));
		servico.criar(new ProvaRequest("Banca e ano", "Vunesp", 2026, null, 90, 30, null));
		servico.criar(new ProvaRequest("Ano e semestre", null, 2026, 2, 90, 30, null));

		verify(repositorio, never()).existsByProgramaAndBancaAndAnoAndSemestre(any(), anyString(), any(), any());
	}

	@Test
	void deve_traduzir_para_409_so_a_violacao_do_indice_da_prova_quando_ha_corrida() {
		when(programas.findByCodigo("ETEC")).thenReturn(Optional.of(etec));
		when(repositorio.existsByProgramaAndBancaAndAnoAndSemestre(etec, "Vunesp", 2026, 1)).thenReturn(false);
		when(repositorio.saveAndFlush(any(Prova.class)))
				.thenThrow(violacao(ConstraintKind.UNIQUE, "prova.uk_prova_programa_banca_ano_semestre"));

		assertThatThrownBy(() -> servico.criar(completa())).isInstanceOf(ConflictException.class)
				.satisfies(e -> assertThat(((ConflictException) e).getCodigo()).isEqualTo(CodigoErro.PROVA_DUPLICADA));
	}

	@Test
	void deve_deixar_subir_a_violacao_de_outra_restricao_quando_nao_e_o_indice_da_prova() {
		when(programas.findByCodigo("ETEC")).thenReturn(Optional.of(etec));
		when(repositorio.existsByProgramaAndBancaAndAnoAndSemestre(etec, "Vunesp", 2026, 1)).thenReturn(false);
		DataIntegrityViolationException outra = violacao(ConstraintKind.CHECK, "prova.ck_prova_ano");
		when(repositorio.saveAndFlush(any(Prova.class))).thenThrow(outra);

		assertThatThrownBy(() -> servico.criar(completa())).isSameAs(outra);
	}

	@Test
	void deve_deixar_subir_outra_violacao_unica_quando_nao_e_a_da_prova() {
		when(programas.findByCodigo("ETEC")).thenReturn(Optional.of(etec));
		DataIntegrityViolationException outra = violacao(ConstraintKind.UNIQUE, "prova.uk_prova_public_id");
		when(repositorio.saveAndFlush(any(Prova.class))).thenThrow(outra);

		assertThatThrownBy(() -> servico.criar(semIdentidade())).isSameAs(outra);
	}

	@Test
	void deve_falhar_com_erro_claro_quando_o_programa_etec_nao_existe() {
		when(programas.findByCodigo("ETEC")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> servico.criar(completa())).isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("ETEC");
	}

	// ----- buscar -----

	@Test
	void deve_devolver_a_prova_quando_o_id_existe() {
		UUID id = UUID.randomUUID();
		Prova prova = new Prova(etec, "T", null, null, null, 60, 10, null);
		when(repositorio.findByPublicId(id)).thenReturn(Optional.of(prova));

		assertThat(servico.buscar(id)).isSameAs(prova);
	}

	@Test
	void deve_lancar_404_prova_nao_encontrada_quando_o_id_nao_existe() {
		UUID id = UUID.randomUUID();
		when(repositorio.findByPublicId(id)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> servico.buscar(id)).isInstanceOf(ResourceNotFoundException.class)
				.satisfies(e -> assertThat(((ResourceNotFoundException) e).getCodigo())
						.isEqualTo(CodigoErro.PROVA_NAO_ENCONTRADA));
	}

	// ----- as regras que a request aplica antes do service -----

	@Test
	void deve_transformar_banca_e_fonte_em_branco_em_nulas_e_aparar_os_textos() {
		ProvaRequest dados = new ProvaRequest("  Título  ", "   ", 2026, 1, 60, 10, "   ");

		assertThat(dados.titulo()).isEqualTo("Título");
		assertThat(dados.banca()).as("banca em branco vira nula (D104)").isNull();
		assertThat(dados.fonteUrl()).isNull();
		assertThat(new ProvaRequest("T", "  Vunesp ", null, null, 60, 10, null).banca()).isEqualTo("Vunesp");
	}

	// ----- questoesCadastradas -----

	private Prova provaComId(long id) {
		Prova prova = new Prova(etec, "T", null, null, null, 60, 10, null);
		org.springframework.test.util.ReflectionTestUtils.setField(prova, "id", id);
		return prova;
	}

	private static br.simulado.etec.catalogo.repository.QuestaoRepository.ContagemPorProva contagem(long provaId, long total) {
		return new br.simulado.etec.catalogo.repository.QuestaoRepository.ContagemPorProva() {
			@Override
			public Long getProvaId() {
				return provaId;
			}

			@Override
			public long getTotal() {
				return total;
			}
		};
	}

	@Test
	void deve_contar_as_questoes_de_todas_as_provas_numa_unica_consulta() {
		when(questoes.contarPorProva(java.util.List.of(1L, 2L, 3L)))
				.thenReturn(java.util.List.of(contagem(1L, 4), contagem(3L, 1)));

		java.util.Map<Long, Long> mapa = servico.contarQuestoes(java.util.List.of(provaComId(1), provaComId(2), provaComId(3)));

		assertThat(mapa).containsEntry(1L, 4L).containsEntry(3L, 1L).doesNotContainKey(2L);
		verify(questoes, org.mockito.Mockito.times(1)).contarPorProva(any());
	}

	@Test
	void deve_nao_consultar_o_banco_quando_nao_ha_prova_para_contar() {
		assertThat(servico.contarQuestoes(java.util.List.of())).isEmpty();

		verify(questoes, never()).contarPorProva(any());
	}

	@Test
	void deve_ter_o_teto_de_300_questoes_como_constante_unica() {
		assertThat(Prova.QUANTIDADE_MAXIMA_DE_QUESTOES).isEqualTo(300);
	}

	// ----- atualizar (PUT, D170) -----

	private Prova provaGravada() {
		return new Prova(etec, "Antiga", "Vunesp", 2025, 2, 120, 50, "https://exemplo.invalid/antiga");
	}

	@Test
	void deve_travar_a_prova_antes_de_qualquer_outra_leitura_no_put() {
		Prova prova = provaGravada();
		when(repositorio.buscarComTrava(prova.getPublicId())).thenReturn(Optional.of(prova));
		when(questoes.buscarMaiorNumero(prova)).thenReturn(Optional.empty());
		when(repositorio.saveAndFlush(prova)).thenReturn(prova);

		servico.atualizar(prova.getPublicId(), completa());

		InOrder ordem = org.mockito.Mockito.inOrder(repositorio, questoes);
		ordem.verify(repositorio).buscarComTrava(prova.getPublicId());
		ordem.verify(repositorio).existsByProgramaAndBancaAndAnoAndSemestreAndPublicIdNot(etec, "Vunesp", 2026, 1,
				prova.getPublicId());
		ordem.verify(questoes).buscarMaiorNumero(prova);
		ordem.verify(repositorio).saveAndFlush(prova);
		verify(repositorio, never()).findByPublicId(any());
	}

	@Test
	void deve_substituir_todos_os_dados_editaveis_e_apagar_o_que_foi_omitido() {
		Prova prova = provaGravada();
		when(repositorio.buscarComTrava(prova.getPublicId())).thenReturn(Optional.of(prova));
		when(questoes.buscarMaiorNumero(prova)).thenReturn(Optional.empty());
		when(repositorio.saveAndFlush(prova)).thenReturn(prova);

		Prova salva = servico.atualizar(prova.getPublicId(), semIdentidade());

		assertThat(salva.getTitulo()).isEqualTo("Simulado solto");
		assertThat(salva.getBanca()).isNull();
		assertThat(salva.getAno()).isNull();
		assertThat(salva.getSemestre()).isNull();
		assertThat(salva.getFonteUrl()).isNull();
		assertThat(salva.getDuracaoMinutos()).isEqualTo(120);
		assertThat(salva.getQuantidadeQuestoes()).isEqualTo(40);
		assertThat(salva.getStatus()).isEqualTo(StatusProva.RASCUNHO);
		verify(repositorio, never()).existsByProgramaAndBancaAndAnoAndSemestreAndPublicIdNot(any(), any(), any(), any(), any());
	}

	@Test
	void deve_lancar_404_no_put_quando_a_prova_nao_existe() {
		UUID id = UUID.randomUUID();
		when(repositorio.buscarComTrava(id)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> servico.atualizar(id, completa())).isInstanceOf(ResourceNotFoundException.class)
				.satisfies(e -> assertThat(((ResourceNotFoundException) e).getCodigo()).isEqualTo(CodigoErro.PROVA_NAO_ENCONTRADA));
	}

	@Test
	void deve_recusar_com_409_prova_nao_editavel_antes_de_qualquer_outra_regra_no_put() {
		Prova publicada = provaGravada();
		ReflectionTestUtils.setField(publicada, "status", StatusProva.PUBLICADA);
		when(repositorio.buscarComTrava(publicada.getPublicId())).thenReturn(Optional.of(publicada));

		assertThatThrownBy(() -> servico.atualizar(publicada.getPublicId(), completa())).isInstanceOf(ConflictException.class)
				.satisfies(e -> assertThat(((ConflictException) e).getCodigo()).isEqualTo(CodigoErro.PROVA_NAO_EDITAVEL));
		assertThat(publicada.getTitulo()).isEqualTo("Antiga");
		verify(questoes, never()).buscarMaiorNumero(any());
		verify(repositorio, never()).saveAndFlush(any());
	}

	@Test
	void deve_recusar_com_409_prova_duplicada_antes_da_regra_da_quantidade() {
		Prova prova = provaGravada();
		when(repositorio.buscarComTrava(prova.getPublicId())).thenReturn(Optional.of(prova));
		when(repositorio.existsByProgramaAndBancaAndAnoAndSemestreAndPublicIdNot(etec, "Vunesp", 2026, 1, prova.getPublicId()))
				.thenReturn(true);

		assertThatThrownBy(() -> servico.atualizar(prova.getPublicId(), completa()))
				.satisfies(e -> assertThat(((ConflictException) e).getCodigo()).isEqualTo(CodigoErro.PROVA_DUPLICADA));
		assertThat(prova.getTitulo()).isEqualTo("Antiga");
		verify(repositorio, never()).saveAndFlush(any());
	}

	@Test
	void deve_recusar_a_quantidade_abaixo_do_maior_numero_cadastrado_informando_qual_e() {
		Prova prova = provaGravada();
		when(repositorio.buscarComTrava(prova.getPublicId())).thenReturn(Optional.of(prova));
		when(questoes.buscarMaiorNumero(prova)).thenReturn(Optional.of(41));

		assertThatThrownBy(() -> servico.atualizar(prova.getPublicId(), semIdentidade()))
				.isInstanceOfSatisfying(ConflictException.class, e -> {
					assertThat(e.getCodigo()).isEqualTo(CodigoErro.PROVA_COM_QUESTOES_ACIMA_DA_QUANTIDADE);
					assertThat(e.getDetalhes()).containsEntry("maiorNumero", 41).hasSize(1);
				});
		assertThat(prova.getQuantidadeQuestoes()).isEqualTo(50);
		verify(repositorio, never()).saveAndFlush(any());
	}

	@Test
	void deve_aceitar_a_quantidade_igual_ao_maior_numero_cadastrado() {
		Prova prova = provaGravada();
		when(repositorio.buscarComTrava(prova.getPublicId())).thenReturn(Optional.of(prova));
		when(questoes.buscarMaiorNumero(prova)).thenReturn(Optional.of(40));
		when(repositorio.saveAndFlush(prova)).thenReturn(prova);

		assertThat(servico.atualizar(prova.getPublicId(), semIdentidade()).getQuantidadeQuestoes()).isEqualTo(40);
	}

	@Test
	void deve_traduzir_para_409_so_a_violacao_do_indice_da_prova_na_corrida_do_put() {
		Prova prova = provaGravada();
		when(repositorio.buscarComTrava(prova.getPublicId())).thenReturn(Optional.of(prova));
		when(questoes.buscarMaiorNumero(prova)).thenReturn(Optional.empty());
		when(repositorio.saveAndFlush(prova)).thenThrow(violacao(ConstraintKind.UNIQUE, ProvaService.UK_PROVA));

		assertThatThrownBy(() -> servico.atualizar(prova.getPublicId(), completa()))
				.satisfies(e -> assertThat(((ConflictException) e).getCodigo()).isEqualTo(CodigoErro.PROVA_DUPLICADA));
	}

	@Test
	void deve_deixar_subir_outra_violacao_na_gravacao_do_put() {
		Prova prova = provaGravada();
		when(repositorio.buscarComTrava(prova.getPublicId())).thenReturn(Optional.of(prova));
		when(questoes.buscarMaiorNumero(prova)).thenReturn(Optional.empty());
		DataIntegrityViolationException outra = violacao(ConstraintKind.UNIQUE, "uk_outra");
		when(repositorio.saveAndFlush(prova)).thenThrow(outra);

		assertThatThrownBy(() -> servico.atualizar(prova.getPublicId(), completa())).isSameAs(outra);
	}

	// ----- publicar (D178) -----

	@Test
	void deve_travar_a_prova_antes_da_cascata_e_publicar_tudo_com_o_mesmo_momento_em_milissegundos() {
		Prova prova = provaGravada();
		when(repositorio.buscarComTrava(prova.getPublicId())).thenReturn(Optional.of(prova));
		when(relogio.instant()).thenReturn(Instant.parse("2026-11-28T10:00:00.123456789Z"));
		Instant momento = Instant.parse("2026-11-28T10:00:00.123Z");
		when(servicoDeQuestoes.publicarQuestoesDaProva(prova, momento)).thenReturn(java.util.List.of());
		when(repositorio.saveAndFlush(prova)).thenReturn(prova);

		Prova publicada = servico.publicar(prova.getPublicId());

		assertThat(publicada.getStatus()).isEqualTo(StatusProva.PUBLICADA);
		assertThat(publicada.getPublicadaEm()).isEqualTo(momento);
		InOrder ordem = org.mockito.Mockito.inOrder(repositorio, servicoDeQuestoes);
		ordem.verify(repositorio).buscarComTrava(prova.getPublicId());
		ordem.verify(servicoDeQuestoes).publicarQuestoesDaProva(prova, momento);
		ordem.verify(repositorio).saveAndFlush(prova);
		verify(repositorio, never()).findByPublicId(any());
	}

	@Test
	void deve_dar_409_com_todas_as_pendencias_e_nao_publicar_a_prova() {
		Prova prova = provaGravada();
		when(repositorio.buscarComTrava(prova.getPublicId())).thenReturn(Optional.of(prova));
		when(relogio.instant()).thenReturn(Instant.parse("2026-11-28T10:00:00Z"));
		java.util.List<Pendencia> pendencias = java.util.List.of(new Pendencia(2, MotivoDePendencia.QUESTAO_AUSENTE),
				new Pendencia(3, MotivoDePendencia.SEM_GABARITO));
		when(servicoDeQuestoes.publicarQuestoesDaProva(any(), any())).thenReturn(pendencias);

		assertThatThrownBy(() -> servico.publicar(prova.getPublicId())).isInstanceOfSatisfying(ConflictException.class, e -> {
			assertThat(e.getCodigo()).isEqualTo(CodigoErro.PROVA_COM_QUESTOES_PENDENTES);
			assertThat(e.getDetalhes()).containsEntry("questoesPendentes", pendencias).hasSize(1);
		});
		assertThat(prova.getStatus()).isEqualTo(StatusProva.RASCUNHO);
		assertThat(prova.getPublicadaEm()).isNull();
		verify(repositorio, never()).saveAndFlush(any());
	}

	@Test
	void deve_recusar_publicar_a_prova_ja_publicada_sem_tocar_nas_questoes() {
		Prova prova = provaGravada();
		prova.publicar(Instant.parse("2026-11-01T10:00:00Z"));
		when(repositorio.buscarComTrava(prova.getPublicId())).thenReturn(Optional.of(prova));

		assertThatThrownBy(() -> servico.publicar(prova.getPublicId()))
				.satisfies(e -> assertThat(((ConflictException) e).getCodigo()).isEqualTo(CodigoErro.PROVA_NAO_EDITAVEL));
		assertThat(prova.getPublicadaEm()).isEqualTo(Instant.parse("2026-11-01T10:00:00Z"));
		org.mockito.Mockito.verifyNoInteractions(servicoDeQuestoes);
	}

	@Test
	void deve_lancar_404_ao_publicar_prova_que_nao_existe() {
		UUID id = UUID.randomUUID();
		when(repositorio.buscarComTrava(id)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> servico.publicar(id)).isInstanceOf(ResourceNotFoundException.class);
		org.mockito.Mockito.verifyNoInteractions(servicoDeQuestoes);
	}

}
