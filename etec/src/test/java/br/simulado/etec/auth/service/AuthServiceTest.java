package br.simulado.etec.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;

import br.simulado.etec.auth.dto.RefreshRequest;
import br.simulado.etec.auth.dto.RegistroRequest;
import br.simulado.etec.auth.dto.SigninRequest;
import br.simulado.etec.auth.dto.TokensResponse;
import br.simulado.etec.auth.model.MotivoRevogacao;
import br.simulado.etec.shared.exception.CodigoErro;
import br.simulado.etec.shared.exception.CredenciaisInvalidasException;
import br.simulado.etec.shared.exception.InvalidTokenException;
import br.simulado.etec.shared.exception.LimiteExcedidoException;
import br.simulado.etec.shared.security.JwtProperties;
import br.simulado.etec.shared.security.JwtTokenProvider;
import br.simulado.etec.shared.security.JwtTokenProvider.ClaimsDoToken;
import br.simulado.etec.shared.security.JwtTokenProvider.TipoToken;
import br.simulado.etec.shared.security.SegredoDeTeste;
import br.simulado.etec.shared.security.limite.Escopo;
import br.simulado.etec.shared.security.limite.LimiteDeTentativas;
import br.simulado.etec.shared.security.limite.RateLimitProperties;
import br.simulado.etec.usuarios.model.Papel;
import br.simulado.etec.usuarios.model.Usuario;
import br.simulado.etec.usuarios.service.UsuarioService;

/**
 * Regras do signin e do refresh. O PasswordEncoder é falso de propósito: o que se prova aqui é QUANTAS comparações
 * acontecem e com qual hash (o hash fictício quando não há usuário válido), que é o que iguala o tempo das respostas.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

	private static final String HASH_FICTICIO = "{bcrypt}hash-ficticio-da-subida";

	private static final String HASH_REAL = "{bcrypt}hash-real-do-usuario";

	private static final Instant AGORA = Instant.parse("2026-11-23T12:00:00Z");

	@Mock
	private UsuarioService usuarios;

	@Mock
	private PasswordEncoder encoder;

	@Mock
	private LimiteDeTentativas limite;

	@Mock
	private SessaoService sessoes;

	private static final String ORIGEM = "v4:127.0.0.1";

	private final RateLimitProperties limites = new RateLimitProperties(
			new RateLimitProperties.Regra(120, Duration.ofMinutes(1)),
			new RateLimitProperties.Regra(5, Duration.ofMinutes(15)),
			new RateLimitProperties.Regra(50, Duration.ofMinutes(15)), new RateLimitProperties.Regra(5, Duration.ofMinutes(15)),
			false);

	private final JwtTokenProvider tokens = new JwtTokenProvider(
			new JwtProperties(SegredoDeTeste.gerar(), "etec-teste", Duration.ofMinutes(15), Duration.ofHours(24)),
			Clock.fixed(AGORA, ZoneOffset.UTC));

	private AuthService servico;

	private Usuario aluno;

	@BeforeEach
	void montar() {
		when(encoder.encode(anyString())).thenReturn(HASH_FICTICIO);
		servico = new AuthService(usuarios, encoder, tokens, limite, limites, sessoes);
		aluno = new Usuario("Ana", "ana@exemplo.invalid", HASH_REAL, LocalDate.of(2008, 5, 10), "v1", Papel.STUDENT);
		ReflectionTestUtils.setField(aluno, "id", 7L);
	}

	private static final TokensResponse PAR = new TokensResponse("acesso", "refresh", "Bearer", 900);

	private void deveRecusarComOMesmo401(SigninRequest dados) {
		assertThatThrownBy(() -> servico.signin(dados, ORIGEM)).isExactlyInstanceOf(CredenciaisInvalidasException.class)
				.hasMessage(CredenciaisInvalidasException.MENSAGEM)
				.extracting(e -> ((CredenciaisInvalidasException) e).getCodigo())
				.isEqualTo(CodigoErro.CREDENCIAIS_INVALIDAS);
	}

	// ----- signin: sucesso -----

	@Test
	void deve_devolver_o_par_de_tokens_quando_as_credenciais_conferem() {
		when(usuarios.buscarPorEmail("ana@exemplo.invalid")).thenReturn(Optional.of(aluno));
		when(encoder.matches("senha-correta", HASH_REAL)).thenReturn(true);
		when(sessoes.iniciar(aluno)).thenReturn(PAR);

		TokensResponse resposta = servico.signin(new SigninRequest("  ANA@exemplo.invalid ", "senha-correta"), ORIGEM);

		assertThat(resposta).as("o par e a gravação do refresh vêm da sessão").isSameAs(PAR);
	}

	// ----- signin: limite de tentativas (D128) -----

	@Test
	void deve_contar_antes_de_conferir_a_senha_primeiro_email_e_origem_depois_a_origem() {
		when(usuarios.buscarPorEmail("ana@exemplo.invalid")).thenReturn(Optional.empty());

		deveRecusarComOMesmo401(new SigninRequest("ana@exemplo.invalid", "errada"));

		InOrder ordem = inOrder(limite, encoder);
		ordem.verify(limite).consumir(Escopo.SIGNIN_EMAIL_ORIGEM, ORIGEM + "|ana@exemplo.invalid", limites.signinEmailOrigem());
		ordem.verify(limite).consumir(Escopo.SIGNIN_ORIGEM, ORIGEM, limites.signinOrigem());
		ordem.verify(encoder).matches(anyString(), anyString());
	}

	@Test
	void deve_responder_429_sem_nenhuma_comparacao_de_bcrypt_nem_consulta_ao_banco_quando_email_e_origem_estouram() {
		doThrow(new LimiteExcedidoException(900)).when(limite).consumir(eq(Escopo.SIGNIN_EMAIL_ORIGEM), anyString(), any());

		assertThatThrownBy(() -> servico.signin(new SigninRequest("ana@exemplo.invalid", "qualquer"), ORIGEM))
				.isExactlyInstanceOf(LimiteExcedidoException.class);

		verify(limite, never()).consumir(eq(Escopo.SIGNIN_ORIGEM), anyString(), any());
		verifyNoInteractions(usuarios);
		verify(encoder, never()).matches(anyString(), anyString());
	}

	@Test
	void deve_responder_429_antes_do_bcrypt_quando_so_a_origem_estoura() {
		lenient().doThrow(new LimiteExcedidoException(120)).when(limite).consumir(eq(Escopo.SIGNIN_ORIGEM), anyString(), any());

		assertThatThrownBy(() -> servico.signin(new SigninRequest("ana@exemplo.invalid", "qualquer"), ORIGEM))
				.isExactlyInstanceOf(LimiteExcedidoException.class);

		verify(encoder, never()).matches(anyString(), anyString());
	}

	@Test
	void deve_deixar_as_falhas_contadas_sem_zerar_nem_devolver() {
		when(usuarios.buscarPorEmail("ana@exemplo.invalid")).thenReturn(Optional.of(aluno));
		when(encoder.matches("errada", HASH_REAL)).thenReturn(false);

		deveRecusarComOMesmo401(new SigninRequest("ana@exemplo.invalid", "errada"));

		verify(limite, never()).zerar(any(), anyString());
		verify(limite, never()).devolver(any(), anyString());
	}

	@Test
	void deve_zerar_o_email_e_origem_e_devolver_uma_ocorrencia_a_origem_quando_o_login_da_certo() {
		when(usuarios.buscarPorEmail("ana@exemplo.invalid")).thenReturn(Optional.of(aluno));
		when(encoder.matches("senha-correta", HASH_REAL)).thenReturn(true);

		servico.signin(new SigninRequest("ana@exemplo.invalid", "senha-correta"), ORIGEM);

		verify(limite).zerar(Escopo.SIGNIN_EMAIL_ORIGEM, ORIGEM + "|ana@exemplo.invalid");
		verify(limite).devolver(Escopo.SIGNIN_ORIGEM, ORIGEM);
	}

	@Test
	void deve_contar_igual_para_email_inexistente_ou_de_formato_invalido_sem_virar_400() {
		when(usuarios.buscarPorEmail("isto-nao-e-email")).thenReturn(Optional.empty());

		deveRecusarComOMesmo401(new SigninRequest("isto-nao-e-email", "qualquer"));

		verify(limite).consumir(Escopo.SIGNIN_EMAIL_ORIGEM, ORIGEM + "|isto-nao-e-email", limites.signinEmailOrigem());
	}

	@Test
	void deve_por_a_origem_antes_do_email_para_o_corte_de_255_caracteres_nunca_remove_la() {
		String longo = "a".repeat(300) + "@exemplo.invalid";

		deveRecusarComOMesmo401(new SigninRequest(longo, "qualquer"));

		ArgumentCaptor<String> valor = ArgumentCaptor.forClass(String.class);
		verify(limite).consumir(eq(Escopo.SIGNIN_EMAIL_ORIGEM), valor.capture(), any());
		assertThat(valor.getValue()).startsWith(ORIGEM + "|");
	}

	// ----- signin: toda falha = mesma exceção + UMA comparação -----

	@Test
	void deve_comparar_com_o_hash_ficticio_e_dar_401_quando_o_usuario_nao_existe() {
		when(usuarios.buscarPorEmail("ninguem@exemplo.invalid")).thenReturn(Optional.empty());

		deveRecusarComOMesmo401(new SigninRequest("ninguem@exemplo.invalid", "qualquer-senha"));

		verify(encoder, times(1)).matches("qualquer-senha", HASH_FICTICIO);
		verify(encoder, times(1)).matches(anyString(), anyString());
	}

	@Test
	void deve_dar_401_quando_a_senha_esta_errada_com_uma_unica_comparacao_com_o_hash_real() {
		when(usuarios.buscarPorEmail("ana@exemplo.invalid")).thenReturn(Optional.of(aluno));
		when(encoder.matches("senha-errada", HASH_REAL)).thenReturn(false);

		deveRecusarComOMesmo401(new SigninRequest("ana@exemplo.invalid", "senha-errada"));

		verify(encoder, times(1)).matches(anyString(), anyString());
	}

	@Test
	void deve_comparar_e_dar_401_quando_a_conta_foi_excluida_mesmo_que_a_senha_confira() {
		ReflectionTestUtils.setField(aluno, "excluidoEm", AGORA);
		when(usuarios.buscarPorEmail("ana@exemplo.invalid")).thenReturn(Optional.of(aluno));
		when(encoder.matches(anyString(), eq(HASH_FICTICIO))).thenReturn(true); // nem assim entra

		deveRecusarComOMesmo401(new SigninRequest("ana@exemplo.invalid", "senha-correta"));

		verify(encoder, times(1)).matches(anyString(), anyString());
		verify(encoder, never()).matches(anyString(), eq(HASH_REAL));
	}

	@Test
	void deve_dar_401_sem_tocar_no_hash_real_quando_a_senha_tem_mais_de_72_bytes() {
		when(usuarios.buscarPorEmail("ana@exemplo.invalid")).thenReturn(Optional.of(aluno));
		// Mesmo que o BCrypt "conferisse" (ele trunca em 72 bytes), o service nem tenta com o hash real.
		when(encoder.matches(anyString(), anyString())).thenReturn(true);
		String longa = "a".repeat(73);

		deveRecusarComOMesmo401(new SigninRequest("ana@exemplo.invalid", longa));

		ArgumentCaptor<String> hash = ArgumentCaptor.forClass(String.class);
		verify(encoder, times(1)).matches(anyString(), hash.capture());
		assertThat(hash.getValue()).isEqualTo(HASH_FICTICIO);
	}

	@Test
	void deve_contar_os_72_bytes_em_utf8_e_nao_em_caracteres() {
		when(usuarios.buscarPorEmail("ana@exemplo.invalid")).thenReturn(Optional.of(aluno));
		String com36Acentuados = "é".repeat(36); // 72 bytes: ainda cabe
		when(encoder.matches(com36Acentuados, HASH_REAL)).thenReturn(true);
		when(sessoes.iniciar(aluno)).thenReturn(PAR);

		assertThat(servico.signin(new SigninRequest("ana@exemplo.invalid", com36Acentuados), ORIGEM).tokenType())
				.isEqualTo("Bearer");

		String com37Acentuados = "é".repeat(37); // 74 bytes: não cabe
		deveRecusarComOMesmo401(new SigninRequest("ana@exemplo.invalid", com37Acentuados));
	}

	@Test
	void deve_comparar_e_dar_401_quando_a_senha_esta_vazia() {
		when(usuarios.buscarPorEmail("ana@exemplo.invalid")).thenReturn(Optional.of(aluno));
		when(encoder.matches("", HASH_REAL)).thenReturn(false);

		deveRecusarComOMesmo401(new SigninRequest("ana@exemplo.invalid", ""));

		verify(encoder, times(1)).matches("", HASH_REAL);
	}

	@Test
	void deve_comparar_com_o_hash_ficticio_quando_o_email_tem_formato_invalido() {
		when(usuarios.buscarPorEmail("isto-nao-e-email")).thenReturn(Optional.empty());

		deveRecusarComOMesmo401(new SigninRequest("isto-nao-e-email", "qualquer-senha"));
		deveRecusarComOMesmo401(new SigninRequest("", "qualquer-senha"));

		verify(encoder, times(2)).matches(anyString(), eq(HASH_FICTICIO));
	}

	@Test
	void deve_comparar_sem_consultar_o_banco_quando_o_email_passa_de_255_caracteres() {
		deveRecusarComOMesmo401(new SigninRequest("a".repeat(256) + "@exemplo.invalid", "qualquer-senha"));

		verify(usuarios, never()).buscarPorEmail(anyString());
		verify(encoder, times(1)).matches(anyString(), eq(HASH_FICTICIO));
	}

	// ----- refresh: rotação, reuso, versão (D133 a D136) -----

	private ClaimsDoToken claimsDe(String refresh) {
		return tokens.validar(refresh, TipoToken.REFRESH);
	}

	@Test
	void deve_devolver_o_par_da_rotacao_quando_o_refresh_e_valido_e_o_usuario_existe() {
		String refresh = tokens.emitir(TipoToken.REFRESH, aluno.getPublicId(), "STUDENT", 0).valor();
		when(usuarios.buscarAtivoPorPublicId(aluno.getPublicId())).thenReturn(Optional.of(aluno));
		when(sessoes.rotacionar(claimsDe(refresh), aluno)).thenReturn(Optional.of(PAR));

		assertThat(servico.refresh(new RefreshRequest(refresh))).isSameAs(PAR);

		verify(sessoes, never()).encerrarTodasAsSessoes(any(), any());
	}

	@Test
	void deve_dar_401_sem_revogacao_em_massa_quando_nao_rotaciona_e_o_refresh_nao_foi_rotacionado() {
		String refresh = tokens.emitir(TipoToken.REFRESH, aluno.getPublicId(), "STUDENT", 0).valor();
		when(usuarios.buscarAtivoPorPublicId(aluno.getPublicId())).thenReturn(Optional.of(aluno));
		when(sessoes.rotacionar(any(), eq(aluno))).thenReturn(Optional.empty());
		when(sessoes.foiRotacionado(claimsDe(refresh).jti())).thenReturn(false);

		assertThatThrownBy(() -> servico.refresh(new RefreshRequest(refresh))).isExactlyInstanceOf(InvalidTokenException.class);

		verify(sessoes, never()).encerrarTodasAsSessoes(any(), any());
	}

	@Test
	void deve_revogar_tudo_por_reuso_e_so_depois_dar_401_quando_o_refresh_ja_foi_rotacionado() {
		String refresh = tokens.emitir(TipoToken.REFRESH, aluno.getPublicId(), "STUDENT", 0).valor();
		when(usuarios.buscarAtivoPorPublicId(aluno.getPublicId())).thenReturn(Optional.of(aluno));
		when(sessoes.rotacionar(any(), eq(aluno))).thenReturn(Optional.empty());
		when(sessoes.foiRotacionado(claimsDe(refresh).jti())).thenReturn(true);

		assertThatThrownBy(() -> servico.refresh(new RefreshRequest(refresh))).isExactlyInstanceOf(InvalidTokenException.class);

		verify(sessoes).encerrarTodasAsSessoes(7L, MotivoRevogacao.REUSO);
	}

	@Test
	void deve_dar_401_sem_rotacionar_quando_a_versao_do_token_nao_e_a_do_usuario() {
		String refreshAntigo = tokens.emitir(TipoToken.REFRESH, aluno.getPublicId(), "STUDENT", 3).valor();
		when(usuarios.buscarAtivoPorPublicId(aluno.getPublicId())).thenReturn(Optional.of(aluno));

		assertThatThrownBy(() -> servico.refresh(new RefreshRequest(refreshAntigo))).isExactlyInstanceOf(InvalidTokenException.class);

		verify(sessoes, never()).rotacionar(any(), any());
		verify(sessoes, never()).encerrarTodasAsSessoes(any(), any());
	}

	@Test
	void deve_dar_401_no_refresh_quando_o_usuario_foi_excluido_ou_nao_existe() {
		String refresh = tokens.emitir(TipoToken.REFRESH, UUID.randomUUID(), "STUDENT", 0).valor();
		when(usuarios.buscarAtivoPorPublicId(org.mockito.ArgumentMatchers.any())).thenReturn(Optional.empty());

		assertThatThrownBy(() -> servico.refresh(new RefreshRequest(refresh))).isInstanceOf(InvalidTokenException.class);

		verify(sessoes, never()).rotacionar(any(), any());
	}

	@Test
	void deve_delegar_o_logout_a_sessao() {
		servico.logout(new RefreshRequest("qualquer-texto"));

		verify(sessoes).logout("qualquer-texto");
	}

	@Test
	void deve_dar_401_no_refresh_quando_o_token_e_um_access_vazio_ou_lixo() {
		String access = tokens.emitir(TipoToken.ACCESS, aluno.getPublicId(), "STUDENT", 0).valor();

		assertThatThrownBy(() -> servico.refresh(new RefreshRequest(access))).isInstanceOf(InvalidTokenException.class);
		assertThatThrownBy(() -> servico.refresh(new RefreshRequest(""))).isInstanceOf(InvalidTokenException.class);
		assertThatThrownBy(() -> servico.refresh(new RefreshRequest("lixo"))).isInstanceOf(InvalidTokenException.class);
		verify(usuarios, never()).buscarAtivoPorPublicId(org.mockito.ArgumentMatchers.any());
	}

	// ----- registrar -----

	@Test
	void deve_repassar_os_dados_ja_normalizados_ao_service_de_usuarios() {
		LocalDate nascimento = LocalDate.of(2008, 5, 10);
		var dados = new RegistroRequest("  Ana  ", " ANA@Exemplo.Invalid ", "senha-12345", nascimento, "v1");

		servico.registrar(dados);

		verify(usuarios).criarAluno("Ana", "ana@exemplo.invalid", "senha-12345", nascimento, "v1");
	}

}
