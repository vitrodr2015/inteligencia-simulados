package br.simulado.etec.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import br.simulado.etec.auth.dto.TrocaDeSenhaRequest;
import br.simulado.etec.auth.model.MotivoRevogacao;
import br.simulado.etec.shared.exception.CodigoErro;
import br.simulado.etec.shared.exception.InvalidRequestException;
import br.simulado.etec.shared.exception.LimiteExcedidoException;
import br.simulado.etec.shared.security.UsuarioAutenticado;
import br.simulado.etec.shared.security.limite.Escopo;
import br.simulado.etec.shared.security.limite.LimiteDeTentativas;
import br.simulado.etec.shared.security.limite.RateLimitProperties;
import br.simulado.etec.usuarios.model.Papel;
import br.simulado.etec.usuarios.model.Usuario;
import br.simulado.etec.usuarios.service.UsuarioService;

@ExtendWith(MockitoExtension.class)
class TrocaDeSenhaServiceTest {

	private static final String HASH_REAL = "{bcrypt}hash-real-falso";

	private static final String HASH_FICTICIO = "{bcrypt}hash-ficticio-falso";

	@Mock
	private UsuarioService usuarios;

	@Mock
	private SessaoService sessoes;

	@Mock
	private PasswordEncoder encoder;

	@Mock
	private LimiteDeTentativas limite;

	@Mock
	private PlatformTransactionManager transacoes;

	private final RateLimitProperties limites = new RateLimitProperties(
			new RateLimitProperties.Regra(120, Duration.ofMinutes(1)), new RateLimitProperties.Regra(5, Duration.ofMinutes(15)),
			new RateLimitProperties.Regra(50, Duration.ofMinutes(15)), new RateLimitProperties.Regra(5, Duration.ofMinutes(15)),
			false);

	private TrocaDeSenhaService servico;

	private Usuario aluno;

	private Usuario admin;

	@BeforeEach
	void montar() {
		lenient().when(transacoes.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
		when(encoder.encode(anyString())).thenReturn(HASH_FICTICIO);
		servico = new TrocaDeSenhaService(usuarios, sessoes, encoder, limite, limites, transacoes);
		aluno = new Usuario("Ana", "ana@exemplo.invalid", HASH_REAL, LocalDate.of(2008, 5, 10), "v1", Papel.STUDENT);
		ReflectionTestUtils.setField(aluno, "id", 7L);
		admin = new Usuario("Admin", "admin@exemplo.invalid", HASH_REAL, LocalDate.of(2000, 1, 1), "v1", Papel.ADMIN);
		ReflectionTestUtils.setField(admin, "id", 8L);
		lenient().when(usuarios.buscarAtivoPorPublicId(aluno.getPublicId())).thenReturn(Optional.of(aluno));
		lenient().when(usuarios.buscarAtivoPorPublicId(admin.getPublicId())).thenReturn(Optional.of(admin));
	}

	private UsuarioAutenticado como(Usuario u) {
		return new UsuarioAutenticado(u.getPublicId(), u.getPapel());
	}

	private void deveDar400(Usuario u, String atual, String nova, CodigoErro codigo, String campo) {
		assertThatThrownBy(() -> servico.trocar(como(u), new TrocaDeSenhaRequest(atual, nova)))
				.isExactlyInstanceOf(InvalidRequestException.class).satisfies(e -> {
					InvalidRequestException ex = (InvalidRequestException) e;
					assertThat(ex.getCodigo()).isEqualTo(codigo);
					if (campo != null) {
						assertThat(ex.getCampos()).singleElement().extracting(c -> c.campo()).isEqualTo(campo);
					}
				});
		verify(sessoes, never()).encerrarTodasAsSessoes(any(), any());
		verify(limite, never()).zerar(any(), anyString());
	}

	@Test
	void deve_contar_no_bucket_do_usuario_antes_de_qualquer_validacao_inclusive_o_400_de_formato() {
		deveDar400(aluno, null, "nova-senha-123", CodigoErro.VALIDACAO_FALHOU, "senhaAtual");

		verify(limite).consumir(Escopo.TROCA_SENHA_USUARIO, aluno.getPublicId().toString(), limites.trocaSenhaUsuario());
		verifyNoInteractions(sessoes);
	}

	@Test
	void deve_responder_429_antes_de_validar_formato_e_sem_nenhuma_comparacao_de_bcrypt() {
		doThrow(new LimiteExcedidoException(900)).when(limite).consumir(eq(Escopo.TROCA_SENHA_USUARIO), anyString(), any());

		assertThatThrownBy(() -> servico.trocar(como(aluno), new TrocaDeSenhaRequest(null, null)))
				.isExactlyInstanceOf(LimiteExcedidoException.class);

		verify(encoder, never()).matches(anyString(), anyString());
		verifyNoInteractions(usuarios, sessoes);
	}

	@Test
	void deve_dar_400_com_campo_quando_a_nova_senha_falta_ou_viola_a_politica_do_papel() {
		deveDar400(aluno, "atual-qualquer", null, CodigoErro.VALIDACAO_FALHOU, "novaSenha");
		deveDar400(aluno, "atual-qualquer", "1234567", CodigoErro.VALIDACAO_FALHOU, "novaSenha");
		deveDar400(admin, "atual-qualquer", "onze-bytes1", CodigoErro.VALIDACAO_FALHOU, "novaSenha");
		deveDar400(admin, "atual-qualquer", " espaco-na-ponta", CodigoErro.VALIDACAO_FALHOU, "novaSenha");
		deveDar400(admin, "atual-qualquer", "troque-a-senha-do-admin", CodigoErro.VALIDACAO_FALHOU, "novaSenha");

		verify(encoder, never()).matches(anyString(), anyString());
	}

	@Test
	void deve_dar_400_senha_atual_incorreta_com_uma_unica_comparacao_e_sem_zerar_o_bucket() {
		when(encoder.matches("errada", HASH_REAL)).thenReturn(false);

		deveDar400(aluno, "errada", "nova-senha-123", CodigoErro.SENHA_ATUAL_INCORRETA, null);

		verify(encoder, times(1)).matches(anyString(), anyString());
	}

	@Test
	void deve_nunca_comparar_a_senha_atual_com_mais_de_72_bytes_com_o_hash_real() {
		String longa = "x".repeat(73);

		deveDar400(aluno, longa, "nova-senha-123", CodigoErro.SENHA_ATUAL_INCORRETA, null);

		verify(encoder, times(1)).matches(anyString(), eq(HASH_FICTICIO));
		verify(encoder, never()).matches(anyString(), eq(HASH_REAL));
	}

	@Test
	void deve_dar_400_no_campo_nova_senha_quando_ela_e_igual_a_atual_sem_trocar_nada() {
		when(encoder.matches("mesma-senha-123", HASH_REAL)).thenReturn(true);

		deveDar400(aluno, "mesma-senha-123", "mesma-senha-123", CodigoErro.VALIDACAO_FALHOU, "novaSenha");
	}

	@Test
	void deve_trocar_o_hash_encerrar_as_sessoes_com_o_motivo_troca_senha_e_so_entao_zerar_o_bucket() {
		when(encoder.matches("senha-atual-123", HASH_REAL)).thenReturn(true);
		when(encoder.encode("nova-senha-123")).thenReturn("{bcrypt}novo-hash-falso");

		servico.trocar(como(aluno), new TrocaDeSenhaRequest("senha-atual-123", "nova-senha-123"));

		InOrder ordem = inOrder(usuarios, sessoes, limite);
		ordem.verify(usuarios).trocarSenhaHash(7L, "{bcrypt}novo-hash-falso");
		ordem.verify(sessoes).encerrarTodasAsSessoes(7L, MotivoRevogacao.TROCA_SENHA);
		ordem.verify(limite).zerar(Escopo.TROCA_SENHA_USUARIO, aluno.getPublicId().toString());
	}

	@Test
	void deve_aplicar_a_politica_do_admin_a_nova_senha_do_admin_e_aceitar_12_bytes() {
		when(encoder.matches("atual-do-admin-123", HASH_REAL)).thenReturn(true);

		servico.trocar(como(admin), new TrocaDeSenhaRequest("atual-do-admin-123", "a".repeat(12)));

		verify(sessoes).encerrarTodasAsSessoes(8L, MotivoRevogacao.TROCA_SENHA);
	}

	@Test
	void deve_dar_401_quando_o_usuario_sumiu_entre_o_filtro_e_o_service() {
		UUID id = UUID.randomUUID();
		when(usuarios.buscarAtivoPorPublicId(id)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> servico.trocar(new UsuarioAutenticado(id, Papel.STUDENT),
				new TrocaDeSenhaRequest("a", "nova-senha-123"))).isInstanceOf(br.simulado.etec.shared.exception.InvalidTokenException.class);
	}

}
