package br.simulado.etec.catalogo.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;

import java.nio.ByteBuffer;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import io.restassured.response.Response;

/**
 * Listagem da Matéria (GET /api/v1/materias) contra o MySQL REAL: paginação, filtros e ordenação.
 * O banco é compartilhado com os outros testes, então cada teste cria os próprios dados com um
 * TOKEN único no nome e filtra por ele, e nunca depende de ordem entre testes.
 */
class MateriaListagemIntegracaoTest extends AbstractMateriaIntegrationTest {

	@Autowired
	private JdbcTemplate jdbc;

	// ----- apoio -----

	private Response listar(Map<String, ?> parametros) {
		return requisicao().queryParams(parametros).get(BASE);
	}

	private static Map<String, String> parametros(String... paresChaveValor) {
		Map<String, String> mapa = new LinkedHashMap<>();
		for (int i = 0; i < paresChaveValor.length; i += 2) {
			mapa.put(paresChaveValor[i], paresChaveValor[i + 1]);
		}
		return mapa;
	}

	/** Token único (letras e dígitos) que vai em todos os nomes do teste. */
	private static String token() {
		return "Lst" + sufixo();
	}

	/** Cria {@code quantidade} matérias "token 000", "token 001"... em ordem e devolve os nomes na ordem de criação. */
	private List<String> criarVarias(String token, int quantidade) {
		List<String> nomes = new ArrayList<>();
		for (int i = 0; i < quantidade; i++) {
			String nome = token + " " + String.format("%03d", i);
			criar(nome, null).then().statusCode(201);
			nomes.add(nome);
		}
		return nomes;
	}

	/** Resultado de percorrer TODAS as páginas de uma consulta. */
	private record Percurso(List<String> nomes, long totalElementos, int paginasVisitadas) {
	}

	private Percurso percorrer(Map<String, String> filtros, int tamanho) {
		List<String> nomes = new ArrayList<>();
		long totalElementos = -1;
		int totalPaginas;
		int pagina = 0;
		do {
			Map<String, String> consulta = new LinkedHashMap<>(filtros);
			consulta.put("page", String.valueOf(pagina));
			consulta.put("size", String.valueOf(tamanho));
			Response resposta = listar(consulta);
			resposta.then().statusCode(200).body("pagina", equalTo(pagina)).body("tamanho", equalTo(tamanho));

			long total = resposta.jsonPath().getLong("totalElementos");
			if (totalElementos >= 0) {
				assertThat(total).as("totalElementos igual em todas as páginas").isEqualTo(totalElementos);
			}
			totalElementos = total;
			totalPaginas = resposta.jsonPath().getInt("totalPaginas");
			nomes.addAll(resposta.jsonPath().getList("conteudo.nome", String.class));
			pagina++;
		}
		while (pagina < totalPaginas);
		return new Percurso(nomes, totalElementos, pagina);
	}

	private void assertSemRepeticaoNemPerda(Percurso percurso, List<String> esperadosNaOrdem, int tamanho) {
		assertThat(new HashSet<>(percurso.nomes())).as("itens repetidos entre páginas").hasSameSizeAs(percurso.nomes());
		assertThat(percurso.nomes()).as("itens na ordem, sem perda").containsExactlyElementsOf(esperadosNaOrdem);
		assertThat(percurso.totalElementos()).isEqualTo(esperadosNaOrdem.size());
		assertThat(percurso.paginasVisitadas()).isEqualTo((int) Math.ceil(esperadosNaOrdem.size() / (double) tamanho));
	}

	private long contarNoBanco(String sql, Object... argumentos) {
		return jdbc.queryForObject(sql, Long.class, argumentos);
	}

	// ----- envelope -----

	@Test
	void deve_devolver_o_envelope_com_os_campos_na_ordem_do_contrato_e_itens_iguais_ao_get_por_id() {
		String token = token();
		criarVarias(token, 3);

		Response resposta = listar(parametros("nome", token));

		resposta.then().statusCode(200);
		assertThat(resposta.asString()).matches(
				"^\\{\"conteudo\":\\[.*],\"pagina\":0,\"tamanho\":20,\"totalElementos\":3,\"totalPaginas\":1}$");
		String id = resposta.path("conteudo[0].id");
		Map<String, Object> item = resposta.path("conteudo[0]");
		assertThat(item.keySet()).containsExactlyInAnyOrder("id", "nome", "descricao", "ativo", "criadoEm", "atualizadoEm");
		Response porId = buscar(id);
		for (String campo : List.of("id", "nome", "descricao", "ativo", "criadoEm", "atualizadoEm")) {
			assertThat(String.valueOf((Object) resposta.path("conteudo[0]." + campo))).as("campo %s", campo)
					.isEqualTo(String.valueOf((Object) porId.path(campo)));
		}
	}

	@Test
	void deve_usar_pagina_0_tamanho_20_e_nome_crescente_quando_nenhum_parametro_e_informado() {
		String token = token();
		for (String sufixoDoNome : List.of("Zeta", "Alfa", "Mike")) {
			criar(token + " " + sufixoDoNome, null).then().statusCode(201);
		}

		Response resposta = listar(parametros("nome", token));

		resposta.then().statusCode(200).body("pagina", equalTo(0)).body("tamanho", equalTo(20));
		assertThat(resposta.jsonPath().getList("conteudo.nome", String.class))
				.containsExactly(token + " Alfa", token + " Mike", token + " Zeta");
	}

	@Test
	void deve_devolver_o_erro_completo_quando_o_parametro_e_invalido() {
		verificarErro(listar(parametros("size", "0")), 400, "Bad Request", "PARAMETRO_INVALIDO");
		verificarErro(listar(parametros("sort", "xyz")), 400, "Bad Request", "ORDENACAO_INVALIDA");
		verificarErro(listar(parametros("ativo", "talvez")), 400, "Bad Request", "PARAMETRO_INVALIDO");
	}

	// ----- size: reduzido ao máximo, e inválido = 400 -----

	@Test
	void deve_reduzir_o_size_a_50_e_devolver_50_no_envelope_quando_o_size_e_gigante() {
		String token = token();
		criarVarias(token, 55);

		Response resposta = listar(parametros("nome", token, "size", "100000"));

		resposta.then().statusCode(200)
				.body("tamanho", equalTo(50))
				.body("conteudo.size()", equalTo(50))
				.body("totalElementos", equalTo(55))
				.body("totalPaginas", equalTo(2));
	}

	@Test
	void deve_reduzir_o_size_a_50_quando_o_size_e_51() {
		String token = token();
		criarVarias(token, 52);

		listar(parametros("nome", token, "size", "51")).then().statusCode(200)
				.body("tamanho", equalTo(50)).body("conteudo.size()", equalTo(50));
	}

	@Test
	void deve_devolver_400_parametro_invalido_quando_page_ou_size_e_negativo_zero_ou_nao_numerico() {
		List<String[]> invalidos = List.of(new String[] { "page", "-1" }, new String[] { "page", "abc" },
				new String[] { "page", "1.5" }, new String[] { "page", "" }, new String[] { "size", "-1" },
				new String[] { "size", "0" }, new String[] { "size", "abc" }, new String[] { "size", "1.5" },
				new String[] { "size", "" });

		for (String[] parametro : invalidos) {
			Response resposta = listar(parametros(parametro[0], parametro[1]));

			verificarErro(resposta, 400, "Bad Request", "PARAMETRO_INVALIDO");
			resposta.then().body("mensagem", containsString("'" + parametro[0] + "'"));
		}
	}

	// ----- sort: lista fixa, sem injeção -----

	@Test
	void deve_devolver_400_e_manter_a_tabela_intacta_quando_o_sort_tenta_injetar_sql() {
		String token = token();
		criarVarias(token, 3);
		long antes = contarNoBanco("select count(*) from materia");

		for (String ataque : List.of("nome;drop table x", "nome,asc;drop table materia", "nome;drop table materia;--",
				"nome,asc) union select 1", "nome,asc'; delete from materia; --")) {
			Response resposta = listar(parametros("nome", token, "sort", ataque));

			verificarErro(resposta, 400, "Bad Request", "ORDENACAO_INVALIDA");
			assertThat(resposta.asString()).doesNotContain("drop").doesNotContain("union");
		}

		assertThat(contarNoBanco("select count(*) from information_schema.tables where table_schema = database()"
				+ " and table_name = 'materia'")).as("a tabela materia continua existindo").isEqualTo(1);
		assertThat(contarNoBanco("select count(*) from information_schema.tables where table_schema = database()"
				+ " and table_name = 'x'")).isZero();
		assertThat(contarNoBanco("select count(*) from materia")).as("nenhuma linha foi apagada").isEqualTo(antes);
		assertThat(contarNoBanco("select count(*) from materia where nome like ?", token + "%")).isEqualTo(3);
	}

	@Test
	void deve_devolver_400_ordenacao_invalida_quando_o_sort_usa_campo_fora_da_lista() {
		for (String campo : List.of("xyz", "id", "ativo", "publicId", "senha", "descricao", "criadoem", "NOME")) {
			verificarErro(listar(parametros("sort", campo + ",asc")), 400, "Bad Request", "ORDENACAO_INVALIDA");
		}
	}

	@Test
	void deve_devolver_400_ordenacao_invalida_quando_a_direcao_ou_o_formato_do_sort_e_invalido() {
		for (String sort : List.of("nome,up", "nome,asc,desc", "nome,", ",asc", "nome asc", "nome, asc")) {
			verificarErro(listar(parametros("sort", sort)), 400, "Bad Request", "ORDENACAO_INVALIDA");
		}
	}

	// ----- página além do fim -----

	@Test
	void deve_devolver_200_vazio_com_os_totais_corretos_quando_a_pagina_esta_alem_do_fim() {
		String token = token();
		criarVarias(token, 5);

		listar(parametros("nome", token, "page", "99", "size", "10")).then().statusCode(200)
				.body("conteudo.size()", equalTo(0))
				.body("pagina", equalTo(99))
				.body("tamanho", equalTo(10))
				.body("totalElementos", equalTo(5))
				.body("totalPaginas", equalTo(1));
	}

	@Test
	void deve_devolver_200_vazio_quando_o_page_e_o_maior_inteiro_ou_maior_ainda() {
		String token = token();
		criarVarias(token, 2);

		for (String page : List.of("2147483647", "99999999999999999999")) {
			Response resposta = listar(parametros("nome", token, "page", page));

			assertThat(resposta.statusCode()).as("page=%s", page).isEqualTo(200);
			resposta.then().body("conteudo.size()", equalTo(0)).body("totalElementos", equalTo(2));
		}
	}

	@Test
	void deve_devolver_200_vazio_quando_nao_ha_nenhum_item_que_case_com_o_filtro() {
		listar(parametros("nome", "zzz-nao-existe-" + sufixo())).then().statusCode(200)
				.body("conteudo.size()", equalTo(0)).body("pagina", equalTo(0)).body("totalElementos", equalTo(0))
				.body("totalPaginas", equalTo(0));
	}

	// ----- paginação estável: percorrer TODAS as páginas -----

	@Test
	void deve_percorrer_todas_as_paginas_sem_repetir_nem_perder_quando_ordena_por_nome_crescente() {
		String token = token();
		List<String> criados = criarVarias(token, 40);
		List<String> esperados = new ArrayList<>(criados);
		Collections.sort(esperados);

		Percurso percurso = percorrer(parametros("nome", token, "sort", "nome,asc"), 7);

		assertSemRepeticaoNemPerda(percurso, esperados, 7);
	}

	@Test
	void deve_percorrer_todas_as_paginas_sem_repetir_nem_perder_quando_ordena_por_nome_decrescente() {
		String token = token();
		List<String> esperados = new ArrayList<>(criarVarias(token, 40));
		Collections.sort(esperados, Collections.reverseOrder());

		Percurso percurso = percorrer(parametros("nome", token, "sort", "nome,desc"), 7);

		assertSemRepeticaoNemPerda(percurso, esperados, 7);
	}

	@Test
	void deve_percorrer_todas_as_paginas_sem_repetir_nem_perder_quando_ordena_por_criadoEm_crescente() {
		String token = token();
		List<String> naOrdemDeCriacao = criarVarias(token, 40);

		// Criadas em sequência: o criadoEm cresce (e, no mesmo milissegundo, o desempate por id mantém a ordem).
		Percurso percurso = percorrer(parametros("nome", token, "sort", "criadoEm,asc"), 7);

		assertSemRepeticaoNemPerda(percurso, naOrdemDeCriacao, 7);
	}

	@Test
	void deve_desempatar_por_id_quando_muitas_linhas_tem_o_mesmo_criado_em() {
		String token = token();
		LocalDateTime mesmoInstante = LocalDateTime.of(2000, 1, 1, 0, 0, 0);
		List<String> naOrdemDeInsercao = new ArrayList<>();
		for (int i = 0; i < 30; i++) {
			String nome = token + " " + String.format("%03d", i);
			inserirComCriadoEm(nome, mesmoInstante);
			naOrdemDeInsercao.add(nome);
		}

		Response primeira = listar(parametros("nome", token, "sort", "criadoEm,asc", "size", "50"));
		assertThat(primeira.jsonPath().getList("conteudo.criadoEm", String.class)).as("todos com o mesmo criadoEm")
				.hasSize(30).containsOnly("2000-01-01T00:00:00.000Z");

		// Empate total na chave de ordenação: só o desempate por id mantém as páginas coerentes.
		assertSemRepeticaoNemPerda(percorrer(parametros("nome", token, "sort", "criadoEm,asc"), 7), naOrdemDeInsercao, 7);
		assertSemRepeticaoNemPerda(percorrer(parametros("nome", token, "sort", "criadoEm,desc"), 7), naOrdemDeInsercao,
				7);
	}

	@Test
	void deve_somar_exatamente_o_total_quando_percorre_todas_as_paginas_sem_filtro() {
		String token = token();
		criarVarias(token, 12);

		Percurso percurso = percorrer(parametros(), 50);

		assertThat(percurso.nomes()).hasSameSizeAs(new HashSet<>(percurso.nomes()));
		assertThat(percurso.nomes().size()).isEqualTo(percurso.totalElementos());
		assertThat(percurso.totalElementos()).isEqualTo(contarNoBanco("select count(*) from materia"));
	}

	private void inserirComCriadoEm(String nome, LocalDateTime criadoEm) {
		UUID publicId = UUID.randomUUID();
		byte[] bytes = ByteBuffer.allocate(16).putLong(publicId.getMostSignificantBits())
				.putLong(publicId.getLeastSignificantBits()).array();
		jdbc.update("insert into materia (public_id, programa_id, nome, descricao, ativo, criado_em, atualizado_em)"
				+ " values (?, (select id from programa where codigo = 'ETEC'), ?, null, true, ?, ?)", bytes, nome, criadoEm, criadoEm);
	}

	// ----- filtro nome -----

	@Test
	void deve_encontrar_sem_distinguir_acento_nem_caixa_quando_filtra_por_nome_no_mysql_real() {
		String sufixo = sufixo();
		criar("Matemática " + sufixo, null).then().statusCode(201);
		criar("FÍSICA " + sufixo, null).then().statusCode(201);
		criar("Química " + sufixo, null).then().statusCode(201);

		listar(parametros("nome", "matematica " + sufixo)).then().statusCode(200)
				.body("totalElementos", equalTo(1)).body("conteudo[0].nome", equalTo("Matemática " + sufixo));
		listar(parametros("nome", "MATEMÁTICA " + sufixo)).then().body("totalElementos", equalTo(1));
		listar(parametros("nome", "fisica " + sufixo)).then().body("conteudo[0].nome", equalTo("FÍSICA " + sufixo));
		listar(parametros("nome", "temática " + sufixo)).then().body("totalElementos", equalTo(1));
		listar(parametros("nome", sufixo)).then().body("totalElementos", equalTo(3));
	}

	@Test
	void deve_tratar_percentual_underscore_e_barra_invertida_como_texto_literal_no_filtro_de_nome() {
		String sufixo = sufixo();
		criar("Alfa " + sufixo, null).then().statusCode(201);
		criar("Beta " + sufixo, null).then().statusCode(201);
		criar("100% " + sufixo, null).then().statusCode(201);
		criar("Sub_traço " + sufixo, null).then().statusCode(201);
		criar("SubXtraço " + sufixo, null).then().statusCode(201);
		criar("Barra\\ " + sufixo, null).then().statusCode(201);

		// % não é curinga: só volta o que CONTÉM o caractere %, e é diferente de "tudo"
		Response comPercentual = listar(parametros("nome", "%", "size", "50"));
		comPercentual.then().statusCode(200);
		assertThat(comPercentual.jsonPath().getLong("totalElementos"))
				.isEqualTo(contarNoBanco("select count(*) from materia where instr(nome, ?) > 0", "%"))
				.isLessThan(contarNoBanco("select count(*) from materia"));
		assertThat(comPercentual.jsonPath().getList("conteudo.nome", String.class)).contains("100% " + sufixo)
				.doesNotContain("Alfa " + sufixo, "Beta " + sufixo).allMatch(nome -> nome.contains("%"));

		// _ não é curinga de um caractere: "Sub_traço" NÃO casa com "SubXtraço"
		listar(parametros("nome", "Sub_traço " + sufixo)).then().statusCode(200)
				.body("totalElementos", equalTo(1)).body("conteudo[0].nome", equalTo("Sub_traço " + sufixo));
		Response comUnderscore = listar(parametros("nome", "_", "size", "50"));
		assertThat(comUnderscore.jsonPath().getList("conteudo.nome", String.class)).allMatch(nome -> nome.contains("_"))
				.doesNotContain("SubXtraço " + sufixo, "Alfa " + sufixo);

		// a barra invertida também é literal
		Response comBarra = listar(parametros("nome", "\\", "size", "50"));
		comBarra.then().statusCode(200);
		assertThat(comBarra.jsonPath().getLong("totalElementos"))
				.isEqualTo(contarNoBanco("select count(*) from materia where instr(nome, ?) > 0", "\\"));
		assertThat(comBarra.jsonPath().getList("conteudo.nome", String.class)).contains("Barra\\ " + sufixo)
				.doesNotContain("Alfa " + sufixo);
	}

	@Test
	void deve_ignorar_o_filtro_de_nome_quando_ele_esta_em_branco() {
		long total = contarNoBanco("select count(*) from materia");

		listar(parametros("nome", "   ")).then().statusCode(200).body("totalElementos", equalTo((int) total));
		listar(parametros("nome", "")).then().statusCode(200).body("totalElementos", equalTo((int) total));
	}

	// ----- filtro ativo e combinados -----

	@Test
	void deve_filtrar_por_ativo_true_false_ou_todos_quando_o_parametro_e_informado_ou_ausente() {
		String token = token();
		List<String> nomes = criarVarias(token, 6);
		for (String nome : nomes.subList(0, 2)) {
			String id = idPorNome(token, nome);
			desativar(id).then().statusCode(200);
		}

		listar(parametros("nome", token, "ativo", "false")).then().statusCode(200).body("totalElementos", equalTo(2));
		listar(parametros("nome", token, "ativo", "true")).then().statusCode(200).body("totalElementos", equalTo(4));
		listar(parametros("nome", token)).then().statusCode(200).body("totalElementos", equalTo(6));
		listar(parametros("nome", token, "ativo", "FALSE")).then().body("totalElementos", equalTo(2));
		verificarErro(listar(parametros("ativo", "talvez")), 400, "Bad Request", "PARAMETRO_INVALIDO");
		verificarErro(listar(parametros("ativo", "1")), 400, "Bad Request", "PARAMETRO_INVALIDO");
	}

	@Test
	void deve_combinar_nome_ativo_ordenacao_e_paginacao_quando_todos_os_parametros_sao_informados() {
		String token = token();
		List<String> nomes = criarVarias(token, 10);
		for (String nome : nomes.subList(0, 6)) {
			desativar(idPorNome(token, nome)).then().statusCode(200);
		}

		Response primeira = listar(parametros("nome", token, "ativo", "false", "sort", "nome,desc", "size", "4"));
		Response segunda = listar(parametros("nome", token, "ativo", "false", "sort", "nome,desc", "size", "4",
				"page", "1"));

		primeira.then().statusCode(200).body("totalElementos", equalTo(6)).body("totalPaginas", equalTo(2))
				.body("conteudo.size()", equalTo(4)).body("conteudo.ativo", equalTo(List.of(false, false, false, false)));
		assertThat(primeira.jsonPath().getList("conteudo.nome", String.class))
				.containsExactly(nomes.get(5), nomes.get(4), nomes.get(3), nomes.get(2));
		assertThat(segunda.jsonPath().getList("conteudo.nome", String.class))
				.containsExactly(nomes.get(1), nomes.get(0));
	}

	/** Descobre o id público de uma matéria pelo nome exato, usando o próprio filtro da listagem. */
	private String idPorNome(String token, String nome) {
		Response resposta = listar(parametros("nome", nome, "size", "50"));
		List<Map<String, Object>> itens = resposta.jsonPath().getList("conteudo");
		return itens.stream().filter(item -> nome.equals(item.get("nome"))).map(item -> (String) item.get("id"))
				.findFirst().orElseThrow();
	}

}
