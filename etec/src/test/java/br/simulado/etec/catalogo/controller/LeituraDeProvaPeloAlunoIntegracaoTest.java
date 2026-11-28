package br.simulado.etec.catalogo.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import br.simulado.etec.usuarios.model.Papel;
import io.restassured.response.Response;

/**
 * A leitura das provas pelo aluno (D189 a D191) contra MySQL REAL, com os filtros de segurança ligados: só as PUBLICADAS, com os
 * mesmos filtros, ordenação e paginação do ADMIN; DTO próprio; rascunho igual a inexistente; GUARDIAN de fora. Os anos 2096 a
 * 2098 são usados só aqui, porque o banco é compartilhado entre os testes.
 */
class LeituraDeProvaPeloAlunoIntegracaoTest extends AbstractPublicacaoIntegrationTest {

	private static final String PROVAS = "/api/v1/provas";

	private static final List<String> CAMPOS_DO_ADMIN = List.of("id", "titulo", "banca", "ano", "semestre", "duracaoMinutos",
			"quantidadeQuestoes", "fonteUrl", "status", "publicadaEm", "criadoEm", "questoesCadastradas");

	private final List<String> subtopicosDoTeste = new ArrayList<>();

	/** Apaga tudo o que o teste criou: primeiro as provas (que usam os subtópicos), depois os subtópicos e as matérias deles. */
	@AfterEach
	void apagarProvasSubtopicosEMaterias() {
		apagarOQueOTesteCriou();
		for (String subtopico : subtopicosDoTeste) {
			Long materia = jdbc.queryForObject("SELECT materia_id FROM subtopico WHERE public_id = UUID_TO_BIN(?)", Long.class,
					subtopico);
			jdbc.update("DELETE FROM subtopico WHERE public_id = UUID_TO_BIN(?)", subtopico);
			jdbc.update("DELETE FROM materia WHERE id = ?", materia);
		}
		subtopicosDoTeste.clear();
	}

	private String subtopico() {
		String subtopico = criarSubtopico();
		subtopicosDoTeste.add(subtopico);
		return subtopico;
	}

	private String rascunho(Integer ano, Integer semestre) {
		String prova = novaProva(1);
		editarProva(prova, corpoDaProva("Prova do aluno " + sufixo(), bancaUnica(), ano, semestre, 1)).then().statusCode(200);
		return prova;
	}

	private String publicada(String subtopico, Integer ano, Integer semestre) {
		String prova = rascunho(ano, semestre);
		importarProntas(prova, subtopico, 1, 1);
		publicar(prova).then().statusCode(200);
		return prova;
	}

	private static String bancaUnica() {
		return "Banca " + sufixo();
	}

	@Test
	void deve_dar_ao_aluno_os_mesmos_filtros_ordem_e_paginacao_do_admin_restritos_as_publicadas() {
		String subtopico = subtopico();
		String a = publicada(subtopico, 2097, 1);
		String b = publicada(subtopico, 2097, 2);
		String c = publicada(subtopico, 2098, 1);
		String semAno = publicada(subtopico, null, null);
		String rascunhoNoMesmoAno = rascunho(2097, 1);
		String rascunhoSozinhoNoAno = rascunho(2096, 2);
		List<String> rascunhos = List.of(rascunhoNoMesmoAno, rascunhoSozinhoNoAno);

		// a mesma consulta do ADMIN com status=PUBLICADA devolve exatamente as mesmas provas, na mesma ordem
		List<Map<String, Object>> consultas = List.of(Map.of(), Map.of("sort", "ano"), Map.of("sort", "ano,desc"),
				Map.of("sort", "semestre"), Map.of("sort", "semestre,desc"), Map.of("ano", 2097), Map.of("semestre", 1),
				Map.of("ano", 2097, "semestre", 2), Map.of("ano", 2096));
		for (Map<String, Object> consulta : consultas) {
			Map<String, Object> doAdmin = new HashMap<>(consulta);
			doAdmin.put("status", "PUBLICADA");
			assertThat(idsDe(listarTodasComo(Papel.STUDENT, consulta))).as(consulta.toString())
					.containsExactlyElementsOf(idsDe(listarTodasComo(Papel.ADMIN, doAdmin)))
					.doesNotContainAnyElementsOf(rascunhos);
		}

		// e, para não depender só da comparação, o resultado esperado destas provas
		assertThat(idsDe(listarTodasComo(Papel.STUDENT, Map.of()))).filteredOn(List.of(a, b, c, semAno)::contains).first()
				.isEqualTo(c);
		assertThat(idsDe(listarTodasComo(Papel.STUDENT, Map.of()))).filteredOn(List.of(a, b, c, semAno)::contains).last()
				.isEqualTo(semAno);
		assertThat(idsDe(listarTodasComo(Papel.STUDENT, Map.of("ano", 2097, "sort", "semestre,desc")))).containsExactly(b, a);
		assertThat(idsDe(listarTodasComo(Papel.STUDENT, Map.of("ano", 2097, "semestre", 1)))).containsExactly(a);
		assertThat(idsDe(listarTodasComo(Papel.STUDENT, Map.of("ano", 2096)))).isEmpty();
		// o ADMIN continua vendo os rascunhos
		assertThat(idsDe(listarTodasComo(Papel.ADMIN, Map.of("ano", 2097)))).containsExactlyInAnyOrder(a, b, rascunhoNoMesmoAno);

		// a paginação e os totais contam só as publicadas
		Response segunda = requisicaoComoStudent().queryParam("ano", 2097).queryParam("sort", "semestre,desc")
				.queryParam("size", 1).queryParam("page", 1).get(PROVAS);
		segunda.then().statusCode(200).body("pagina", equalTo(1)).body("tamanho", equalTo(1)).body("totalElementos", equalTo(2))
				.body("totalPaginas", equalTo(2));
		assertThat(segunda.jsonPath().getList("conteudo.id", String.class)).containsExactly(a);
		requisicaoComoStudent().queryParam("size", 500).get(PROVAS).then().statusCode(200).body("tamanho", equalTo(50));

		// os parâmetros inválidos são recusados como para o ADMIN
		for (String invalido : new String[] { "page=-1", "size=0", "ano=abc", "semestre=3" }) {
			requisicaoComoStudent().get(PROVAS + "?" + invalido).then().statusCode(400).body("codigo",
					equalTo("PARAMETRO_INVALIDO"));
		}
		requisicaoComoStudent().get(PROVAS + "?sort=titulo").then().statusCode(400).body("codigo",
				equalTo("ORDENACAO_INVALIDA"));
	}

	@Test
	void deve_dar_ao_aluno_um_dto_proprio_sem_mudar_o_formato_do_admin() {
		String prova = publicada(subtopico(), 2098, 2);

		Map<String, Object> doAdmin = requisicao().get(PROVAS + "/" + prova).then().statusCode(200).extract().jsonPath()
				.getMap("$");
		Map<String, Object> doAluno = requisicaoComoStudent().get(PROVAS + "/" + prova).then().statusCode(200).extract()
				.jsonPath().getMap("$");

		assertThat(doAdmin.keySet()).containsExactlyElementsOf(CAMPOS_DO_ADMIN);
		assertThat(doAluno.keySet()).containsExactlyElementsOf(CAMPOS_DO_ALUNO);
		for (String campo : CAMPOS_DO_ALUNO) {
			assertThat(doAluno.get(campo)).as(campo).isEqualTo(doAdmin.get(campo));
		}
		assertThat(doAluno.get("publicadaEm")).isNotNull();
		assertThat(listarTodasComo(Papel.STUDENT, Map.of("ano", 2098))).singleElement().satisfies(item -> {
			assertThat(item.keySet()).containsExactlyElementsOf(CAMPOS_DO_ALUNO);
			assertThat(item).isEqualTo(doAluno);
		});
		assertThat(listarTodasComo(Papel.ADMIN, Map.of("ano", 2098))).singleElement()
				.satisfies(item -> assertThat(item).isEqualTo(doAdmin));
	}

	@Test
	void deve_responder_ao_aluno_o_mesmo_404_para_rascunho_e_para_inexistente_ate_a_publicacao() {
		String prova = rascunho(2096, 1);
		importarProntas(prova, subtopico(), 1, 1);

		Response doRascunho = requisicaoComoStudent().get(PROVAS + "/" + prova);
		Response daInexistente = requisicaoComoStudent().get(PROVAS + "/" + UUID.randomUUID());

		verificarErro(doRascunho, 404, "Not Found", "PROVA_NAO_ENCONTRADA");
		assertThat(semDataHora(doRascunho)).isEqualTo(semDataHora(daInexistente));
		requisicaoComoStudent().head(PROVAS + "/" + prova).then().statusCode(404);
		assertThat(idsDe(listarTodasComo(Papel.STUDENT, Map.of("ano", 2096)))).isEmpty();
		requisicao().get(PROVAS + "/" + prova).then().statusCode(200).body("status", equalTo("RASCUNHO"));

		publicar(prova).then().statusCode(200);

		requisicaoComoStudent().get(PROVAS + "/" + prova).then().statusCode(200).body("id", equalTo(prova));
		requisicaoComoStudent().head(PROVAS + "/" + prova).then().statusCode(200);
		assertThat(idsDe(listarTodasComo(Papel.STUDENT, Map.of("ano", 2096)))).containsExactly(prova);
	}

	@Test
	void deve_recusar_o_filtro_status_do_aluno_com_qualquer_valor() {
		for (String valor : new String[] { "PUBLICADA", "RASCUNHO", "publicada", "OUTRO", "" }) {
			verificarErro(requisicaoComoStudent().queryParam("status", valor).get(PROVAS), 400, "Bad Request",
					"PARAMETRO_INVALIDO");
		}
		requisicao().queryParam("status", "PUBLICADA").get(PROVAS).then().statusCode(200);
	}

	@Test
	void deve_continuar_negando_o_guardian_em_toda_leitura_de_prova() {
		String subtopico = subtopico();
		String publicada = publicada(subtopico, 2098, 1);
		String emRascunho = rascunho(2096, 2);

		for (String caminho : new String[] { PROVAS, PROVAS + "?status=PUBLICADA", PROVAS + "/" + publicada,
				PROVAS + "/" + emRascunho, PROVAS + "/" + UUID.randomUUID(), PROVAS + "/abc" }) {
			verificarErro(requisicaoComoGuardian().get(caminho), 403, "Forbidden", "ACESSO_NEGADO");
			requisicaoComoGuardian().head(caminho).then().statusCode(403);
		}
	}

	private static Map<String, Object> semDataHora(Response resposta) {
		Map<String, Object> corpo = new HashMap<>(resposta.jsonPath().getMap("$"));
		assertThat(corpo.remove("dataHora")).isNotNull();
		return corpo;
	}

}
