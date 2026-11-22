package br.simulado.etec.catalogo.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import br.simulado.etec.integracao.AbstractIntegrationTest;
import br.simulado.etec.usuarios.model.Papel;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

/**
 * Visibilidade por papel com MySQL real e os filtros LIGADOS (D28, D47, D103): STUDENT só vê ativos (subtópico só de
 * matéria ativa); {@code ativo=false} de STUDENT = 400; o inativo, para STUDENT, dá o MESMO 404 do inexistente; o
 * filtro por matéria inativa dá lista vazia; ADMIN vê tudo. Os dados têm um sufixo único e a listagem filtra por ele,
 * porque o banco é compartilhado entre os testes.
 */
class VisibilidadeIntegracaoTest extends AbstractIntegrationTest {

	private static final String MATERIAS = "/api/v1/materias";

	private static final String SUBTOPICOS = "/api/v1/subtopicos";

	private String sufixo;

	private String materiaAtiva;

	private String materiaInativa;

	private String subAtivoEmMateriaAtiva;

	private String subInativoEmMateriaAtiva;

	private String subAtivoEmMateriaInativa;

	private RequestSpecification admin() {
		return requisicaoComo(Papel.ADMIN);
	}

	private RequestSpecification student() {
		return requisicaoComo(Papel.STUDENT);
	}

	private String criarMateria(String nome) {
		return admin().body("{\"nome\":\"" + nome + "\"}").post(MATERIAS).then().statusCode(201).extract().path("id");
	}

	private String criarSubtopico(String materiaId, String nome) {
		return admin().body("{\"materiaId\":\"" + materiaId + "\",\"nome\":\"" + nome + "\"}").post(SUBTOPICOS).then()
				.statusCode(201).extract().path("id");
	}

	@BeforeEach
	void montar() {
		sufixo = sufixo();
		materiaAtiva = criarMateria("Vis ativa " + sufixo);
		materiaInativa = criarMateria("Vis inativa " + sufixo);
		subAtivoEmMateriaAtiva = criarSubtopico(materiaAtiva, "Vis sub ativo " + sufixo);
		subInativoEmMateriaAtiva = criarSubtopico(materiaAtiva, "Vis sub inativo " + sufixo);
		subAtivoEmMateriaInativa = criarSubtopico(materiaInativa, "Vis sub em materia inativa " + sufixo);
		admin().patch(SUBTOPICOS + "/" + subInativoEmMateriaAtiva + "/disable").then().statusCode(200);
		admin().patch(MATERIAS + "/" + materiaInativa + "/disable").then().statusCode(200);
	}

	private Response listarMaterias(RequestSpecification como) {
		return como.queryParam("nome", sufixo).get(MATERIAS);
	}

	private Response listarSubtopicos(RequestSpecification como) {
		return como.queryParam("nome", sufixo).get(SUBTOPICOS);
	}

	// ----- listagens -----

	@Test
	void deve_listar_so_as_materias_ativas_para_o_student_e_todas_para_o_admin() {
		listarMaterias(student()).then().statusCode(200).body("totalElementos", equalTo(1)).body("conteudo[0].nome",
				equalTo("Vis ativa " + sufixo));
		listarMaterias(admin()).then().statusCode(200).body("totalElementos", equalTo(2)).body("conteudo.nome",
				containsInAnyOrder("Vis ativa " + sufixo, "Vis inativa " + sufixo));
	}

	@Test
	void deve_listar_so_subtopicos_ativos_de_materia_ativa_para_o_student_e_todos_para_o_admin() {
		listarSubtopicos(student()).then().statusCode(200).body("totalElementos", equalTo(1))
				.body("conteudo.nome", contains("Vis sub ativo " + sufixo));
		listarSubtopicos(admin()).then().statusCode(200).body("totalElementos", equalTo(3));
	}

	@Test
	void deve_dar_400_parametro_invalido_quando_o_student_envia_ativo_false_e_deixar_o_admin_filtrar() {
		student().queryParam("ativo", "false").get(MATERIAS).then().statusCode(400).body("codigo",
				equalTo("PARAMETRO_INVALIDO"));
		student().queryParam("ativo", "false").get(SUBTOPICOS).then().statusCode(400).body("codigo",
				equalTo("PARAMETRO_INVALIDO"));
		admin().queryParam("nome", sufixo).queryParam("ativo", "false").get(MATERIAS).then().statusCode(200)
				.body("totalElementos", equalTo(1)).body("conteudo[0].nome", equalTo("Vis inativa " + sufixo));
		student().queryParam("nome", sufixo).queryParam("ativo", "true").get(MATERIAS).then().statusCode(200)
				.body("totalElementos", equalTo(1));
	}

	@Test
	void deve_devolver_lista_vazia_ao_student_que_filtra_por_materia_inativa_e_os_subtopicos_ao_admin() {
		student().queryParam("materiaId", materiaInativa).get(SUBTOPICOS).then().statusCode(200)
				.body("totalElementos", equalTo(0)).body("conteudo.size()", equalTo(0));
		admin().queryParam("materiaId", materiaInativa).get(SUBTOPICOS).then().statusCode(200)
				.body("totalElementos", equalTo(1));
	}

	// ----- por id -----

	private static String semDataHora(String json) {
		return json.replaceAll("\"dataHora\":\"[^\"]+\",?", "");
	}

	@Test
	void deve_dar_o_mesmo_404_ao_student_para_materia_inativa_e_inexistente_e_200_ao_admin() {
		String inativa = student().get(MATERIAS + "/" + materiaInativa).then().statusCode(404)
				.body("codigo", equalTo("MATERIA_NAO_ENCONTRADA")).extract().asString();
		String inexistente = student().get(MATERIAS + "/" + UUID.randomUUID()).then().statusCode(404).extract().asString();

		assertThat(semDataHora(inativa)).isEqualTo(semDataHora(inexistente));
		admin().get(MATERIAS + "/" + materiaInativa).then().statusCode(200).body("ativo", equalTo(false));
		student().get(MATERIAS + "/" + materiaAtiva).then().statusCode(200);
	}

	@Test
	void deve_dar_o_mesmo_404_ao_student_para_subtopico_inativo_ou_de_materia_inativa_e_200_ao_admin() {
		String inexistente = student().get(SUBTOPICOS + "/" + UUID.randomUUID()).then().statusCode(404)
				.body("codigo", equalTo("SUBTOPICO_NAO_ENCONTRADO")).extract().asString();

		for (String escondido : new String[] { subInativoEmMateriaAtiva, subAtivoEmMateriaInativa }) {
			String corpo = student().get(SUBTOPICOS + "/" + escondido).then().statusCode(404).extract().asString();
			assertThat(semDataHora(corpo)).isEqualTo(semDataHora(inexistente));
			admin().get(SUBTOPICOS + "/" + escondido).then().statusCode(200);
		}
		student().get(SUBTOPICOS + "/" + subAtivoEmMateriaAtiva).then().statusCode(200);
	}

	@Test
	void deve_valer_o_mesmo_para_o_head() {
		assertThat(student().head(MATERIAS + "/" + materiaInativa).statusCode()).isEqualTo(404);
		assertThat(admin().head(MATERIAS + "/" + materiaInativa).statusCode()).isEqualTo(200);
		assertThat(student().head(SUBTOPICOS + "/" + subAtivoEmMateriaInativa).statusCode()).isEqualTo(404);
		assertThat(admin().head(SUBTOPICOS + "/" + subAtivoEmMateriaInativa).statusCode()).isEqualTo(200);
	}

	// ----- a visibilidade acompanha o estado na requisição seguinte -----

	@Test
	void deve_mostrar_ao_student_o_que_o_admin_reativa_e_esconder_o_que_ele_desativa() {
		listarSubtopicos(student()).then().body("totalElementos", equalTo(1));

		admin().patch(MATERIAS + "/" + materiaInativa + "/enable").then().statusCode(200);
		listarSubtopicos(student()).then().body("totalElementos", equalTo(2));
		listarMaterias(student()).then().body("totalElementos", equalTo(2));

		admin().patch(SUBTOPICOS + "/" + subAtivoEmMateriaAtiva + "/disable").then().statusCode(200);
		listarSubtopicos(student()).then().body("totalElementos", equalTo(1));
		student().get(SUBTOPICOS + "/" + subAtivoEmMateriaAtiva).then().statusCode(404);
	}

	@Test
	void deve_valer_o_papel_do_banco_na_visibilidade_com_o_token_antigo() {
		var usuario = criarUsuarioNoBanco(Papel.STUDENT);
		String tokenAntigo = tokenDeAcesso(usuario);
		requisicaoCom(tokenAntigo).queryParam("nome", sufixo).get(MATERIAS).then().body("totalElementos", equalTo(1));

		mudarPapelNoBanco(usuario, Papel.ADMIN);

		requisicaoCom(tokenAntigo).queryParam("nome", sufixo).get(MATERIAS).then().body("totalElementos", equalTo(2));
	}

}
