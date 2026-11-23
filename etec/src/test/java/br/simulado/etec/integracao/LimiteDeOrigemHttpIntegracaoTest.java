package br.simulado.etec.integracao;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

import br.simulado.etec.shared.security.limite.RelogioDeTeste;
import io.restassured.response.Response;

/**
 * O limite por ORIGEM por HTTP real: o de /auth/* (toda requisição POST, antes do corpo), o de signin por origem (só falhas, com
 * devolução no sucesso) e a origem da requisição (o X-Forwarded-For forjado nunca fura o limite; só vale quando o socket é
 * um proxy da lista explícita, e só o valor mais à direita que não é proxy). Um contexto por cenário, cada um no próprio
 * schema, com o relógio de teste.
 */
class LimiteDeOrigemHttpIntegracaoTest {

	private static final String CORS = "https://app.exemplo.invalid";

	private static final String EMAIL_ALTO = "--app.rate-limit.signin-email-origem.limite=1000";

	private static final String ORIGEM_ALTA = "--app.rate-limit.signin-origem.limite=100000";

	private static final String AUTH_ALTO = "--app.rate-limit.auth-por-origem.limite=100000";

	private static AppDeLimite subir(String... extras) throws SQLException {
		List<String> args = new ArrayList<>(List.of("--app.cors.allowed-origins=" + CORS, "--app.rate-limit.signin-email-origem.janela=15m",
				"--app.rate-limit.signin-origem.janela=15m", "--app.rate-limit.auth-por-origem.janela=1m"));
		args.addAll(List.of(extras));
		return AppDeLimite.iniciar(AppDeLimite.novoSchema(), new RelogioDeTeste(), true, args.toArray(String[]::new));
	}

	private static List<Integer> status(List<Response> respostas) {
		return respostas.stream().map(Response::statusCode).toList();
	}

	// ----- /auth/* por origem: só as rotas POST contam, antes do corpo -----

	@Test
	void deve_contar_toda_requisicao_post_de_auth_e_dar_429_antes_do_400_sem_contar_preflight_nem_get() throws SQLException {
		try (AppDeLimite app = subir("--app.rate-limit.auth-por-origem.limite=3", EMAIL_ALTO, ORIGEM_ALTA)) {
			for (int i = 0; i < 10; i++) {
				app.http().header("Origin", CORS).header("Access-Control-Request-Method", "POST")
						.options("/api/v1/auth/signin").then().statusCode(200);
				app.http().get("/api/v1/termos/vigente").then().statusCode(200);
			}

			// três POST de verdade (register, refresh e signin): corpo ruim = 400, e cada um conta
			app.http().body("{}").post("/api/v1/auth/register").then().statusCode(400);
			app.http().body("{}").post("/api/v1/auth/refresh").then().statusCode(400);
			app.http().body("isto não é json").post("/api/v1/auth/signin").then().statusCode(400);
			// o quarto (logout, que conta e nem existe ainda) já é 429, e a resposta vem antes de qualquer 401 ou 400
			Response quarta = app.http().header("Origin", CORS).body("isto também não é json").post("/api/v1/auth/logout");

			quarta.then().statusCode(429).header("Retry-After", "60").header("Cache-Control", "no-store")
					.contentType("application/json").body("codigo", Matchers.equalTo("LIMITE_EXCEDIDO"));
			assertThat(quarta.header("Access-Control-Allow-Origin")).as("CORS também no 429 do filtro").isEqualTo(CORS);
			app.http().body("{}").post("/api/v1/auth/signin").then().statusCode(429);

			app.relogio.avancar(Duration.ofSeconds(60));
			app.http().body("{}").post("/api/v1/auth/signin").then().statusCode(400);
		}
	}

	// ----- signin por origem: só falhas, com devolução no sucesso -----

	@Test
	void deve_contar_so_as_falhas_na_origem_e_devolver_a_ocorrencia_quando_o_login_da_certo() throws SQLException {
		try (AppDeLimite app = subir("--app.rate-limit.signin-origem.limite=3", EMAIL_ALTO, AUTH_ALTO)) {
			String aluno = app.criarAluno();

			List<Response> respostas = new ArrayList<>();
			respostas.add(app.signin("a1@exemplo.invalid", "errada")); // falha 1 (e-mail inexistente)
			respostas.add(app.signin("a2@exemplo.invalid", "errada")); // falha 2
			respostas.add(app.signin(aluno, AppDeLimite.SENHA_DO_ALUNO)); // sucesso: conta e devolve
			respostas.add(app.signin("a3@exemplo.invalid", "errada")); // falha 3, ainda dentro do limite
			respostas.add(app.signin("a4@exemplo.invalid", "errada")); // falha 4: acima do limite de 3

			assertThat(status(respostas)).containsExactly(401, 401, 200, 401, 429);
			assertThat(respostas.get(4).header("Retry-After")).isEqualTo("900");
			// e um login certo na origem bloqueada também dá 429 (não há como o atacante achar a senha "de graça")
			app.signin(aluno, AppDeLimite.SENHA_DO_ALUNO).then().statusCode(429);
			app.relogio.avancar(Duration.ofMinutes(15));
			app.signin(aluno, AppDeLimite.SENHA_DO_ALUNO).then().statusCode(200);
		}
	}

	// ----- origem da requisição -----

	private static List<Integer> tres(AppDeLimite app, String email, String... xff) {
		List<Response> respostas = new ArrayList<>();
		for (String valor : xff) {
			respostas.add(app.signin(email, "errada", "X-Forwarded-For", valor));
		}
		return status(respostas);
	}

	@Test
	void deve_ignorar_o_x_forwarded_for_forjado_sem_proxy_configurado() throws SQLException {
		try (AppDeLimite app = subir("--app.rate-limit.signin-email-origem.limite=2", ORIGEM_ALTA, AUTH_ALTO)) {
			String email = "alvo.sem.proxy@exemplo.invalid";

			assertThat(tres(app, email, "203.0.113.1", "203.0.113.2", "203.0.113.3"))
					.as("cada tentativa com um IP inventado: ainda é a mesma origem, e a 3ª dá 429").containsExactly(401, 401, 429);
			app.signin(email, "errada", "Forwarded", "for=198.51.100.7", "X-Real-IP", "198.51.100.8").then().statusCode(429);
		}
	}

	@Test
	void deve_ignorar_o_x_forwarded_for_quando_o_socket_nao_esta_na_lista_de_proxies_confiaveis() throws SQLException {
		try (AppDeLimite app = subir("--app.rate-limit.signin-email-origem.limite=2", ORIGEM_ALTA, AUTH_ALTO,
				"--server.tomcat.remoteip.remote-ip-header=X-Forwarded-For",
				"--server.tomcat.remoteip.internal-proxies=10\\.0\\.0\\.1")) {
			assertThat(tres(app, "alvo.proxy.errado@exemplo.invalid", "203.0.113.1", "203.0.113.2", "203.0.113.3"))
					.as("o loopback não é proxy confiável: o cabeçalho vale nada").containsExactly(401, 401, 429);
		}
	}

	@Test
	void deve_usar_o_cliente_do_cabecalho_so_quando_o_socket_e_proxy_confiavel_e_ignorar_o_valor_forjado_a_esquerda() throws SQLException {
		try (AppDeLimite app = subir("--app.rate-limit.signin-email-origem.limite=2", ORIGEM_ALTA, AUTH_ALTO,
				"--server.tomcat.remoteip.remote-ip-header=X-Forwarded-For",
				"--server.tomcat.remoteip.internal-proxies=127\\.0\\.0\\.1|0:0:0:0:0:0:0:1|::1")) {
			String email = "alvo.proxy.certo@exemplo.invalid";

			assertThat(tres(app, email, "203.0.113.1", "203.0.113.1", "203.0.113.1")).as("mesmo cliente: 3ª dá 429")
					.containsExactly(401, 401, 429);
			assertThat(tres(app, email, "203.0.113.2")).as("outro cliente, outra origem, outro contador").containsExactly(401);

			assertThat(tres(app, email, "1.1.1.1, 203.0.113.9", "2.2.2.2, 203.0.113.9", "3.3.3.3, 203.0.113.9"))
					.as("o valor forjado à esquerda é ignorado: vale o primeiro à direita que não é proxy")
					.containsExactly(401, 401, 429);
		}
	}

}
