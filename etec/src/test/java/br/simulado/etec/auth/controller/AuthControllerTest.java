package br.simulado.etec.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;

/**
 * Contrato das rotas públicas de autenticação, em MockMvc com H2. Atenção: ESTES TESTES NÃO TESTAM SEGURANÇA. Quando a
 * cadeia de filtros entra (passo 7), eles passam a rodar com {@code addFilters = false}; o 401 e o 403 da cadeia são
 * provados nos testes de integração com os filtros ligados. A unicidade por collation do MySQL é do teste de
 * integração com MySQL real.
 */
@SpringBootTest
// SEM filtros de segurança (addFilters = false): este teste cobre o CONTRATO da rota e NÃO testa segurança.
// O 401 e o 403 da cadeia são provados nos testes de integração, com os filtros ligados.
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

	private static final String REGISTER = "/api/v1/auth/register";

	private static final String SIGNIN = "/api/v1/auth/signin";

	private static final String REFRESH = "/api/v1/auth/refresh";

	private static final String DATA_HORA = "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}Z";

	private static final String SENHA = "senha-de-teste-123";

	@Autowired
	private MockMvc mockMvc;

	// ----- apoio -----

	private static String emailNovo() {
		return "aluno." + UUID.randomUUID().toString().substring(0, 8) + "@exemplo.invalid";
	}

	private static String cadastro(String email, String senha, String nascimento, String termos) {
		return "{\"nome\":\"Ana Teste\",\"email\":\"" + email + "\",\"senha\":\"" + senha + "\",\"dataNascimento\":\""
				+ nascimento + "\",\"aceiteTermosVersao\":\"" + termos + "\"}";
	}

	private ResultActions postar(String rota, String json) throws Exception {
		return mockMvc.perform(post(rota).contentType(MediaType.APPLICATION_JSON).content(json));
	}

	private ResultActions cadastrar(String email) throws Exception {
		return postar(REGISTER, cadastro(email, SENHA, "2008-05-10", "v1-teste"));
	}

	private ResultActions entrar(String email, String senha) throws Exception {
		return postar(SIGNIN, "{\"email\":\"" + email + "\",\"senha\":\"" + senha + "\"}");
	}

	private static ResultActions erro(ResultActions resposta, int status, String erro, String codigo) throws Exception {
		return resposta.andExpect(status().is(status)).andExpect(content().contentType(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.status").value(status)).andExpect(jsonPath("$.erro").value(erro))
				.andExpect(jsonPath("$.codigo").value(codigo)).andExpect(jsonPath("$.dataHora", matchesPattern(DATA_HORA)));
	}

	private static ResultActions erroDeCampo(ResultActions resposta, String campo) throws Exception {
		return erro(resposta, 400, "Bad Request", "VALIDACAO_FALHOU").andExpect(jsonPath("$.campos.length()").value(1))
				.andExpect(jsonPath("$.campos[0].campo").value(campo));
	}

	private String tokensDe(String email) throws Exception {
		cadastrar(email).andExpect(status().isCreated());
		return entrar(email, SENHA).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
	}

	// ----- POST /auth/register -----

	@Test
	void deve_devolver_201_com_o_usuario_sem_location_sem_tokens_e_sem_senha_quando_o_cadastro_e_valido()
			throws Exception {
		String email = emailNovo();

		ResultActions resposta = cadastrar(email).andExpect(status().isCreated())
				.andExpect(content().contentType(MediaType.APPLICATION_JSON))
				.andExpect(header().doesNotExist("Location")).andExpect(jsonPath("$.email").value(email))
				.andExpect(jsonPath("$.nome").value("Ana Teste")).andExpect(jsonPath("$.papel").value("STUDENT"))
				.andExpect(jsonPath("$.dataNascimento").value("2008-05-10")).andExpect(jsonPath("$.menor").isBoolean())
				.andExpect(jsonPath("$.criadoEm", matchesPattern(DATA_HORA)))
				.andExpect(jsonPath("$.id", matchesPattern("[0-9a-f-]{36}")));

		String corpo = resposta.andReturn().getResponse().getContentAsString();
		assertThat(corpo).doesNotContain("accessToken").doesNotContain("refreshToken").doesNotContain("senha")
				.doesNotContain(SENHA).doesNotContain("hash").doesNotContain("aceiteTermos").doesNotContain("bcrypt");
		assertThat(corpo).startsWith("{\"id\":\"").containsSubsequence("\"id\"", "\"nome\"", "\"email\"",
				"\"dataNascimento\"", "\"papel\"", "\"menor\"", "\"criadoEm\"");
		// JSON compacto (D74): sem espaço depois de ":" ou ",", sem quebra de linha.
		assertThat(corpo).doesNotContain("\": ").doesNotContain(", \"").doesNotContain("\n");
	}

	@Test
	void deve_normalizar_o_email_para_minusculas_e_aparar_o_nome_e_o_email() throws Exception {
		String email = emailNovo();
		String json = "{\"nome\":\"  Ana Teste  \",\"email\":\"  " + email.toUpperCase()
				+ "  \",\"senha\":\"" + SENHA + "\",\"dataNascimento\":\"2008-05-10\",\"aceiteTermosVersao\":\"v1-teste\"}";

		postar(REGISTER, json).andExpect(status().isCreated()).andExpect(jsonPath("$.email").value(email))
				.andExpect(jsonPath("$.nome").value("Ana Teste"));
	}

	@Test
	void deve_ignorar_papel_e_demais_campos_de_saida_enviados_no_cadastro() throws Exception {
		String json = "{\"nome\":\"Ana Teste\",\"email\":\"" + emailNovo() + "\",\"senha\":\"" + SENHA
				+ "\",\"dataNascimento\":\"2008-05-10\",\"aceiteTermosVersao\":\"v1-teste\",\"papel\":\"ADMIN\","
				+ "\"id\":\"" + UUID.randomUUID() + "\",\"menor\":false,\"criadoEm\":\"2000-01-01T00:00:00.000Z\"}";

		postar(REGISTER, json).andExpect(status().isCreated()).andExpect(jsonPath("$.papel").value("STUDENT"));
	}

	@Test
	void deve_marcar_menor_de_18_anos_na_leitura() throws Exception {
		postar(REGISTER, cadastro(emailNovo(), SENHA, "2000-01-01", "v1-teste")).andExpect(status().isCreated())
				.andExpect(jsonPath("$.menor").value(false));
		postar(REGISTER, cadastro(emailNovo(), SENHA, "2020-01-01", "v1-teste")).andExpect(status().isCreated())
				.andExpect(jsonPath("$.menor").value(true));
	}

	@Test
	void deve_devolver_409_email_duplicado_inclusive_com_outra_caixa() throws Exception {
		String email = emailNovo();
		cadastrar(email).andExpect(status().isCreated());

		erro(cadastrar(email), 409, "Conflict", "EMAIL_DUPLICADO");
		erro(cadastrar(email.toUpperCase()), 409, "Conflict", "EMAIL_DUPLICADO");
		erro(postar(REGISTER, cadastro("  " + email + " ", SENHA, "2008-05-10", "v1-teste")), 409, "Conflict",
				"EMAIL_DUPLICADO");
	}

	@Test
	void deve_devolver_400_com_campos_quando_os_termos_nao_sao_os_vigentes() throws Exception {
		erroDeCampo(postar(REGISTER, cadastro(emailNovo(), SENHA, "2008-05-10", "v0")), "aceiteTermosVersao");
		erroDeCampo(postar(REGISTER, cadastro(emailNovo(), SENHA, "2008-05-10", "")), "aceiteTermosVersao");
	}

	@Test
	void deve_validar_o_termo_antes_de_conferir_o_email_duplicado() throws Exception {
		String email = emailNovo();
		cadastrar(email).andExpect(status().isCreated());

		erroDeCampo(postar(REGISTER, cadastro(email, SENHA, "2008-05-10", "v0")), "aceiteTermosVersao");
	}

	@Test
	void deve_devolver_400_na_senha_fora_de_8_a_72_bytes() throws Exception {
		erroDeCampo(postar(REGISTER, cadastro(emailNovo(), "1234567", "2008-05-10", "v1-teste")), "senha");
		erroDeCampo(postar(REGISTER, cadastro(emailNovo(), "a".repeat(73), "2008-05-10", "v1-teste")), "senha");
		erroDeCampo(postar(REGISTER, cadastro(emailNovo(), "é".repeat(37), "2008-05-10", "v1-teste")), "senha");
		postar(REGISTER, cadastro(emailNovo(), "a".repeat(72), "2008-05-10", "v1-teste")).andExpect(status().isCreated());
		postar(REGISTER, cadastro(emailNovo(), "é".repeat(36), "2008-05-10", "v1-teste")).andExpect(status().isCreated());
		postar(REGISTER, cadastro(emailNovo(), "12345678", "2008-05-10", "v1-teste")).andExpect(status().isCreated());
	}

	@Test
	void deve_devolver_400_com_campos_email_quando_o_email_nao_e_ascii_ou_e_invalido() throws Exception {
		erroDeCampo(postar(REGISTER, cadastro("josé@exemplo.invalid", SENHA, "2008-05-10", "v1-teste")), "email");
		erroDeCampo(postar(REGISTER, cadastro("sem-arroba", SENHA, "2008-05-10", "v1-teste")), "email");
		erroDeCampo(postar(REGISTER, cadastro("a@b", SENHA, "2008-05-10", "v1-teste")), "email");
		erroDeCampo(postar(REGISTER, cadastro("a".repeat(250) + "@exemplo.invalid", SENHA, "2008-05-10", "v1-teste")),
				"email");
	}

	@Test
	void deve_devolver_400_no_nome_curto_longo_ou_so_de_espacos() throws Exception {
		for (String nome : new String[] { "A", "   ", "", "x".repeat(101) }) {
			String json = "{\"nome\":\"" + nome + "\",\"email\":\"" + emailNovo() + "\",\"senha\":\"" + SENHA
					+ "\",\"dataNascimento\":\"2008-05-10\",\"aceiteTermosVersao\":\"v1-teste\"}";
			erroDeCampo(postar(REGISTER, json), "nome");
		}
	}

	@Test
	void deve_devolver_400_com_a_mensagem_exata_da_d60_quando_a_data_esta_fora_do_formato_ou_nao_existe() throws Exception {
		for (String data : new String[] { "2000-02-30", "2000-2-3", "08/05/2000", "2008-05-10T00:00:00Z", " 2008-05-10",
				"", "texto" }) {
			erroDeCampo(postar(REGISTER, cadastro(emailNovo(), SENHA, data, "v1-teste")), "dataNascimento")
					.andExpect(jsonPath("$.campos[0].mensagem").value("Data inválida. Use o formato aaaa-mm-dd."));
		}
	}

	@Test
	void deve_devolver_400_na_data_futura_de_hoje_ou_anterior_a_1900() throws Exception {
		erroDeCampo(postar(REGISTER, cadastro(emailNovo(), SENHA, "2999-01-01", "v1-teste")), "dataNascimento");
		erroDeCampo(postar(REGISTER, cadastro(emailNovo(), SENHA, "1899-12-31", "v1-teste")), "dataNascimento");
		postar(REGISTER, cadastro(emailNovo(), SENHA, "1900-01-01", "v1-teste")).andExpect(status().isCreated());
	}

	@Test
	void deve_devolver_corpo_ilegivel_quando_a_data_ou_um_texto_nao_e_texto() throws Exception {
		String base = "\"email\":\"" + emailNovo() + "\",\"senha\":\"" + SENHA + "\",\"aceiteTermosVersao\":\"v1-teste\"";
		erro(postar(REGISTER, "{\"nome\":\"Ana\"," + base + ",\"dataNascimento\":20080510}"), 400, "Bad Request",
				"CORPO_ILEGIVEL");
		erro(postar(REGISTER, "{\"nome\":123," + base + ",\"dataNascimento\":\"2008-05-10\"}"), 400, "Bad Request",
				"CORPO_ILEGIVEL");
		erro(postar(REGISTER, "{\"nome\":\"Ana\",\"email\":true,\"senha\":\"" + SENHA
				+ "\",\"dataNascimento\":\"2008-05-10\",\"aceiteTermosVersao\":\"v1-teste\"}"), 400, "Bad Request",
				"CORPO_ILEGIVEL");
		erro(postar(REGISTER, "{\"nome\":\"Ana\",\"email\":\"" + emailNovo() + "\",\"senha\":12345678,"
				+ "\"dataNascimento\":\"2008-05-10\",\"aceiteTermosVersao\":\"v1-teste\"}"), 400, "Bad Request",
				"CORPO_ILEGIVEL");
		erro(postar(REGISTER, "{\"nome\":"), 400, "Bad Request", "CORPO_ILEGIVEL");
	}

	@Test
	void deve_listar_todos_os_campos_obrigatorios_ausentes_uma_vez_cada() throws Exception {
		erro(postar(REGISTER, "{}"), 400, "Bad Request", "VALIDACAO_FALHOU").andExpect(jsonPath("$.campos.length()").value(5))
				.andExpect(jsonPath("$.campos[*].campo").value(org.hamcrest.Matchers.containsInAnyOrder("nome", "email",
						"senha", "dataNascimento", "aceiteTermosVersao")));
	}

	@Test
	void deve_nunca_repetir_a_senha_nem_o_email_na_resposta_de_erro() throws Exception {
		String corpo = postar(REGISTER, cadastro("sem-arroba", "SENHA-SECRETA-1", "2008-05-10", "v0")).andReturn()
				.getResponse().getContentAsString();

		assertThat(corpo).doesNotContain("SENHA-SECRETA-1").doesNotContain("sem-arroba");
	}

	// ----- POST /auth/signin -----

	@Test
	void deve_devolver_200_com_o_par_de_tokens_e_no_store_exato_quando_as_credenciais_conferem() throws Exception {
		String email = emailNovo();
		cadastrar(email).andExpect(status().isCreated());

		ResultActions resposta = entrar(email, SENHA).andExpect(status().isOk())
				.andExpect(content().contentType(MediaType.APPLICATION_JSON))
				.andExpect(header().string("Cache-Control", "no-store")).andExpect(jsonPath("$.tokenType").value("Bearer"))
				.andExpect(jsonPath("$.expiresIn").value(900))
				.andExpect(jsonPath("$.accessToken", matchesPattern("[\\w-]+\\.[\\w-]+\\.[\\w-]+")))
				.andExpect(jsonPath("$.refreshToken", matchesPattern("[\\w-]+\\.[\\w-]+\\.[\\w-]+")));

		String corpo = resposta.andReturn().getResponse().getContentAsString();
		assertThat(corpo).startsWith("{\"accessToken\":\"").containsSubsequence("\"accessToken\"", "\"refreshToken\"",
				"\"tokenType\"", "\"expiresIn\"");
		assertThat((String) JsonPath.read(corpo, "$.accessToken")).isNotEqualTo(JsonPath.read(corpo, "$.refreshToken"));
	}

	@Test
	void deve_aceitar_o_email_com_outra_caixa_e_espacos_no_signin() throws Exception {
		String email = emailNovo();
		cadastrar(email).andExpect(status().isCreated());

		entrar("  " + email.toUpperCase() + " ", SENHA).andExpect(status().isOk());
	}

	@Test
	void deve_devolver_o_mesmo_401_para_qualquer_falha_de_credencial() throws Exception {
		String email = emailNovo();
		cadastrar(email).andExpect(status().isCreated());

		String[] respostas = {
				entrar(email, "senha-errada-123").andReturn().getResponse().getContentAsString(),
				entrar(emailNovo(), SENHA).andReturn().getResponse().getContentAsString(),
				entrar("isto-nao-e-email", SENHA).andReturn().getResponse().getContentAsString(),
				entrar(email, "").andReturn().getResponse().getContentAsString(),
				entrar(email, "a".repeat(73)).andReturn().getResponse().getContentAsString(),
				entrar(email, SENHA + "x".repeat(70)).andReturn().getResponse().getContentAsString(),
				entrar("a".repeat(300) + "@exemplo.invalid", SENHA).andReturn().getResponse().getContentAsString() };

		for (String resposta : respostas) {
			assertThat((Integer) JsonPath.read(resposta, "$.status")).isEqualTo(401);
			assertThat((String) JsonPath.read(resposta, "$.codigo")).isEqualTo("CREDENCIAIS_INVALIDAS");
			assertThat((String) JsonPath.read(resposta, "$.mensagem")).isEqualTo("E-mail ou senha inválidos.");
			assertThat((String) JsonPath.read(resposta, "$.erro")).isEqualTo("Unauthorized");
			assertThat(resposta).doesNotContain("campos").doesNotContain("detalhes");
		}
	}

	@Test
	void deve_nao_entrar_com_uma_senha_de_73_bytes_cujo_prefixo_de_72_e_a_senha_real() throws Exception {
		// O BCrypt trunca em 72 bytes: sem a barreira, este sufixo entraria.
		String email = emailNovo();
		postar(REGISTER, cadastro(email, "a".repeat(72), "2008-05-10", "v1-teste")).andExpect(status().isCreated());

		entrar(email, "a".repeat(72)).andExpect(status().isOk());
		erro(entrar(email, "a".repeat(72) + "QUALQUER-COISA"), 401, "Unauthorized", "CREDENCIAIS_INVALIDAS");
	}

	@Test
	void deve_devolver_400_quando_email_ou_senha_estao_ausentes_nulos_ou_nao_sao_texto() throws Exception {
		erro(postar(SIGNIN, "{}"), 400, "Bad Request", "VALIDACAO_FALHOU").andExpect(jsonPath("$.campos.length()").value(2));
		erroDeCampo(postar(SIGNIN, "{\"email\":\"a@b.com\"}"), "senha");
		erroDeCampo(postar(SIGNIN, "{\"senha\":\"x\"}"), "email");
		erroDeCampo(postar(SIGNIN, "{\"email\":null,\"senha\":\"x\"}"), "email");
		erro(postar(SIGNIN, "{\"email\":123,\"senha\":\"x\"}"), 400, "Bad Request", "CORPO_ILEGIVEL");
		erro(postar(SIGNIN, "{\"email\":\"a@b.com\",\"senha\":true}"), 400, "Bad Request", "CORPO_ILEGIVEL");
		erro(postar(SIGNIN, "{\"email\":[\"a\"],\"senha\":\"x\"}"), 400, "Bad Request", "CORPO_ILEGIVEL");
		erro(postar(SIGNIN, "nao-e-json"), 400, "Bad Request", "CORPO_ILEGIVEL");
	}

	@Test
	void deve_devolver_415_quando_o_corpo_nao_e_json_e_406_quando_o_accept_e_xml() throws Exception {
		erro(mockMvc.perform(post(SIGNIN).contentType(MediaType.TEXT_PLAIN).content("a=b")), 415, "Unsupported Media Type",
				"TIPO_NAO_SUPORTADO");
		erro(mockMvc.perform(post(SIGNIN).contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_XML)
				.content("{\"email\":\"a@b.com\",\"senha\":\"x\"}")), 406, "Not Acceptable", "FORMATO_NAO_ACEITO");
	}

	// ----- POST /auth/refresh -----

	@Test
	void deve_devolver_200_com_um_par_novo_e_no_store_exato_quando_o_refresh_e_valido() throws Exception {
		String tokens = tokensDe(emailNovo());
		String refresh = JsonPath.read(tokens, "$.refreshToken");

		ResultActions resposta = postar(REFRESH, "{\"refreshToken\":\"" + refresh + "\"}").andExpect(status().isOk())
				.andExpect(header().string("Cache-Control", "no-store")).andExpect(jsonPath("$.tokenType").value("Bearer"))
				.andExpect(jsonPath("$.expiresIn").value(900));

		String novo = resposta.andReturn().getResponse().getContentAsString();
		assertThat((String) JsonPath.read(novo, "$.refreshToken")).isNotEqualTo(refresh);
		assertThat((String) JsonPath.read(novo, "$.accessToken")).isNotEqualTo(JsonPath.read(tokens, "$.accessToken"));
	}

	@Test
	void deve_devolver_401_token_invalido_quando_o_refresh_e_um_access_vazio_ou_lixo() throws Exception {
		String tokens = tokensDe(emailNovo());
		String access = JsonPath.read(tokens, "$.accessToken");

		erro(postar(REFRESH, "{\"refreshToken\":\"" + access + "\"}"), 401, "Unauthorized", "TOKEN_INVALIDO")
				.andExpect(jsonPath("$.mensagem").value("Token inválido ou expirado."));
		erro(postar(REFRESH, "{\"refreshToken\":\"\"}"), 401, "Unauthorized", "TOKEN_INVALIDO");
		erro(postar(REFRESH, "{\"refreshToken\":\"   \"}"), 401, "Unauthorized", "TOKEN_INVALIDO");
		erro(postar(REFRESH, "{\"refreshToken\":\"lixo\"}"), 401, "Unauthorized", "TOKEN_INVALIDO");
	}

	@Test
	void deve_devolver_400_quando_o_refresh_esta_ausente_nulo_ou_nao_e_texto() throws Exception {
		erroDeCampo(postar(REFRESH, "{}"), "refreshToken");
		erroDeCampo(postar(REFRESH, "{\"refreshToken\":null}"), "refreshToken");
		erro(postar(REFRESH, "{\"refreshToken\":123}"), 400, "Bad Request", "CORPO_ILEGIVEL");
		erro(postar(REFRESH, "{\"refreshToken\":{\"a\":1}}"), 400, "Bad Request", "CORPO_ILEGIVEL");
	}

	@Test
	void deve_nunca_aceitar_o_refresh_na_url() throws Exception {
		String tokens = tokensDe(emailNovo());
		String refresh = JsonPath.read(tokens, "$.refreshToken");

		erro(mockMvc.perform(post(REFRESH + "?refreshToken=" + refresh).contentType(MediaType.APPLICATION_JSON)
				.content("{}")), 400, "Bad Request", "VALIDACAO_FALHOU");
	}

	// ----- GET /termos/vigente -----

	@Test
	void deve_devolver_os_termos_vigentes_das_propriedades() throws Exception {
		String corpo = mockMvc.perform(get("/api/v1/termos/vigente")).andExpect(status().isOk())
				.andExpect(content().contentType(MediaType.APPLICATION_JSON)).andExpect(jsonPath("$.versao").value("v1-teste"))
				.andExpect(jsonPath("$.url").value("https://exemplo.invalid/termos")).andReturn().getResponse()
				.getContentAsString();

		assertThat(corpo).isEqualTo("{\"versao\":\"v1-teste\",\"url\":\"https://exemplo.invalid/termos\"}");
	}

	@Test
	void deve_aceitar_no_cadastro_exatamente_a_versao_devolvida_pelos_termos() throws Exception {
		String versao = JsonPath.read(
				mockMvc.perform(get("/api/v1/termos/vigente")).andReturn().getResponse().getContentAsString(), "$.versao");

		postar(REGISTER, cadastro(emailNovo(), SENHA, "2008-05-10", versao)).andExpect(status().isCreated());
	}

}
