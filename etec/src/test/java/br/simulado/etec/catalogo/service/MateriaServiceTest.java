package br.simulado.etec.catalogo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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

import br.simulado.etec.shared.security.EscopoDeLeitura;
import br.simulado.etec.catalogo.dto.MateriaRequest;
import br.simulado.etec.catalogo.model.Materia;
import br.simulado.etec.catalogo.model.ProgramaDeProva;
import br.simulado.etec.catalogo.repository.MateriaRepository;
import br.simulado.etec.catalogo.repository.ProgramaRepository;
import br.simulado.etec.shared.exception.CodigoErro;
import br.simulado.etec.shared.exception.ConflictException;
import br.simulado.etec.shared.exception.ResourceNotFoundException;
import br.simulado.etec.shared.programa.Programa;

@ExtendWith(MockitoExtension.class)
class MateriaServiceTest {

	@Mock
	private MateriaRepository repositorio;

	@Mock
	private ProgramaRepository programas;

	@InjectMocks
	private MateriaService servico;

	private final ProgramaDeProva etec = new ProgramaDeProva(Programa.ETEC, "ETEC");

	private static MateriaRequest dados(String nome) {
		return new MateriaRequest(nome, "Descrição de teste");
	}

	// ----- criar -----

	@Test
	void deve_criar_a_materia_ativa_quando_o_nome_e_novo() {
		when(programas.findByCodigo("ETEC")).thenReturn(Optional.of(etec));
		when(repositorio.existsByProgramaAndNome(etec, "Matemática")).thenReturn(false);
		when(repositorio.saveAndFlush(any(Materia.class))).thenAnswer(chamada -> chamada.getArgument(0));

		Materia criada = servico.criar(dados("Matemática"));

		assertThat(criada.getNome()).isEqualTo("Matemática");
		assertThat(criada.getDescricao()).isEqualTo("Descrição de teste");
		assertThat(criada.isAtivo()).isTrue();
		assertThat(criada.getPublicId()).isNotNull();
		assertThat(criada.getPrograma()).as("a matéria nasce no programa ETEC (D143)").isSameAs(etec);
	}

	@Test
	void deve_recusar_com_409_quando_o_nome_ja_existe_na_checagem_previa() {
		when(programas.findByCodigo("ETEC")).thenReturn(Optional.of(etec));
		when(repositorio.existsByProgramaAndNome(etec, "Matemática")).thenReturn(true);

		assertThatThrownBy(() -> servico.criar(dados("Matemática")))
				.isInstanceOfSatisfying(ConflictException.class,
						erro -> assertThat(erro.getCodigo()).isEqualTo(CodigoErro.MATERIA_NOME_DUPLICADO));
		verify(repositorio, never()).saveAndFlush(any());
	}

	@Test
	void deve_recusar_com_409_quando_o_indice_unico_barra_a_corrida() {
		when(programas.findByCodigo("ETEC")).thenReturn(Optional.of(etec));
		when(repositorio.existsByProgramaAndNome(etec, "Matemática")).thenReturn(false);
		when(repositorio.saveAndFlush(any(Materia.class)))
				.thenThrow(violacao(ConstraintKind.UNIQUE, "materia.uk_materia_programa_nome"));

		assertThatThrownBy(() -> servico.criar(dados("Matemática")))
				.isInstanceOfSatisfying(ConflictException.class,
						erro -> assertThat(erro.getCodigo()).isEqualTo(CodigoErro.MATERIA_NOME_DUPLICADO));
	}

	@Test
	void deve_deixar_subir_sem_traduzir_a_violacao_que_nao_e_a_do_nome_da_materia() {
		when(programas.findByCodigo("ETEC")).thenReturn(Optional.of(etec));
		when(repositorio.existsByProgramaAndNome(etec, "Matemática")).thenReturn(false);
		DataIntegrityViolationException naoNulo = violacao(ConstraintKind.NOT_NULL, "programa_id");
		DataIntegrityViolationException outraUnica = violacao(ConstraintKind.UNIQUE, "materia.uk_materia_public_id");

		when(repositorio.saveAndFlush(any(Materia.class))).thenThrow(naoNulo);
		assertThatThrownBy(() -> servico.criar(dados("Matemática"))).isSameAs(naoNulo);

		when(repositorio.saveAndFlush(any(Materia.class))).thenThrow(outraUnica);
		assertThatThrownBy(() -> servico.criar(dados("Matemática"))).isSameAs(outraUnica);
	}

	@Test
	void deve_falhar_com_erro_claro_quando_o_programa_etec_nao_existe_na_tabela() {
		when(programas.findByCodigo("ETEC")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> servico.criar(dados("Matemática"))).isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("ETEC");
		verify(repositorio, never()).saveAndFlush(any());
	}

	// ----- buscar -----

	@Test
	void deve_devolver_a_materia_quando_o_id_existe() {
		Materia materia = new Materia(etec, "Física", null);
		when(repositorio.findByPublicId(materia.getPublicId())).thenReturn(Optional.of(materia));

		assertThat(servico.buscar(materia.getPublicId(), EscopoDeLeitura.TUDO)).isSameAs(materia);
	}

	@Test
	void deve_lancar_404_quando_o_id_nao_existe() {
		UUID id = UUID.randomUUID();
		when(repositorio.findByPublicId(id)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> servico.buscar(id, EscopoDeLeitura.TUDO))
				.isInstanceOfSatisfying(ResourceNotFoundException.class,
						erro -> assertThat(erro.getCodigo()).isEqualTo(CodigoErro.MATERIA_NAO_ENCONTRADA));
	}

	// ----- atualizar -----

	@Test
	void deve_trocar_nome_e_descricao_sem_mexer_no_ativo_quando_atualiza() {
		Materia materia = new Materia(etec, "Fisica", "antiga");
		materia.desativar();
		when(repositorio.findByPublicId(materia.getPublicId())).thenReturn(Optional.of(materia));
		when(repositorio.existsByProgramaAndNomeAndPublicIdNot(etec, "Física", materia.getPublicId())).thenReturn(false);
		when(repositorio.saveAndFlush(materia)).thenReturn(materia);

		Materia atualizada = servico.atualizar(materia.getPublicId(), new MateriaRequest("Física", "nova"));

		assertThat(atualizada.getNome()).isEqualTo("Física");
		assertThat(atualizada.getDescricao()).isEqualTo("nova");
		assertThat(atualizada.isAtivo()).isFalse();
	}

	@Test
	void deve_permitir_o_proprio_nome_quando_atualiza_o_mesmo_registro() {
		Materia materia = new Materia(etec, "Física", "antiga");
		when(repositorio.findByPublicId(materia.getPublicId())).thenReturn(Optional.of(materia));
		// outro registro com o mesmo nome: não existe (o próprio registro é excluído da checagem)
		when(repositorio.existsByProgramaAndNomeAndPublicIdNot(etec, "Física", materia.getPublicId())).thenReturn(false);
		when(repositorio.saveAndFlush(materia)).thenReturn(materia);

		Materia atualizada = servico.atualizar(materia.getPublicId(), new MateriaRequest("Física", "outra"));

		assertThat(atualizada.getNome()).isEqualTo("Física");
		assertThat(atualizada.getDescricao()).isEqualTo("outra");
	}

	@Test
	void deve_recusar_com_409_quando_outro_registro_ja_tem_o_nome() {
		Materia materia = new Materia(etec, "Física", null);
		when(repositorio.findByPublicId(materia.getPublicId())).thenReturn(Optional.of(materia));
		when(repositorio.existsByProgramaAndNomeAndPublicIdNot(etec, "Química", materia.getPublicId())).thenReturn(true);

		assertThatThrownBy(() -> servico.atualizar(materia.getPublicId(), dados("Química")))
				.isInstanceOfSatisfying(ConflictException.class,
						erro -> assertThat(erro.getCodigo()).isEqualTo(CodigoErro.MATERIA_NOME_DUPLICADO));
		assertThat(materia.getNome()).isEqualTo("Física");
	}

	@Test
	void deve_recusar_com_409_quando_o_indice_unico_barra_a_corrida_na_atualizacao() {
		Materia materia = new Materia(etec, "Física", null);
		when(repositorio.findByPublicId(materia.getPublicId())).thenReturn(Optional.of(materia));
		when(repositorio.existsByProgramaAndNomeAndPublicIdNot(etec, "Química", materia.getPublicId())).thenReturn(false);
		when(repositorio.saveAndFlush(materia)).thenThrow(violacao(ConstraintKind.UNIQUE, "materia.uk_materia_programa_nome"));

		assertThatThrownBy(() -> servico.atualizar(materia.getPublicId(), dados("Química")))
				.isInstanceOfSatisfying(ConflictException.class,
						erro -> assertThat(erro.getCodigo()).isEqualTo(CodigoErro.MATERIA_NOME_DUPLICADO));
	}

	@Test
	void deve_lancar_404_quando_atualiza_um_id_inexistente() {
		UUID id = UUID.randomUUID();
		when(repositorio.findByPublicId(id)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> servico.atualizar(id, dados("Qualquer")))
				.isInstanceOf(ResourceNotFoundException.class);
	}

	// ----- desativar / ativar -----

	@Test
	void deve_devolver_o_mesmo_resultado_quando_desativa_duas_vezes() {
		Materia materia = new Materia(etec, "Física", null);
		when(repositorio.findByPublicId(materia.getPublicId())).thenReturn(Optional.of(materia));
		when(repositorio.saveAndFlush(materia)).thenReturn(materia);

		Materia primeira = servico.desativar(materia.getPublicId());
		Materia segunda = servico.desativar(materia.getPublicId());

		assertThat(primeira.isAtivo()).isFalse();
		assertThat(segunda.isAtivo()).isFalse();
	}

	@Test
	void deve_devolver_o_mesmo_resultado_quando_ativa_duas_vezes() {
		Materia materia = new Materia(etec, "Física", null);
		materia.desativar();
		when(repositorio.findByPublicId(materia.getPublicId())).thenReturn(Optional.of(materia));
		when(repositorio.saveAndFlush(materia)).thenReturn(materia);

		Materia primeira = servico.ativar(materia.getPublicId());
		Materia segunda = servico.ativar(materia.getPublicId());

		assertThat(primeira.isAtivo()).isTrue();
		assertThat(segunda.isAtivo()).isTrue();
	}

	@Test
	void deve_lancar_404_quando_desativa_ou_ativa_um_id_inexistente() {
		UUID id = UUID.randomUUID();
		when(repositorio.findByPublicId(id)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> servico.desativar(id)).isInstanceOf(ResourceNotFoundException.class);
		assertThatThrownBy(() -> servico.ativar(id)).isInstanceOf(ResourceNotFoundException.class);
	}

	// ----- apagar -----

	@Test
	void deve_apagar_quando_a_instrucao_atomica_remove_uma_linha() {
		UUID id = UUID.randomUUID();
		when(repositorio.apagarPorPublicId(id)).thenReturn(1);

		servico.apagar(id);

		verify(repositorio).apagarPorPublicId(id);
		verify(repositorio, never()).findByPublicId(any());
	}

	@Test
	void deve_lancar_404_quando_a_instrucao_atomica_nao_remove_nenhuma_linha() {
		UUID id = UUID.randomUUID();
		when(repositorio.apagarPorPublicId(id)).thenReturn(0);

		assertThatThrownBy(() -> servico.apagar(id))
				.isInstanceOfSatisfying(ResourceNotFoundException.class,
						erro -> assertThat(erro.getCodigo()).isEqualTo(CodigoErro.MATERIA_NAO_ENCONTRADA));
	}


	// ----- apagar com subtópicos: a FK é a garantia; só ela é traduzida (B5) -----

	private static DataIntegrityViolationException violacao(ConstraintKind tipo, String nome) {
		return new DataIntegrityViolationException("falha",
				new ConstraintViolationException("v", new SQLException("x"), tipo, nome));
	}

	@Test
	void deve_recusar_com_409_materia_com_subtopicos_quando_a_fk_do_subtopico_barra_o_delete() {
		UUID id = UUID.randomUUID();
		when(repositorio.apagarPorPublicId(id)).thenThrow(violacao(ConstraintKind.FOREIGN_KEY, "fk_subtopico_materia"));

		assertThatThrownBy(() -> servico.apagar(id)).isInstanceOfSatisfying(ConflictException.class,
				erro -> assertThat(erro.getCodigo()).isEqualTo(CodigoErro.MATERIA_COM_SUBTOPICOS));
	}

	@Test
	void deve_deixar_subir_sem_traduzir_a_fk_de_outra_tabela() {
		UUID id = UUID.randomUUID();
		DataIntegrityViolationException outra = violacao(ConstraintKind.FOREIGN_KEY, "fk_questao_prova");
		when(repositorio.apagarPorPublicId(id)).thenThrow(outra);

		assertThatThrownBy(() -> servico.apagar(id)).isSameAs(outra);
	}

	@Test
	void deve_deixar_subir_sem_traduzir_uma_violacao_que_nao_e_de_chave_estrangeira() {
		UUID id = UUID.randomUUID();
		DataIntegrityViolationException outra = violacao(ConstraintKind.UNIQUE, "uk_qualquer");
		when(repositorio.apagarPorPublicId(id)).thenThrow(outra);

		assertThatThrownBy(() -> servico.apagar(id)).isSameAs(outra);
	}

	@Test
	void deve_deixar_subir_uma_falha_de_integridade_sem_violacao_conhecida() {
		UUID id = UUID.randomUUID();
		DataIntegrityViolationException desconhecida = new DataIntegrityViolationException("sem causa do Hibernate");
		when(repositorio.apagarPorPublicId(id)).thenThrow(desconhecida);

		assertThatThrownBy(() -> servico.apagar(id)).isSameAs(desconhecida);
	}


	// ----- visibilidade por papel (D28, D103) -----

	@Test
	void deve_dar_404_da_inexistente_quando_o_student_busca_materia_inativa() {
		Materia inativa = new Materia(etec, "Inativa", null);
		inativa.desativar();
		when(repositorio.findByPublicId(inativa.getPublicId())).thenReturn(Optional.of(inativa));

		assertThatThrownBy(() -> servico.buscar(inativa.getPublicId(), EscopoDeLeitura.SO_ATIVOS))
				.isInstanceOfSatisfying(ResourceNotFoundException.class,
						erro -> assertThat(erro.getCodigo()).isEqualTo(CodigoErro.MATERIA_NAO_ENCONTRADA));
		assertThat(servico.buscar(inativa.getPublicId(), EscopoDeLeitura.TUDO)).isSameAs(inativa);
	}

	@Test
	void deve_devolver_a_materia_ativa_ao_student() {
		Materia ativa = new Materia(etec, "Ativa", null);
		when(repositorio.findByPublicId(ativa.getPublicId())).thenReturn(Optional.of(ativa));

		assertThat(servico.buscar(ativa.getPublicId(), EscopoDeLeitura.SO_ATIVOS)).isSameAs(ativa);
	}

	@Test
	void deve_recusar_ativo_false_do_student_sem_consultar_o_banco() {
		assertThatThrownBy(() -> servico.listar(EscopoDeLeitura.SO_ATIVOS, null, false, org.springframework.data.domain.Pageable.unpaged()))
				.isInstanceOfSatisfying(br.simulado.etec.shared.exception.InvalidRequestException.class,
						erro -> assertThat(erro.getCodigo()).isEqualTo(CodigoErro.PARAMETRO_INVALIDO));
		org.mockito.Mockito.verifyNoInteractions(repositorio);
	}
}
