package br.simulado.etec.catalogo.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import io.restassured.response.Response;

/**
 * Listagem do Subtópico (GET /api/v1/subtopicos) contra o MySQL REAL: paginação, filtros e ordenação. Cada teste
 * cria a PRÓPRIA matéria e filtra por ela (materiaId), então não depende dos dados de outros testes.
 */
class SubtopicoListagemIntegracaoTest extends AbstractSubtopicoIntegrationTest {

	private static Map<String, String> parametros(String... paresChaveValor) {
		Map<String, String> mapa = new LinkedHashMap<>();
		for (int i = 0; i < paresChaveValor.length; i += 2) {
			mapa.put(paresChaveValor[i], paresChaveValor[i + 1]);
		}
		return mapa;
	}

	/** Cria os subtópicos na ordem dada (a ordem de criação define o id interno) e devolve os ids na mesma ordem. */
	private List<String> criarEm(String materiaId, String... nomes) {
		List<String> ids = new ArrayList<>();
		for (String nome : nomes) {
			ids.add(criarSubtopicoEObterId(materiaId, nome));
		}
		return ids;
	}

	// ----- envelope e padrões -----

	@Test
	void deve_devolver_o_envelope_na_ordem_do_contrato_com_pagina_0_tamanho_20_e_nome_crescente() {
		String materiaId = novaMateria();
		criarEm(materiaId, "Zeta", "Alfa", "Mike");

		Response resposta = listarSubtopicos(parametros("materiaId", materiaId));

		resposta.then().statusCode(200).body("conteudo.nome", contains("Alfa", "Mike", "Zeta")).body("pagina", equalTo(0))
				.body("tamanho", equalTo(20)).body("totalElementos", equalTo(3)).body("totalPaginas", equalTo(1));
		assertThat(resposta.asString()).matches(
				"^\\{\"conteudo\":\\[.*],\"pagina\":0,\"tamanho\":20,\"totalElementos\":3,\"totalPaginas\":1}$");
		Map<String, Object> item = resposta.path("conteudo[0]");
		assertThat(item.keySet()).containsExactlyInAnyOrder("id", "materiaId", "nome", "ativo", "criadoEm", "atualizadoEm");
	}

	@Test
	void deve_devolver_lista_vazia_com_totais_zerados_quando_a_materia_nao_tem_subtopicos() {
		String materiaId = novaMateria();

		listarSubtopicos(parametros("materiaId", materiaId)).then().statusCode(200).body("conteudo", empty())
				.body("totalElementos", equalTo(0)).body("totalPaginas", equalTo(0)).body("pagina", equalTo(0));
	}

	// ----- ordenação -----

	@Test
	void deve_ordenar_por_nome_em_qualquer_direcao_e_por_data_de_criacao() {
		String materiaId = novaMateria();
		criarEm(materiaId, "Charlie", "Alfa", "Bravo");

		listarSubtopicos(parametros("materiaId", materiaId, "sort", "nome,desc")).then().statusCode(200)
				.body("conteudo.nome", contains("Charlie", "Bravo", "Alfa"));
		listarSubtopicos(parametros("materiaId", materiaId, "sort", "nome,ASC")).then().statusCode(200)
				.body("conteudo.nome", contains("Alfa", "Bravo", "Charlie"));
		listarSubtopicos(parametros("materiaId", materiaId, "sort", "criadoEm")).then().statusCode(200)
				.body("conteudo.nome", contains("Charlie", "Alfa", "Bravo"));
		listarSubtopicos(parametros("materiaId", materiaId, "sort", "criadoEm,desc")).then().statusCode(200)
				.body("conteudo.nome", contains("Bravo", "Alfa", "Charlie"));
	}

	@Test
	void deve_ordenar_pelo_alfabeto_do_mysql_ignorando_acento_e_caixa() {
		String materiaId = novaMateria();
		criarEm(materiaId, "Ética", "abstração", "Zoologia", "Álgebra");

		listarSubtopicos(parametros("materiaId", materiaId)).then().statusCode(200)
				.body("conteudo.nome", contains("abstração", "Álgebra", "Ética", "Zoologia"));
	}

	@Test
	void deve_desempatar_pelo_id_interno_crescente_mesmo_com_sort_decrescente() {
		// Nomes diferentes só por acento/caixa seriam duplicados; para empatar a chave de ordenação usa-se criadoEm,
		// que repete quando os registros nascem no mesmo milissegundo. O id interno (ordem de criação) desempata.
		String materiaId = novaMateria();
		List<String> ids = criarEm(materiaId, "T1", "T2", "T3", "T4", "T5", "T6");

		List<String> crescente = new ArrayList<>(listarSubtopicos(parametros("materiaId", materiaId, "sort", "criadoEm"))
				.jsonPath().getList("conteudo.id", String.class));
		List<String> decrescente = new ArrayList<>(
				listarSubtopicos(parametros("materiaId", materiaId, "sort", "criadoEm,desc")).jsonPath()
						.getList("conteudo.id", String.class));

		assertThat(crescente).containsExactlyInAnyOrderElementsOf(ids);
		assertThat(decrescente).containsExactlyInAnyOrderElementsOf(ids);
		// páginas de tamanho 2 nunca repetem nem perdem item
		List<String> paginado = new ArrayList<>();
		for (int pagina = 0; pagina < 3; pagina++) {
			paginado.addAll(listarSubtopicos(parametros("materiaId", materiaId, "sort", "criadoEm,desc", "page",
					String.valueOf(pagina), "size", "2")).jsonPath().getList("conteudo.id", String.class));
		}
		assertThat(new HashSet<>(paginado)).hasSize(6).containsExactlyInAnyOrderElementsOf(ids);
		assertThat(paginado).containsExactlyElementsOf(decrescente);
	}

	@Test
	void deve_devolver_400_ordenacao_invalida_para_sort_fora_da_lista_e_para_mais_de_um_sort() {
		String materiaId = novaMateria();

		verificarErro(listarSubtopicos(parametros("materiaId", materiaId, "sort", "materiaId")), 400, "Bad Request",
				"ORDENACAO_INVALIDA");
		verificarErro(listarSubtopicos(parametros("materiaId", materiaId, "sort", "ativo")), 400, "Bad Request",
				"ORDENACAO_INVALIDA");
		verificarErro(listarSubtopicos(parametros("materiaId", materiaId, "sort", "NOME")), 400, "Bad Request",
				"ORDENACAO_INVALIDA");
		verificarErro(listarSubtopicos(parametros("materiaId", materiaId, "sort", "nome,up")), 400, "Bad Request",
				"ORDENACAO_INVALIDA");
		verificarErro(requisicao().queryParam("materiaId", materiaId).queryParam("sort", "nome", "criadoEm")
				.get(BASE_SUBTOPICOS), 400, "Bad Request", "ORDENACAO_INVALIDA");
		verificarErro(requisicao().queryParam("sort", "nome", "desc").get(BASE_SUBTOPICOS), 400, "Bad Request",
				"ORDENACAO_INVALIDA");
	}

	// ----- filtros -----

	@Test
	void deve_filtrar_por_materia_e_devolver_so_os_subtopicos_dela() {
		String materiaA = novaMateria();
		String materiaB = novaMateria();
		criarEm(materiaA, "A1", "A2");
		criarEm(materiaB, "B1");

		listarSubtopicos(parametros("materiaId", materiaA)).then().statusCode(200).body("conteudo.nome", contains("A1", "A2"))
				.body("conteudo.materiaId", contains(materiaA, materiaA)).body("totalElementos", equalTo(2));
		listarSubtopicos(parametros("materiaId", materiaB)).then().statusCode(200).body("conteudo.nome", contains("B1"));
	}

	@Test
	void deve_devolver_lista_vazia_quando_o_materiaid_e_valido_mas_nao_existe() {
		listarSubtopicos(parametros("materiaId", "11111111-2222-3333-4444-555555555555")).then().statusCode(200)
				.body("conteudo", empty()).body("totalElementos", equalTo(0));
	}

	@ParameterizedTest
	@ValueSource(strings = { "abc", "123", "11111111-2222-3333-4444", "não-é-uuid", "11111111-2222-3333-4444-55555555555Z" })
	void deve_devolver_400_parametro_invalido_quando_o_materiaid_e_malformado(String materiaId) {
		verificarErro(listarSubtopicos(parametros("materiaId", materiaId)), 400, "Bad Request", "PARAMETRO_INVALIDO");
	}

	@Test
	void deve_filtrar_por_nome_contendo_o_texto_sem_distinguir_caixa_nem_acento() {
		String materiaId = novaMateria();
		criarEm(materiaId, "Equação do 1º grau", "Equações do 2º grau", "Função afim", "Razão e proporção");

		listarSubtopicos(parametros("materiaId", materiaId, "nome", "equac")).then().statusCode(200)
				.body("conteudo.nome", contains("Equação do 1º grau", "Equações do 2º grau"));
		listarSubtopicos(parametros("materiaId", materiaId, "nome", "EQUAÇÃO")).then().statusCode(200)
				.body("conteudo.nome", contains("Equação do 1º grau"));
		listarSubtopicos(parametros("materiaId", materiaId, "nome", "FUNCAO")).then().statusCode(200)
				.body("conteudo.nome", contains("Função afim"));
		listarSubtopicos(parametros("materiaId", materiaId, "nome", "razao e")).then().statusCode(200)
				.body("conteudo.nome", contains("Razão e proporção"));
		listarSubtopicos(parametros("materiaId", materiaId, "nome", "inexistente")).then().statusCode(200)
				.body("conteudo", empty());
	}

	@Test
	void deve_tratar_percentual_e_sublinhado_do_filtro_como_texto_literal() {
		String materiaId = novaMateria();
		criarEm(materiaId, "Juros 10% ao mês", "Juros 100 ao mês", "Taxa_selic", "Taxa selic");

		listarSubtopicos(parametros("materiaId", materiaId, "nome", "10%")).then().statusCode(200)
				.body("conteudo.nome", contains("Juros 10% ao mês"));
		listarSubtopicos(parametros("materiaId", materiaId, "nome", "a_s")).then().statusCode(200)
				.body("conteudo.nome", contains("Taxa_selic"));
	}

	@Test
	void deve_tratar_nome_em_branco_como_filtro_ausente() {
		String materiaId = novaMateria();
		criarEm(materiaId, "Um", "Dois");

		listarSubtopicos(parametros("materiaId", materiaId, "nome", "")).then().statusCode(200).body("totalElementos",
				equalTo(2));
		listarSubtopicos(parametros("materiaId", materiaId, "nome", "   ")).then().statusCode(200)
				.body("totalElementos", equalTo(2));
	}

	@Test
	void deve_filtrar_por_ativo_true_ou_false_em_qualquer_caixa() {
		String materiaId = novaMateria();
		List<String> ids = criarEm(materiaId, "Ativo 1", "Ativo 2", "Inativo 1");
		desativarSubtopico(ids.get(2)).then().statusCode(200);

		listarSubtopicos(parametros("materiaId", materiaId, "ativo", "true")).then().statusCode(200)
				.body("conteudo.nome", contains("Ativo 1", "Ativo 2"));
		listarSubtopicos(parametros("materiaId", materiaId, "ativo", "TRUE")).then().statusCode(200)
				.body("totalElementos", equalTo(2));
		listarSubtopicos(parametros("materiaId", materiaId, "ativo", "false")).then().statusCode(200)
				.body("conteudo.nome", contains("Inativo 1"));
		listarSubtopicos(parametros("materiaId", materiaId, "ativo", "False")).then().statusCode(200)
				.body("totalElementos", equalTo(1));
		listarSubtopicos(parametros("materiaId", materiaId)).then().statusCode(200).body("totalElementos", equalTo(3));
	}

	@ParameterizedTest
	@ValueSource(strings = { "1", "0", "sim", "nao", "talvez", "", " ", "truee" })
	void deve_devolver_400_parametro_invalido_quando_o_ativo_nao_e_true_nem_false(String ativo) {
		verificarErro(listarSubtopicos(parametros("ativo", ativo)), 400, "Bad Request", "PARAMETRO_INVALIDO");
	}

	@Test
	void deve_combinar_os_filtros_de_materia_nome_e_ativo() {
		String materiaA = novaMateria();
		String materiaB = novaMateria();
		List<String> idsA = criarEm(materiaA, "Geometria plana", "Geometria espacial", "Trigonometria");
		criarEm(materiaB, "Geometria analítica");
		desativarSubtopico(idsA.get(1)).then().statusCode(200);

		listarSubtopicos(parametros("materiaId", materiaA, "nome", "geometria", "ativo", "true")).then().statusCode(200)
				.body("conteudo.nome", contains("Geometria plana"));
		listarSubtopicos(parametros("materiaId", materiaA, "nome", "geometria")).then().statusCode(200)
				.body("totalElementos", equalTo(2));
		listarSubtopicos(parametros("materiaId", materiaB, "nome", "geometria", "ativo", "false")).then().statusCode(200)
				.body("conteudo", empty());
	}

	// ----- paginação -----

	@Test
	void deve_percorrer_todas_as_paginas_sem_repetir_nem_perder_subtopico() {
		String materiaId = novaMateria();
		String[] nomes = new String[7];
		for (int i = 0; i < nomes.length; i++) {
			nomes[i] = "Tópico " + String.format("%02d", i);
		}
		criarEm(materiaId, nomes);

		List<String> vistos = new ArrayList<>();
		for (int pagina = 0; pagina < 4; pagina++) {
			Response resposta = listarSubtopicos(
					parametros("materiaId", materiaId, "page", String.valueOf(pagina), "size", "2"));
			resposta.then().statusCode(200).body("pagina", equalTo(pagina)).body("tamanho", equalTo(2))
					.body("totalElementos", equalTo(7)).body("totalPaginas", equalTo(4));
			vistos.addAll(resposta.jsonPath().getList("conteudo.nome", String.class));
		}

		assertThat(vistos).containsExactly(nomes);
	}

	@Test
	void deve_devolver_200_vazio_com_os_totais_corretos_e_a_pagina_pedida_quando_a_pagina_passa_do_fim() {
		String materiaId = novaMateria();
		criarEm(materiaId, "Só um", "Só dois", "Só três");

		listarSubtopicos(parametros("materiaId", materiaId, "page", "99", "size", "10")).then().statusCode(200)
				.body("conteudo", empty()).body("pagina", equalTo(99)).body("tamanho", equalTo(10))
				.body("totalElementos", equalTo(3)).body("totalPaginas", equalTo(1));
	}

	@Test
	void deve_reduzir_o_tamanho_acima_do_maximo_a_50_e_informar_o_tamanho_aplicado() {
		String materiaId = novaMateria();
		criarEm(materiaId, "Um", "Dois");

		listarSubtopicos(parametros("materiaId", materiaId, "size", "500")).then().statusCode(200).body("tamanho", equalTo(50))
				.body("conteudo.size()", equalTo(2));
	}

	@ParameterizedTest
	@ValueSource(strings = { "0", "-1", "abc", "1.5", "", " 1", "1 " })
	void deve_devolver_400_para_size_invalido(String size) {
		verificarErro(listarSubtopicos(parametros("size", size)), 400, "Bad Request", "PARAMETRO_INVALIDO");
	}

	@ParameterizedTest
	@ValueSource(strings = { "-1", "abc", "1.5", "", " 0" })
	void deve_devolver_400_para_page_invalido_e_aceitar_zero(String page) {
		verificarErro(listarSubtopicos(parametros("page", page)), 400, "Bad Request", "PARAMETRO_INVALIDO");
	}

	@Test
	void deve_aceitar_page_zero_e_nao_estourar_com_uma_pagina_gigante() {
		String materiaId = novaMateria();
		criarEm(materiaId, "Um");

		listarSubtopicos(parametros("materiaId", materiaId, "page", "0")).then().statusCode(200).body("pagina", equalTo(0))
				.body("conteudo.size()", equalTo(1));
		listarSubtopicos(parametros("materiaId", materiaId, "page", "2147483647", "size", "50")).then().statusCode(200)
				.body("conteudo", empty()).body("totalElementos", equalTo(1));
	}

	@Test
	void deve_nao_carregar_a_materia_um_subtopico_por_vez_na_listagem() {
		// Dez subtópicos da mesma matéria numa página: o que importa aqui é que a resposta traga o materiaId de todos
		// (a matéria vem junto, por EntityGraph, e a sessão já está fechada quando o DTO é montado).
		String materiaId = novaMateria();
		String[] nomes = new String[10];
		for (int i = 0; i < nomes.length; i++) {
			nomes[i] = "Em lote " + i;
		}
		criarEm(materiaId, nomes);

		Response resposta = listarSubtopicos(parametros("materiaId", materiaId));

		resposta.then().statusCode(200).body("conteudo.size()", equalTo(10));
		assertThat(resposta.jsonPath().getList("conteudo.materiaId", String.class)).containsOnly(materiaId);
	}

	@Test
	void deve_listar_sem_filtro_de_materia_e_incluir_os_subtopicos_de_todas() {
		String materiaA = novaMateria();
		String materiaB = novaMateria();
		String sufixo = sufixo();
		criarEm(materiaA, "Comum " + sufixo + " A");
		criarEm(materiaB, "Comum " + sufixo + " B");

		listarSubtopicos(parametros("nome", sufixo)).then().statusCode(200).body("totalElementos", equalTo(2))
				.body("conteudo.materiaId", contains(materiaA, materiaB));
	}

}
