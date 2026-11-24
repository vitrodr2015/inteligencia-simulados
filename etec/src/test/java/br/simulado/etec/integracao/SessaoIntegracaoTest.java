package br.simulado.etec.integracao;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;

import br.simulado.etec.shared.security.JwtTokenProvider.TipoToken;
import br.simulado.etec.shared.security.TokensForjados;
import br.simulado.etec.usuarios.model.Papel;
import br.simulado.etec.usuarios.model.Usuario;
import br.simulado.etec.usuarios.service.UsuarioService;
import io.restassured.response.Response;

/**
 * O ciclo de vida do token contra MySQL REAL e HTTP real (Módulo 13B, D133 a D139): refresh guardado e rotacionado, reuso,
 * logout, versão do token e troca de senha. Cada teste cria o próprio usuário (com senha de verdade) e só olha para ele.
 */
class SessaoIntegracaoTest extends AbstractIntegrationTest {

	private static final String SENHA = "senha-de-teste-123";

	@Autowired
	private UsuarioService usuarios;

	@Autowired
	private PasswordEncoder encoder;

	@Value("${app.termos.versao}")
	private String termosVersao;

	// ----- apoio -----

	private record Tokens(String access, String refresh) {
	}

	private String novoAluno() {
		String email = "sessao." + sufixo() + "@exemplo.invalid";
		usuarios.criarAluno("Aluno de Sessão", email, SENHA, LocalDate.of(2008, 5, 10), termosVersao);
		return email;
	}

	private String novoUsuarioComSenha(Papel papel, String senha) {
		String email = "sessao." + sufixo() + "@exemplo.invalid";
		usuariosDeTeste.saveAndFlush(new Usuario("Usuário de Sessão", email, encoder.encode(senha), LocalDate.of(2000, 1, 1),
				termosVersao, papel));
		return email;
	}

	private Response signin(String email, String senha) {
		return requisicaoSemToken().body("{\"email\":\"" + email + "\",\"senha\":\"" + senha + "\"}")
				.post("/api/v1/auth/signin");
	}

	private Tokens entrar(String email) {
		Response r = signin(email, SENHA);
		r.then().statusCode(200);
		return new Tokens(r.jsonPath().getString("accessToken"), r.jsonPath().getString("refreshToken"));
	}

	private Response refresh(String refresh) {
		return requisicaoSemToken().body("{\"refreshToken\":\"" + refresh + "\"}").post("/api/v1/auth/refresh");
	}

	private Response logout(String texto) {
		return requisicaoSemToken().body("{\"refreshToken\":\"" + texto + "\"}").post("/api/v1/auth/logout");
	}

	private Response trocarSenha(String access, String atual, String nova) {
		return requisicaoCom(access).body("{\"senhaAtual\":\"" + atual + "\",\"novaSenha\":\"" + nova + "\"}")
				.post("/api/v1/me/senha");
	}

	private long refreshAtivos(String email) {
		return jdbc.queryForObject("SELECT COUNT(*) FROM refresh_token r JOIN usuario u ON u.id = r.usuario_id "
				+ "WHERE u.email = ? AND r.revogado_em IS NULL", Long.class, email);
	}

	private long refreshRevogadosPor(String email, String motivo) {
		return jdbc.queryForObject("SELECT COUNT(*) FROM refresh_token r JOIN usuario u ON u.id = r.usuario_id "
				+ "WHERE u.email = ? AND r.motivo_revogacao = ?", Long.class, email, motivo);
	}

	private int versao(String email) {
		return jdbc.queryForObject("SELECT versao_token FROM usuario WHERE email = ?", Integer.class, email);
	}

	private int statusDoMe(String access) {
		return requisicaoCom(access).get("/api/v1/me").statusCode();
	}

	// ----- signin grava um refresh por emissão -----

	@Test
	void deve_gravar_uma_linha_por_refresh_emitido_so_com_o_jti() {
		String email = novoAluno();

		entrar(email);
		entrar(email);

		assertThat(refreshAtivos(email)).as("um por signin; vários aparelhos ao mesmo tempo").isEqualTo(2);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM refresh_token r JOIN usuario u ON u.id = r.usuario_id "
				+ "WHERE u.email = ? AND LENGTH(r.jti) = 16", Long.class, email)).isEqualTo(2);
	}

	// ----- rotação -----

	@Test
	void deve_rotacionar_trocando_o_refresh_por_um_par_novo_e_revogando_o_usado() {
		String email = novoAluno();
		Tokens inicial = entrar(email);

		Response r = refresh(inicial.refresh());

		r.then().statusCode(200).header("Cache-Control", Matchers.containsString("no-store"))
				.body("tokenType", Matchers.equalTo("Bearer"));
		assertThat(r.jsonPath().getString("refreshToken")).isNotEqualTo(inicial.refresh());
		assertThat(refreshRevogadosPor(email, "ROTACAO")).isEqualTo(1);
		assertThat(refreshAtivos(email)).as("o usado foi revogado e o novo está ativo").isEqualTo(1);
		assertThat(statusDoMe(r.jsonPath().getString("accessToken"))).isEqualTo(200);
	}

	@Test
	void deve_dar_401_no_refresh_ja_usado() {
		String email = novoAluno();
		Tokens inicial = entrar(email);
		refresh(inicial.refresh()).then().statusCode(200);

		refresh(inicial.refresh()).then().statusCode(401).body("codigo", Matchers.equalTo("TOKEN_INVALIDO"));
	}

	// ----- reuso: revoga tudo e sobe a versão, GRAVANDO mesmo com o 401 -----

	@Test
	void deve_revogar_todos_os_refresh_e_subir_a_versao_com_o_banco_real_depois_do_401_do_reuso_e_derrubar_o_access_vivo() {
		String email = novoAluno();
		Tokens aparelho1 = entrar(email);
		Tokens aparelho2 = entrar(email);
		Response rotacionado = refresh(aparelho1.refresh());
		rotacionado.then().statusCode(200);
		String refreshNovo = rotacionado.jsonPath().getString("refreshToken");
		assertThat(statusDoMe(aparelho2.access())).as("antes do reuso o access vive").isEqualTo(200);
		assertThat(versao(email)).isZero();

		refresh(aparelho1.refresh()).then().statusCode(401).body("codigo", Matchers.equalTo("TOKEN_INVALIDO"));

		assertThat(refreshAtivos(email)).as("o 401 NÃO desfez a revogação em massa").isZero();
		assertThat(refreshRevogadosPor(email, "REUSO")).as("os dois que estavam ativos (o novo e o do outro aparelho)")
				.isEqualTo(2);
		assertThat(versao(email)).as("a versão subiu e gravou").isEqualTo(1);
		refresh(refreshNovo).then().statusCode(401);
		refresh(aparelho2.refresh()).then().statusCode(401);
		assertThat(statusDoMe(aparelho2.access())).as("o reuso derruba também o access token vivo").isEqualTo(401);
	}

	@Test
	void deve_dar_so_401_sem_revogacao_em_massa_quando_o_refresh_e_desconhecido() {
		String email = novoAluno();
		Tokens vivo = entrar(email);
		UUID id = usuariosDeTeste.findByEmail(email).orElseThrow().getPublicId();
		String desconhecido = TokensForjados.valido(id, "STUDENT", TipoToken.REFRESH);

		refresh(desconhecido).then().statusCode(401).body("codigo", Matchers.equalTo("TOKEN_INVALIDO"));

		assertThat(refreshAtivos(email)).as("sem revogação em massa").isEqualTo(1);
		assertThat(versao(email)).isZero();
		assertThat(statusDoMe(vivo.access())).isEqualTo(200);
	}

	@Test
	void deve_dar_so_401_sem_revogacao_em_massa_quando_o_refresh_foi_revogado_por_outro_motivo_o_logout() {
		String email = novoAluno();
		Tokens deslogado = entrar(email);
		Tokens outro = entrar(email);
		logout(deslogado.refresh()).then().statusCode(204);

		refresh(deslogado.refresh()).then().statusCode(401);

		assertThat(refreshAtivos(email)).as("o outro aparelho continua ativo").isEqualTo(1);
		assertThat(versao(email)).isZero();
		refresh(outro.refresh()).then().statusCode(200);
	}

	// ----- concorrência: dois refresh simultâneos com o mesmo token -----

	@Test
	void deve_deixar_exatamente_um_de_dois_refresh_simultaneos_ganhar_e_o_perdedor_dispara_a_revogacao_em_massa() throws Exception {
		String email = novoAluno();
		Tokens inicial = entrar(email);
		ExecutorService pool = Executors.newFixedThreadPool(2);
		CountDownLatch largada = new CountDownLatch(1);
		try {
			List<Future<Response>> futuros = new ArrayList<>();
			for (int i = 0; i < 2; i++) {
				futuros.add(pool.submit(() -> {
					largada.await();
					return refresh(inicial.refresh());
				}));
			}
			largada.countDown();
			List<Integer> status = new ArrayList<>();
			for (Future<Response> f : futuros) {
				status.add(f.get().statusCode());
			}

			assertThat(status).as("exatamente um ganha").containsExactlyInAnyOrder(200, 401);
			// RISCO CONHECIDO (D49, seção 11 do contrato): o perdedor é indistinguível de um REUSO e dispara a revogação em
			// massa, que derruba até o par novo do vencedor. Num PWA em rede ruim (a resposta se perde e o cliente repete, ou
			// duas abas renovam juntas) isso desloga o usuário de tudo. A rever na Sprint do front.
			assertThat(refreshAtivos(email)).as("o par novo do vencedor também foi revogado").isZero();
			assertThat(versao(email)).isEqualTo(1);
		}
		finally {
			pool.shutdownNow();
		}
	}

	// ----- logout -----

	@Test
	void deve_dar_401_no_refresh_depois_do_logout_com_o_mesmo_token_e_204_em_qualquer_texto() {
		String email = novoAluno();
		Tokens tokens = entrar(email);

		logout(tokens.refresh()).then().statusCode(204);
		refresh(tokens.refresh()).then().statusCode(401);
		logout(tokens.refresh()).then().statusCode(204);

		logout("um texto qualquer").then().statusCode(204);
		logout("").then().statusCode(204);
		logout(tokens.access()).then().statusCode(204);
		logout(TokensForjados.valido(UUID.randomUUID(), "STUDENT", TipoToken.REFRESH)).then().statusCode(204);
		assertThat(refreshRevogadosPor(email, "LOGOUT")).as("só o jti enviado, uma vez").isEqualTo(1);
	}

	@Test
	void deve_pedir_o_refresh_no_logout_e_nao_derrubar_o_access_vivo() {
		String email = novoAluno();
		Tokens tokens = entrar(email);

		requisicaoSemToken().body("{}").post("/api/v1/auth/logout").then().statusCode(400);
		requisicaoSemToken().body("{\"refreshToken\":123}").post("/api/v1/auth/logout").then().statusCode(400);
		logout(tokens.refresh()).then().statusCode(204);

		assertThat(statusDoMe(tokens.access())).as("o logout NÃO derruba o access vivo (até 15 min, documentado)").isEqualTo(200);
		assertThat(refreshAtivos(email)).isZero();
	}

	@Test
	void deve_revogar_so_o_refresh_do_logout_e_deixar_os_outros_aparelhos() {
		String email = novoAluno();
		Tokens a = entrar(email);
		Tokens b = entrar(email);

		logout(a.refresh()).then().statusCode(204);

		assertThat(refreshAtivos(email)).isEqualTo(1);
		refresh(b.refresh()).then().statusCode(200);
	}

	// ----- versão do token -----

	@Test
	void deve_dar_401_no_access_e_no_refresh_com_versao_diferente_ou_sem_o_claim() {
		String email = novoAluno();
		Tokens tokens = entrar(email);
		UUID id = usuariosDeTeste.findByEmail(email).orElseThrow().getPublicId();
		jdbc.update("UPDATE usuario SET versao_token = versao_token + 1 WHERE email = ?", email);

		assertThat(statusDoMe(tokens.access())).as("access de versão antiga").isEqualTo(401);
		refresh(tokens.refresh()).then().statusCode(401);
		assertThat(statusDoMe(TokensForjados.valido(id, "STUDENT", TipoToken.ACCESS))).as("versão 0 contra 1").isEqualTo(401);
		assertThat(statusDoMe(provedorDeTokens.emitir(TipoToken.ACCESS, id, "STUDENT", 1).valor())).as("versão certa").isEqualTo(200);
	}

	// ----- troca de senha -----

	@Test
	void deve_trocar_a_senha_invalidar_o_access_e_o_refresh_antigos_e_so_a_senha_nova_entrar() {
		String email = novoAluno();
		Tokens aparelho1 = entrar(email);
		Tokens aparelho2 = entrar(email);
		String nova = "outra-senha-456";

		Response r = trocarSenha(aparelho1.access(), SENHA, nova);

		r.then().statusCode(204).header("Cache-Control", Matchers.containsString("no-store"));
		assertThat(r.asString()).as("204 sem corpo e sem tokens").isEmpty();
		assertThat(statusDoMe(aparelho1.access())).as("access antigo").isEqualTo(401);
		assertThat(statusDoMe(aparelho2.access())).as("access de outro aparelho").isEqualTo(401);
		refresh(aparelho1.refresh()).then().statusCode(401);
		refresh(aparelho2.refresh()).then().statusCode(401);
		assertThat(refreshRevogadosPor(email, "TROCA_SENHA")).isEqualTo(2);
		assertThat(versao(email)).isEqualTo(1);
		signin(email, SENHA).then().statusCode(401);
		signin(email, nova).then().statusCode(200).body("tokenType", Matchers.equalTo("Bearer"));
	}

	@Test
	void deve_dar_400_senha_atual_incorreta_sem_trocar_nada_e_400_quando_a_nova_e_igual_a_atual() {
		String email = novoAluno();
		Tokens tokens = entrar(email);

		trocarSenha(tokens.access(), "errada-errada", "outra-senha-456").then().statusCode(400)
				.body("codigo", Matchers.equalTo("SENHA_ATUAL_INCORRETA"));
		trocarSenha(tokens.access(), "x".repeat(80), "outra-senha-456").then().statusCode(400)
				.body("codigo", Matchers.equalTo("SENHA_ATUAL_INCORRETA"));
		trocarSenha(tokens.access(), SENHA, SENHA).then().statusCode(400).body("codigo", Matchers.equalTo("VALIDACAO_FALHOU"))
				.body("campos[0].campo", Matchers.equalTo("novaSenha"));

		assertThat(versao(email)).isZero();
		assertThat(statusDoMe(tokens.access())).as("nada mudou").isEqualTo(200);
		signin(email, SENHA).then().statusCode(200);
	}

	@Test
	void deve_validar_o_formato_e_a_politica_da_nova_senha_por_papel() {
		String aluno = novoAluno();
		String accessAluno = entrar(aluno).access();
		String admin = novoUsuarioComSenha(Papel.ADMIN, "senha-do-admin-123");
		Response entradaAdmin = signin(admin, "senha-do-admin-123");
		String accessAdmin = entradaAdmin.jsonPath().getString("accessToken");

		requisicaoCom(accessAluno).body("{}").post("/api/v1/me/senha").then().statusCode(400)
				.body("campos[0].campo", Matchers.equalTo("senhaAtual"));
		requisicaoCom(accessAluno).body("{\"senhaAtual\":\"" + SENHA + "\"}").post("/api/v1/me/senha").then().statusCode(400)
				.body("campos[0].campo", Matchers.equalTo("novaSenha"));
		requisicaoCom(accessAluno).body("não é json").post("/api/v1/me/senha").then().statusCode(400);
		trocarSenha(accessAluno, SENHA, "1234567").then().statusCode(400).body("campos[0].campo", Matchers.equalTo("novaSenha"));
		trocarSenha(accessAluno, SENHA, "x".repeat(73)).then().statusCode(400);
		// ADMIN segue a D125: 12 a 72 bytes, sem espaço nas pontas e sem marcador de exemplo
		trocarSenha(accessAdmin, "senha-do-admin-123", "onze-bytes1").then().statusCode(400)
				.body("campos[0].campo", Matchers.equalTo("novaSenha"));
		trocarSenha(accessAdmin, "senha-do-admin-123", " espaco-na-ponta").then().statusCode(400);
		trocarSenha(accessAdmin, "senha-do-admin-123", "troque-a-senha-do-admin").then().statusCode(400);
		trocarSenha(accessAdmin, "senha-do-admin-123", "<defina-aqui-ja>").then().statusCode(400);

		assertThat(versao(aluno)).isZero();
		assertThat(versao(admin)).isZero();
		// as mesmas senhas curtas que o ADMIN recusa, o aluno aceita (8 bytes)
		trocarSenha(accessAluno, SENHA, "12345678").then().statusCode(204);
		trocarSenha(accessAdmin, "senha-do-admin-123", "senha-nova-do-admin-1").then().statusCode(204);
	}

	@Test
	void deve_negar_a_troca_de_senha_ao_anonimo_e_ao_guardian() {
		requisicaoSemToken().body("{}").post("/api/v1/me/senha").then().statusCode(401);
		requisicaoComoGuardian().body("{\"senhaAtual\":\"a\",\"novaSenha\":\"b\"}").post("/api/v1/me/senha").then().statusCode(403);
	}

}
