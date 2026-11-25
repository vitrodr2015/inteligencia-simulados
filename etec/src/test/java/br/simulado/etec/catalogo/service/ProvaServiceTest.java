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

@ExtendWith(MockitoExtension.class)
class ProvaServiceTest {

	@Mock
	private ProvaRepository repositorio;

	@Mock
	private ProgramaRepository programas;

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

	@Test
	void deve_ter_o_teto_de_300_questoes_como_constante_unica() {
		assertThat(Prova.QUANTIDADE_MAXIMA_DE_QUESTOES).isEqualTo(300);
	}

}
