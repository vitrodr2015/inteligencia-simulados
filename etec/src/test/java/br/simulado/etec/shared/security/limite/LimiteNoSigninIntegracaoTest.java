package br.simulado.etec.shared.security.limite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import br.simulado.etec.integracao.AbstractIntegrationTest;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.read.ListAppender;
import io.restassured.response.Response;

/**
 * O que o limite do signin conta e o que ele escreve no log, contra a aplicação inteira e o MySQL real, no contexto
 * compartilhado (D128, D129, D131). Como os limites do yml de teste são altíssimos, o 429 é provocado levando o contador ao
 * limite direto na tabela.
 */
class LimiteNoSigninIntegracaoTest extends AbstractIntegrationTest {

	/** Endereço fixado pelo teste (IPv4 de loopback), para a origem e as chaves derivadas serem conhecidas. */
	private static final String ENDERECO = "127.0.0.1";

	private static final String ORIGEM = "v4:" + ENDERECO;

	@Autowired
	private ChavesDoLimite chaves;

	@Autowired
	private RateLimitProperties limites;

	@AfterEach
	void apagarOsContadoresQueOTesteCriou() {
		jdbc.update("DELETE FROM limite_tentativa");
	}

	private Response signin(String corpo) {
		return requisicaoSemToken().baseUri("http://" + ENDERECO).body(corpo).post("/api/v1/auth/signin");
	}

	private static String credenciais(String email) {
		return "{\"email\":\"" + email + "\",\"senha\":\"senha-errada-123\"}";
	}

	private String chaveDaOrigemEmAuth() {
		return chaves.de(Escopo.AUTH_ORIGEM, ORIGEM);
	}

	private String chaveDoEmailEOrigem(String email) {
		return chaves.de(Escopo.SIGNIN_EMAIL_ORIGEM, ORIGEM + "|" + email);
	}

	private String chaveDaOrigemNoSignin() {
		return chaves.de(Escopo.SIGNIN_ORIGEM, ORIGEM);
	}

	/** A tabela inteira, chave e contador. */
	private Map<String, Integer> contadores() {
		Map<String, Integer> linhas = new LinkedHashMap<>();
		jdbc.query("SELECT chave, contador FROM limite_tentativa ORDER BY id",
				rs -> {
					linhas.put(rs.getString(1), rs.getInt(2));
				});
		return linhas;
	}

	/**
	 * D129: sem e-mail o 400 sai antes do service, então nada conta por e-mail e origem nem por falha de origem. O limite
	 * geral de POST em /auth/* (D128) roda antes de ler o corpo e é o único que soma.
	 */
	@Test
	void deve_nao_contar_no_limite_do_signin_quando_o_login_chega_sem_email() {
		String email = "ninguem." + sufixo() + "@exemplo.invalid";
		signin(credenciais(email)).then().statusCode(401);
		Map<String, Integer> antes = contadores();
		assertThat(antes).as("um 401 conta nos três: é o que prova que as chaves do teste são as da aplicação")
				.containsExactlyInAnyOrderEntriesOf(Map.of(chaveDaOrigemEmAuth(), 1, chaveDoEmailEOrigem(email), 1,
						chaveDaOrigemNoSignin(), 1));

		List<String> semEmail = List.of("{\"senha\":\"senha-errada-123\"}", "{\"email\":null,\"senha\":\"senha-errada-123\"}",
				"{}");
		for (String corpo : semEmail) {
			signin(corpo).then().statusCode(400).body("codigo", equalTo("VALIDACAO_FALHOU"));
		}

		Map<String, Integer> esperado = new LinkedHashMap<>(antes);
		esperado.put(chaveDaOrigemEmAuth(), 1 + semEmail.size());
		assertThat(contadores()).as("nenhuma linha nova; só a do limite geral de /auth/* soma")
				.containsExactlyInAnyOrderEntriesOf(esperado);
	}

	/** D131: nem o endereço nem as chaves derivadas dele vão para o log, nas falhas de login e nos dois 429. */
	@Test
	void deve_nunca_escrever_a_origem_no_log_quando_o_login_falha_e_quando_da_429() {
		String email = "ninguem." + sufixo() + "@exemplo.invalid";
		List<String> linhas = capturarLog(() -> {
			for (int i = 0; i < 3; i++) {
				signin(credenciais(email)).then().statusCode(401);
			}
			// 429 do service (e-mail e origem)
			levarAoLimite(chaveDoEmailEOrigem(email), limites.signinEmailOrigem().limite());
			signin(credenciais(email)).then().statusCode(429).body("codigo", equalTo("LIMITE_EXCEDIDO"));
			// 429 do filtro (limite geral por origem)
			levarAoLimite(chaveDaOrigemEmAuth(), limites.authPorOrigem().limite());
			signin(credenciais(email)).then().statusCode(429).body("codigo", equalTo("LIMITE_EXCEDIDO"));
		});

		assertThat(linhas).as("controle: o log foi mesmo capturado, inclusive em DEBUG, nos dois 429")
				.anyMatch(l -> l.contains("Limite excedido no escopo SIGNIN_EMAIL_ORIGEM"))
				.anyMatch(l -> l.contains("Limite excedido no escopo AUTH_ORIGEM"));
		assertThat(String.join("\n", linhas)).doesNotContain(ENDERECO, "v4:", chaveDaOrigemEmAuth(),
				chaveDoEmailEOrigem(email), chaveDaOrigemNoSignin());
	}

	private void levarAoLimite(String chave, int limite) {
		assertThat(jdbc.update("UPDATE limite_tentativa SET contador = ? WHERE chave = ?", limite, chave)).isEqualTo(1);
	}

	/**
	 * Tudo o que se escreve no log durante a ação, num appender em memória na raiz: o nível normal da raiz mais o DEBUG da
	 * aplicação (é em DEBUG que o limite registra). Cada linha leva a mensagem, o MDC e a pilha, quando há.
	 */
	private static List<String> capturarLog(Runnable acao) {
		Logger raiz = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
		Logger daAplicacao = (Logger) LoggerFactory.getLogger("br.simulado.etec");
		Level nivelAnterior = daAplicacao.getLevel();
		ListAppender<ILoggingEvent> memoria = new ListAppender<>();
		memoria.setContext(raiz.getLoggerContext());
		memoria.start();
		daAplicacao.setLevel(Level.DEBUG);
		raiz.addAppender(memoria);
		try {
			acao.run();
		}
		finally {
			raiz.detachAppender(memoria);
			daAplicacao.setLevel(nivelAnterior);
			memoria.stop();
		}
		return List.copyOf(memoria.list).stream()
				.map(e -> e.getFormattedMessage() + " " + e.getMDCPropertyMap() + " "
						+ (e.getThrowableProxy() == null ? "" : ThrowableProxyUtil.asString(e.getThrowableProxy())))
				.collect(Collectors.toList());
	}

}
