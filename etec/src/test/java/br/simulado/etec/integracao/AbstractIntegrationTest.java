package br.simulado.etec.integracao;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;

/**
 * Base dos testes de integração: aplicação inteira (porta aleatória, HTTP real) contra um MySQL de
 * verdade, com as migrations de verdade (Flyway) e {@code ddl-auto: validate}.
 *
 * <p>UM contêiner estático, iniciado uma única vez para todas as classes (o início custa segundos;
 * um por classe deixaria o build lento). Todas as subclasses compartilham a mesma configuração do
 * Spring, então o contexto é reaproveitado (nada de {@code @DirtiesContext}).
 *
 * <p>Como o banco é compartilhado, cada teste cria os próprios dados com nomes únicos
 * ({@link #nomeUnico(String)}) e não depende de ordem nem de limpeza. Esta classe será reaproveitada
 * pelos testes de login e de 401/403 (Módulos 10 e 11).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("testcontainers")
public abstract class AbstractIntegrationTest {

	/** Mesmos parâmetros do banco local (README, "Banco local"): MySQL 8.4, utf8mb4 e utf8mb4_unicode_ci. */
	@SuppressWarnings("resource") // singleton: nunca fechado de propósito; o Ryuk o remove ao fim da JVM
	@ServiceConnection
	protected static final MySQLContainer MYSQL = new MySQLContainer(DockerImageName.parse("mysql:8.4"))
			.withDatabaseName("etec")
			.withCommand("--character-set-server=utf8mb4", "--collation-server=utf8mb4_unicode_ci");

	static {
		// Padrão "contêiner singleton": iniciado uma vez; o Ryuk do Testcontainers o remove ao fim da JVM.
		MYSQL.start();
	}

	@LocalServerPort
	protected int porta;

	/** Requisição HTTP real para a aplicação, em JSON UTF-8 (o RestAssured usaria ISO-8859-1 por padrão). */
	protected RequestSpecification requisicao() {
		return RestAssured.given()
				.baseUri("http://localhost")
				.port(porta)
				.contentType(ContentType.JSON.withCharset(StandardCharsets.UTF_8))
				.accept(ContentType.JSON);
	}

	/** Sufixo aleatório curto, para os dados de cada teste não colidirem com os de outros. */
	protected static String sufixo() {
		return UUID.randomUUID().toString().substring(0, 8);
	}

	/** Nome único a partir de uma base (ex.: {@code "Matemática 3f2a9c1b"}). */
	protected static String nomeUnico(String base) {
		return base + " " + sufixo();
	}

}
