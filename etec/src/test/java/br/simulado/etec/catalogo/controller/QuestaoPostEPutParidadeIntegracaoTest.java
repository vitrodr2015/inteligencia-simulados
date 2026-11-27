package br.simulado.etec.catalogo.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import io.restassured.response.Response;

/**
 * O PUT de questão tem DTO próprio (numero e provaId opcionais), então as regras de conteúdo existem duas vezes. Este teste
 * manda o MESMO corpo ao cadastro (POST) e à edição (PUT) e exige a MESMA resposta: status, código e a lista de campos com as
 * mensagens. Uma regra mudada num lado só quebra aqui.
 */
class QuestaoPostEPutParidadeIntegracaoTest extends AbstractEdicaoIntegrationTest {

	/** Os campos do corpo válido, cada um com o JSON cru, para cada caso estragar um. */
	private static Map<String, String> valido(String subtopico, int numero) {
		Map<String, String> campos = new LinkedHashMap<>();
		campos.put("numero", String.valueOf(numero));
		campos.put("subtopicoId", texto(subtopico));
		campos.put("enunciado", texto("Enunciado"));
		campos.put("alternativas", alternativas("um", "dois", "três", "quatro", "cinco"));
		return campos;
	}

	private static String json(Map<String, String> campos) {
		return campos.entrySet().stream().map(e -> "\"" + e.getKey() + "\":" + e.getValue())
				.collect(Collectors.joining(",", "{", "}"));
	}

	private static String texto(String valor) {
		return "\"" + valor + "\"";
	}

	private static String longo(int tamanho) {
		return texto("x".repeat(tamanho));
	}

	private static Arguments caso(String nome, Consumer<Map<String, String>> estrago) {
		return Arguments.of(nome, estrago);
	}

	private static Arguments troca(String campo, String jsonCru) {
		String resumo = jsonCru.length() > 60 ? jsonCru.substring(0, 60) + "… (" + jsonCru.length() + ")" : jsonCru;
		return caso(campo + " = " + resumo, m -> m.put(campo, jsonCru));
	}

	private static Arguments sem(String campo) {
		return caso("sem " + campo, m -> m.remove(campo));
	}

	private static String alternativasCom(String trecho, String substituto) {
		return alternativas("um", "dois", "três", "quatro", "cinco").replace(trecho, substituto);
	}

	static Stream<Arguments> corposInvalidos() {
		return Stream.of(
				troca("numero", "0"), troca("numero", "301"), troca("numero", texto("1")), troca("numero", "1.5"),
				sem("subtopicoId"), troca("subtopicoId", "null"), troca("subtopicoId", texto("abc")),
				troca("subtopicoId", texto("11111111-1111-1111-1111-11111111111")),
				troca("subtopicoId", texto(UUID.randomUUID().toString())),
				troca("textoApoio", longo(10_001)), troca("textoApoio", texto("<script>x</script>")),
				troca("textoApoio", texto("a<br/>b")), troca("textoApoio", "1"),
				troca("grupoApoio", "0"), troca("grupoApoio", texto("2")),
				sem("enunciado"), troca("enunciado", "null"), troca("enunciado", texto("")), troca("enunciado", texto("   ")),
				troca("enunciado", longo(10_001)), troca("enunciado", texto("1 < 2")), troca("enunciado", texto("<B>x</B>")),
				troca("enunciado", texto("<b>x")),
				troca("imagemUrl", texto("http://localhost/a.png")), troca("imagemUrl", texto("https://fora.exemplo.invalid/a.png")),
				troca("imagemUrl", texto("https://usuario@localhost/a.png")),
				troca("imagemUrl", texto("https://localhost/" + "a".repeat(483))), troca("imagemUrl", texto("abc")),
				sem("alternativas"), troca("alternativas", "null"), troca("alternativas", texto("cinco")),
				troca("alternativas", alternativas("um", "dois", "três", "quatro")),
				troca("alternativas", alternativasCom("]", ",{\"letra\":\"A\",\"texto\":\"seis\"}]")),
				troca("alternativas", alternativasCom("\"B\"", "\"A\"")), troca("alternativas", alternativasCom("\"E\"", "\"F\"")),
				troca("alternativas", alternativasCom("\"A\"", "\"a\"")),
				troca("alternativas", alternativasCom("{\"letra\":\"C\",\"texto\":\"três\"}", "null")),
				troca("alternativas", alternativasCom("\"um\"", "\"\"")),
				troca("alternativas", alternativasCom("\"um\"", longo(2_001))),
				troca("alternativas", alternativasCom("\"um\"", "\"<p>um</p>\"")),
				troca("alternativas", alternativasCom("\"letra\":\"D\",", "")),
				troca("alternativas", alternativasCom(",\"texto\":\"quatro\"", "")),
				troca("gabarito", texto("F")), troca("gabarito", texto("a")), troca("gabarito", texto("")),
				troca("gabarito", texto("AB")),
				troca("dificuldade", texto("EXTREMA")), troca("dificuldade", texto("facil")),
				troca("explicacao", longo(10_001)), troca("explicacao", texto("<a href='x'>y</a>")),
				caso("vários campos ao mesmo tempo", m -> {
					m.put("enunciado", texto(""));
					m.put("gabarito", texto("Z"));
					m.put("grupoApoio", "-1");
				}));
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("corposInvalidos")
	void deve_responder_igual_no_post_e_no_put_ao_mesmo_corpo_invalido(String nome, Consumer<Map<String, String>> estrago) {
		String prova = novaProva(10);
		String subtopico = criarSubtopico();
		String questao = cadastrarId(prova, subtopico, 1, "Gravada");
		Map<String, String> campos = valido(subtopico, 1);
		estrago.accept(campos);

		compararAsDuasRotas(prova, questao, json(campos));

		buscarQuestao(questao).then().body("enunciado", equalTo("Gravada"));
	}

	@ParameterizedTest
	@ValueSource(strings = { "{\"numero\": ", "[]", "", "\"texto\"" })
	void deve_responder_igual_no_post_e_no_put_ao_corpo_ilegivel(String corpo) {
		String prova = novaProva(10);
		String questao = cadastrarId(prova, criarSubtopico(), 1, "Gravada");

		compararAsDuasRotas(prova, questao, corpo);
	}

	private void compararAsDuasRotas(String prova, String questao, String corpo) {
		Response post = cadastrar(prova, corpo);
		Response put = editarQuestao(questao, corpo);

		assertThat(post.statusCode()).as(post.asString()).isEqualTo(400);
		assertThat(put.statusCode()).as(put.asString()).isEqualTo(400);
		assertThat((String) put.path("codigo")).isEqualTo(post.<String>path("codigo"));
		assertThat(put.<List<Map<String, String>>>path("campos")).isEqualTo(post.<List<Map<String, String>>>path("campos"));
	}

	/** O contrário também diverge: um limite mais apertado num lado só. Nos limites exatos, as duas aceitam. */
	@Test
	void deve_aceitar_no_post_e_no_put_o_mesmo_corpo_com_todos_os_limites_no_maximo() {
		String prova = novaProva(300);
		String subtopico = criarSubtopico();
		String emoji = "😀";
		Map<String, String> campos = valido(subtopico, 300);
		campos.put("enunciado", texto(emoji.repeat(10_000)));
		campos.put("textoApoio", longo(10_000));
		campos.put("explicacao", longo(10_000));
		campos.put("grupoApoio", "1");
		campos.put("imagemUrl", texto("https://localhost/" + "a".repeat(482)));
		campos.put("alternativas", alternativasCom("\"um\"", longo(2_000)));
		campos.put("gabarito", texto("E"));
		campos.put("dificuldade", texto("DIFICIL"));

		Response post = cadastrar(prova, json(campos));
		post.then().statusCode(201);

		editarQuestao(post.path("id"), json(campos)).then().statusCode(200);
	}

}
