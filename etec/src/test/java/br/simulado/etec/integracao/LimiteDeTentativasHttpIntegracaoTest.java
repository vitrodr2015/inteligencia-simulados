package br.simulado.etec.integracao;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import br.simulado.etec.shared.security.limite.ChavesDoLimite;
import br.simulado.etec.shared.security.limite.Escopo;
import br.simulado.etec.shared.security.limite.RelogioDeTeste;
import io.restassured.response.Response;

/**
 * O limite de tentativas por HTTP real, MySQL real e o relógio de teste (D128 a D130). Duas instâncias da aplicação (dois
 * contextos) apontam para o MESMO schema: é assim que se prova que o contador é compartilhado. Limites desta classe: 5
 * falhas por e-mail+origem em 15 min, 50 por origem; o de /auth/* é alto (outro teste o cobre).
 */
class LimiteDeTentativasHttpIntegracaoTest {

	private static final String ORIGEM_DE_LOOPBACK = "v4:127.0.0.1";

	private static final String CORS = "https://app.exemplo.invalid";

	private static final String[] LIMITES = { "--app.rate-limit.signin-email-origem.limite=5",
			"--app.rate-limit.signin-email-origem.janela=15m", "--app.rate-limit.signin-origem.limite=50",
			"--app.rate-limit.signin-origem.janela=15m", "--app.rate-limit.auth-por-origem.limite=100000",
			"--app.cors.allowed-origins=" + CORS };

	private static RelogioDeTeste relogio;

	private static String schema;

	private static AppDeLimite a;

	private static AppDeLimite b;

	@BeforeAll
	static void subir() throws SQLException {
		relogio = new RelogioDeTeste();
		schema = AppDeLimite.novoSchema();
		a = AppDeLimite.iniciar(schema, relogio, true, LIMITES);
		b = AppDeLimite.iniciar(schema, relogio, true, LIMITES);
	}

	@AfterAll
	static void descer() {
		b.close();
		a.close();
	}

	@BeforeEach
	void zerar() {
		a.jdbc().update("DELETE FROM limite_tentativa");
		relogio.definir(Instant.now());
		a.comparacoes.set(0);
		b.comparacoes.set(0);
	}

	private static int linhasDeContador() {
		return a.jdbc().queryForObject("SELECT COUNT(*) FROM limite_tentativa", Integer.class);
	}

	private static Integer contadorDa(Escopo escopo, String valor) {
		String chave = a.contexto.getBean(ChavesDoLimite.class).de(escopo, valor);
		List<Integer> v = a.jdbc().queryForList("SELECT contador FROM limite_tentativa WHERE chave = ?", Integer.class, chave);
		return v.isEmpty() ? null : v.get(0);
	}

	private static String emailEOrigem(String email) {
		return ORIGEM_DE_LOOPBACK + "|" + email;
	}

	// ----- 429, Retry-After e volta do login -----

	@Test
	void deve_dar_429_na_6a_falha_com_retry_after_e_voltar_o_login_so_quando_a_janela_passa_sem_bloqueio_permanente() {
		String email = a.criarAluno();
		for (int i = 1; i <= 5; i++) {
			a.signin(email, "errada-" + i).then().statusCode(401).body("codigo", org.hamcrest.Matchers.equalTo("CREDENCIAIS_INVALIDAS"));
		}

		Response sexta = a.signin(email, "errada-6", "Origin", CORS);

		sexta.then().statusCode(429).header("Retry-After", "900").header("Cache-Control", "no-store")
				.contentType("application/json").body("codigo", org.hamcrest.Matchers.equalTo("LIMITE_EXCEDIDO"))
				.body("status", org.hamcrest.Matchers.equalTo(429));
		assertThat(sexta.header("Access-Control-Allow-Origin")).as("o 429 leva os cabeçalhos de CORS dos 401 e 403").isEqualTo(CORS);
		a.signin(email, AppDeLimite.SENHA_DO_ALUNO).then().statusCode(429);

		relogio.avancar(Duration.ofSeconds(899));
		a.signin(email, AppDeLimite.SENHA_DO_ALUNO).then().statusCode(429).header("Retry-After", "1");

		relogio.avancar(Duration.ofSeconds(1));
		a.signin(email, AppDeLimite.SENHA_DO_ALUNO).then().statusCode(200).body("tokenType", org.hamcrest.Matchers.equalTo("Bearer"));
		// e o contador recomeça do zero: mais 5 falhas, e só a 6ª dá 429
		for (int i = 1; i <= 5; i++) {
			a.signin(email, "errada-" + i).then().statusCode(401);
		}
		a.signin(email, "errada-6").then().statusCode(429);
	}

	// ----- contador compartilhado -----

	@Test
	void deve_somar_as_falhas_de_duas_instancias_no_mesmo_schema() {
		String email = a.criarAluno();

		for (int i = 0; i < 3; i++) {
			a.signin(email, "errada").then().statusCode(401);
		}
		for (int i = 0; i < 2; i++) {
			b.signin(email, "errada").then().statusCode(401);
		}

		a.signin(email, "errada").then().statusCode(429);
		b.signin(email, AppDeLimite.SENHA_DO_ALUNO).then().statusCode(429);
	}

	// ----- indistinguível: e-mail existente x inexistente -----

	private record Resposta(int status, List<String> cabecalhos, String retryAfter, String cacheControl, String tipo,
			Map<String, Object> corpo) {
	}

	private static Resposta resumo(Response r) {
		Map<String, Object> corpo = new java.util.TreeMap<>(r.jsonPath().getMap("$"));
		corpo.remove("dataHora");
		List<String> nomes = r.headers().asList().stream().map(h -> h.getName().toLowerCase()).filter(n -> !n.equals("date"))
				.sorted().distinct().toList();
		return new Resposta(r.statusCode(), nomes, r.header("Retry-After"), r.header("Cache-Control"), r.contentType(), corpo);
	}

	@Test
	void deve_responder_igual_para_email_existente_e_inexistente_no_401_e_no_429_e_sem_gastar_bcrypt_no_429() {
		String existente = a.criarAluno();
		String inexistente = "ninguem." + System.nanoTime() + "@exemplo.invalid";
		List<Resposta> deExistente = new ArrayList<>();
		List<Resposta> deInexistente = new ArrayList<>();

		for (int i = 1; i <= 5; i++) {
			deExistente.add(resumo(a.signin(existente, "errada-" + i)));
			deInexistente.add(resumo(a.signin(inexistente, "errada-" + i)));
		}
		int antesDoBloqueio = a.comparacoes.get();
		for (int i = 6; i <= 8; i++) {
			deExistente.add(resumo(a.signin(existente, "errada-" + i)));
			deInexistente.add(resumo(a.signin(inexistente, "errada-" + i)));
		}

		assertThat(deExistente).as("status, cabeçalhos, Retry-After exato e corpo sem dataHora").isEqualTo(deInexistente);
		assertThat(deExistente.stream().map(Resposta::status)).containsExactly(401, 401, 401, 401, 401, 429, 429, 429);
		assertThat(deExistente.get(5).retryAfter()).isEqualTo("900");
		assertThat(antesDoBloqueio).as("o contador de BCrypt está ligado: 10 falhas, 10 comparações").isEqualTo(10);
		assertThat(a.comparacoes.get()).as("o 429 não gasta nenhuma comparação de BCrypt").isEqualTo(antesDoBloqueio);
	}

	// ----- sucesso zera e devolve -----

	@Test
	void deve_apagar_o_email_e_origem_e_devolver_uma_ocorrencia_a_origem_quando_o_login_da_certo() {
		String email = a.criarAluno();
		for (int i = 0; i < 4; i++) {
			a.signin(email, "errada").then().statusCode(401);
		}
		assertThat(contadorDa(Escopo.SIGNIN_EMAIL_ORIGEM, emailEOrigem(email))).isEqualTo(4);
		assertThat(contadorDa(Escopo.SIGNIN_ORIGEM, ORIGEM_DE_LOOPBACK)).isEqualTo(4);

		a.signin(email, AppDeLimite.SENHA_DO_ALUNO).then().statusCode(200);

		assertThat(contadorDa(Escopo.SIGNIN_EMAIL_ORIGEM, emailEOrigem(email))).as("apagado no sucesso").isNull();
		assertThat(contadorDa(Escopo.SIGNIN_ORIGEM, ORIGEM_DE_LOOPBACK)).as("o sucesso devolve 1: só as 4 falhas contam").isEqualTo(4);
		for (int i = 0; i < 5; i++) {
			a.signin(email, "errada").then().statusCode(401);
		}
		a.signin(email, "errada").then().statusCode(429);
	}

	// ----- o incremento grava mesmo com o 401 (nunca sofre rollback com a exceção) -----

	@Test
	void deve_ter_as_linhas_dos_contadores_depois_de_um_signin_com_senha_errada_que_deu_401() {
		String email = a.criarAluno();

		a.signin(email, "errada").then().statusCode(401);

		assertThat(contadorDa(Escopo.SIGNIN_EMAIL_ORIGEM, emailEOrigem(email))).isEqualTo(1);
		assertThat(contadorDa(Escopo.SIGNIN_ORIGEM, ORIGEM_DE_LOOPBACK)).isEqualTo(1);
		assertThat(linhasDeContador()).as("e-mail+origem, origem e a de /auth/*").isEqualTo(3);
	}

	// ----- concorrência: 40 em paralelo -----

	@Test
	void deve_deixar_chegar_ao_bcrypt_no_maximo_5_de_40_tentativas_paralelas_com_o_mesmo_email() throws Exception {
		String email = a.criarAluno();
		int tentativas = 40;
		ExecutorService pool = Executors.newFixedThreadPool(tentativas);
		CountDownLatch largada = new CountDownLatch(1);
		try {
			List<Future<Response>> futuros = IntStream.range(0, tentativas).mapToObj(i -> pool.submit(() -> {
				largada.await();
				return a.signin(email, "errada-" + i);
			})).toList();
			largada.countDown();
			List<Integer> status = new ArrayList<>();
			for (Future<Response> f : futuros) {
				Response r = f.get();
				status.add(r.statusCode());
				if (r.statusCode() == 429) {
					assertThat(r.header("Retry-After")).isEqualTo("900");
				}
			}

			assertThat(status.stream().filter(s -> s == 401).count()).as("exatamente 5 passaram").isEqualTo(5);
			assertThat(status.stream().filter(s -> s == 429).count()).as("os outros 35 deram 429").isEqualTo(35);
			assertThat(new TreeSet<>(status)).containsExactly(401, 429);
			assertThat(a.comparacoes.get()).as("exatamente as 5 que passaram gastaram BCrypt (nunca mais de 5)").isEqualTo(5);
		}
		finally {
			pool.shutdownNow();
		}
	}

}
