package br.simulado.etec.admin.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

import br.simulado.etec.usuarios.model.Papel;

/**
 * O limite de 2 MiB por HTTP REAL (Tomcat de verdade, no contexto compartilhado): com {@code Content-Length} e sem ele (envio em
 * partes). O ponto que o MockMvc não prova: o cliente RECEBE o 413, em vez de ver a conexão derrubada (depende do
 * {@code server.tomcat.max-swallow-size}).
 */
class ImportacaoCorpoIntegracaoTest extends AbstractImportacaoIntegrationTest {

	private static final int LIMITE = LimiteDeCorpoDaImportacao.LIMITE_EM_BYTES;

	private HttpResponse<String> enviar(String rota, byte[] corpo, boolean comTamanho, String token, String tipo)
			throws IOException, InterruptedException {
		HttpRequest.Builder requisicao = HttpRequest.newBuilder(URI.create("http://localhost:" + porta + rota))
				.header("Content-Type", tipo)
				.POST(comTamanho ? HttpRequest.BodyPublishers.ofByteArray(corpo)
						: HttpRequest.BodyPublishers.ofInputStream(() -> new ByteArrayInputStream(corpo)));
		if (token != null) {
			requisicao.header("Authorization", "Bearer " + token);
		}
		try (HttpClient cliente = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build()) {
			return cliente.send(requisicao.build(), HttpResponse.BodyHandlers.ofString());
		}
	}

	private HttpResponse<String> importarBytes(byte[] corpo, boolean comTamanho) throws IOException, InterruptedException {
		return enviar(ROTA, corpo, comTamanho, tokenDeAcesso(usuarioDoPapel(Papel.ADMIN)), "application/json");
	}

	/** Um JSON válido, completado com espaços até ter exatamente {@code bytes} bytes. */
	private static byte[] preenchidoAte(String json, int bytes) {
		byte[] inicio = json.getBytes(StandardCharsets.UTF_8);
		byte[] corpo = new byte[bytes];
		Arrays.fill(corpo, (byte) ' ');
		System.arraycopy(inicio, 0, corpo, 0, inicio.length);
		return corpo;
	}

	@ParameterizedTest(name = "com Content-Length = {0}")
	@ValueSource(booleans = { true, false })
	void deve_aceitar_exatamente_2_mib_e_recusar_um_byte_a_mais(boolean comTamanho) throws Exception {
		String prova = novaProva(10);
		String lote = lote(prova, itens(criarSubtopico(), 1, 2, "No limite"));

		HttpResponse<String> acima = importarBytes(preenchidoAte(lote, LIMITE + 1), comTamanho);
		assertThat(acima.statusCode()).isEqualTo(413);
		assertThat(questoesDaProva(prova)).as("o 413 não grava nada").isZero();

		HttpResponse<String> noLimite = importarBytes(preenchidoAte(lote, LIMITE), comTamanho);
		assertThat(noLimite.statusCode()).as(noLimite.body()).isEqualTo(200);
		assertThat(questoesDaProva(prova)).isEqualTo(2);
	}

	/** Vários tamanhos grandes, várias vezes: o cliente recebe SEMPRE o 413, e não uma conexão derrubada. */
	@ParameterizedTest(name = "{0} bytes, com Content-Length = {1}")
	@CsvSource({ "3000000,true", "3000000,false", "6000000,true", "6000000,false", "16000000,true", "16000000,false" })
	void deve_entregar_o_413_ao_cliente_com_corpo_grande(int bytes, boolean comTamanho) throws Exception {
		byte[] corpo = preenchidoAte("{\"provaId\":", bytes);

		for (int vez = 0; vez < 3; vez++) {
			HttpResponse<String> resposta = importarBytes(corpo, comTamanho);

			assertThat(resposta.statusCode()).isEqualTo(413);
			assertThat(resposta.headers().firstValue("content-type")).contains("application/json");
			assertThat(resposta.body()).contains("\"codigo\":\"CORPO_MUITO_GRANDE\"").contains("\"erro\":\"Content Too Large\"")
					.contains("Divida o lote");
		}
	}

	@ParameterizedTest(name = "com Content-Length = {0}")
	@ValueSource(booleans = { true, false })
	void deve_contar_bytes_e_nao_caracteres(boolean comTamanho) throws Exception {
		// 1.100.000 letras de 2 bytes: menos de 2 Mi CARACTERES, mais de 2 MiB em BYTES
		String json = "{\"provaId\":\"x\",\"lixo\":\"" + "ç".repeat(1_100_000) + "\"}";
		assertThat(json.length()).isLessThan(LIMITE);

		assertThat(importarBytes(json.getBytes(StandardCharsets.UTF_8), comTamanho).statusCode()).isEqualTo(413);
	}

	@ParameterizedTest(name = "com Content-Length = {0}")
	@ValueSource(booleans = { true, false })
	void deve_recusar_o_anonimo_com_401_e_o_aluno_com_403_sem_tratar_o_tamanho(boolean comTamanho) throws Exception {
		byte[] corpo = preenchidoAte("{}", 3_000_000);

		assertThat(enviar(ROTA, corpo, comTamanho, null, "application/json").statusCode()).isEqualTo(401);
		assertThat(enviar(ROTA, corpo, comTamanho, tokenDeAcesso(usuarioDoPapel(Papel.STUDENT)), "application/json").statusCode())
				.isEqualTo(403);
	}

	@Test
	void deve_dar_415_antes_do_413_por_http_real() throws Exception {
		HttpResponse<String> resposta = enviar(ROTA, preenchidoAte("{}", 3_000_000), true,
				tokenDeAcesso(usuarioDoPapel(Papel.ADMIN)), "text/plain");

		assertThat(resposta.statusCode()).isEqualTo(415);
	}

	@Test
	void deve_limitar_so_a_rota_da_importacao() throws Exception {
		String prova = novaProva(5);
		String item = corpo(criarSubtopico(), 1, "Pelo unitário");
		byte[] grande = preenchidoAte(item, LIMITE + 100);

		HttpResponse<String> resposta = enviar("/api/v1/admin/provas/" + prova + "/questoes", grande, true,
				tokenDeAcesso(usuarioDoPapel(Papel.ADMIN)), "application/json");

		assertThat(resposta.statusCode()).as(resposta.body()).isEqualTo(201);
		assertThat(List.of(questoesDaProva(prova))).containsExactly(1);
	}

}
