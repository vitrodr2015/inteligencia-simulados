package br.simulado.etec.shared.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import br.simulado.etec.shared.exception.InvalidTokenException;
import br.simulado.etec.shared.security.JwtTokenProvider.TipoToken;
import br.simulado.etec.usuarios.model.Papel;
import br.simulado.etec.usuarios.model.Usuario;
import br.simulado.etec.usuarios.service.UsuarioService;

/**
 * O token só vale se o usuário ainda existir no banco (D26), e quem manda no papel é o banco (D87), não o claim.
 */
@ExtendWith(MockitoExtension.class)
class AutenticadorDeTokenTests {

	@Mock
	private UsuarioService usuarios;

	private final JwtTokenProvider tokens = new JwtTokenProvider(
			new JwtProperties(SegredoDeTeste.gerar(), "etec-teste", Duration.ofMinutes(15), Duration.ofHours(24)),
			Clock.fixed(Instant.parse("2026-11-23T12:00:00Z"), ZoneOffset.UTC));

	private final Usuario aluno = new Usuario("Ana", "ana@exemplo.invalid", "h", LocalDate.of(2008, 5, 10), "v1",
			Papel.STUDENT);

	private AutenticadorDeToken autenticador() {
		return new AutenticadorDeToken(tokens, usuarios);
	}

	@Test
	void deve_autenticar_com_o_papel_do_banco_quando_o_token_e_valido_e_o_usuario_existe() {
		String token = tokens.emitir(TipoToken.ACCESS, aluno.getPublicId(), "STUDENT").valor();
		when(usuarios.buscarAtivoPorPublicId(aluno.getPublicId())).thenReturn(Optional.of(aluno));

		UsuarioAutenticado autenticado = autenticador().autenticar(token);

		assertThat(autenticado.id()).isEqualTo(aluno.getPublicId());
		assertThat(autenticado.papel()).isEqualTo(Papel.STUDENT);
	}

	@Test
	void deve_usar_o_papel_do_banco_mesmo_quando_o_claim_diz_admin() {
		// Token legítimo (assinado com o segredo certo), mas com o claim papel=ADMIN para um STUDENT.
		String token = tokens.emitir(TipoToken.ACCESS, aluno.getPublicId(), "ADMIN").valor();
		when(usuarios.buscarAtivoPorPublicId(aluno.getPublicId())).thenReturn(Optional.of(aluno));

		assertThat(autenticador().autenticar(token).papel()).isEqualTo(Papel.STUDENT);
	}

	@Test
	void deve_recusar_o_token_quando_o_usuario_foi_excluido_ou_nao_existe() {
		String token = tokens.emitir(TipoToken.ACCESS, aluno.getPublicId(), "STUDENT").valor();
		when(usuarios.buscarAtivoPorPublicId(aluno.getPublicId())).thenReturn(Optional.empty());

		assertThatThrownBy(() -> autenticador().autenticar(token)).isInstanceOf(InvalidTokenException.class);
	}

	@Test
	void deve_recusar_sem_consultar_o_banco_quando_o_token_e_um_refresh_ou_e_invalido() {
		String refresh = tokens.emitir(TipoToken.REFRESH, aluno.getPublicId(), "STUDENT").valor();

		assertThatThrownBy(() -> autenticador().autenticar(refresh)).isInstanceOf(InvalidTokenException.class);
		assertThatThrownBy(() -> autenticador().autenticar("lixo")).isInstanceOf(InvalidTokenException.class);
		verify(usuarios, never()).buscarAtivoPorPublicId(any());
	}

	@Test
	void deve_consultar_o_banco_a_cada_chamada_e_nunca_guardar_o_resultado() {
		String token = tokens.emitir(TipoToken.ACCESS, aluno.getPublicId(), "STUDENT").valor();
		when(usuarios.buscarAtivoPorPublicId(aluno.getPublicId())).thenReturn(Optional.of(aluno))
				.thenReturn(Optional.empty());

		AutenticadorDeToken autenticador = autenticador();

		assertThat(autenticador.autenticar(token)).isNotNull();
		assertThatThrownBy(() -> autenticador.autenticar(token)).isInstanceOf(InvalidTokenException.class);
	}

}
