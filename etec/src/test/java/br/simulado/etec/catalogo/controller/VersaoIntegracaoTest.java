package br.simulado.etec.catalogo.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import io.restassured.response.Response;

/** Nova versão e publicação avulsa de questão (D181 a D183) contra MySQL REAL: a troca da publicada e o que não pode mudar. */
class VersaoIntegracaoTest extends AbstractPublicacaoIntegrationTest {

	private static final String COLUNAS_DO_CONTEUDO = "subtopico_id, texto_apoio, grupo_apoio, enunciado, imagem_url, gabarito, "
			+ "dificuldade, explicacao";

	private static final String IMAGEM = "https://imagens.exemplo.invalid/q2.png";

	/** Uma questão com TODOS os campos opcionais preenchidos, para a cópia não perder nenhum. */
	private static String corpoCompleto(String subtopico, int numero) {
		return "{\"numero\":" + numero + ",\"subtopicoId\":\"" + subtopico + "\",\"textoApoio\":\"Leia o <b>texto</b>.\","
				+ "\"grupoApoio\":3,\"enunciado\":\"Enunciado completo\",\"imagemUrl\":\"" + IMAGEM + "\",\"alternativas\":"
				+ alternativas("alfa", "beta", "gama", "delta", "épsilon")
				+ ",\"gabarito\":\"D\",\"dificuldade\":\"MEDIA\",\"explicacao\":\"Porque sim.\"}";
	}

	/** [prova publicada, questão 1 (simples), questão 2 (completa)]. */
	private List<String> provaPublicadaComUmaQuestaoCompleta(String subtopico) {
		String prova = novaProva(2);
		String um = cadastrarPronta(prova, subtopico, 1);
		String dois = cadastrar(prova, corpoCompleto(subtopico, 2)).then().statusCode(201).extract().path("id");
		publicar(prova).then().statusCode(200);
		return List.of(prova, um, dois);
	}

	private Map<String, Object> identidade(String questao) {
		return jdbc.queryForMap("SELECT * FROM questao WHERE id = ?", idInternoDaQuestao(questao));
	}

	private Map<String, Object> linhaDaProva(String prova) {
		return jdbc.queryForMap("SELECT * FROM prova WHERE id = ?", idInternoDaProva(prova));
	}

	private static Map<String, Object> semVersaoNemStatus(Response resposta) {
		Map<String, Object> corpo = new java.util.LinkedHashMap<>(resposta.jsonPath().getMap("$"));
		corpo.keySet().removeAll(List.of("versao", "status"));
		return corpo;
	}

	// ----- nova versão (D181) -----

	@Test
	void deve_criar_a_versao_2_como_copia_completa_da_publicada_sem_tocar_nela() {
		List<String> ids = provaPublicadaComUmaQuestaoCompleta(criarSubtopico());
		String prova = ids.get(0);
		String questao = ids.get(2);
		List<Map<String, Object>> publicada = fotoDaVersao(questao, 1);
		Map<String, Object> identidade = identidade(questao);
		Map<String, Object> provaAntes = linhaDaProva(prova);

		Response criada = novaVersao(questao);

		criada.then().statusCode(201).header("Location", matchesPattern(".*/api/v1/admin/questoes/" + questao + "$"))
				.body("id", equalTo(questao)).body("versao", equalTo(2)).body("status", equalTo("RASCUNHO"))
				.body("numero", equalTo(2)).body("provaId", equalTo(prova)).body("gabarito", equalTo("D"))
				.body("alternativas", hasSize(5));
		assertThat(semVersaoNemStatus(criada)).isEqualTo(semVersaoNemStatus(lerVersao(questao, 1)));
		assertThat(jdbc.queryForMap("SELECT " + COLUNAS_DO_CONTEUDO + " FROM questao_versao WHERE questao_id = ? AND versao = 2",
				idInternoDaQuestao(questao))).isEqualTo(jdbc.queryForMap("SELECT " + COLUNAS_DO_CONTEUDO
						+ " FROM questao_versao WHERE questao_id = ? AND versao = 1", idInternoDaQuestao(questao)))
				.doesNotContainValue(null);
		assertThat(jdbc.queryForObject("SELECT publicada_em FROM questao_versao WHERE questao_id = ? AND versao = 2",
				java.sql.Timestamp.class, idInternoDaQuestao(questao))).isNull();
		// as alternativas são linhas NOVAS, com o mesmo par letra e texto
		List<Map<String, Object>> copia = fotoDaVersao(questao, 2);
		assertThat(alternativasDe(copia)).isEqualTo(alternativasDe(publicada));
		Set<Object> idsDasDuas = new HashSet<>();
		publicada.stream().skip(1).forEach(linha -> idsDasDuas.add(linha.get("id")));
		copia.stream().skip(1).forEach(linha -> idsDasDuas.add(linha.get("id")));
		assertThat(idsDasDuas).hasSize(10);

		assertThat(fotoDaVersao(questao, 1)).as("a publicada não muda").isEqualTo(publicada);
		assertThat(identidade(questao)).as("numero, prova, hash e id").usingRecursiveComparison().isEqualTo(identidade);
		assertThat(linhaDaProva(prova)).as("a prova não muda").usingRecursiveComparison().isEqualTo(provaAntes);
		assertThat(historico(questao)).containsExactly("1 PUBLICADA", "2 RASCUNHO");
	}

	private static List<String> alternativasDe(List<Map<String, Object>> foto) {
		return foto.stream().skip(1).map(linha -> linha.get("letra") + " " + linha.get("texto")).toList();
	}

	/** Ponta a ponta: prova publicada, nova versão, PUT no rascunho, publicar avulsa. */
	@Test
	void deve_publicar_a_versao_2_arquivando_a_1_com_exatamente_uma_publicada_por_questao() {
		String subtopico = criarSubtopico();
		List<String> ids = provaPublicadaComUmaQuestaoCompleta(subtopico);
		String prova = ids.get(0);
		String questao = ids.get(2);
		List<Map<String, Object>> versao1 = conteudoDaVersao(questao, 1);
		Map<String, Object> identidade = identidade(questao);
		Map<String, Object> provaAntes = linhaDaProva(prova);
		List<Map<String, Object>> vizinha = fotoDaVersao(ids.get(1), 1);

		novaVersao(questao).then().statusCode(201);
		verificarErro(gabarito(prova, item(2, "E")), 409, "Conflict", "PROVA_NAO_EDITAVEL");
		editarQuestao(questao, corpoDoPut(subtopico, "Enunciado corrigido", "\"gabarito\":\"B\"")).then().statusCode(200)
				.body("versao", equalTo(2)).body("status", equalTo("RASCUNHO"));
		assertThat(conteudoDaVersao(questao, 1)).as("o PUT é no rascunho").isEqualTo(versao1);

		publicarQuestao(questao).then().statusCode(200).body("id", equalTo(questao)).body("versao", equalTo(2))
				.body("status", equalTo("PUBLICADA")).body("enunciado", equalTo("Enunciado corrigido"))
				.body("gabarito", equalTo("B")).body("alternativas", hasSize(5));

		assertThat(historico(questao)).containsExactly("1 ARQUIVADA", "2 PUBLICADA");
		assertThat(versoesPorStatus(prova)).containsExactly("ARQUIVADA=1", "PUBLICADA=2");
		assertThat(jdbc.queryForList("SELECT COUNT(*) FROM questao_versao v JOIN questao q ON q.id = v.questao_id "
				+ "WHERE q.prova_id = ? AND v.status = 'PUBLICADA' GROUP BY q.id", Integer.class, idInternoDaProva(prova)))
				.containsExactly(1, 1);
		assertThat(conteudoDaVersao(questao, 1)).as("a arquivada guarda o conteúdo e a data dela").isEqualTo(versao1);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM questao_versao a JOIN questao_versao n ON n.questao_id = "
				+ "a.questao_id AND n.versao = 2 WHERE a.questao_id = ? AND a.versao = 1 AND n.publicada_em > a.publicada_em",
				Integer.class, idInternoDaQuestao(questao))).as("a nova foi publicada depois").isEqualTo(1);
		assertThat(identidade(questao)).usingRecursiveComparison().isEqualTo(identidade);
		assertThat(linhaDaProva(prova)).usingRecursiveComparison().isEqualTo(provaAntes);
		assertThat(fotoDaVersao(ids.get(1), 1)).as("a outra questão não é tocada").isEqualTo(vizinha);
		lerVersao(questao, 1).then().statusCode(200).body("versao", equalTo(1)).body("status", equalTo("ARQUIVADA"))
				.body("enunciado", equalTo("Enunciado completo")).body("gabarito", equalTo("D"))
				.body("imagemUrl", equalTo(IMAGEM)).body("alternativas[4].texto", equalTo("épsilon"));
		buscarQuestao(questao).then().body("versao", equalTo(2)).body("status", equalTo("PUBLICADA"));
	}

	/** A Tentativa vai referenciar a versão pelo id: depois de arquivada, nada nela muda, nem o atualizado_em (D183). */
	@Test
	void deve_deixar_a_versao_arquivada_identica_nos_ciclos_seguintes() {
		String subtopico = criarSubtopico();
		String questao = questaoPublicada(subtopico).get(1);
		Map<String, Object> identidade = identidade(questao);
		novaVersao(questao).then().statusCode(201);
		// a versão 2 muda o conteúdo: a 3 é copiada de um conteúdo que já não é o que deu o hash da identidade
		editarQuestao(questao, corpoDoPut(subtopico, "Segunda", "\"gabarito\":\"B\"")).then().statusCode(200);
		publicarQuestao(questao).then().statusCode(200);
		List<Map<String, Object>> arquivada = fotoDaVersao(questao, 1);
		List<Map<String, Object>> segunda = conteudoDaVersao(questao, 2);

		novaVersao(questao).then().statusCode(201).body("versao", equalTo(3)).body("enunciado", equalTo("Segunda"));
		assertThat(identidade(questao)).as("numero e hash não mudam com a versão nova").usingRecursiveComparison()
				.isEqualTo(identidade);
		editarQuestao(questao, corpoDoPut(subtopico, "Terceira", "\"gabarito\":\"C\"")).then().statusCode(200);
		publicarQuestao(questao).then().statusCode(200).body("versao", equalTo(3));

		assertThat(historico(questao)).containsExactly("1 ARQUIVADA", "2 ARQUIVADA", "3 PUBLICADA");
		assertThat(fotoDaVersao(questao, 1)).isEqualTo(arquivada);
		assertThat(conteudoDaVersao(questao, 2)).isEqualTo(segunda);
	}

	/**
	 * Pela API a publicada é sempre a maior versão quando não há rascunho. Uma ARQUIVADA mais alta, montada à mão, mostra que
	 * o numero novo sai da maior que existe, e não da publicada.
	 */
	@Test
	void deve_numerar_a_versao_nova_a_partir_da_maior_que_existe() {
		String subtopico = criarSubtopico();
		String questao = questaoPublicada(subtopico).get(1);
		inserirVersao(idInternoDaQuestao(questao), 7, "ARQUIVADA", idInternoDoSubtopico(subtopico), "Antiga");

		novaVersao(questao).then().statusCode(201).body("versao", equalTo(8)).body("enunciado", equalTo("Questão 1"));
	}

	@ParameterizedTest
	@ValueSource(strings = { "subtopicos", "materias" })
	void deve_copiar_o_subtopico_desativado_e_so_barrar_na_publicacao(String oQueDesativar) {
		String subtopico = criarSubtopico();
		String questao = questaoPublicada(subtopico).get(1);
		String alvo = oQueDesativar.equals("subtopicos") ? subtopico
				: jdbc.queryForObject("SELECT BIN_TO_UUID(m.public_id) FROM materia m JOIN subtopico s ON s.materia_id = m.id "
						+ "WHERE s.id = ?", String.class, idInternoDoSubtopico(subtopico));
		requisicao().patch("/api/v1/" + oQueDesativar + "/" + alvo + "/disable").then().statusCode(200);

		novaVersao(questao).then().statusCode(201).body("subtopicoId", equalTo(subtopico));
		List<Map<String, Object>> publicada = fotoDaVersao(questao, 1);
		List<Map<String, Object>> rascunho = fotoDaVersao(questao, 2);

		assertThat(pendencias(publicarQuestao(questao))).containsExactly("1 SUBTOPICO_INATIVO");

		assertThat(historico(questao)).containsExactly("1 PUBLICADA", "2 RASCUNHO");
		assertThat(fotoDaVersao(questao, 1)).isEqualTo(publicada);
		assertThat(fotoDaVersao(questao, 2)).isEqualTo(rascunho);

		requisicao().patch("/api/v1/" + oQueDesativar + "/" + alvo + "/enable").then().statusCode(200);
		publicarQuestao(questao).then().statusCode(200);
		assertThat(historico(questao)).containsExactly("1 ARQUIVADA", "2 PUBLICADA");
	}

	// ----- publicação avulsa: conferências e atomicidade (D182) -----

	/** Uma conferência por vez: a versão antiga continua PUBLICADA e o rascunho continua aberto, sem mudar nenhuma linha. */
	@ParameterizedTest
	@CsvSource({ "SEM_GABARITO", "SUBTOPICO_INATIVO", "IMAGEM_NAO_PERMITIDA" })
	void deve_recusar_a_publicacao_avulsa_com_a_pendencia_e_nao_mudar_nenhuma_linha(String motivo) {
		String subtopico = criarSubtopico();
		List<String> ids = questaoPublicada(subtopico);
		String questao = ids.get(1);
		novaVersao(questao).then().statusCode(201);
		switch (motivo) {
			case "SEM_GABARITO" -> editarQuestao(questao, corpoDoPut(subtopico, "Sem gabarito", "")).then().statusCode(200);
			case "SUBTOPICO_INATIVO" -> requisicao().patch("/api/v1/subtopicos/" + subtopico + "/disable").then().statusCode(200);
			// a entrada recusaria o host; no banco, é o caso de um host que saiu da lista depois do cadastro
			default -> trocarImagemDoRascunho(questao, "https://saiu-da-lista.exemplo.invalid/a.png");
		}
		List<Object> antes = fotoDaProva(ids.get(0));

		assertThat(pendencias(publicarQuestao(questao))).containsExactly("1 " + motivo);

		assertThat(historico(questao)).containsExactly("1 PUBLICADA", "2 RASCUNHO");
		assertThat(fotoDaProva(ids.get(0))).usingRecursiveComparison().isEqualTo(antes);
	}

	@Test
	void deve_juntar_todas_as_pendencias_da_questao_num_so_409_com_o_numero_dela() {
		String subtopico = criarSubtopico();
		List<String> ids = provaPublicadaComUmaQuestaoCompleta(subtopico);
		String questao = ids.get(2);
		novaVersao(questao).then().statusCode(201);
		editarQuestao(questao, corpoDoPut(subtopico, "Sem gabarito", "")).then().statusCode(200);
		trocarImagemDoRascunho(questao, "https://saiu-da-lista.exemplo.invalid/a.png");
		requisicao().patch("/api/v1/subtopicos/" + subtopico + "/disable").then().statusCode(200);

		assertThat(pendencias(publicarQuestao(questao))).containsExactly("2 SEM_GABARITO", "2 SUBTOPICO_INATIVO",
				"2 IMAGEM_NAO_PERMITIDA");
		assertThat(historico(questao)).containsExactly("1 PUBLICADA", "2 RASCUNHO");
	}

	private void trocarImagemDoRascunho(String questao, String url) {
		assertThat(jdbc.update("UPDATE questao_versao SET imagem_url = ? WHERE questao_id = ? AND status = 'RASCUNHO'", url,
				idInternoDaQuestao(questao))).isEqualTo(1);
	}

	// ----- recusas de estado -----

	/**
	 * Uma falha do banco ENTRE o arquivar e o publicar, provocada por um gatilho temporário que recusa só a publicação deste
	 * rascunho: o arquivamento já foi mandado ao banco e precisa se desfazer junto (uma transação só).
	 */
	@Test
	void deve_desfazer_o_arquivamento_quando_publicar_o_rascunho_falha_no_banco() throws Exception {
		List<String> ids = questaoPublicada(criarSubtopico());
		String questao = ids.get(1);
		novaVersao(questao).then().statusCode(201);
		List<Object> antes = fotoDaProva(ids.get(0));
		String gatilho = "recusar_publicacao_" + sufixo();
		comoRoot("CREATE TRIGGER " + gatilho + " BEFORE UPDATE ON questao_versao FOR EACH ROW BEGIN IF NEW.questao_id = "
				+ idInternoDaQuestao(questao) + " AND OLD.status = 'RASCUNHO' AND NEW.status = 'PUBLICADA' THEN "
				+ "SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'falha provocada pelo teste'; END IF; END");
		try {
			verificarErro(publicarQuestao(questao), 500, "Internal Server Error", "ERRO_INTERNO");
		}
		finally {
			comoRoot("DROP TRIGGER " + gatilho);
		}

		assertThat(historico(questao)).containsExactly("1 PUBLICADA", "2 RASCUNHO");
		assertThat(fotoDaProva(ids.get(0))).usingRecursiveComparison().isEqualTo(antes);
		publicarQuestao(questao).then().statusCode(200);
		assertThat(historico(questao)).containsExactly("1 ARQUIVADA", "2 PUBLICADA");
	}

	private static void comoRoot(String sql) throws SQLException {
		try (Connection root = DriverManager.getConnection(MYSQL.getJdbcUrl(), "root", MYSQL.getPassword());
				Statement comando = root.createStatement()) {
			comando.execute(sql);
		}
	}

	@Test
	void deve_recusar_publicar_avulsa_numa_prova_em_rascunho_mesmo_com_tudo_pronto() {
		String prova = novaProva(2);
		String questao = cadastrarPronta(prova, criarSubtopico(), 1);
		List<Object> antes = fotoDaProva(prova);

		verificarErro(publicarQuestao(questao), 409, "Conflict", "PROVA_NAO_PUBLICADA");
		assertThat(fotoDaProva(prova)).usingRecursiveComparison().isEqualTo(antes);

		// nem com uma publicada e um rascunho montados à mão, estado em que a troca em si funcionaria
		publicarNoBanco(questao);
		abrirRascunhoNoBanco(questao);
		verificarErro(publicarQuestao(questao), 409, "Conflict", "PROVA_NAO_PUBLICADA");
		assertThat(historico(questao)).containsExactly("1 PUBLICADA", "2 RASCUNHO");
	}

	@Test
	void deve_dar_409_ao_publicar_sem_rascunho_e_ao_abrir_versao_com_rascunho_aberto() {
		String questao = questaoPublicada(criarSubtopico()).get(1);
		List<Map<String, Object>> publicada = fotoDaVersao(questao, 1);

		verificarErro(publicarQuestao(questao), 409, "Conflict", "QUESTAO_SEM_RASCUNHO");
		assertThat(fotoDaVersao(questao, 1)).isEqualTo(publicada);

		novaVersao(questao).then().statusCode(201);
		verificarErro(novaVersao(questao), 409, "Conflict", "QUESTAO_COM_RASCUNHO");
		assertThat(historico(questao)).containsExactly("1 PUBLICADA", "2 RASCUNHO");

		publicarQuestao(questao).then().statusCode(200);
		verificarErro(publicarQuestao(questao), 409, "Conflict", "QUESTAO_SEM_RASCUNHO");
		assertThat(historico(questao)).containsExactly("1 ARQUIVADA", "2 PUBLICADA");
	}

	@Test
	void deve_dar_409_ao_abrir_versao_de_questao_nunca_publicada() {
		String questao = cadastrarPronta(novaProva(1), criarSubtopico(), 1);

		verificarErro(novaVersao(questao), 409, "Conflict", "QUESTAO_COM_RASCUNHO");
		assertThat(historico(questao)).containsExactly("1 RASCUNHO");
	}

	/** Defensivo: a API não deixa uma questão sem rascunho e sem publicada. */
	@Test
	void deve_dar_409_conflito_quando_nao_ha_publicada_para_copiar() {
		String questao = questaoPublicada(criarSubtopico()).get(1);
		jdbc.update("UPDATE questao_versao SET status = 'ARQUIVADA' WHERE questao_id = ?", idInternoDaQuestao(questao));

		verificarErro(novaVersao(questao), 409, "Conflict", "CONFLITO");
		assertThat(historico(questao)).containsExactly("1 ARQUIVADA");
	}

	@Test
	void deve_dar_404_e_400_nas_duas_rotas_com_id_inexistente_ou_malformado() {
		for (String id : new String[] { UUID.randomUUID().toString(), "abc" }) {
			int esperado = id.equals("abc") ? 400 : 404;
			String codigo = id.equals("abc") ? "PARAMETRO_INVALIDO" : "QUESTAO_NAO_ENCONTRADA";
			String erro = id.equals("abc") ? "Bad Request" : "Not Found";
			verificarErro(novaVersao(id), esperado, erro, codigo);
			verificarErro(publicarQuestao(id), esperado, erro, codigo);
		}
	}

	// ----- leitura e listagem (D41, D19, D56) -----

	/** Fecha a lacuna QUE-28 do inventário: a arquivada se lê por ?versao=n e não aparece sem ele. */
	@Test
	void deve_ler_a_versao_arquivada_por_versao_e_nunca_mostra_la_sem_esse_parametro() {
		String subtopico = criarSubtopico();
		List<String> ids = provaPublicadaComUmaQuestaoCompleta(subtopico);
		String prova = ids.get(0);
		String questao = ids.get(2);
		String outroSubtopico = criarSubtopico();
		novaVersao(questao).then().statusCode(201);
		editarQuestao(questao, corpoDoPut(outroSubtopico, "Segunda", "\"gabarito\":\"B\"")).then().statusCode(200);
		publicarQuestao(questao).then().statusCode(200);

		lerVersao(questao, 1).then().statusCode(200).body("status", equalTo("ARQUIVADA"))
				.body("enunciado", equalTo("Enunciado completo")).body("subtopicoId", equalTo(subtopico));
		verificarErro(lerVersao(questao, 3), 404, "Not Found", "QUESTAO_NAO_ENCONTRADA");
		buscarQuestao(questao).then().body("versao", equalTo(2)).body("status", equalTo("PUBLICADA"));

		listar("provaId=" + prova).then().body("totalElementos", equalTo(2)).body("conteudo.numero", equalTo(List.of(1, 2)))
				.body("conteudo.versao", equalTo(List.of(1, 2)))
				.body("conteudo.status", equalTo(List.of("PUBLICADA", "PUBLICADA")));
		// o filtro age sobre a versão exibida: o subtópico que só a arquivada tem não acha a questão
		listar("provaId=" + prova + "&subtopicoId=" + subtopico).then().body("conteudo.numero", equalTo(List.of(1)));
		listar("provaId=" + prova + "&subtopicoId=" + outroSubtopico).then().body("conteudo.numero", equalTo(List.of(2)));
		verificarErro(listar("status=ARQUIVADA"), 400, "Bad Request", "PARAMETRO_INVALIDO");

		novaVersao(questao).then().statusCode(201);
		listar("provaId=" + prova).then().body("totalElementos", equalTo(2)).body("conteudo.versao", equalTo(List.of(1, 3)))
				.body("conteudo.status", equalTo(List.of("PUBLICADA", "RASCUNHO")));
		listar("provaId=" + prova + "&status=PUBLICADA").then().body("conteudo.numero", equalTo(List.of(1)));
		buscarQuestao(questao).then().body("versao", equalTo(3)).body("status", equalTo("RASCUNHO"));
	}

	private Response listar(String filtros) {
		return requisicao().get("/api/v1/admin/questoes?" + filtros);
	}

}
