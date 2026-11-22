package br.simulado.etec.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import br.simulado.etec.integracao.AbstractIntegrationTest;
import br.simulado.etec.shared.security.JwtTokenProvider.TipoToken;
import br.simulado.etec.shared.security.TokensForjados;
import br.simulado.etec.usuarios.model.Papel;
import io.restassured.http.ContentType;
import io.restassured.response.Response;

/**
 * Autenticação de ponta a ponta com MySQL real e os filtros de segurança LIGADOS: o login entra, e token vencido,
 * adulterado, de outro segredo, de outro emissor, com alg=none, com papel editado sem reassinar e do tipo errado dão
 * 401; reassinado com o segredo certo e papel ADMIN para um STUDENT dá 200 com o papel do BANCO (D87); excluir o
 * usuário no banco derruba o token na requisição seguinte.
 */
@ExtendWith(OutputCaptureExtension.class)
class AutenticacaoIntegracaoTest extends AbstractIntegrationTest {

	private static final String REGISTER = "/api/v1/auth/register";

	private static final String SIGNIN = "/api/v1/auth/signin";

	private static final String REFRESH = "/api/v1/auth/refresh";

	private static final String ME = "/api/v1/me";

	private static final String MATERIAS = "/api/v1/materias";

	private static final String SENHA = "senha-de-teste-123";

	private static final String DATA_HORA = "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}Z";

	// ----- apoio -----

	private static String emailNovo() {
		return "aluno." + sufixo() + "@exemplo.invalid";
	}

	private Response cadastrar(String email, String senha) {
		return requisicaoSemToken().body("{\"nome\":\"Ana Teste\",\"email\":\"" + email + "\",\"senha\":\"" + senha
				+ "\",\"dataNascimento\":\"2008-05-10\",\"aceiteTermosVersao\":\"v1-teste\"}").post(REGISTER);
	}

	private Response entrar(String email, String senha) {
		return requisicaoSemToken().body("{\"email\":\"" + email + "\",\"senha\":\"" + senha + "\"}").post(SIGNIN);
	}

	/** Cadastra, entra e devolve [accessToken, refreshToken, id]. */
	private String[] novoUsuarioLogado(String email) {
		String id = cadastrar(email, SENHA).then().statusCode(201).extract().path("id");
		Response tokens = entrar(email, SENHA);
		tokens.then().statusCode(200);
		return new String[] { tokens.path("accessToken"), tokens.path("refreshToken"), id };
	}

	private void excluirNoBanco(String email) {
		assertThat(jdbc.update("UPDATE usuario SET excluido_em = CURRENT_TIMESTAMP(3) WHERE email = ?", email))
				.isEqualTo(1);
	}

	/** O 401 de rota protegida: ErrorResponse completo, JSON puro, mesmo código para qualquer token ruim. */
	private static void deveSer401NaoAutenticado(Response resposta) {
		resposta.then().statusCode(401).header("Content-Type", "application/json")
				.body("status", equalTo(401)).body("erro", equalTo("Unauthorized"))
				.body("codigo", equalTo("NAO_AUTENTICADO"))
				.body("mensagem", equalTo("É necessário estar autenticado para acessar este recurso."))
				.body("dataHora", matchesPattern(DATA_HORA)).body("campos", org.hamcrest.Matchers.nullValue())
				.body("detalhes", org.hamcrest.Matchers.nullValue());
	}

	// ----- o login entra -----

	@Test
	void deve_entrar_quando_cadastra_faz_signin_e_le_o_proprio_usuario() {
		String email = emailNovo();
		String id = cadastrar("  " + email.toUpperCase() + " ", SENHA).then().statusCode(201).extract().path("id");

		Response tokens = entrar(email, SENHA);
		tokens.then().statusCode(200).header("Cache-Control", "no-store").body("tokenType", equalTo("Bearer"))
				.body("expiresIn", equalTo(900));

		Response me = requisicaoCom(tokens.path("accessToken")).get(ME);

		me.then().statusCode(200).header("Content-Type", "application/json").body("id", equalTo(id))
				.body("email", equalTo(email)).body("nome", equalTo("Ana Teste")).body("papel", equalTo("STUDENT"))
				.body("dataNascimento", equalTo("2008-05-10")).body("menor", equalTo(false))
				.body("criadoEm", matchesPattern(DATA_HORA));
		String corpo = me.asString();
		assertThat(corpo).doesNotContain("senha").doesNotContain("hash").doesNotContain("aceiteTermos")
				.doesNotContain("bcrypt").doesNotContain(SENHA);
		assertThat(corpo).containsSubsequence("\"id\"", "\"nome\"", "\"email\"", "\"dataNascimento\"", "\"papel\"",
				"\"menor\"", "\"criadoEm\"");
	}

	@Test
	void deve_gravar_o_hash_bcrypt_com_prefixo_e_nunca_a_senha() {
		String email = emailNovo();
		cadastrar(email, SENHA).then().statusCode(201);

		String hash = jdbc.queryForObject("SELECT senha_hash FROM usuario WHERE email = ?", String.class, email);

		assertThat(hash).startsWith("{bcrypt}$2").doesNotContain(SENHA).hasSize(68);
	}

	@Test
	void deve_devolver_no_store_exato_no_signin_e_no_refresh_e_manter_os_cabecalhos_extras_do_spring_security() {
		String[] sessao = novoUsuarioLogado(emailNovo());

		Response signin = entrar(emailNovo(), SENHA); // 401: não é o foco, só a resposta de sucesso abaixo
		assertThat(signin.statusCode()).isEqualTo(401);
		Response ok = entrar(jdbc.queryForObject("SELECT email FROM usuario WHERE public_id = UNHEX(REPLACE(?, '-', ''))",
				String.class, sessao[2]), SENHA);
		assertThat(ok.header("Cache-Control")).isEqualTo("no-store");
		Response refresh = requisicaoSemToken().body("{\"refreshToken\":\"" + sessao[1] + "\"}").post(REFRESH);
		assertThat(refresh.statusCode()).isEqualTo(200);
		assertThat(refresh.header("Cache-Control")).isEqualTo("no-store");
		// Os cabeçalhos extras do Spring Security ficam ligados nas demais respostas (aceitos, não desligados).
		Response me = requisicaoCom(sessao[0]).get(ME);
		assertThat(me.header("X-Content-Type-Options")).isEqualTo("nosniff");
		assertThat(me.header("Cache-Control")).contains("no-cache", "no-store");
	}

	// ----- token ruim = 401 -----

	@Test
	void deve_dar_401_quando_o_token_esta_vencido() {
		String[] sessao = novoUsuarioLogado(emailNovo());
		UUID id = UUID.fromString(sessao[2]);

		deveSer401NaoAutenticado(requisicaoCom(TokensForjados.vencido(id, "STUDENT")).get(ME));
		requisicaoCom(TokensForjados.valido(id, "STUDENT", TipoToken.ACCESS)).get(ME).then().statusCode(200);
	}

	@Test
	void deve_dar_401_quando_o_token_foi_adulterado_na_assinatura() {
		String[] sessao = novoUsuarioLogado(emailNovo());

		deveSer401NaoAutenticado(requisicaoCom(TokensForjados.comAssinaturaAdulterada(sessao[0])).get(ME));
	}

	@Test
	void deve_dar_401_quando_o_token_e_de_outro_segredo_ou_outro_emissor() {
		UUID id = UUID.fromString(novoUsuarioLogado(emailNovo())[2]);

		deveSer401NaoAutenticado(requisicaoCom(TokensForjados.deOutroSegredo(id, "STUDENT")).get(ME));
		deveSer401NaoAutenticado(requisicaoCom(TokensForjados.deOutroEmissor(id, "STUDENT")).get(ME));
	}

	@Test
	void deve_dar_401_quando_o_token_usa_alg_none_ou_outro_algoritmo() {
		UUID id = UUID.fromString(novoUsuarioLogado(emailNovo())[2]);

		deveSer401NaoAutenticado(requisicaoCom(TokensForjados.semAssinatura(id, "ADMIN")).get(ME));
		deveSer401NaoAutenticado(requisicaoCom(TokensForjados.comOutroAlgoritmo(id, "STUDENT")).get(ME));
	}

	@Test
	void deve_dar_401_quando_o_papel_foi_editado_sem_reassinar() {
		String[] sessao = novoUsuarioLogado(emailNovo());
		String editado = TokensForjados.comPapelEditadoSemReassinar(sessao[0], "STUDENT", "ADMIN");

		deveSer401NaoAutenticado(requisicaoCom(editado).get(ME));
		deveSer401NaoAutenticado(requisicaoCom(editado).get(MATERIAS));
	}

	@Test
	void deve_dar_401_quando_o_tipo_e_o_errado_nos_dois_sentidos() {
		String[] sessao = novoUsuarioLogado(emailNovo());

		deveSer401NaoAutenticado(requisicaoCom(sessao[1]).get(ME)); // refresh como Bearer
		requisicaoSemToken().body("{\"refreshToken\":\"" + sessao[0] + "\"}").post(REFRESH).then().statusCode(401)
				.body("codigo", equalTo("TOKEN_INVALIDO")); // access no refresh
	}

	@Test
	void deve_dar_200_com_o_papel_do_banco_quando_o_token_e_reassinado_com_o_segredo_certo_e_papel_admin() {
		String[] sessao = novoUsuarioLogado(emailNovo());
		UUID id = UUID.fromString(sessao[2]);
		String tokenDeAdminParaUmAluno = TokensForjados.valido(id, "ADMIN", TipoToken.ACCESS);

		requisicaoCom(tokenDeAdminParaUmAluno).get(ME).then().statusCode(200).body("papel", equalTo("STUDENT"));
	}

	@Test
	void deve_dar_401_quando_o_cabecalho_nao_e_um_bearer_valido() {
		String[] sessao = novoUsuarioLogado(emailNovo());

		deveSer401NaoAutenticado(requisicaoSemToken().header("Authorization", "Basic dXNlcjpwYXNz").get(ME));
		deveSer401NaoAutenticado(requisicaoSemToken().header("Authorization", sessao[0]).get(ME)); // sem o esquema
		deveSer401NaoAutenticado(requisicaoSemToken().header("Authorization", "Bearer").get(ME));
		deveSer401NaoAutenticado(requisicaoSemToken().header("Authorization", "Bearer lixo").get(ME));
		deveSer401NaoAutenticado(requisicaoSemToken().get(ME));
		// o token na URL nunca vale
		deveSer401NaoAutenticado(requisicaoSemToken().queryParam("access_token", sessao[0]).get(ME));
	}

	// ----- excluir o usuário derruba o token -----

	@Test
	void deve_derrubar_o_token_na_requisicao_seguinte_quando_o_usuario_e_excluido_no_banco() {
		String email = emailNovo();
		String[] sessao = novoUsuarioLogado(email);
		requisicaoCom(sessao[0]).get(ME).then().statusCode(200);

		excluirNoBanco(email);

		deveSer401NaoAutenticado(requisicaoCom(sessao[0]).get(ME));
		deveSer401NaoAutenticado(requisicaoCom(sessao[0]).get(MATERIAS));
		requisicaoSemToken().body("{\"refreshToken\":\"" + sessao[1] + "\"}").post(REFRESH).then().statusCode(401)
				.body("codigo", equalTo("TOKEN_INVALIDO"));
		entrar(email, SENHA).then().statusCode(401).body("codigo", equalTo("CREDENCIAIS_INVALIDAS"));
	}

	// ----- rotas públicas ignoram token ruim -----

	@Test
	void deve_atender_as_rotas_publicas_mesmo_com_um_token_ruim_no_cabecalho() {
		String email = emailNovo();
		cadastrar(email, SENHA).then().statusCode(201);

		requisicaoCom("token-velho-ou-lixo").body("{\"email\":\"" + email + "\",\"senha\":\"" + SENHA + "\"}")
				.post(SIGNIN).then().statusCode(200);
		requisicaoCom("token-velho-ou-lixo").get("/api/v1/termos/vigente").then().statusCode(200);
	}

	// ----- signin: mesma resposta para qualquer falha -----

	@Test
	void deve_dar_o_mesmo_401_em_qualquer_falha_de_credencial_com_mysql_real() {
		String email = emailNovo();
		cadastrar(email, SENHA).then().statusCode(201);
		String excluido = emailNovo();
		cadastrar(excluido, SENHA).then().statusCode(201);
		excluirNoBanco(excluido);

		String[] corpos = {
				entrar(email, "senha-errada-123").asString(),
				entrar(emailNovo(), SENHA).asString(),
				entrar("isto-nao-e-email", SENHA).asString(),
				entrar(email, "").asString(),
				entrar(email, "a".repeat(73)).asString(),
				entrar(excluido, SENHA).asString() };

		for (String corpo : corpos) {
			assertThat(corpo).contains("\"status\":401").contains("\"codigo\":\"CREDENCIAIS_INVALIDAS\"")
					.contains("\"mensagem\":\"E-mail ou senha inválidos.\"").contains("\"erro\":\"Unauthorized\"")
					.doesNotContain("campos");
		}
	}

	@Test
	void deve_barrar_a_senha_de_73_bytes_cujo_prefixo_de_72_e_a_senha_real() {
		String email = emailNovo();
		cadastrar(email, "b".repeat(72)).then().statusCode(201);

		entrar(email, "b".repeat(72)).then().statusCode(200);
		entrar(email, "b".repeat(72) + "SUFIXO").then().statusCode(401).body("codigo", equalTo("CREDENCIAIS_INVALIDAS"));
	}

	@Test
	void deve_aceitar_senha_de_72_bytes_com_acentos_e_recusar_74_no_cadastro() {
		String com72Bytes = "é".repeat(36);
		String email = emailNovo();

		cadastrar(email, com72Bytes).then().statusCode(201);
		entrar(email, com72Bytes).then().statusCode(200);
		cadastrar(emailNovo(), "é".repeat(37)).then().statusCode(400).body("campos[0].campo", equalTo("senha"));
	}

	// ----- cadastro com MySQL real -----

	@Test
	void deve_dar_409_email_duplicado_inclusive_com_outra_caixa_e_nao_aceitar_email_com_acento() {
		String email = emailNovo();
		cadastrar(email, SENHA).then().statusCode(201);

		cadastrar(email, SENHA).then().statusCode(409).body("codigo", equalTo("EMAIL_DUPLICADO"));
		cadastrar(email.toUpperCase(), SENHA).then().statusCode(409).body("codigo", equalTo("EMAIL_DUPLICADO"));
		// D119: acento = 400 (sem ele, a collation do MySQL o confundiria com o e-mail sem acento)
		cadastrar("josé." + sufixo() + "@exemplo.invalid", SENHA).then().statusCode(400)
				.body("codigo", equalTo("VALIDACAO_FALHOU")).body("campos[0].campo", equalTo("email"));
	}

	@Test
	void deve_gravar_apenas_um_usuario_quando_20_cadastros_do_mesmo_email_chegam_ao_mesmo_tempo() throws Exception {
		String email = emailNovo();
		var pool = java.util.concurrent.Executors.newFixedThreadPool(20);
		try {
			var largada = new java.util.concurrent.CountDownLatch(1);
			java.util.List<java.util.concurrent.Future<Integer>> futuros = new java.util.ArrayList<>();
			for (int i = 0; i < 20; i++) {
				futuros.add(pool.submit(() -> {
					largada.await();
					return cadastrar(email, SENHA).statusCode();
				}));
			}
			largada.countDown();
			int criados = 0;
			int conflitos = 0;
			for (var futuro : futuros) {
				int status = futuro.get(60, java.util.concurrent.TimeUnit.SECONDS);
				if (status == 201) {
					criados++;
				}
				else if (status == 409) {
					conflitos++;
				}
			}

			assertThat(criados).isEqualTo(1);
			assertThat(conflitos).isEqualTo(19);
			assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM usuario WHERE email = ?", Integer.class, email))
					.isEqualTo(1);
		}
		finally {
			pool.shutdownNow();
		}
	}

	// ----- sem token nas rotas protegidas -----

	@Test
	void deve_dar_401_sem_token_em_toda_rota_protegida_e_200_com_token() {
		deveSer401NaoAutenticado(requisicaoSemToken().get(MATERIAS));
		deveSer401NaoAutenticado(requisicaoSemToken().body("{\"nome\":\"Nova\"}").post(MATERIAS));
		deveSer401NaoAutenticado(requisicaoSemToken().get("/api/v1/subtopicos"));
		deveSer401NaoAutenticado(requisicaoSemToken().get(ME));
		requisicao().get(MATERIAS).then().statusCode(200);
	}

	@Test
	void deve_dar_401_em_json_mesmo_com_accept_de_xml_ou_yaml() {
		for (String tipo : new String[] { "application/xml", "application/yaml", "text/html", "*/*" }) {
			deveSer401NaoAutenticado(requisicaoSemToken().accept(tipo).get(MATERIAS));
			deveSer401NaoAutenticado(requisicaoCom("lixo").accept(tipo).get(MATERIAS));
		}
	}

	@Test
	void deve_dar_401_no_metodo_nao_listado_de_uma_rota_publica_porque_o_permit_all_e_por_metodo() {
		deveSer401NaoAutenticado(requisicaoSemToken().get(SIGNIN));
		deveSer401NaoAutenticado(requisicaoSemToken().get(REGISTER));
		deveSer401NaoAutenticado(requisicaoSemToken().put(REFRESH));
		deveSer401NaoAutenticado(requisicaoSemToken().post("/api/v1/termos/vigente"));
	}

	// ----- D116: rota inexistente -----

	@Test
	void deve_dar_401_ao_anonimo_e_404_a_quem_esta_autenticado_em_rota_inexistente() {
		deveSer401NaoAutenticado(requisicaoSemToken().get("/api/v1/nao-existe"));
		deveSer401NaoAutenticado(requisicaoSemToken().body("{}").post("/api/v1/nao-existe"));
		deveSer401NaoAutenticado(requisicaoSemToken().get("/nao-existe"));
		for (String rota : new String[] { "/api/v1/nao-existe", "/nao-existe" }) {
			requisicao().get(rota).then().statusCode(404).body("codigo", equalTo("RECURSO_NAO_ENCONTRADO"))
					.header("Content-Type", "application/json");
		}
	}

	// ----- actuator e /error (D113, D120) -----

	@Test
	void deve_dar_401_ao_anonimo_e_403_a_quem_esta_autenticado_no_actuator_e_no_error() {
		for (String rota : new String[] { "/actuator", "/actuator/env", "/actuator/info", "/actuator/health/xyz",
				"/actuator/health/", "/error", "/error/x" }) {
			deveSer401NaoAutenticado(requisicaoSemToken().get(rota));
			requisicao().get(rota).then().statusCode(403).header("Content-Type", "application/json")
					.body("status", equalTo(403)).body("erro", equalTo("Forbidden")).body("codigo", equalTo("ACESSO_NEGADO"))
					.body("mensagem", equalTo("Você não tem permissão para acessar este recurso."))
					.body("dataHora", matchesPattern(DATA_HORA));
		}
	}

	@Test
	void deve_dar_403_ao_admin_tambem_no_actuator() {
		var admin = criarUsuarioNoBanco(Papel.ADMIN);

		requisicaoCom(tokenDeAcesso(admin)).get("/actuator/env").then().statusCode(403).body("codigo",
				equalTo("ACESSO_NEGADO"));
	}

	@Test
	void deve_deixar_os_tres_caminhos_de_saude_passarem_pela_seguranca_sem_credencial() {
		// O actuator ainda não está no projeto (Módulo 18): sem o endpoint, a resposta é 404 do MVC, e não 401 nem
		// 403. O que se prova aqui é que a SEGURANÇA não bloqueia esses três caminhos para o anônimo.
		for (String rota : new String[] { "/actuator/health", "/actuator/health/liveness", "/actuator/health/readiness" }) {
			int status = requisicaoSemToken().get(rota).statusCode();
			assertThat(status).as(rota).isNotIn(401, 403);
		}
	}

	// ----- nada sensível no log -----

	@Test
	void deve_nunca_por_senha_token_ou_corpo_no_log(CapturedOutput saida) {
		String senhaSecreta = "SENHA-SECRETA-LOG-" + sufixo();
		String email = emailNovo();
		cadastrar(email, senhaSecreta).then().statusCode(201);
		String[] sessao = new String[2];
		Response tokens = entrar(email, senhaSecreta);
		sessao[0] = tokens.path("accessToken");
		sessao[1] = tokens.path("refreshToken");

		entrar(email, senhaSecreta + "-errada");
		requisicaoSemToken().body("{\"email\":\"" + email + "\",\"senha\":\"" + senhaSecreta + "\",").post(SIGNIN);
		requisicaoSemToken().body("{\"refreshToken\":\"" + sessao[0] + "\"}").post(REFRESH);
		requisicaoCom(TokensForjados.comAssinaturaAdulterada(sessao[0])).get(ME);
		requisicaoCom(sessao[0]).get(ME);
		requisicaoSemToken().body("{\"senha\":\"" + senhaSecreta + "\",\"dataNascimento\":123}").post(REGISTER);

		String log = saida.getAll();
		assertThat(log).doesNotContain(senhaSecreta).doesNotContain(sessao[0]).doesNotContain(sessao[1])
				.doesNotContain(sessao[0].split("\\.")[2]).doesNotContain("Using generated security password");
	}

	// ----- formato -----

	@Test
	void deve_dar_415_e_400_no_formato_padrao_nas_rotas_publicas() {
		requisicaoSemToken().contentType("text/plain").body("a=b").post(SIGNIN).then().statusCode(415)
				.body("codigo", equalTo("TIPO_NAO_SUPORTADO"));
		requisicaoSemToken().contentType(ContentType.JSON).body("{\"email\":123,\"senha\":\"x\"}").post(SIGNIN).then()
				.statusCode(400).body("codigo", equalTo("CORPO_ILEGIVEL")).body("mensagem", not(containsString("123")));
	}

}
