package br.simulado.etec.usuarios.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import br.simulado.etec.shared.config.TermosProperties;
import br.simulado.etec.shared.exception.CodigoErro;
import br.simulado.etec.shared.exception.ConflictException;
import br.simulado.etec.shared.exception.InvalidRequestException;
import br.simulado.etec.usuarios.model.Papel;
import br.simulado.etec.usuarios.model.Usuario;
import br.simulado.etec.usuarios.repository.UsuarioRepository;

@ExtendWith(MockitoExtension.class)
class UsuarioServiceTest {

	private static final LocalDate NASCIMENTO = LocalDate.of(2008, 5, 10);

	@Mock
	private UsuarioRepository repositorio;

	@Mock
	private PasswordEncoder encoder;

	private UsuarioService servico() {
		return new UsuarioService(repositorio, encoder, new TermosProperties("v1", "https://exemplo.invalid/termos"));
	}

	@Test
	void deve_criar_student_com_hash_da_senha_quando_os_dados_sao_validos() {
		when(repositorio.findByEmail("a@exemplo.invalid")).thenReturn(Optional.empty());
		when(encoder.encode("senha-12345")).thenReturn("{bcrypt}hash");
		when(repositorio.saveAndFlush(any(Usuario.class))).thenAnswer(i -> i.getArgument(0));

		Usuario criado = servico().criarAluno("Ana", "a@exemplo.invalid", "senha-12345", NASCIMENTO, "v1");

		assertThat(criado.getPapel()).isEqualTo(Papel.STUDENT);
		assertThat(criado.getSenhaHash()).isEqualTo("{bcrypt}hash").doesNotContain("senha-12345");
		assertThat(criado.getEmail()).isEqualTo("a@exemplo.invalid");
		assertThat(criado.getAceiteTermosVersao()).isEqualTo("v1");
	}

	@Test
	void deve_recusar_com_400_no_campo_dos_termos_quando_a_versao_nao_e_a_vigente() {
		assertThatThrownBy(() -> servico().criarAluno("Ana", "a@exemplo.invalid", "senha-12345", NASCIMENTO, "v0"))
				.isInstanceOfSatisfying(InvalidRequestException.class, e -> {
					assertThat(e.getCodigo()).isEqualTo(CodigoErro.VALIDACAO_FALHOU);
					assertThat(e.getCampos()).singleElement().extracting("campo").isEqualTo("aceiteTermosVersao");
				});
		verify(repositorio, never()).saveAndFlush(any());
	}

	@Test
	void deve_recusar_termos_nulos() {
		assertThatThrownBy(() -> servico().criarAluno("Ana", "a@exemplo.invalid", "senha-12345", NASCIMENTO, null))
				.isInstanceOf(InvalidRequestException.class);
	}

	@Test
	void deve_recusar_com_409_email_duplicado_quando_o_email_ja_existe() {
		when(repositorio.findByEmail("a@exemplo.invalid")).thenReturn(
				Optional.of(new Usuario("Outra", "a@exemplo.invalid", "h", NASCIMENTO, "v1", Papel.STUDENT)));

		assertThatThrownBy(() -> servico().criarAluno("Ana", "a@exemplo.invalid", "senha-12345", NASCIMENTO, "v1"))
				.isInstanceOfSatisfying(ConflictException.class,
						e -> assertThat(e.getCodigo()).isEqualTo(CodigoErro.EMAIL_DUPLICADO));
		verify(repositorio, never()).saveAndFlush(any());
	}

	@Test
	void deve_deixar_subir_outra_violacao_de_integridade_sem_traduzir() {
		when(repositorio.findByEmail("a@exemplo.invalid")).thenReturn(Optional.empty());
		when(encoder.encode("senha-12345")).thenReturn("{bcrypt}hash");
		when(repositorio.saveAndFlush(any(Usuario.class))).thenThrow(new DataIntegrityViolationException("outra"));

		assertThatThrownBy(() -> servico().criarAluno("Ana", "a@exemplo.invalid", "senha-12345", NASCIMENTO, "v1"))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void deve_gravar_o_hash_e_nunca_a_senha_pura() {
		when(repositorio.findByEmail("a@exemplo.invalid")).thenReturn(Optional.empty());
		when(encoder.encode("senha-12345")).thenReturn("{bcrypt}hash");
		when(repositorio.saveAndFlush(any(Usuario.class))).thenAnswer(i -> i.getArgument(0));

		servico().criarAluno("Ana", "a@exemplo.invalid", "senha-12345", NASCIMENTO, "v1");

		ArgumentCaptor<Usuario> capturado = ArgumentCaptor.forClass(Usuario.class);
		verify(repositorio).saveAndFlush(capturado.capture());
		assertThat(capturado.getValue().getSenhaHash()).isEqualTo("{bcrypt}hash");
	}

}
