package br.simulado.etec.shared.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import br.simulado.etec.shared.exception.InvalidTokenException;
import br.simulado.etec.usuarios.model.Papel;
import tools.jackson.databind.json.JsonMapper;

/** O filtro isolado: token ruim não derruba a requisição; falha de banco é 500; nada do token vai para o log. */
@ExtendWith(OutputCaptureExtension.class)
class JwtTokenFilterTest {

	private static final String TOKEN_SECRETO = "eyJ.TOKEN-SECRETO-DO-TESTE.assinatura";

	private final AutenticadorDeToken autenticador = mock(AutenticadorDeToken.class);

	private final SecurityErrorWriter escritor = new SecurityErrorWriter(JsonMapper.builder().build(),
			Clock.fixed(Instant.parse("2026-11-23T12:00:00Z"), ZoneOffset.UTC));

	private final JwtTokenFilter filtro = new JwtTokenFilter(autenticador, escritor);

	private final UUID usuario = UUID.randomUUID();

	@AfterEach
	void limpar() {
		SecurityContextHolder.clearContext();
	}

	private MockHttpServletRequest comAutorizacao(String... valores) {
		MockHttpServletRequest requisicao = new MockHttpServletRequest("GET", "/api/v1/materias");
		for (String valor : valores) {
			requisicao.addHeader("Authorization", valor);
		}
		return requisicao;
	}

	/** Roda o filtro e devolve a autenticação que a cadeia (o resto da aplicação) enxergou. */
	private Authentication autenticacaoVistaPelaCadeia(MockHttpServletRequest requisicao,
			MockHttpServletResponse resposta) throws Exception {
		AtomicReference<Authentication> vista = new AtomicReference<>();
		MockFilterChain cadeia = new MockFilterChain() {
			@Override
			public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res)
					throws java.io.IOException, jakarta.servlet.ServletException {
				vista.set(SecurityContextHolder.getContext().getAuthentication());
				super.doFilter(req, res);
			}
		};
		filtro.doFilter(requisicao, resposta, cadeia);
		return vista.get();
	}

	@Test
	void deve_seguir_sem_autenticacao_quando_nao_ha_cabecalho() throws Exception {
		MockHttpServletResponse resposta = new MockHttpServletResponse();

		assertThat(autenticacaoVistaPelaCadeia(comAutorizacao(), resposta)).isNull();
		assertThat(resposta.getStatus()).isEqualTo(200);
	}

	@Test
	void deve_autenticar_com_a_autoridade_do_papel_do_banco_quando_o_token_e_valido() throws Exception {
		when(autenticador.autenticar(TOKEN_SECRETO)).thenReturn(new UsuarioAutenticado(usuario, Papel.STUDENT));

		Authentication autenticacao = autenticacaoVistaPelaCadeia(comAutorizacao("Bearer " + TOKEN_SECRETO),
				new MockHttpServletResponse());

		assertThat(autenticacao).isNotNull();
		assertThat(autenticacao.isAuthenticated()).isTrue();
		assertThat(autenticacao.getPrincipal()).isEqualTo(new UsuarioAutenticado(usuario, Papel.STUDENT));
		assertThat(autenticacao.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_STUDENT");
	}

	@Test
	void deve_aceitar_o_esquema_bearer_sem_distinguir_caixa() throws Exception {
		when(autenticador.autenticar(TOKEN_SECRETO)).thenReturn(new UsuarioAutenticado(usuario, Papel.ADMIN));

		assertThat(autenticacaoVistaPelaCadeia(comAutorizacao("bEaReR " + TOKEN_SECRETO), new MockHttpServletResponse()))
				.isNotNull();
	}

	@Test
	void deve_seguir_sem_autenticacao_e_sem_erro_quando_o_token_e_recusado() throws Exception {
		when(autenticador.autenticar(anyString())).thenThrow(new InvalidTokenException());
		MockHttpServletResponse resposta = new MockHttpServletResponse();

		assertThat(autenticacaoVistaPelaCadeia(comAutorizacao("Bearer " + TOKEN_SECRETO), resposta)).isNull();
		assertThat(resposta.getStatus()).isEqualTo(200);
	}

	@Test
	void deve_ignorar_cabecalhos_que_nao_sao_um_bearer_unico_e_nao_vazio() throws Exception {
		for (MockHttpServletRequest requisicao : new MockHttpServletRequest[] { comAutorizacao("Basic dXNlcjpwYXNz"),
				comAutorizacao("Bearer"), comAutorizacao("Bearer "), comAutorizacao("Bearer    "),
				comAutorizacao(TOKEN_SECRETO), comAutorizacao("Bearer a", "Bearer b") }) {
			assertThat(autenticacaoVistaPelaCadeia(requisicao, new MockHttpServletResponse())).isNull();
		}
		org.mockito.Mockito.verifyNoInteractions(autenticador);
	}

	@Test
	void deve_responder_500_generico_em_error_response_quando_o_banco_falha_no_filtro() throws Exception {
		when(autenticador.autenticar(anyString()))
				.thenThrow(new DataAccessResourceFailureException("jdbc:mysql://segredo:3306 senha=ABC"));
		MockHttpServletResponse resposta = new MockHttpServletResponse();
		MockFilterChain cadeia = new MockFilterChain();

		filtro.doFilter(comAutorizacao("Bearer " + TOKEN_SECRETO), resposta, cadeia);

		assertThat(resposta.getStatus()).isEqualTo(500);
		assertThat(resposta.getContentType()).isEqualTo("application/json");
		String corpo = resposta.getContentAsString();
		assertThat(corpo).contains("\"status\":500").contains("\"codigo\":\"ERRO_INTERNO\"")
				.contains("\"erro\":\"Internal Server Error\"").doesNotContain("jdbc").doesNotContain("segredo")
				.doesNotContain("senha").doesNotContain("DataAccess").doesNotContain("campos");
		assertThat(cadeia.getRequest()).as("a cadeia não pode continuar").isNull();
	}

	@Test
	void deve_nunca_por_o_token_no_log_nem_quando_ele_e_recusado_nem_quando_o_banco_falha(CapturedOutput saida)
			throws Exception {
		when(autenticador.autenticar(anyString())).thenThrow(new InvalidTokenException())
				.thenThrow(new DataAccessResourceFailureException("falha de banco"));

		autenticacaoVistaPelaCadeia(comAutorizacao("Bearer " + TOKEN_SECRETO), new MockHttpServletResponse());
		filtro.doFilter(comAutorizacao("Bearer " + TOKEN_SECRETO), new MockHttpServletResponse(), new MockFilterChain());

		assertThat(saida.getAll()).doesNotContain("TOKEN-SECRETO").doesNotContain(TOKEN_SECRETO);
	}

}
