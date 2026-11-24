package br.simulado.etec.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import br.simulado.etec.auth.dto.TokensResponse;
import br.simulado.etec.auth.repository.RefreshTokenRepository;
import br.simulado.etec.shared.security.JwtTokenProvider;
import br.simulado.etec.shared.security.JwtTokenProvider.ClaimsDoToken;
import br.simulado.etec.shared.security.JwtTokenProvider.TipoToken;
import br.simulado.etec.usuarios.model.Usuario;
import br.simulado.etec.usuarios.service.UsuarioService;

/**
 * Se a inserção do refresh novo falha, o rollback devolve a validade ao antigo (D133): a rotação é uma transação só.
 */
@SpringBootTest
class RotacaoComFalhaNaInsercaoTest {

	@Autowired
	private SessaoService sessoes;

	@Autowired
	private UsuarioService usuarios;

	@Autowired
	private JwtTokenProvider tokens;

	@MockitoSpyBean
	private RefreshTokenRepository refreshTokens;

	@Test
	void deve_devolver_a_validade_ao_refresh_antigo_quando_a_insercao_do_novo_falha() {
		String email = "rollback." + UUID.randomUUID().toString().substring(0, 8) + "@exemplo.invalid";
		usuarios.criarAluno("Aluno", email, "senha-de-teste-123", LocalDate.of(2008, 5, 10), "v1-teste");
		Usuario usuario = usuarios.buscarPorEmail(email).orElseThrow();
		TokensResponse inicial = sessoes.iniciar(usuario);
		ClaimsDoToken claims = tokens.validar(inicial.refreshToken(), TipoToken.REFRESH);
		doThrow(new IllegalStateException("falha ao inserir")).when(refreshTokens).save(any());

		assertThatThrownBy(() -> sessoes.rotacionar(claims, usuario)).isInstanceOf(IllegalStateException.class);

		reset(refreshTokens);
		assertThat(refreshTokens.findByJti(UUID.fromString(claims.jti())).orElseThrow().getRevogadoEm())
				.as("o UPDATE de revogação foi desfeito junto").isNull();
		assertThat(sessoes.rotacionar(claims, usuario)).as("o refresh antigo ainda rotaciona").isPresent();
	}

}
