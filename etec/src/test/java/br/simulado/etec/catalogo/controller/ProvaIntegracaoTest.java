package br.simulado.etec.catalogo.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.matchesPattern;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;

import io.restassured.response.Response;

/**
 * A prova contra MySQL REAL (migration V8 lida do {@code information_schema}, CHECKs por INSERT direto, collation, ordem dos
 * nulos e o fluxo ponta a ponta), por HTTP real. O banco é compartilhado: cada teste cria dados próprios e as asserções de
 * listagem não dependem do que outros testes deixaram.
 */
class ProvaIntegracaoTest extends AbstractProvaIntegrationTest {

	// ----- a migration V8, lida do banco -----

	@Test
	void deve_ter_as_colunas_da_prova_com_tipo_e_obrigatoriedade_do_plano_aprovado() {
		Map<String, String> colunas = new LinkedHashMap<>();
		jdbc.query("SELECT column_name, column_type, is_nullable FROM information_schema.columns "
				+ "WHERE table_schema = DATABASE() AND table_name = 'prova' ORDER BY ordinal_position",
				resultado -> {
					colunas.put(resultado.getString("column_name"),
							resultado.getString("column_type") + "/" + resultado.getString("is_nullable"));
				});

		Map<String, String> esperado = new LinkedHashMap<>();
		esperado.put("id", "bigint/NO");
		esperado.put("public_id", "binary(16)/NO");
		esperado.put("programa_id", "bigint/NO");
		esperado.put("titulo", "varchar(150)/NO");
		esperado.put("banca", "varchar(80)/YES");
		esperado.put("ano", "smallint/YES");
		esperado.put("semestre", "tinyint/YES");
		esperado.put("duracao_minutos", "smallint/NO");
		esperado.put("quantidade_questoes", "smallint/NO");
		esperado.put("fonte_url", "varchar(500)/YES");
		esperado.put("status", "varchar(20)/NO");
		esperado.put("publicada_em", "datetime(3)/YES");
		esperado.put("criado_em", "datetime(3)/NO");
		esperado.put("atualizado_em", "datetime(3)/NO");
		assertThat(colunas).containsExactlyEntriesOf(esperado);
	}

	@Test
	void deve_ser_innodb_utf8mb4_unicode_ci() {
		Map<String, Object> tabela = jdbc.queryForMap("SELECT engine, table_collation FROM information_schema.tables "
				+ "WHERE table_schema = DATABASE() AND table_name = 'prova'");

		assertThat(tabela.get("ENGINE")).isEqualTo("InnoDB");
		assertThat(tabela.get("TABLE_COLLATION")).isEqualTo("utf8mb4_unicode_ci");
	}

	@Test
	void deve_ter_a_unique_o_public_id_unico_e_os_dois_indices_de_consulta() {
		List<String> unicaDaTripla = jdbc.queryForList("SELECT column_name FROM information_schema.statistics "
				+ "WHERE table_schema = DATABASE() AND table_name = 'prova' AND index_name = 'uk_prova_programa_banca_ano_semestre' "
				+ "AND non_unique = 0 ORDER BY seq_in_index", String.class);
		List<String> anoSemestre = jdbc.queryForList("SELECT column_name FROM information_schema.statistics "
				+ "WHERE table_schema = DATABASE() AND table_name = 'prova' AND index_name = 'idx_prova_ano_semestre' "
				+ "AND non_unique = 1 ORDER BY seq_in_index", String.class);
		List<String> status = jdbc.queryForList("SELECT column_name FROM information_schema.statistics "
				+ "WHERE table_schema = DATABASE() AND table_name = 'prova' AND index_name = 'idx_prova_status' "
				+ "AND non_unique = 1 ORDER BY seq_in_index", String.class);
		List<String> publicId = jdbc.queryForList("SELECT column_name FROM information_schema.statistics "
				+ "WHERE table_schema = DATABASE() AND table_name = 'prova' AND index_name = 'uk_prova_public_id' "
				+ "AND non_unique = 0", String.class);

		assertThat(unicaDaTripla).containsExactly("programa_id", "banca", "ano", "semestre");
		assertThat(anoSemestre).containsExactly("ano", "semestre");
		assertThat(status).containsExactly("status");
		assertThat(publicId).containsExactly("public_id");
	}

	@Test
	void deve_ter_a_chave_estrangeira_do_programa_com_restrict() {
		String regra = jdbc.queryForObject("SELECT delete_rule FROM information_schema.referential_constraints "
				+ "WHERE constraint_schema = DATABASE() AND constraint_name = 'fk_prova_programa' AND table_name = 'prova'",
				String.class);

		assertThat(regra).isEqualTo("RESTRICT");
		assertThatThrownBy(() -> jdbc.update("DELETE FROM programa WHERE codigo = 'ETEC'"))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void deve_ter_os_seis_checks_da_prova_ativos_com_os_limites_do_plano() {
		Map<String, String> checks = new LinkedHashMap<>();
		jdbc.query("SELECT cc.constraint_name AS nome, cc.check_clause AS clausula, tc.enforced AS ativo "
				+ "FROM information_schema.check_constraints cc JOIN information_schema.table_constraints tc "
				+ "ON tc.constraint_schema = cc.constraint_schema AND tc.constraint_name = cc.constraint_name "
				+ "WHERE cc.constraint_schema = DATABASE() AND tc.table_name = 'prova' AND tc.constraint_type = 'CHECK' "
				+ "ORDER BY cc.constraint_name", resultado -> {
					assertThat(resultado.getString("ativo")).as(resultado.getString("nome")).isEqualTo("YES");
					checks.put(resultado.getString("nome"), resultado.getString("clausula"));
				});

		assertThat(checks).containsOnlyKeys("ck_prova_ano", "ck_prova_semestre", "ck_prova_duracao_minutos",
				"ck_prova_quantidade_questoes", "ck_prova_status", "ck_prova_publicada_em");
		assertThat(checks.get("ck_prova_ano")).contains("2000").contains("2100");
		assertThat(checks.get("ck_prova_semestre")).contains("1").contains("2");
		assertThat(checks.get("ck_prova_duracao_minutos")).contains("600");
		assertThat(checks.get("ck_prova_quantidade_questoes")).contains("300");
		assertThat(checks.get("ck_prova_status")).contains("RASCUNHO").contains("PUBLICADA");
		assertThat(checks.get("ck_prova_publicada_em")).contains("publicada_em").contains("PUBLICADA");
	}

	// ----- os CHECKs recusam linha inválida por INSERT direto -----

	@ParameterizedTest(name = "ano {0} recusado")
	@CsvSource({ "1999", "2101", "0", "-1" })
	void deve_recusar_no_banco_um_ano_fora_de_2000_a_2100(int ano) {
		assertThatThrownBy(() -> inserirNoBanco("Ano ruim", null, ano, null, 60, 10, "RASCUNHO", null))
				.isInstanceOf(DataAccessException.class).hasMessageContaining("ck_prova_ano");
	}

	@ParameterizedTest(name = "semestre {0} recusado")
	@CsvSource({ "0", "3", "-1" })
	void deve_recusar_no_banco_um_semestre_que_nao_e_1_nem_2(int semestre) {
		assertThatThrownBy(() -> inserirNoBanco("Semestre ruim", null, null, semestre, 60, 10, "RASCUNHO", null))
				.isInstanceOf(DataAccessException.class).hasMessageContaining("ck_prova_semestre");
	}

	@ParameterizedTest(name = "duração {0} recusada")
	@CsvSource({ "0", "601", "-1" })
	void deve_recusar_no_banco_uma_duracao_fora_de_1_a_600(int duracao) {
		assertThatThrownBy(() -> inserirNoBanco("Duração ruim", null, null, null, duracao, 10, "RASCUNHO", null))
				.isInstanceOf(DataAccessException.class).hasMessageContaining("ck_prova_duracao_minutos");
	}

	@ParameterizedTest(name = "quantidade {0} recusada")
	@CsvSource({ "0", "301", "1000", "-1" })
	void deve_recusar_no_banco_uma_quantidade_de_questoes_fora_de_1_a_300(int quantidade) {
		assertThatThrownBy(() -> inserirNoBanco("Quantidade ruim", null, null, null, 60, quantidade, "RASCUNHO", null))
				.isInstanceOf(DataAccessException.class).hasMessageContaining("ck_prova_quantidade_questoes");
	}

	@Test
	void deve_aceitar_no_banco_os_limites_de_cada_check() {
		assertThat(inserirNoBanco("Mínimos", null, 2000, 1, 1, 1, "RASCUNHO", null)).isEqualTo(1);
		assertThat(inserirNoBanco("Máximos", null, 2100, 2, 600, 300, "RASCUNHO", null)).isEqualTo(1);
	}

	@Test
	void deve_recusar_no_banco_um_status_desconhecido_inclusive_com_caixa_diferente() {
		for (String status : new String[] { "ARQUIVADA", "OUTRO", "", "rascunho", "Rascunho", "publicada" }) {
			// publicada_em nulo: assim o ck_prova_publicada_em passa e SÓ o ck_prova_status pode ser o motivo da recusa
			assertThatThrownBy(() -> inserirNoBanco("Status ruim", null, null, null, 60, 10, status, null))
					.as("status '%s'", status).isInstanceOf(DataAccessException.class)
					.hasMessageContaining("ck_prova_status");
		}
	}

	@Test
	void deve_recusar_no_banco_status_publicada_sem_publicada_em_e_rascunho_com_publicada_em() {
		assertThatThrownBy(() -> inserirNoBanco("Publicada sem data", null, null, null, 60, 10, "PUBLICADA", null))
				.isInstanceOf(DataAccessException.class).hasMessageContaining("ck_prova_publicada_em");
		assertThatThrownBy(
				() -> inserirNoBanco("Rascunho com data", null, null, null, 60, 10, "RASCUNHO", "2026-01-01 10:00:00.000"))
				.isInstanceOf(DataAccessException.class).hasMessageContaining("ck_prova_publicada_em");
	}

	@Test
	void deve_aceitar_no_banco_as_duas_combinacoes_validas_de_status_e_publicada_em() {
		assertThat(inserirNoBanco("Rascunho válido", null, null, null, 60, 10, "RASCUNHO", null)).isEqualTo(1);
		assertThat(inserirNoBanco("Publicada válida", null, null, null, 60, 10, "PUBLICADA", "2026-01-01 10:00:00.000"))
				.isEqualTo(1);
	}

	// ----- unicidade (D150) -----

	@Test
	void deve_recusar_no_banco_duas_provas_com_a_mesma_banca_ano_e_semestre() {
		String banca = bancaUnica();
		assertThat(inserirNoBanco("Primeira", banca, 2040, 1, 60, 10, "RASCUNHO", null)).isEqualTo(1);

		assertThatThrownBy(() -> inserirNoBanco("Segunda", banca, 2040, 1, 60, 10, "RASCUNHO", null))
				.isInstanceOf(DataAccessException.class).hasMessageContaining("uk_prova_programa_banca_ano_semestre");
		// a collation ignora caixa e acento, como na matéria
		assertThatThrownBy(() -> inserirNoBanco("Terceira", banca.toUpperCase(), 2040, 1, 60, 10, "RASCUNHO", null))
				.isInstanceOf(DataAccessException.class).hasMessageContaining("uk_prova_programa_banca_ano_semestre");
	}

	@Test
	void deve_aceitar_no_banco_provas_que_diferem_em_banca_ano_ou_semestre() {
		String banca = bancaUnica();
		assertThat(inserirNoBanco("Base", banca, 2041, 1, 60, 10, "RASCUNHO", null)).isEqualTo(1);

		assertThat(inserirNoBanco("Outro semestre", banca, 2041, 2, 60, 10, "RASCUNHO", null)).isEqualTo(1);
		assertThat(inserirNoBanco("Outro ano", banca, 2042, 1, 60, 10, "RASCUNHO", null)).isEqualTo(1);
		assertThat(inserirNoBanco("Outra banca", bancaUnica(), 2041, 1, 60, 10, "RASCUNHO", null)).isEqualTo(1);
	}

	@Test
	void deve_aceitar_no_banco_varias_provas_com_banca_ano_ou_semestre_nulos() {
		String banca = bancaUnica();

		// NULL não conflita num índice único do MySQL: toda combinação parcial convive (D150)
		for (int i = 0; i < 2; i++) {
			assertThat(inserirNoBanco("Sem nada " + i, null, null, null, 60, 10, "RASCUNHO", null)).isEqualTo(1);
			assertThat(inserirNoBanco("Só banca " + i, banca, null, null, 60, 10, "RASCUNHO", null)).isEqualTo(1);
			assertThat(inserirNoBanco("Banca e ano " + i, banca, 2043, null, 60, 10, "RASCUNHO", null)).isEqualTo(1);
			assertThat(inserirNoBanco("Banca e semestre " + i, banca, null, 1, 60, 10, "RASCUNHO", null)).isEqualTo(1);
			assertThat(inserirNoBanco("Ano e semestre " + i, null, 2043, 1, 60, 10, "RASCUNHO", null)).isEqualTo(1);
		}
	}

	@Test
	void deve_criar_duas_provas_sem_banca_com_o_mesmo_ano_e_semestre_pela_api() {
		criar(corpo("Sem banca A", null, 2044, 1)).then().statusCode(201);
		criar(corpo("Sem banca B", null, 2044, 1)).then().statusCode(201);
		criar(corpo("Sem banca C", "   ", 2044, 1)).then().statusCode(201);
	}

	@Test
	void deve_aceitar_pela_api_as_combinacoes_parciais_repetidas() {
		String banca = bancaUnica();
		for (int i = 0; i < 2; i++) {
			criar(corpo("Só banca " + i, banca, null, null)).then().statusCode(201);
			criar(corpo("Banca e ano " + i, banca, 2045, null)).then().statusCode(201);
			criar(corpo("Banca e semestre " + i, banca, null, 2)).then().statusCode(201);
			criar(corpo("Sem nada " + i, null, null, null)).then().statusCode(201);
		}
	}

	@Test
	void deve_devolver_409_prova_duplicada_quando_a_tripla_se_repete_pela_api() {
		String banca = bancaUnica();
		criarEObterId(corpo("Original", banca, 2046, 1));

		Response repetida = criar(corpo("Outro título, mesma tripla", banca, 2046, 1));

		verificarErro(repetida, 409, "Conflict", "PROVA_DUPLICADA");
		repetida.then().body("mensagem", equalTo("Já existe uma prova com essa banca, ano e semestre."));
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM prova WHERE banca = ? AND ano = 2046", Integer.class, banca))
				.isEqualTo(1);
	}

	@Test
	void deve_tratar_banca_com_outra_caixa_ou_acento_ou_espacos_nas_pontas_como_a_mesma_banca() {
		String banca = "Fundação " + sufixo();
		criarEObterId(corpo("Original", banca, 2047, 2));

		verificarErro(criar(corpo("Caixa", banca.toUpperCase(), 2047, 2)), 409, "Conflict", "PROVA_DUPLICADA");
		verificarErro(criar(corpo("Acento", banca.replace("ç", "c").replace("ã", "a"), 2047, 2)), 409, "Conflict",
				"PROVA_DUPLICADA");
		verificarErro(criar(corpo("Espaços", "   " + banca + "   ", 2047, 2)), 409, "Conflict", "PROVA_DUPLICADA");
	}

	@Test
	void deve_aceitar_a_mesma_banca_em_outro_ano_ou_semestre_pela_api() {
		String banca = bancaUnica();
		criarEObterId(corpo("Base", banca, 2048, 1));

		criar(corpo("Outro semestre", banca, 2048, 2)).then().statusCode(201);
		criar(corpo("Outro ano", banca, 2049, 1)).then().statusCode(201);
	}

	@Test
	void deve_validar_antes_de_checar_o_conflito_quando_o_corpo_e_invalido_e_repetido() {
		String banca = bancaUnica();
		criarEObterId(corpo("Original", banca, 2050, 1));

		// mesma tripla, mas com quantidade inválida: 400 (corpo) vem antes do 409 (conflito)
		verificarErro(criar(json("Repetida", banca, 2050, 1, 180, 301)), 400, "Bad Request", "VALIDACAO_FALHOU");
	}

	// ----- limites pela API (o teto de 300 vale no código e no banco) -----

	@Test
	void deve_criar_com_300_questoes_e_recusar_com_301_pela_api() {
		criar(json("No teto", null, null, null, 180, 300)).then().statusCode(201).body("quantidadeQuestoes", equalTo(300));
		verificarErro(criar(json("Acima do teto", null, null, null, 180, 301)), 400, "Bad Request", "VALIDACAO_FALHOU");
	}

	// ----- o que a API grava de fato -----

	@Test
	void deve_gravar_rascunho_sem_publicada_em_e_com_datas_em_utc_quando_cria() {
		String id = criarEObterId(corpo("Gravada", bancaUnica(), 2051, 1));

		Map<String, Object> linha = jdbc.queryForMap("SELECT status, publicada_em, criado_em, atualizado_em, programa_id "
				+ "FROM prova WHERE public_id = UUID_TO_BIN(?)", id);

		assertThat(linha.get("STATUS")).isEqualTo("RASCUNHO");
		assertThat(linha.get("PUBLICADA_EM")).isNull();
		assertThat(linha.get("CRIADO_EM")).isNotNull();
		assertThat(linha.get("ATUALIZADO_EM")).isNotNull();
		Long etec = jdbc.queryForObject("SELECT id FROM programa WHERE codigo = 'ETEC'", Long.class);
		assertThat(((Number) linha.get("PROGRAMA_ID")).longValue()).as("a prova nasce no programa ETEC").isEqualTo(etec);
	}

	// ----- ordenação e os nulos do ano -----

	/** Anos na ordem devolvida, com {@code null} para as provas sem ano. */
	private static List<Integer> anos(List<Map<String, Object>> provas) {
		List<Integer> anos = new ArrayList<>();
		for (Map<String, Object> prova : provas) {
			anos.add((Integer) prova.get("ano"));
		}
		return anos;
	}

	private static List<String> titulos(List<Map<String, Object>> provas) {
		return provas.stream().map(prova -> (String) prova.get("titulo")).toList();
	}

	private void criarProvasParaOrdenar(String marca) {
		criarEObterId(corpo(marca + " nula A", null, null, null));
		criarEObterId(corpo(marca + " 2061", bancaUnica(), 2061, 1));
		criarEObterId(corpo(marca + " nula B", null, null, null));
		criarEObterId(corpo(marca + " 2063", bancaUnica(), 2063, 1));
		criarEObterId(corpo(marca + " 2062", bancaUnica(), 2062, 2));
	}

	@Test
	void deve_ordenar_por_ano_decrescente_com_os_anos_nulos_no_fim_quando_nao_ha_sort() {
		String marca = "Padrão " + sufixo();
		criarProvasParaOrdenar(marca);

		List<Integer> anos = anos(listarTodas(null));

		// padrão descendente: os anos preenchidos vêm primeiro, do maior para o menor, e os nulos no FIM (MySQL)
		int primeiroNulo = anos.indexOf(null);
		assertThat(primeiroNulo).as("há provas sem ano").isGreaterThanOrEqualTo(0);
		assertThat(anos.subList(0, primeiroNulo)).isSortedAccordingTo(java.util.Comparator.reverseOrder());
		assertThat(anos.subList(primeiroNulo, anos.size())).containsOnlyNulls();
		assertThat(anos.indexOf(2063)).isLessThan(anos.indexOf(2062));
	}

	@Test
	void deve_ordenar_por_ano_crescente_com_os_anos_nulos_no_inicio_quando_o_sort_nao_tem_direcao() {
		String marca = "Asc " + sufixo();
		criarProvasParaOrdenar(marca);

		for (String sort : new String[] { "ano", "ano,asc", "ano,ASC" }) {
			List<Integer> anos = anos(listarTodas(sort));

			// ascendente: os nulos (o menor valor) vêm no INÍCIO, depois os anos do menor para o maior (D102)
			int ultimoNulo = anos.lastIndexOf(null);
			assertThat(ultimoNulo).as("sort=%s: há provas sem ano", sort).isGreaterThanOrEqualTo(0);
			assertThat(anos.subList(0, ultimoNulo + 1)).as("sort=%s", sort).containsOnlyNulls();
			assertThat(anos.subList(ultimoNulo + 1, anos.size())).as("sort=%s", sort).isSorted();
		}
	}

	@Test
	void deve_desempatar_pelo_id_interno_na_ordem_de_criacao_nas_duas_direcoes() {
		String marca = "Desempate " + sufixo();
		criarProvasParaOrdenar(marca);

		for (String sort : new String[] { null, "ano,asc", "ano,desc", "semestre" }) {
			List<String> titulos = titulos(listarTodas(sort));
			assertThat(titulos.indexOf(marca + " nula A")).as("sort=%s", sort)
					.isLessThan(titulos.indexOf(marca + " nula B"));
		}
	}

	@Test
	void deve_ordenar_por_semestre_com_os_nulos_primeiro_na_ordem_crescente() {
		criarProvasParaOrdenar("Semestre " + sufixo());

		List<Map<String, Object>> provas = listarTodas("semestre,asc");

		List<Integer> semestres = new ArrayList<>();
		for (Map<String, Object> prova : provas) {
			semestres.add((Integer) prova.get("semestre"));
		}
		int ultimoNulo = semestres.lastIndexOf(null);
		assertThat(semestres.subList(0, ultimoNulo + 1)).containsOnlyNulls();
		assertThat(semestres.subList(ultimoNulo + 1, semestres.size())).isSorted();
	}

	@Test
	void deve_nao_repetir_nem_perder_provas_ao_paginar_com_tamanho_pequeno() {
		criarProvasParaOrdenar("Paginação " + sufixo());
		int total = requisicao().get(BASE).path("totalElementos");

		List<String> ids = new ArrayList<>();
		int pagina = 0;
		List<?> conteudo;
		do {
			conteudo = requisicao().queryParam("size", 7).queryParam("page", pagina++).get(BASE).path("conteudo");
			for (Object prova : conteudo) {
				ids.add((String) ((Map<?, ?>) prova).get("id"));
			}
		}
		while (!conteudo.isEmpty());

		assertThat(ids).hasSize(total).doesNotHaveDuplicates();
	}

	// ----- filtros -----

	@Test
	void deve_filtrar_por_ano_e_semestre_e_devolver_so_o_que_casa() {
		String banca = bancaUnica();
		criarEObterId(corpo("Casa A", banca, 2071, 1));
		criarEObterId(corpo("Casa B", bancaUnica(), 2071, 1));
		criarEObterId(corpo("Outro semestre", banca, 2071, 2));
		criarEObterId(corpo("Outro ano", banca, 2072, 1));

		Response resposta = requisicao().queryParam("ano", 2071).queryParam("semestre", 1).get(BASE);

		resposta.then().statusCode(200).body("totalElementos", equalTo(2));
		assertThat(titulos(resposta.path("conteudo"))).containsExactlyInAnyOrder("Casa A", "Casa B");
		requisicao().queryParam("ano", 2071).get(BASE).then().body("totalElementos", equalTo(3));
		requisicao().queryParam("ano", 2073).get(BASE).then().body("totalElementos", equalTo(0));
	}

	@Test
	void deve_filtrar_por_status_incluindo_a_prova_publicada_inserida_no_banco() {
		String marca = "Status " + sufixo();
		inserirNoBanco(marca + " publicada", null, 2074, 1, 60, 10, "PUBLICADA", "2026-02-01 08:00:00.000");
		criarEObterId(corpo(marca + " rascunho", null, 2074, 1));

		List<String> publicadas = titulos(requisicao().queryParam("ano", 2074).queryParam("status", "PUBLICADA").get(BASE)
				.path("conteudo"));
		List<String> rascunhos = titulos(requisicao().queryParam("ano", 2074).queryParam("status", "RASCUNHO").get(BASE)
				.path("conteudo"));

		assertThat(publicadas).contains(marca + " publicada").doesNotContain(marca + " rascunho");
		assertThat(rascunhos).contains(marca + " rascunho").doesNotContain(marca + " publicada");
		Response publicada = requisicao().queryParam("ano", 2074).queryParam("status", "PUBLICADA").get(BASE);
		publicada.then().body("conteudo.find { it.titulo == '" + marca + " publicada' }.publicadaEm",
				matchesPattern(DATA_HORA));
	}

	// ----- fluxo ponta a ponta: criar, listar, detalhar -----

	@Test
	void deve_criar_listar_e_detalhar_a_mesma_prova_de_ponta_a_ponta() {
		String banca = bancaUnica();
		Response criada = requisicao().body("{\"titulo\":\"  Vestibulinho do fluxo  \",\"banca\":\"" + banca
				+ "\",\"ano\":2081,\"semestre\":2,\"duracaoMinutos\":180,\"quantidadeQuestoes\":50,"
				+ "\"fonteUrl\":\"https://exemplo.invalid/prova.pdf\"}").post(CRIAR);
		criada.then().statusCode(201)
				.header("Location", matchesPattern("/api/v1/provas/[0-9a-f-]{36}"))
				.body("titulo", equalTo("Vestibulinho do fluxo"))
				.body("status", equalTo("RASCUNHO"))
				.body("publicadaEm", org.hamcrest.Matchers.nullValue())
				.body("criadoEm", matchesPattern(DATA_HORA));
		String id = criada.path("id");
		assertThat(criada.getHeader("Location")).isEqualTo("/api/v1/provas/" + id);
		assertThat(criada.asString()).doesNotContain("programa");

		Response lista = requisicao().queryParam("ano", 2081).get(BASE);
		lista.then().statusCode(200).body("totalElementos", equalTo(1)).body("conteudo[0].id", equalTo(id));
		assertThat(lista.asString()).doesNotContain("programa");

		Response detalhe = requisicao().get(criada.getHeader("Location"));
		detalhe.then().statusCode(200)
				.body("id", equalTo(id))
				.body("titulo", equalTo("Vestibulinho do fluxo"))
				.body("banca", equalTo(banca))
				.body("ano", equalTo(2081))
				.body("semestre", equalTo(2))
				.body("duracaoMinutos", equalTo(180))
				.body("quantidadeQuestoes", equalTo(50))
				.body("fonteUrl", equalTo("https://exemplo.invalid/prova.pdf"))
				.body("status", equalTo("RASCUNHO"))
				.body("publicadaEm", org.hamcrest.Matchers.nullValue())
				.body("criadoEm", equalTo(criada.path("criadoEm")));
		assertThat(detalhe.asString()).doesNotContain("programa");
	}

	@Test
	void deve_devolver_404_prova_nao_encontrada_e_400_de_id_malformado_pela_api() {
		verificarErro(buscar(java.util.UUID.randomUUID().toString()), 404, "Not Found", "PROVA_NAO_ENCONTRADA");
		verificarErro(buscar("abc"), 400, "Bad Request", "PARAMETRO_INVALIDO");
	}

}
