package br.simulado.etec.catalogo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import br.simulado.etec.catalogo.dto.MateriaRequest;
import br.simulado.etec.catalogo.model.Materia;
import br.simulado.etec.catalogo.repository.MateriaRepository;
import br.simulado.etec.shared.exception.CodigoErro;
import br.simulado.etec.shared.exception.ConflictException;
import br.simulado.etec.shared.exception.ResourceNotFoundException;

@ExtendWith(MockitoExtension.class)
class MateriaServiceTest {

	@Mock
	private MateriaRepository repositorio;

	@InjectMocks
	private MateriaService servico;

	private static MateriaRequest dados(String nome) {
		return new MateriaRequest(nome, "Descrição de teste");
	}

	// ----- criar -----

	@Test
	void deve_criar_a_materia_ativa_quando_o_nome_e_novo() {
		when(repositorio.existsByNome("Matemática")).thenReturn(false);
		when(repositorio.saveAndFlush(any(Materia.class))).thenAnswer(chamada -> chamada.getArgument(0));

		Materia criada = servico.criar(dados("Matemática"));

		assertThat(criada.getNome()).isEqualTo("Matemática");
		assertThat(criada.getDescricao()).isEqualTo("Descrição de teste");
		assertThat(criada.isAtivo()).isTrue();
		assertThat(criada.getPublicId()).isNotNull();
	}

	@Test
	void deve_recusar_com_409_quando_o_nome_ja_existe_na_checagem_previa() {
		when(repositorio.existsByNome("Matemática")).thenReturn(true);

		assertThatThrownBy(() -> servico.criar(dados("Matemática")))
				.isInstanceOfSatisfying(ConflictException.class,
						erro -> assertThat(erro.getCodigo()).isEqualTo(CodigoErro.MATERIA_NOME_DUPLICADO));
		verify(repositorio, never()).saveAndFlush(any());
	}

	@Test
	void deve_recusar_com_409_quando_o_indice_unico_barra_a_corrida() {
		when(repositorio.existsByNome("Matemática")).thenReturn(false);
		when(repositorio.saveAndFlush(any(Materia.class))).thenThrow(new DataIntegrityViolationException("índice único"));

		assertThatThrownBy(() -> servico.criar(dados("Matemática")))
				.isInstanceOfSatisfying(ConflictException.class,
						erro -> assertThat(erro.getCodigo()).isEqualTo(CodigoErro.MATERIA_NOME_DUPLICADO));
	}

	// ----- buscar -----

	@Test
	void deve_devolver_a_materia_quando_o_id_existe() {
		Materia materia = new Materia("Física", null);
		when(repositorio.findByPublicId(materia.getPublicId())).thenReturn(Optional.of(materia));

		assertThat(servico.buscar(materia.getPublicId())).isSameAs(materia);
	}

	@Test
	void deve_lancar_404_quando_o_id_nao_existe() {
		UUID id = UUID.randomUUID();
		when(repositorio.findByPublicId(id)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> servico.buscar(id))
				.isInstanceOfSatisfying(ResourceNotFoundException.class,
						erro -> assertThat(erro.getCodigo()).isEqualTo(CodigoErro.MATERIA_NAO_ENCONTRADA));
	}

	// ----- atualizar -----

	@Test
	void deve_trocar_nome_e_descricao_sem_mexer_no_ativo_quando_atualiza() {
		Materia materia = new Materia("Fisica", "antiga");
		materia.desativar();
		when(repositorio.findByPublicId(materia.getPublicId())).thenReturn(Optional.of(materia));
		when(repositorio.existsByNomeAndPublicIdNot("Física", materia.getPublicId())).thenReturn(false);
		when(repositorio.saveAndFlush(materia)).thenReturn(materia);

		Materia atualizada = servico.atualizar(materia.getPublicId(), new MateriaRequest("Física", "nova"));

		assertThat(atualizada.getNome()).isEqualTo("Física");
		assertThat(atualizada.getDescricao()).isEqualTo("nova");
		assertThat(atualizada.isAtivo()).isFalse();
	}

	@Test
	void deve_permitir_o_proprio_nome_quando_atualiza_o_mesmo_registro() {
		Materia materia = new Materia("Física", "antiga");
		when(repositorio.findByPublicId(materia.getPublicId())).thenReturn(Optional.of(materia));
		// outro registro com o mesmo nome: não existe (o próprio registro é excluído da checagem)
		when(repositorio.existsByNomeAndPublicIdNot("Física", materia.getPublicId())).thenReturn(false);
		when(repositorio.saveAndFlush(materia)).thenReturn(materia);

		Materia atualizada = servico.atualizar(materia.getPublicId(), new MateriaRequest("Física", "outra"));

		assertThat(atualizada.getNome()).isEqualTo("Física");
		assertThat(atualizada.getDescricao()).isEqualTo("outra");
	}

	@Test
	void deve_recusar_com_409_quando_outro_registro_ja_tem_o_nome() {
		Materia materia = new Materia("Física", null);
		when(repositorio.findByPublicId(materia.getPublicId())).thenReturn(Optional.of(materia));
		when(repositorio.existsByNomeAndPublicIdNot("Química", materia.getPublicId())).thenReturn(true);

		assertThatThrownBy(() -> servico.atualizar(materia.getPublicId(), dados("Química")))
				.isInstanceOfSatisfying(ConflictException.class,
						erro -> assertThat(erro.getCodigo()).isEqualTo(CodigoErro.MATERIA_NOME_DUPLICADO));
		assertThat(materia.getNome()).isEqualTo("Física");
	}

	@Test
	void deve_recusar_com_409_quando_o_indice_unico_barra_a_corrida_na_atualizacao() {
		Materia materia = new Materia("Física", null);
		when(repositorio.findByPublicId(materia.getPublicId())).thenReturn(Optional.of(materia));
		when(repositorio.existsByNomeAndPublicIdNot("Química", materia.getPublicId())).thenReturn(false);
		when(repositorio.saveAndFlush(materia)).thenThrow(new DataIntegrityViolationException("índice único"));

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
		Materia materia = new Materia("Física", null);
		when(repositorio.findByPublicId(materia.getPublicId())).thenReturn(Optional.of(materia));
		when(repositorio.saveAndFlush(materia)).thenReturn(materia);

		Materia primeira = servico.desativar(materia.getPublicId());
		Materia segunda = servico.desativar(materia.getPublicId());

		assertThat(primeira.isAtivo()).isFalse();
		assertThat(segunda.isAtivo()).isFalse();
	}

	@Test
	void deve_devolver_o_mesmo_resultado_quando_ativa_duas_vezes() {
		Materia materia = new Materia("Física", null);
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

}
