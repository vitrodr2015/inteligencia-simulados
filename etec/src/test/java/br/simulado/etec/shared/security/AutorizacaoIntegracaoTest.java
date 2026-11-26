package br.simulado.etec.shared.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;

import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import br.simulado.etec.integracao.AbstractIntegrationTest;
import br.simulado.etec.shared.security.JwtTokenProvider.TipoToken;
import br.simulado.etec.shared.security.MatrizDeAutorizacao.Caso;
import br.simulado.etec.shared.security.MatrizDeAutorizacao.Ctx;
import br.simulado.etec.shared.security.MatrizDeAutorizacao.Perfil;
import br.simulado.etec.usuarios.model.Papel;
import br.simulado.etec.usuarios.model.Usuario;
import io.restassured.http.Method;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

/**
 * Quem pode o quê, com os filtros LIGADOS e MySQL real: a matriz de {@link MatrizDeAutorizacao} (anônimo, GUARDIAN,
 * STUDENT e ADMIN, inclusive HEAD) e a troca de papel no banco valendo na requisição seguinte com o token antigo, nos
 * dois sentidos (D87).
 */
class AutorizacaoIntegracaoTest extends AbstractIntegrationTest {

	private static final String MATERIAS = "/api/v1/materias";

	// ----- apoio -----

	static Stream<Arguments> matriz() {
		return MatrizDeAutorizacao.casos().stream()
				.flatMap(caso -> Stream.of(Perfil.values()).map(perfil -> Arguments.of(caso, perfil)));
	}

	/** Cria, como ADMIN, os recursos que a linha pode usar (cada execução tem os seus). */
	private Ctx novoContexto() {
		String sufixo = sufixo();
		String materia = requisicaoComo(Papel.ADMIN).body("{\"nome\":\"Matéria base " + sufixo + "\"}").post(MATERIAS)
				.then().statusCode(201).extract().path("id");
		String subtopico = requisicaoComo(Papel.ADMIN)
				.body("{\"materiaId\":\"" + materia + "\",\"nome\":\"Subtópico base " + sufixo + "\"}")
				.post("/api/v1/subtopicos").then().statusCode(201).extract().path("id");
		String materiaVazia = requisicaoComo(Papel.ADMIN).body("{\"nome\":\"Matéria vazia " + sufixo + "\"}").post(MATERIAS)
				.then().statusCode(201).extract().path("id");
		String prova = requisicaoComo(Papel.ADMIN)
				.body("{\"titulo\":\"Prova base " + sufixo + "\",\"duracaoMinutos\":180,\"quantidadeQuestoes\":50}")
				.post("/api/v1/admin/provas").then().statusCode(201).extract().path("id");
		// a questão fica num subtópico PRÓPRIO: um subtópico com questão não pode ser apagado (409), e a matriz apaga o subtopicoId
		String subtopicoDaQuestao = requisicaoComo(Papel.ADMIN)
				.body("{\"materiaId\":\"" + materia + "\",\"nome\":\"Subtópico da questão " + sufixo + "\"}")
				.post("/api/v1/subtopicos").then().statusCode(201).extract().path("id");
		String questao = requisicaoComo(Papel.ADMIN)
				.body("{\"numero\":1,\"subtopicoId\":\"" + subtopicoDaQuestao + "\",\"enunciado\":\"Enunciado\",\"alternativas\":["
						+ "{\"letra\":\"A\",\"texto\":\"a\"},{\"letra\":\"B\",\"texto\":\"b\"},{\"letra\":\"C\",\"texto\":\"c\"},"
						+ "{\"letra\":\"D\",\"texto\":\"d\"},{\"letra\":\"E\",\"texto\":\"e\"}]}")
				.post("/api/v1/admin/provas/" + prova + "/questoes").then().statusCode(201).extract().path("id");
		return new Ctx(materia, subtopico, materiaVazia, prova, questao, MatrizDeAutorizacao.uuidInexistente(), sufixo);
	}

	private RequestSpecification comoPerfil(Perfil perfil) {
		return switch (perfil) {
			case ANONIMO -> requisicaoSemToken();
			case GUARDIAN -> requisicaoComo(Papel.GUARDIAN);
			case STUDENT -> requisicaoComo(Papel.STUDENT);
			case ADMIN -> requisicaoComo(Papel.ADMIN);
		};
	}

	/** Dispara a requisição da linha como o perfil dado. */
	private Response disparar(Caso caso, Perfil perfil, Ctx ctx) {
		RequestSpecification requisicao = comoPerfil(perfil);
		String corpo = (caso.corpo() != null) ? caso.corpo().apply(ctx) : null;
		if (corpo != null) {
			requisicao = requisicao.body(corpo);
		}
		return requisicao.request(Method.valueOf(caso.metodo()), caso.caminho().apply(ctx));
	}

	private static boolean ehRotaPublica(Caso caso) {
		return caso.anonimo() != 401 || caso.padrao() != null && caso.padrao().startsWith("/api/v1/auth/");
	}

	// ----- a matriz -----

	@ParameterizedTest(name = "{1} em {0}")
	@MethodSource("matriz")
	void deve_responder_o_status_da_matriz_para_cada_perfil_e_rota(Caso caso, Perfil perfil) {
		Ctx ctx = novoContexto();

		Response resposta = disparar(caso, perfil, ctx);

		assertThat(resposta.statusCode()).as("%s como %s", caso, perfil).isEqualTo(caso.esperado(perfil));
		// O 401 e o 403 da cadeia saem no ErrorResponse, em JSON puro, e sem corpo no HEAD.
		if (caso.esperado(perfil) == 403 || (caso.esperado(perfil) == 401 && !ehRotaPublica(caso))) {
			if (!"HEAD".equals(caso.metodo())) {
				resposta.then().header("Content-Type", "application/json")
						.body("codigo", equalTo(caso.esperado(perfil) == 403 ? "ACESSO_NEGADO" : "NAO_AUTENTICADO"));
			}
		}
		if ("HEAD".equals(caso.metodo())) {
			assertThat(resposta.asString()).as("HEAD não tem corpo").isEmpty();
		}
	}

	@Test
	void deve_dar_ao_head_o_mesmo_status_do_get_em_toda_linha_da_matriz() {
		for (Caso get : MatrizDeAutorizacao.casos()) {
			if (!"GET".equals(get.metodo()) || get.padrao() == null) {
				continue;
			}
			boolean temHead = MatrizDeAutorizacao.casos().stream().anyMatch(
					c -> "HEAD".equals(c.metodo()) && get.padrao().equals(c.padrao()) && mesmosStatus(get, c));
			assertThat(temHead).as("toda rota GET precisa de uma linha HEAD com os mesmos status na matriz: %s", get).isTrue();
		}
	}

	private static boolean mesmosStatus(Caso a, Caso b) {
		for (Perfil perfil : Perfil.values()) {
			if (a.esperado(perfil) != b.esperado(perfil)) {
				return false;
			}
		}
		return true;
	}

	// ----- troca de papel no banco vale na requisição seguinte, com o token antigo -----

	/** Tenta criar uma matéria com o token dado: 201 só para ADMIN. */
	private int tentarEscrever(String token) {
		return requisicaoCom(token).body("{\"nome\":\"Matéria do teste de papel " + sufixo() + "\"}").post(MATERIAS)
				.statusCode();
	}

	@Test
	void deve_liberar_a_escrita_na_requisicao_seguinte_quando_o_student_vira_admin_no_banco_com_o_token_antigo() {
		Usuario usuario = criarUsuarioNoBanco(Papel.STUDENT);
		String tokenAntigo = tokenDeAcesso(usuario);

		assertThat(tentarEscrever(tokenAntigo)).isEqualTo(403);
		mudarPapelNoBanco(usuario, Papel.ADMIN);

		assertThat(tentarEscrever(tokenAntigo)).isEqualTo(201);
	}

	@Test
	void deve_barrar_a_escrita_na_requisicao_seguinte_quando_o_admin_vira_student_no_banco_com_o_token_antigo() {
		Usuario usuario = criarUsuarioNoBanco(Papel.ADMIN);
		String tokenAntigo = tokenDeAcesso(usuario);

		assertThat(tentarEscrever(tokenAntigo)).isEqualTo(201);
		mudarPapelNoBanco(usuario, Papel.STUDENT);

		assertThat(tentarEscrever(tokenAntigo)).isEqualTo(403);
		requisicaoCom(tokenAntigo).get(MATERIAS).then().statusCode(200); // a leitura continua
	}

	@Test
	void deve_barrar_o_me_na_requisicao_seguinte_quando_o_student_vira_guardian_e_liberar_na_volta() {
		Usuario usuario = criarUsuarioNoBanco(Papel.STUDENT);
		String tokenAntigo = tokenDeAcesso(usuario);

		requisicaoCom(tokenAntigo).get("/api/v1/me").then().statusCode(200);
		mudarPapelNoBanco(usuario, Papel.GUARDIAN);
		requisicaoCom(tokenAntigo).get("/api/v1/me").then().statusCode(403).body("codigo", equalTo("ACESSO_NEGADO"));
		requisicaoCom(tokenAntigo).get(MATERIAS).then().statusCode(403);
		mudarPapelNoBanco(usuario, Papel.STUDENT);

		requisicaoCom(tokenAntigo).get("/api/v1/me").then().statusCode(200);
	}

	@Test
	void deve_valer_o_papel_do_banco_e_nao_o_claim_do_token_nos_dois_sentidos() {
		Usuario aluno = criarUsuarioNoBanco(Papel.STUDENT);
		Usuario admin = criarUsuarioNoBanco(Papel.ADMIN);
		// Token legítimo (segredo certo), mas com o claim papel contrariando o banco.
		String alunoComClaimDeAdmin = provedorDeTokens.emitir(TipoToken.ACCESS, aluno.getPublicId(), "ADMIN", 0).valor();
		String adminComClaimDeAluno = provedorDeTokens.emitir(TipoToken.ACCESS, admin.getPublicId(), "STUDENT", 0).valor();

		assertThat(tentarEscrever(alunoComClaimDeAdmin)).isEqualTo(403);
		assertThat(tentarEscrever(adminComClaimDeAluno)).isEqualTo(201);
	}

	@Test
	void deve_derrubar_o_token_quando_o_usuario_e_excluido_mesmo_sendo_admin() {
		Usuario admin = criarUsuarioNoBanco(Papel.ADMIN);
		String token = tokenDeAcesso(admin);
		assertThat(tentarEscrever(token)).isEqualTo(201);

		jdbc.update("UPDATE usuario SET excluido_em = CURRENT_TIMESTAMP(3) WHERE email = ?", admin.getEmail());

		assertThat(tentarEscrever(token)).isEqualTo(401);
	}

	@Test
	void deve_atender_o_guardian_nas_rotas_publicas_e_negar_o_resto_inclusive_o_me() {
		requisicaoComoGuardian().get("/api/v1/termos/vigente").then().statusCode(200);
		requisicaoComoGuardian().get("/api/v1/me").then().statusCode(403);
		requisicaoComoGuardian().get(MATERIAS).then().statusCode(403);
		requisicaoComoGuardian().get("/api/v1/materias/" + UUID.randomUUID()).then().statusCode(403);
	}

}
