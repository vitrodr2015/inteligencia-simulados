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

import br.simulado.etec.shared.security.EscopoDeLeitura;
import br.simulado.etec.catalogo.dto.SubtopicoAtualizacaoRequest;
import br.simulado.etec.catalogo.dto.SubtopicoCriacaoRequest;
import br.simulado.etec.catalogo.model.Materia;
import br.simulado.etec.catalogo.model.ProgramaDeProva;
import br.simulado.etec.shared.programa.Programa;
import br.simulado.etec.catalogo.model.Subtopico;
import br.simulado.etec.catalogo.repository.MateriaRepository;
import br.simulado.etec.catalogo.repository.SubtopicoRepository;
import br.simulado.etec.shared.exception.ApiException;
import br.simulado.etec.shared.exception.CodigoErro;
import br.simulado.etec.shared.exception.ConflictException;
import br.simulado.etec.shared.exception.InvalidRequestException;
import br.simulado.etec.shared.exception.ResourceNotFoundException;

@ExtendWith(MockitoExtension.class)
class SubtopicoServiceTest {

	@Mock
	private SubtopicoRepository repositorio;

	@Mock
	private MateriaRepository materias;

	@InjectMocks
	private SubtopicoService servico;

	private final ProgramaDeProva etec = new ProgramaDeProva(Programa.ETEC, "ETEC");

	private final Materia materia = new Materia(etec, "Matemática", null);

	private Subtopico subtopico(String nome) {
		return new Subtopico(materia, nome);
	}

	private static DataIntegrityViolationException violacao(ConstraintKind tipo, String nome) {
		return new DataIntegrityViolationException("falha",
				new ConstraintViolationException("v", new SQLException("x"), tipo, nome));
	}

	// ----- criar -----

	@Test
	void deve_criar_o_subtopico_ativo_na_materia_quando_tudo_esta_certo() {
		when(materias.findByPublicId(materia.getPublicId())).thenReturn(Optional.of(materia));
		when(repositorio.existsByMateriaAndNome(materia, "Álgebra")).thenReturn(false);
		when(repositorio.saveAndFlush(any(Subtopico.class))).thenAnswer(chamada -> chamada.getArgument(0));

		Subtopico criado = servico.criar(new SubtopicoCriacaoRequest(materia.getPublicId(), "Álgebra"));

		assertThat(criado.getNome()).isEqualTo("Álgebra");
		assertThat(criado.getMateria()).isSameAs(materia);
		assertThat(criado.isAtivo()).isTrue();
		assertThat(criado.getPublicId()).isNotNull();
	}

	@Test
	void deve_recusar_com_400_e_campo_materia_id_quando_a_materia_nao_existe() {
		UUID inexistente = UUID.randomUUID();
		when(materias.findByPublicId(inexistente)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> servico.criar(new SubtopicoCriacaoRequest(inexistente, "Álgebra")))
				.isInstanceOfSatisfying(InvalidRequestException.class, erro -> {
					assertThat(erro.getCodigo()).isEqualTo(CodigoErro.VALIDACAO_FALHOU);
					assertThat(erro.getCampos()).singleElement().satisfies(campo -> {
						assertThat(campo.campo()).isEqualTo("materiaId");
						assertThat(campo.mensagem()).isNotBlank();
					});
				});
		verify(repositorio, never()).saveAndFlush(any());
	}

	@Test
	void deve_recusar_com_409_materia_inativa_quando_a_materia_esta_desativada() {
		materia.desativar();
		when(materias.findByPublicId(materia.getPublicId())).thenReturn(Optional.of(materia));

		assertThatThrownBy(() -> servico.criar(new SubtopicoCriacaoRequest(materia.getPublicId(), "Álgebra")))
				.isInstanceOfSatisfying(ConflictException.class,
						erro -> assertThat(erro.getCodigo()).isEqualTo(CodigoErro.MATERIA_INATIVA));
		verify(repositorio, never()).saveAndFlush(any());
	}

	@Test
	void deve_responder_materia_inativa_antes_de_nome_duplicado() {
		materia.desativar();
		when(materias.findByPublicId(materia.getPublicId())).thenReturn(Optional.of(materia));

		assertThatThrownBy(() -> servico.criar(new SubtopicoCriacaoRequest(materia.getPublicId(), "Álgebra")))
				.isInstanceOfSatisfying(ConflictException.class,
						erro -> assertThat(erro.getCodigo()).isEqualTo(CodigoErro.MATERIA_INATIVA));
		verify(repositorio, never()).existsByMateriaAndNome(any(), anyString());
	}

	@Test
	void deve_recusar_com_409_quando_o_nome_ja_existe_na_checagem_previa() {
		when(materias.findByPublicId(materia.getPublicId())).thenReturn(Optional.of(materia));
		when(repositorio.existsByMateriaAndNome(materia, "Álgebra")).thenReturn(true);

		assertThatThrownBy(() -> servico.criar(new SubtopicoCriacaoRequest(materia.getPublicId(), "Álgebra")))
				.isInstanceOfSatisfying(ConflictException.class,
						erro -> assertThat(erro.getCodigo()).isEqualTo(CodigoErro.SUBTOPICO_NOME_DUPLICADO));
		verify(repositorio, never()).saveAndFlush(any());
	}

	@Test
	void deve_recusar_com_409_quando_o_indice_unico_barra_a_corrida() {
		when(materias.findByPublicId(materia.getPublicId())).thenReturn(Optional.of(materia));
		when(repositorio.existsByMateriaAndNome(materia, "Álgebra")).thenReturn(false);
		when(repositorio.saveAndFlush(any(Subtopico.class))).thenThrow(violacao(ConstraintKind.UNIQUE, "uk_subtopico_materia_nome"));

		assertThatThrownBy(() -> servico.criar(new SubtopicoCriacaoRequest(materia.getPublicId(), "Álgebra")))
				.isInstanceOfSatisfying(ConflictException.class,
						erro -> assertThat(erro.getCodigo()).isEqualTo(CodigoErro.SUBTOPICO_NOME_DUPLICADO));
	}

	@Test
	void deve_recusar_com_400_quando_a_fk_barra_porque_a_materia_foi_apagada_na_corrida() {
		when(materias.findByPublicId(materia.getPublicId())).thenReturn(Optional.of(materia));
		when(repositorio.existsByMateriaAndNome(materia, "Álgebra")).thenReturn(false);
		when(repositorio.saveAndFlush(any(Subtopico.class))).thenThrow(violacao(ConstraintKind.FOREIGN_KEY, "fk_subtopico_materia"));

		assertThatThrownBy(() -> servico.criar(new SubtopicoCriacaoRequest(materia.getPublicId(), "Álgebra")))
				.isInstanceOfSatisfying(InvalidRequestException.class,
						erro -> assertThat(erro.getCampos()).singleElement()
								.satisfies(campo -> assertThat(campo.campo()).isEqualTo("materiaId")));
	}

	@Test
	void deve_deixar_subir_qualquer_outra_violacao_de_integridade_sem_traduzir() {
		when(materias.findByPublicId(materia.getPublicId())).thenReturn(Optional.of(materia));
		when(repositorio.existsByMateriaAndNome(materia, "Álgebra")).thenReturn(false);
		DataIntegrityViolationException naoEsperada = violacao(ConstraintKind.NOT_NULL, "nome");
		when(repositorio.saveAndFlush(any(Subtopico.class))).thenThrow(naoEsperada);

		assertThatThrownBy(() -> servico.criar(new SubtopicoCriacaoRequest(materia.getPublicId(), "Álgebra")))
				.isSameAs(naoEsperada);
	}

	// ----- buscar -----

	@Test
	void deve_devolver_o_subtopico_quando_o_id_existe_e_404_quando_nao() {
		Subtopico existente = subtopico("Funções");
		UUID inexistente = UUID.randomUUID();
		when(repositorio.findByPublicId(existente.getPublicId())).thenReturn(Optional.of(existente));
		when(repositorio.findByPublicId(inexistente)).thenReturn(Optional.empty());

		assertThat(servico.buscar(existente.getPublicId(), EscopoDeLeitura.TUDO)).isSameAs(existente);
		assertThatThrownBy(() -> servico.buscar(inexistente, EscopoDeLeitura.TUDO)).isInstanceOfSatisfying(ResourceNotFoundException.class,
				erro -> assertThat(erro.getCodigo()).isEqualTo(CodigoErro.SUBTOPICO_NAO_ENCONTRADO));
	}

	// ----- atualizar (D79: 404, 409, CAMPO_IMUTAVEL) -----

	@Test
	void deve_trocar_o_nome_quando_nao_ha_conflito_e_o_materia_id_e_omitido_ou_igual() {
		Subtopico existente = subtopico("Funções");
		when(repositorio.findByPublicId(existente.getPublicId())).thenReturn(Optional.of(existente));
		when(repositorio.existsByMateriaAndNomeAndPublicIdNot(materia, "Funções afins", existente.getPublicId()))
				.thenReturn(false);
		when(repositorio.saveAndFlush(any(Subtopico.class))).thenAnswer(chamada -> chamada.getArgument(0));

		Subtopico semMateria = servico.atualizar(existente.getPublicId(), new SubtopicoAtualizacaoRequest(null, "Funções afins"));
		assertThat(semMateria.getNome()).isEqualTo("Funções afins");

		Subtopico comMateriaIgual = servico.atualizar(existente.getPublicId(),
				new SubtopicoAtualizacaoRequest(materia.getPublicId(), "Funções afins"));
		assertThat(comMateriaIgual.getMateria()).isSameAs(materia);
	}

	@Test
	void deve_recusar_com_404_antes_de_qualquer_outra_regra_quando_o_subtopico_nao_existe() {
		UUID inexistente = UUID.randomUUID();
		when(repositorio.findByPublicId(inexistente)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> servico.atualizar(inexistente, new SubtopicoAtualizacaoRequest(UUID.randomUUID(), "Qualquer")))
				.isInstanceOfSatisfying(ResourceNotFoundException.class,
						erro -> assertThat(erro.getCodigo()).isEqualTo(CodigoErro.SUBTOPICO_NAO_ENCONTRADO));
	}

	@Test
	void deve_recusar_com_409_antes_do_campo_imutavel_quando_o_nome_repete_e_a_materia_difere() {
		Subtopico existente = subtopico("Funções");
		when(repositorio.findByPublicId(existente.getPublicId())).thenReturn(Optional.of(existente));
		when(repositorio.existsByMateriaAndNomeAndPublicIdNot(materia, "Ocupado", existente.getPublicId())).thenReturn(true);

		assertThatThrownBy(() -> servico.atualizar(existente.getPublicId(),
				new SubtopicoAtualizacaoRequest(UUID.randomUUID(), "Ocupado")))
				.isInstanceOfSatisfying(ConflictException.class,
						erro -> assertThat(erro.getCodigo()).isEqualTo(CodigoErro.SUBTOPICO_NOME_DUPLICADO));
	}

	@Test
	void deve_recusar_com_campo_imutavel_quando_o_materia_id_difere_e_nao_ha_outro_conflito() {
		Subtopico existente = subtopico("Funções");
		when(repositorio.findByPublicId(existente.getPublicId())).thenReturn(Optional.of(existente));
		when(repositorio.existsByMateriaAndNomeAndPublicIdNot(any(), anyString(), any())).thenReturn(false);

		assertThatThrownBy(() -> servico.atualizar(existente.getPublicId(),
				new SubtopicoAtualizacaoRequest(UUID.randomUUID(), "Outro nome")))
				.isInstanceOfSatisfying(InvalidRequestException.class,
						erro -> assertThat(erro.getCodigo()).isEqualTo(CodigoErro.CAMPO_IMUTAVEL));
		verify(repositorio, never()).saveAndFlush(any());
		assertThat(existente.getNome()).as("nada foi alterado").isEqualTo("Funções");
	}

	@Test
	void deve_permitir_renomear_com_a_materia_desativada() {
		materia.desativar();
		Subtopico existente = subtopico("Funções");
		when(repositorio.findByPublicId(existente.getPublicId())).thenReturn(Optional.of(existente));
		when(repositorio.saveAndFlush(any(Subtopico.class))).thenAnswer(chamada -> chamada.getArgument(0));

		Subtopico renomeado = servico.atualizar(existente.getPublicId(), new SubtopicoAtualizacaoRequest(null, "Funções 2"));

		assertThat(renomeado.getNome()).isEqualTo("Funções 2");
	}

	@Test
	void deve_traduzir_a_unicidade_da_corrida_no_put() {
		Subtopico existente = subtopico("Funções");
		when(repositorio.findByPublicId(existente.getPublicId())).thenReturn(Optional.of(existente));
		when(repositorio.saveAndFlush(any(Subtopico.class))).thenThrow(violacao(ConstraintKind.UNIQUE, "uk"));

		assertThatThrownBy(() -> servico.atualizar(existente.getPublicId(), new SubtopicoAtualizacaoRequest(null, "Funções 2")))
				.isInstanceOfSatisfying(ConflictException.class,
						erro -> assertThat(erro.getCodigo()).isEqualTo(CodigoErro.SUBTOPICO_NOME_DUPLICADO));
	}

	// ----- desativar / ativar (D62, D109) -----

	@Test
	void deve_desativar_o_subtopico_ativo_gravando_uma_vez() {
		Subtopico existente = subtopico("Funções");
		when(repositorio.findByPublicId(existente.getPublicId())).thenReturn(Optional.of(existente));
		when(repositorio.saveAndFlush(any(Subtopico.class))).thenAnswer(chamada -> chamada.getArgument(0));

		assertThat(servico.desativar(existente.getPublicId()).isAtivo()).isFalse();
		verify(repositorio).saveAndFlush(existente);
	}

	@Test
	void deve_devolver_o_mesmo_resultado_sem_gravar_quando_desativa_um_subtopico_ja_inativo() {
		Subtopico existente = subtopico("Funções");
		existente.desativar();
		when(repositorio.findByPublicId(existente.getPublicId())).thenReturn(Optional.of(existente));

		assertThat(servico.desativar(existente.getPublicId()).isAtivo()).isFalse();
		verify(repositorio, never()).saveAndFlush(any());
	}

	@Test
	void deve_ativar_o_subtopico_inativo_quando_a_materia_esta_ativa() {
		Subtopico existente = subtopico("Funções");
		existente.desativar();
		when(repositorio.findByPublicId(existente.getPublicId())).thenReturn(Optional.of(existente));
		when(repositorio.saveAndFlush(any(Subtopico.class))).thenAnswer(chamada -> chamada.getArgument(0));

		assertThat(servico.ativar(existente.getPublicId()).isAtivo()).isTrue();
		verify(repositorio).saveAndFlush(existente);
	}

	@Test
	void deve_recusar_com_409_materia_inativa_quando_o_enable_ativaria_um_subtopico_inativo_de_materia_desativada() {
		materia.desativar();
		Subtopico existente = subtopico("Funções");
		existente.desativar();
		when(repositorio.findByPublicId(existente.getPublicId())).thenReturn(Optional.of(existente));

		assertThatThrownBy(() -> servico.ativar(existente.getPublicId())).isInstanceOfSatisfying(ConflictException.class,
				erro -> assertThat(erro.getCodigo()).isEqualTo(CodigoErro.MATERIA_INATIVA));
		assertThat(existente.isAtivo()).isFalse();
		verify(repositorio, never()).saveAndFlush(any());
	}

	@Test
	void deve_responder_200_sem_efeito_quando_o_enable_chega_a_um_subtopico_ja_ativo_mesmo_com_a_materia_desativada() {
		materia.desativar();
		Subtopico existente = subtopico("Funções");
		when(repositorio.findByPublicId(existente.getPublicId())).thenReturn(Optional.of(existente));

		assertThat(servico.ativar(existente.getPublicId()).isAtivo()).isTrue();
		verify(repositorio, never()).saveAndFlush(any());
	}

	@Test
	void deve_responder_404_em_disable_e_enable_quando_o_subtopico_nao_existe() {
		UUID inexistente = UUID.randomUUID();
		when(repositorio.findByPublicId(inexistente)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> servico.desativar(inexistente)).isInstanceOf(ResourceNotFoundException.class);
		assertThatThrownBy(() -> servico.ativar(inexistente)).isInstanceOf(ResourceNotFoundException.class);
	}

	// ----- apagar -----

	@Test
	void deve_apagar_quando_o_delete_atomico_apaga_uma_linha_e_dar_404_quando_apaga_zero() {
		UUID existente = UUID.randomUUID();
		UUID inexistente = UUID.randomUUID();
		when(repositorio.apagarPorPublicId(existente)).thenReturn(1);
		when(repositorio.apagarPorPublicId(inexistente)).thenReturn(0);

		servico.apagar(existente);
		assertThatThrownBy(() -> servico.apagar(inexistente)).isInstanceOfSatisfying(ApiException.class,
				erro -> assertThat(erro.getCodigo()).isEqualTo(CodigoErro.SUBTOPICO_NAO_ENCONTRADO));
	}

	@Test
	void deve_dar_409_subtopico_com_questoes_quando_o_banco_recusa_o_delete_pela_fk_da_versao_de_questao() {
		UUID id = UUID.randomUUID();
		when(repositorio.apagarPorPublicId(id)).thenThrow(new DataIntegrityViolationException("fk",
				new ConstraintViolationException("fk", new SQLException("x"), "delete", ConstraintKind.FOREIGN_KEY,
						"questao_versao.fk_questao_versao_subtopico")));

		assertThatThrownBy(() -> servico.apagar(id)).isInstanceOfSatisfying(ConflictException.class,
				erro -> assertThat(erro.getCodigo()).isEqualTo(CodigoErro.SUBTOPICO_COM_QUESTOES));
	}

	@Test
	void deve_deixar_subir_qualquer_outra_violacao_de_integridade_no_delete_do_subtopico() {
		UUID id = UUID.randomUUID();
		DataIntegrityViolationException outraFk = new DataIntegrityViolationException("fk",
				new ConstraintViolationException("fk", new SQLException("x"), "delete", ConstraintKind.FOREIGN_KEY,
						"alguma_tabela.fk_outra"));
		DataIntegrityViolationException check = new DataIntegrityViolationException("ck",
				new ConstraintViolationException("ck", new SQLException("x"), "delete", ConstraintKind.CHECK, "tabela.ck_qualquer"));

		UUID idDoCheck = UUID.randomUUID();
		when(repositorio.apagarPorPublicId(id)).thenThrow(outraFk);
		assertThatThrownBy(() -> servico.apagar(id)).as("outra FK não vira 409 falso").isSameAs(outraFk);

		when(repositorio.apagarPorPublicId(idDoCheck)).thenThrow(check);
		assertThatThrownBy(() -> servico.apagar(idDoCheck)).as("um CHECK não vira 409 falso").isSameAs(check);
	}

	// ----- visibilidade por papel (D28, D103) -----

	@Test
	void deve_dar_404_do_inexistente_quando_o_student_busca_subtopico_inativo_ou_de_materia_inativa() {
		Subtopico inativo = subtopico("Inativo");
		inativo.desativar();
		Materia materiaInativa = new Materia(etec, "Materia inativa", null);
		Subtopico emMateriaInativa = new Subtopico(materiaInativa, "Em materia inativa");
		materiaInativa.desativar();
		when(repositorio.findByPublicId(inativo.getPublicId())).thenReturn(Optional.of(inativo));
		when(repositorio.findByPublicId(emMateriaInativa.getPublicId())).thenReturn(Optional.of(emMateriaInativa));

		for (Subtopico escondido : new Subtopico[] { inativo, emMateriaInativa }) {
			assertThatThrownBy(() -> servico.buscar(escondido.getPublicId(), EscopoDeLeitura.SO_ATIVOS))
					.isInstanceOfSatisfying(ResourceNotFoundException.class,
							erro -> assertThat(erro.getCodigo()).isEqualTo(CodigoErro.SUBTOPICO_NAO_ENCONTRADO));
			assertThat(servico.buscar(escondido.getPublicId(), EscopoDeLeitura.TUDO)).isSameAs(escondido);
		}
	}

	@Test
	void deve_devolver_o_subtopico_ativo_de_materia_ativa_ao_student() {
		Subtopico visivel = subtopico("Visivel");
		when(repositorio.findByPublicId(visivel.getPublicId())).thenReturn(Optional.of(visivel));

		assertThat(servico.buscar(visivel.getPublicId(), EscopoDeLeitura.SO_ATIVOS)).isSameAs(visivel);
	}

	@Test
	void deve_recusar_ativo_false_do_student_sem_consultar_o_banco() {
		assertThatThrownBy(() -> servico.listar(EscopoDeLeitura.SO_ATIVOS, null, null, false,
				org.springframework.data.domain.Pageable.unpaged())).isInstanceOfSatisfying(InvalidRequestException.class,
						erro -> assertThat(erro.getCodigo()).isEqualTo(CodigoErro.PARAMETRO_INVALIDO));
		org.mockito.Mockito.verifyNoInteractions(repositorio);
	}
}
