package br.simulado.etec.usuarios.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.hibernate.exception.ConstraintViolationException;
import org.hibernate.exception.ConstraintViolationException.ConstraintKind;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;

import br.simulado.etec.shared.config.AdminInicialAmbiente.Ausente;
import br.simulado.etec.shared.config.AdminInicialAmbiente.Invalida;
import br.simulado.etec.shared.config.AdminInicialAmbiente.Valida;
import br.simulado.etec.shared.config.TermosProperties;
import br.simulado.etec.usuarios.model.Papel;
import br.simulado.etec.usuarios.model.Usuario;
import br.simulado.etec.usuarios.repository.UsuarioRepository;
import br.simulado.etec.usuarios.service.AdminInicialService.Resultado;

/**
 * Todos os ramos do bootstrap do administrador, em desenvolvimento (só log) e em produção (derruba a subida), e a prova
 * de que nada sensível (senha, hash, e-mail) vai para o log nem para as mensagens de erro.
 */
@ExtendWith({ MockitoExtension.class, OutputCaptureExtension.class })
class AdminInicialServiceTest {

	private static final String EMAIL = "admin.secreto@exemplo.invalid";

	private static final String SENHA = "Senha-Muito-Secreta-123";

	private static final String HASH = "{bcrypt}hash-que-nao-pode-vazar";

	@Mock
	private UsuarioRepository repositorio;

	@Mock
	private PasswordEncoder encoder;

	private AdminInicialService servico;

	@BeforeEach
	void montar() {
		servico = new AdminInicialService(repositorio, encoder, new TermosProperties("v7", "https://exemplo.invalid/termos"),
				mock(PlatformTransactionManager.class));
	}

	private static Valida valida() {
		return new Valida(EMAIL, SENHA);
	}

	private static void semSegredoNaSaida(CapturedOutput saida) {
		assertThat(saida.getAll()).doesNotContain(SENHA).doesNotContain(HASH).doesNotContain(EMAIL)
				.doesNotContain("admin.secreto");
	}

	// ----- já existe admin: as variáveis são ignoradas -----

	@Test
	void deve_ignorar_as_variaveis_sem_criar_nem_alterar_nada_quando_ja_existe_admin_ativo(CapturedOutput saida) {
		when(repositorio.existsByPapelAndExcluidoEmIsNull(Papel.ADMIN)).thenReturn(true);

		for (boolean producao : new boolean[] { false, true }) {
			assertThat(servico.garantirAdministradorInicial(valida(), producao)).isEqualTo(Resultado.JA_EXISTIA_ADMIN);
		}

		verify(repositorio, never()).saveAndFlush(any());
		verify(repositorio, never()).save(any());
		verifyNoInteractions(encoder); // nem a senha nova é processada: não há troca de senha
		assertThat(saida.getAll()).contains("foram ignoradas");
		semSegredoNaSaida(saida);
	}

	@Test
	void deve_ignorar_tambem_variaveis_invalidas_e_ausentes_quando_ja_existe_admin() {
		when(repositorio.existsByPapelAndExcluidoEmIsNull(Papel.ADMIN)).thenReturn(true);

		assertThat(servico.garantirAdministradorInicial(new Invalida(List.of("ADMIN_SENHA tem menos de 12 bytes.")), true))
				.isEqualTo(Resultado.JA_EXISTIA_ADMIN);
		assertThat(servico.garantirAdministradorInicial(new Ausente(), true)).isEqualTo(Resultado.JA_EXISTIA_ADMIN);
		verify(repositorio, never()).saveAndFlush(any());
	}

	// ----- sem variáveis e sem admin -----

	@Test
	void deve_so_avisar_fora_de_producao_e_derrubar_a_subida_em_producao_quando_nao_ha_admin_nem_variaveis(
			CapturedOutput saida) {
		assertThat(servico.garantirAdministradorInicial(new Ausente(), false)).isEqualTo(Resultado.SEM_ADMIN_E_SEM_VARIAVEIS);
		assertThat(saida.getAll()).contains("Nenhum administrador existe");

		assertThatThrownBy(() -> servico.garantirAdministradorInicial(new Ausente(), true))
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("Nenhum administrador existe")
				.hasMessageContaining("D45");
		verify(repositorio, never()).saveAndFlush(any());
	}

	// ----- variáveis inválidas -----

	@Test
	void deve_registrar_o_erro_fora_de_producao_e_derrubar_a_subida_em_producao_com_variaveis_invalidas(
			CapturedOutput saida) {
		Invalida invalida = new Invalida(List.of("ADMIN_SENHA tem menos de 12 bytes."));

		assertThat(servico.garantirAdministradorInicial(invalida, false)).isEqualTo(Resultado.VARIAVEIS_INVALIDAS);
		assertThat(saida.getAll()).contains("NÃO criado").contains("menos de 12 bytes");
		assertThatThrownBy(() -> servico.garantirAdministradorInicial(invalida, true))
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("menos de 12 bytes");
		verify(repositorio, never()).saveAndFlush(any());
	}

	// ----- NUNCA promove -----

	@Test
	void deve_nunca_promover_usuario_existente_com_o_email_do_admin(CapturedOutput saida) {
		Usuario comum = new Usuario("Aluno", EMAIL, HASH, LocalDate.of(2008, 5, 10), "v1", Papel.STUDENT);
		when(repositorio.existsByPapelAndExcluidoEmIsNull(Papel.ADMIN)).thenReturn(false);
		when(repositorio.findByEmail(EMAIL)).thenReturn(Optional.of(comum));

		assertThat(servico.garantirAdministradorInicial(valida(), false)).isEqualTo(Resultado.EMAIL_DE_USUARIO_EXISTENTE);
		assertThatThrownBy(() -> servico.garantirAdministradorInicial(valida(), true))
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("nunca promove")
				.hasMessageNotContaining(EMAIL).hasMessageNotContaining(SENHA);

		assertThat(comum.getPapel()).isEqualTo(Papel.STUDENT);
		verify(repositorio, never()).saveAndFlush(any());
		verify(repositorio, never()).save(any());
		verifyNoInteractions(encoder);
		semSegredoNaSaida(saida);
	}

	// ----- criação -----

	@Test
	void deve_criar_o_admin_com_os_dados_do_contrato_e_a_senha_so_como_hash(CapturedOutput saida) {
		when(repositorio.findByEmail(EMAIL)).thenReturn(Optional.empty());
		when(encoder.encode(SENHA)).thenReturn(HASH);
		when(repositorio.saveAndFlush(any(Usuario.class))).thenAnswer(i -> i.getArgument(0));

		assertThat(servico.garantirAdministradorInicial(valida(), true)).isEqualTo(Resultado.CRIADO);

		ArgumentCaptor<Usuario> capturado = ArgumentCaptor.forClass(Usuario.class);
		verify(repositorio).saveAndFlush(capturado.capture());
		Usuario admin = capturado.getValue();
		assertThat(admin.getPapel()).isEqualTo(Papel.ADMIN);
		assertThat(admin.getNome()).isEqualTo("Administrador");
		assertThat(admin.getDataNascimento()).isEqualTo(LocalDate.of(2000, 1, 1));
		assertThat(admin.getAceiteTermosVersao()).isEqualTo("v7");
		assertThat(admin.getEmail()).isEqualTo(EMAIL);
		assertThat(admin.getSenhaHash()).isEqualTo(HASH).doesNotContain(SENHA);
		assertThat(saida.getAll()).contains("Administrador inicial criado.");
		semSegredoNaSaida(saida);
	}

	@Test
	void deve_criar_o_admin_tambem_fora_de_producao() {
		when(repositorio.findByEmail(EMAIL)).thenReturn(Optional.empty());
		when(encoder.encode(anyString())).thenReturn(HASH);
		when(repositorio.saveAndFlush(any(Usuario.class))).thenAnswer(i -> i.getArgument(0));

		assertThat(servico.garantirAdministradorInicial(valida(), false)).isEqualTo(Resultado.CRIADO);
	}

	// ----- subidas ao mesmo tempo -----

	private static DataIntegrityViolationException violacaoDeUnicidade() {
		return new DataIntegrityViolationException("falha",
				new ConstraintViolationException("v", new SQLException("x"), ConstraintKind.UNIQUE, "uk_usuario_email"));
	}

	@Test
	void deve_seguir_normalmente_quando_outra_instancia_criou_o_mesmo_admin_no_meio_da_corrida(CapturedOutput saida) {
		Usuario criadoPelaOutra = new Usuario("Administrador", EMAIL, HASH, LocalDate.of(2000, 1, 1), "v7", Papel.ADMIN);
		when(repositorio.findByEmail(EMAIL)).thenReturn(Optional.empty()).thenReturn(Optional.of(criadoPelaOutra));
		when(encoder.encode(SENHA)).thenReturn(HASH);
		when(repositorio.saveAndFlush(any(Usuario.class))).thenThrow(violacaoDeUnicidade());

		assertThat(servico.garantirAdministradorInicial(valida(), true)).isEqualTo(Resultado.CRIADO_POR_OUTRA_INSTANCIA);
		assertThat(saida.getAll()).contains("outra instância");
		semSegredoNaSaida(saida);
	}

	@Test
	void deve_tratar_como_usuario_existente_quando_a_corrida_foi_ganha_por_um_usuario_comum() {
		Usuario comum = new Usuario("Aluno", EMAIL, HASH, LocalDate.of(2008, 5, 10), "v1", Papel.STUDENT);
		when(repositorio.findByEmail(EMAIL)).thenReturn(Optional.empty()).thenReturn(Optional.of(comum));
		when(encoder.encode(SENHA)).thenReturn(HASH);
		when(repositorio.saveAndFlush(any(Usuario.class))).thenThrow(violacaoDeUnicidade());

		assertThat(servico.garantirAdministradorInicial(valida(), false)).isEqualTo(Resultado.EMAIL_DE_USUARIO_EXISTENTE);
		assertThatThrownBy(() -> servico.garantirAdministradorInicial(valida(), true))
				.isInstanceOf(IllegalStateException.class);
		assertThat(comum.getPapel()).isEqualTo(Papel.STUDENT);
	}

	@Test
	void deve_deixar_subir_qualquer_outra_violacao_de_integridade_sem_esconder() {
		when(repositorio.findByEmail(EMAIL)).thenReturn(Optional.empty());
		when(encoder.encode(SENHA)).thenReturn(HASH);
		when(repositorio.saveAndFlush(any(Usuario.class))).thenThrow(new DataIntegrityViolationException("outra"));

		assertThatThrownBy(() -> servico.garantirAdministradorInicial(valida(), false))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	// ----- nada sensível em lugar nenhum -----

	@Test
	void deve_nunca_por_senha_hash_ou_email_em_mensagem_de_erro_nem_no_log_em_nenhum_ramo(CapturedOutput saida) {
		Usuario comum = new Usuario("Aluno", EMAIL, HASH, LocalDate.of(2008, 5, 10), "v1", Papel.STUDENT);
		when(repositorio.findByEmail(EMAIL)).thenReturn(Optional.of(comum));

		for (boolean producao : new boolean[] { false, true }) {
			try {
				servico.garantirAdministradorInicial(valida(), producao);
				servico.garantirAdministradorInicial(new Ausente(), producao);
				servico.garantirAdministradorInicial(new Invalida(List.of("ADMIN_SENHA tem menos de 12 bytes.")), producao);
			}
			catch (IllegalStateException e) {
				assertThat(e.getMessage()).doesNotContain(SENHA).doesNotContain(HASH).doesNotContain(EMAIL);
				assertThat(e.getCause()).isNull();
			}
		}
		semSegredoNaSaida(saida);
	}

}
