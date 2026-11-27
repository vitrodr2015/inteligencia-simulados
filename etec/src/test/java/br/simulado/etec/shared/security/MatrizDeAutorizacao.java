package br.simulado.etec.shared.security;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

/**
 * A matriz "quem pode o quê" (contrato, seção 2) em forma de tabela de testes: cada linha é um método, uma rota e o
 * status esperado para quatro perfis (anônimo, GUARDIAN, STUDENT e ADMIN). Os testes de autorização e a varredura de
 * rotas usam esta mesma lista. <b>Cada recurso novo acrescenta as suas linhas aqui, no próprio PR</b>; a varredura
 * quebra o build se existir uma rota mapeada que não esteja nesta matriz.
 *
 * <p>Convenções: 401 para o anônimo em tudo que não é público; 403 a quem está logado e não tem permissão (GUARDIAN em
 * tudo que exige papel, D122); a autorização vem ANTES de tudo, então STUDENT recebe 403 numa escrita mesmo com id
 * inexistente, id malformado ou corpo inválido, e ADMIN recebe 404, 400 e 400 (D79). Rota ou método sem regra: 401 e
 * 403 (D121).
 */
public final class MatrizDeAutorizacao {

	private MatrizDeAutorizacao() {
	}

	/** Os perfis da matriz, na ordem das colunas. */
	public enum Perfil {
		ANONIMO, GUARDIAN, STUDENT, ADMIN
	}

	/** Recursos criados (por um ADMIN) para cada execução de uma linha. */
	public record Ctx(String materiaId, String subtopicoId, String materiaVaziaId, String provaId, String questaoId, String idInexistente,
			String sufixo) {
	}

	/**
	 * Uma linha. {@code padrao} é a rota como o Spring a mapeia (por exemplo {@code /api/v1/materias/{id}}) e é
	 * {@code null} nas linhas de rotas que NÃO existem (negativas). {@code corpo} é opcional.
	 */
	public record Caso(String nome, String metodo, String padrao, Function<Ctx, String> caminho,
			Function<Ctx, String> corpo, int anonimo, int guardian, int student, int admin) {

		public int esperado(Perfil perfil) {
			return switch (perfil) {
				case ANONIMO -> anonimo;
				case GUARDIAN -> guardian;
				case STUDENT -> student;
				case ADMIN -> admin;
			};
		}

		@Override
		public String toString() {
			return metodo + " " + (padrao != null ? padrao : nome);
		}
	}

	private static final List<Caso> CASOS = new ArrayList<>();

	private static void linha(String nome, String metodo, String padrao, Function<Ctx, String> caminho,
			Function<Ctx, String> corpo, int anonimo, int guardian, int student, int admin) {
		CASOS.add(new Caso(nome, metodo, padrao, caminho, corpo, anonimo, guardian, student, admin));
	}

	/** Linha de rota fixa, sem corpo. */
	private static void fixa(String metodo, String caminho, int anonimo, int guardian, int student, int admin) {
		linha(caminho, metodo, caminho, c -> caminho, null, anonimo, guardian, student, admin);
	}

	/** Um lote válido com uma questão no subtópico da execução. */
	private static String loteDeUmaQuestao(String provaId, Ctx c) {
		return "{\"provaId\":\"" + provaId + "\",\"questoes\":[{\"numero\":2,\"subtopicoId\":\"" + c.subtopicoId()
				+ "\",\"enunciado\":\"Enunciado do lote " + c.sufixo() + "\",\"alternativas\":[{\"letra\":\"A\",\"texto\":\"a\"},"
				+ "{\"letra\":\"B\",\"texto\":\"b\"},{\"letra\":\"C\",\"texto\":\"c\"},{\"letra\":\"D\",\"texto\":\"d\"},"
				+ "{\"letra\":\"E\",\"texto\":\"e\"}]}]}";
	}

	private static String edicaoDeProva(Ctx c) {
		return "{\"titulo\":\"Prova editada " + c.sufixo() + "\",\"duracaoMinutos\":120,\"quantidadeQuestoes\":50}";
	}

	/** Troca o subtópico da questão pelo da execução (ativo), sem numero nem provaId. */
	private static String edicaoDeQuestao(Ctx c) {
		return "{\"subtopicoId\":\"" + c.subtopicoId() + "\",\"enunciado\":\"Enunciado editado " + c.sufixo()
				+ "\",\"alternativas\":[{\"letra\":\"A\",\"texto\":\"a\"},{\"letra\":\"B\",\"texto\":\"b\"},"
				+ "{\"letra\":\"C\",\"texto\":\"c\"},{\"letra\":\"D\",\"texto\":\"d\"},{\"letra\":\"E\",\"texto\":\"e\"}]}";
	}

	private static String json(String chave, String valor) {
		return "{\"" + chave + "\":\"" + valor + "\"}";
	}

	static {
		// ----- públicas (o status é o da própria rota: nenhuma é barrada pela segurança) -----
		linha("signin com credencial errada", "POST", "/api/v1/auth/signin", c -> "/api/v1/auth/signin",
				c -> "{\"email\":\"ninguem@exemplo.invalid\",\"senha\":\"qualquer-123\"}", 401, 401, 401, 401);
		linha("refresh inválido", "POST", "/api/v1/auth/refresh", c -> "/api/v1/auth/refresh",
				c -> "{\"refreshToken\":\"lixo\"}", 401, 401, 401, 401);
		linha("logout com um texto qualquer", "POST", "/api/v1/auth/logout", c -> "/api/v1/auth/logout",
				c -> "{\"refreshToken\":\"lixo\"}", 204, 204, 204, 204);
		linha("logout sem o refresh", "POST", "/api/v1/auth/logout", c -> "/api/v1/auth/logout", c -> "{}", 400, 400, 400, 400);
		linha("register com corpo vazio", "POST", "/api/v1/auth/register", c -> "/api/v1/auth/register", c -> "{}", 400,
				400, 400, 400);
		fixa("GET", "/api/v1/termos/vigente", 200, 200, 200, 200);
		fixa("HEAD", "/api/v1/termos/vigente", 200, 200, 200, 200);
		// O actuator ainda não está no projeto (Módulo 18): sem o endpoint, o que se prova é que a SEGURANÇA não barra
		// os três caminhos de saúde (404 do MVC, e não 401 nem 403). Quando o actuator entrar, passa a ser 200.
		for (String saude : new String[] { "/actuator/health", "/actuator/health/liveness", "/actuator/health/readiness" }) {
			fixa("GET", saude, 404, 404, 404, 404);
			fixa("HEAD", saude, 404, 404, 404, 404);
		}

		// ----- /me: STUDENT e ADMIN (D122: GUARDIAN não); PATCH e DELETE ainda sem regra (negados) -----
		fixa("GET", "/api/v1/me", 401, 403, 200, 200);
		fixa("HEAD", "/api/v1/me", 401, 403, 200, 200);
		fixa("PATCH", "/api/v1/me", 401, 403, 403, 403);
		// troca de senha (D139): STUDENT e ADMIN; GUARDIAN 403; corpo vazio = 400 do service (depois do 429)
		linha("trocar a senha com corpo vazio", "POST", "/api/v1/me/senha", c -> "/api/v1/me/senha", c -> "{}", 401, 403, 400, 400);
		linha("GET em /me/senha", "GET", null, c -> "/api/v1/me/senha", null, 401, 403, 403, 403);
		linha("PUT em /me/senha", "PUT", null, c -> "/api/v1/me/senha", c -> "{}", 401, 403, 403, 403);
		fixa("DELETE", "/api/v1/me", 401, 403, 403, 403);

		// ----- catálogo: leitura (STUDENT e ADMIN) -----
		for (String metodo : new String[] { "GET", "HEAD" }) {
			fixa(metodo, "/api/v1/materias", 401, 403, 200, 200);
			fixa(metodo, "/api/v1/subtopicos", 401, 403, 200, 200);
			linha("matéria por id", metodo, "/api/v1/materias/{id}", c -> "/api/v1/materias/" + c.materiaId(), null, 401, 403,
					200, 200);
			linha("subtópico por id", metodo, "/api/v1/subtopicos/{id}", c -> "/api/v1/subtopicos/" + c.subtopicoId(), null,
					401, 403, 200, 200);
			linha("matéria inexistente", metodo, "/api/v1/materias/{id}", c -> "/api/v1/materias/" + c.idInexistente(), null,
					401, 403, 404, 404);
			linha("subtópico inexistente", metodo, "/api/v1/subtopicos/{id}",
					c -> "/api/v1/subtopicos/" + c.idInexistente(), null, 401, 403, 404, 404);
			linha("matéria com id malformado", metodo, "/api/v1/materias/{id}", c -> "/api/v1/materias/abc", null, 401, 403,
					400, 400);
		}

		// ----- catálogo: escrita (só ADMIN). A autorização vem antes da existência, do formato e do conflito -----
		linha("criar matéria", "POST", "/api/v1/materias", c -> "/api/v1/materias",
				c -> "{\"nome\":\"Matéria da matriz " + c.sufixo() + "\"}", 401, 403, 403, 201);
		linha("criar matéria com corpo inválido", "POST", "/api/v1/materias", c -> "/api/v1/materias", c -> "{}", 401, 403,
				403, 400);
		linha("criar subtópico", "POST", "/api/v1/subtopicos", c -> "/api/v1/subtopicos",
				c -> "{\"materiaId\":\"" + c.materiaId() + "\",\"nome\":\"Subtópico da matriz " + c.sufixo() + "\"}", 401, 403,
				403, 201);
		linha("criar subtópico com corpo inválido", "POST", "/api/v1/subtopicos", c -> "/api/v1/subtopicos", c -> "{}", 401,
				403, 403, 400);
		linha("alterar matéria", "PUT", "/api/v1/materias/{id}", c -> "/api/v1/materias/" + c.materiaId(),
				c -> "{\"nome\":\"Matéria alterada " + c.sufixo() + "\"}", 401, 403, 403, 200);
		linha("alterar matéria inexistente", "PUT", "/api/v1/materias/{id}", c -> "/api/v1/materias/" + c.idInexistente(),
				c -> "{\"nome\":\"Qualquer nome\"}", 401, 403, 403, 404);
		linha("alterar matéria com id malformado", "PUT", "/api/v1/materias/{id}", c -> "/api/v1/materias/abc",
				c -> "{\"nome\":\"Qualquer nome\"}", 401, 403, 403, 400);
		linha("alterar subtópico", "PUT", "/api/v1/subtopicos/{id}", c -> "/api/v1/subtopicos/" + c.subtopicoId(),
				c -> json("nome", "Subtópico alterado " + c.sufixo()), 401, 403, 403, 200);
		linha("alterar subtópico inexistente", "PUT", "/api/v1/subtopicos/{id}",
				c -> "/api/v1/subtopicos/" + c.idInexistente(), c -> json("nome", "Qualquer nome"), 401, 403, 403, 404);
		for (String acao : new String[] { "disable", "enable" }) {
			linha(acao + " da matéria", "PATCH", "/api/v1/materias/{id}/" + acao,
					c -> "/api/v1/materias/" + c.materiaId() + "/" + acao, null, 401, 403, 403, 200);
			linha(acao + " da matéria inexistente", "PATCH", "/api/v1/materias/{id}/" + acao,
					c -> "/api/v1/materias/" + c.idInexistente() + "/" + acao, null, 401, 403, 403, 404);
			linha(acao + " do subtópico", "PATCH", "/api/v1/subtopicos/{id}/" + acao,
					c -> "/api/v1/subtopicos/" + c.subtopicoId() + "/" + acao, null, 401, 403, 403, 200);
			linha(acao + " do subtópico inexistente", "PATCH", "/api/v1/subtopicos/{id}/" + acao,
					c -> "/api/v1/subtopicos/" + c.idInexistente() + "/" + acao, null, 401, 403, 403, 404);
		}
		linha("apagar matéria sem subtópicos", "DELETE", "/api/v1/materias/{id}",
				c -> "/api/v1/materias/" + c.materiaVaziaId(), null, 401, 403, 403, 204);
		linha("apagar matéria inexistente", "DELETE", "/api/v1/materias/{id}",
				c -> "/api/v1/materias/" + c.idInexistente(), null, 401, 403, 403, 404);
		linha("apagar matéria com id malformado", "DELETE", "/api/v1/materias/{id}", c -> "/api/v1/materias/abc", null, 401,
				403, 403, 400);
		linha("apagar subtópico", "DELETE", "/api/v1/subtopicos/{id}", c -> "/api/v1/subtopicos/" + c.subtopicoId(), null,
				401, 403, 403, 204);
		linha("apagar subtópico inexistente", "DELETE", "/api/v1/subtopicos/{id}",
				c -> "/api/v1/subtopicos/" + c.idInexistente(), null, 401, 403, 403, 404);

		// ----- provas (Sprint 2, fatia 2a): as três rotas são só do ADMIN; STUDENT e GUARDIAN levam 403 (D154) -----
		linha("criar prova", "POST", "/api/v1/admin/provas", c -> "/api/v1/admin/provas",
				c -> "{\"titulo\":\"Prova da matriz " + c.sufixo() + "\",\"duracaoMinutos\":180,\"quantidadeQuestoes\":50}", 401,
				403, 403, 201);
		linha("criar prova com corpo inválido", "POST", "/api/v1/admin/provas", c -> "/api/v1/admin/provas", c -> "{}", 401,
				403, 403, 400);
		for (String metodo : new String[] { "GET", "HEAD" }) {
			fixa(metodo, "/api/v1/provas", 401, 403, 403, 200);
			linha("prova por id", metodo, "/api/v1/provas/{id}", c -> "/api/v1/provas/" + c.provaId(), null, 401, 403, 403, 200);
			linha("prova inexistente", metodo, "/api/v1/provas/{id}", c -> "/api/v1/provas/" + c.idInexistente(), null, 401, 403,
					403, 404);
			linha("prova com id malformado", metodo, "/api/v1/provas/{id}", c -> "/api/v1/provas/abc", null, 401, 403, 403, 400);
		}
		// o que ainda NÃO existe em provas é negado, inclusive para o ADMIN (D121): DELETE, PATCH e o resto de /admin/**
		for (String metodo : new String[] { "DELETE", "PATCH", "POST" }) {
			linha(metodo + " em /admin/provas/{id} (ainda não existe)", metodo, null, c -> "/api/v1/admin/provas/" + c.provaId(),
					c -> "{}", 401, 403, 403, 403);
		}
		for (String metodo : new String[] { "PUT", "DELETE", "PATCH" }) {
			linha(metodo + " em /provas/{id} (ainda não existe)", metodo, null, c -> "/api/v1/provas/" + c.provaId(), c -> "{}",
					401, 403, 403, 403);
			linha(metodo + " na coleção /admin/provas (ainda não existe)", metodo, null, c -> "/api/v1/admin/provas", c -> "{}",
					401, 403, 403, 403);
			linha(metodo + " na coleção /provas (ainda não existe)", metodo, null, c -> "/api/v1/provas", c -> "{}", 401, 403,
					403, 403);
		}
		linha("POST em /provas (a criação é só em /admin/provas)", "POST", null, c -> "/api/v1/provas", c -> "{}", 401, 403, 403,
				403);
		linha("POST em /provas/{id}", "POST", null, c -> "/api/v1/provas/" + c.provaId(), c -> "{}", 401, 403, 403, 403);
		linha("GET em /admin/provas (a listagem é em /provas)", "GET", null, c -> "/api/v1/admin/provas", null, 401, 403, 403, 403);
		linha("GET em /admin/provas/{id} (a leitura é em /provas/{id})", "GET", null, c -> "/api/v1/admin/provas/" + c.provaId(),
				null, 401, 403, 403, 403);
		linha("publicar prova (ainda não existe)", "PATCH", null, c -> "/api/v1/provas/" + c.provaId() + "/publish", null, 401,
				403, 403, 403);
		linha("publicar prova por /admin (não existe)", "PATCH", null,
				c -> "/api/v1/admin/provas/" + c.provaId() + "/publish", null, 401, 403, 403, 403);

		// ----- edição do rascunho (fatia 4, passada 1): três PUT, só ADMIN -----
		linha("editar prova", "PUT", "/api/v1/admin/provas/{id}", c -> "/api/v1/admin/provas/" + c.provaId(),
				MatrizDeAutorizacao::edicaoDeProva, 401, 403, 403, 200);
		linha("editar prova com corpo inválido", "PUT", "/api/v1/admin/provas/{id}",
				c -> "/api/v1/admin/provas/" + c.provaId(), c -> "{}", 401, 403, 403, 400);
		linha("editar prova inexistente", "PUT", "/api/v1/admin/provas/{id}", c -> "/api/v1/admin/provas/" + c.idInexistente(),
				MatrizDeAutorizacao::edicaoDeProva, 401, 403, 403, 404);
		linha("editar prova com id malformado", "PUT", "/api/v1/admin/provas/{id}", c -> "/api/v1/admin/provas/abc", c -> "{}",
				401, 403, 403, 400);
		linha("editar questão", "PUT", "/api/v1/admin/questoes/{id}", c -> "/api/v1/admin/questoes/" + c.questaoId(),
				MatrizDeAutorizacao::edicaoDeQuestao, 401, 403, 403, 200);
		linha("editar questão com corpo inválido", "PUT", "/api/v1/admin/questoes/{id}",
				c -> "/api/v1/admin/questoes/" + c.questaoId(), c -> "{}", 401, 403, 403, 400);
		linha("editar questão inexistente", "PUT", "/api/v1/admin/questoes/{id}",
				c -> "/api/v1/admin/questoes/" + c.idInexistente(), MatrizDeAutorizacao::edicaoDeQuestao, 401, 403, 403, 404);
		linha("editar questão com id malformado", "PUT", "/api/v1/admin/questoes/{id}", c -> "/api/v1/admin/questoes/abc",
				c -> "{}", 401, 403, 403, 400);
		// o campo desconhecido é ignorado (D86): está no corpo só para a execução criar a questão numero 1 nesta prova
		linha("gabarito em lote", "PUT", "/api/v1/admin/provas/{id}/gabarito",
				c -> "/api/v1/admin/provas/" + c.provaId() + "/gabarito",
				c -> "{\"itens\":[{\"numero\":1,\"gabarito\":\"A\"}],\"criaAQuestao\":\"" + c.questaoId() + "\"}", 401, 403, 403,
				200);
		linha("gabarito com lote vazio", "PUT", "/api/v1/admin/provas/{id}/gabarito",
				c -> "/api/v1/admin/provas/" + c.provaId() + "/gabarito", c -> "{\"itens\":[]}", 401, 403, 403, 400);
		linha("gabarito de número que a prova não tem", "PUT", "/api/v1/admin/provas/{id}/gabarito",
				c -> "/api/v1/admin/provas/" + c.provaId() + "/gabarito", c -> "{\"itens\":[{\"numero\":1,\"gabarito\":\"A\"}]}",
				401, 403, 403, 400);
		linha("gabarito em prova inexistente", "PUT", "/api/v1/admin/provas/{id}/gabarito",
				c -> "/api/v1/admin/provas/" + c.idInexistente() + "/gabarito",
				c -> "{\"itens\":[{\"numero\":1,\"gabarito\":\"A\"}]}", 401, 403, 403, 404);
		linha("gabarito com id de prova malformado", "PUT", "/api/v1/admin/provas/{id}/gabarito",
				c -> "/api/v1/admin/provas/abc/gabarito", c -> "{}", 401, 403, 403, 400);
		for (String metodo : new String[] { "GET", "HEAD", "POST", "PATCH", "DELETE" }) {
			linha(metodo + " em /admin/provas/{id}/gabarito (não existe)", metodo, null,
					c -> "/api/v1/admin/provas/" + c.provaId() + "/gabarito",
					metodo.equals("POST") || metodo.equals("PATCH") ? c -> "{}" : null, 401, 403, 403, 403);
		}
		// importação em lote (fatia 3): uma rota, só POST, só ADMIN
		linha("importar um lote", "POST", "/api/v1/admin/importacoes", c -> "/api/v1/admin/importacoes",
				c -> loteDeUmaQuestao(c.provaId(), c), 401, 403, 403, 200);
		linha("importar com corpo inválido", "POST", "/api/v1/admin/importacoes", c -> "/api/v1/admin/importacoes", c -> "{}", 401,
				403, 403, 400);
		linha("importar em prova inexistente (referência no corpo)", "POST", "/api/v1/admin/importacoes",
				c -> "/api/v1/admin/importacoes", c -> loteDeUmaQuestao(c.idInexistente(), c), 401, 403, 403, 400);
		for (String metodo : new String[] { "GET", "HEAD", "PUT", "PATCH", "DELETE" }) {
			Function<Ctx, String> semCorpoOuVazio = metodo.equals("GET") || metodo.equals("HEAD") || metodo.equals("DELETE") ? null
					: c -> "{}";
			linha(metodo + " em /admin/importacoes (não existe)", metodo, null, c -> "/api/v1/admin/importacoes", semCorpoOuVazio,
					401, 403, 403, 403);
			linha(metodo + " em /admin/importacoes/{id} (não existe)", metodo, null,
					c -> "/api/v1/admin/importacoes/" + c.idInexistente(), semCorpoOuVazio, 401, 403, 403, 403);
		}
		// listagem de questões (Sprint 2, fatia 2b-ii): só ADMIN
		for (String metodo : new String[] { "GET", "HEAD" }) {
			fixa(metodo, "/api/v1/admin/questoes", 401, 403, 403, 200);
		}

		// ----- questões (Sprint 2, fatia 2b-i): as duas rotas novas são só do ADMIN; o resto segue negado (D121) -----
		linha("cadastrar questão", "POST", "/api/v1/admin/provas/{id}/questoes", c -> "/api/v1/admin/provas/" + c.provaId() + "/questoes",
				c -> "{\"numero\":2,\"subtopicoId\":\"" + c.subtopicoId() + "\",\"enunciado\":\"Enunciado da matriz " + c.sufixo()
						+ "\",\"alternativas\":[{\"letra\":\"A\",\"texto\":\"a\"},{\"letra\":\"B\",\"texto\":\"b\"},"
						+ "{\"letra\":\"C\",\"texto\":\"c\"},{\"letra\":\"D\",\"texto\":\"d\"},{\"letra\":\"E\",\"texto\":\"e\"}]}",
				401, 403, 403, 201);
		linha("cadastrar questão com corpo inválido", "POST", "/api/v1/admin/provas/{id}/questoes",
				c -> "/api/v1/admin/provas/" + c.provaId() + "/questoes", c -> "{}", 401, 403, 403, 400);
		linha("cadastrar questão em prova inexistente", "POST", "/api/v1/admin/provas/{id}/questoes",
				c -> "/api/v1/admin/provas/" + c.idInexistente() + "/questoes",
				c -> "{\"numero\":2,\"subtopicoId\":\"" + c.subtopicoId() + "\",\"enunciado\":\"E\",\"alternativas\":["
						+ "{\"letra\":\"A\",\"texto\":\"a\"},{\"letra\":\"B\",\"texto\":\"b\"},{\"letra\":\"C\",\"texto\":\"c\"},"
						+ "{\"letra\":\"D\",\"texto\":\"d\"},{\"letra\":\"E\",\"texto\":\"e\"}]}",
				401, 403, 403, 404);
		linha("cadastrar questão com id de prova malformado", "POST", "/api/v1/admin/provas/{id}/questoes",
				c -> "/api/v1/admin/provas/abc/questoes", c -> "{}", 401, 403, 403, 400);
		for (String metodo : new String[] { "GET", "HEAD" }) {
			linha("questão por id", metodo, "/api/v1/admin/questoes/{id}", c -> "/api/v1/admin/questoes/" + c.questaoId(), null, 401,
					403, 403, 200);
			linha("questão inexistente", metodo, "/api/v1/admin/questoes/{id}", c -> "/api/v1/admin/questoes/" + c.idInexistente(),
					null, 401, 403, 403, 404);
			linha("questão com id malformado", metodo, "/api/v1/admin/questoes/{id}", c -> "/api/v1/admin/questoes/abc", null, 401,
					403, 403, 400);
		}
		for (String metodo : new String[] { "DELETE", "PATCH", "POST" }) {
			linha(metodo + " em /admin/questoes/{id} (ainda não existe)", metodo, null,
					c -> "/api/v1/admin/questoes/" + c.questaoId(), c -> "{}", 401, 403, 403, 403);
		}
		linha("GET em /admin/questoes/{id}/versoes (não existe)", "GET", null,
				c -> "/api/v1/admin/questoes/" + c.questaoId() + "/versoes", null, 401, 403, 403, 403);
		for (String metodo : new String[] { "PUT", "DELETE", "PATCH" }) {
			linha(metodo + " em /admin/provas/{id}/questoes (ainda não existe)", metodo, null,
					c -> "/api/v1/admin/provas/" + c.provaId() + "/questoes", c -> "{}", 401, 403, 403, 403);
			linha(metodo + " na coleção /admin/questoes (ainda não existe)", metodo, null, c -> "/api/v1/admin/questoes", c -> "{}",
					401, 403, 403, 403);
		}
		linha("GET em /admin/provas/{id}/questoes (a listagem é em /admin/questoes)", "GET", null,
				c -> "/api/v1/admin/provas/" + c.provaId() + "/questoes", null, 401, 403, 403, 403);
		linha("POST em /admin/questoes (o cadastro é em /admin/provas/{id}/questoes)", "POST", null, c -> "/api/v1/admin/questoes",
				c -> "{}", 401, 403, 403, 403);
		linha("POST /versoes da questão (ainda não existe)", "POST", null,
				c -> "/api/v1/admin/questoes/" + c.questaoId() + "/versoes", c -> "{}", 401, 403, 403, 403);
		linha("publicar questão (ainda não existe)", "PATCH", null, c -> "/api/v1/admin/questoes/" + c.questaoId() + "/publish", null,
				401, 403, 403, 403);
		linha("STUDENT não lê questão do admin (a leitura do aluno é outra rota)", "GET", null,
				c -> "/api/v1/questoes/" + c.questaoId(), null, 401, 403, 403, 403);

		// ----- sem regra = negado (D121): rota inexistente, método sem regra, actuator e /error -----
		linha("GET em rota inexistente", "GET", null, c -> "/api/v1/nao-existe", null, 401, 403, 403, 403);
		linha("POST em rota inexistente", "POST", null, c -> "/api/v1/nao-existe", c -> "{}", 401, 403, 403, 403);
		linha("rota inexistente fora de /api/v1", "GET", null, c -> "/nao-existe", null, 401, 403, 403, 403);
		linha("DELETE na coleção de matérias", "DELETE", null, c -> "/api/v1/materias", null, 401, 403, 403, 403);
		linha("PATCH na coleção de subtópicos", "PATCH", null, c -> "/api/v1/subtopicos", null, 401, 403, 403, 403);
		linha("PUT em /me", "PUT", null, c -> "/api/v1/me", c -> "{}", 401, 403, 403, 403);
		linha("sub-rota inexistente de matéria", "GET", null, c -> "/api/v1/materias/" + c.materiaId() + "/x/y", null, 401,
				403, 403, 403);
		linha("GET em rota só de POST", "GET", null, c -> "/api/v1/auth/signin", null, 401, 403, 403, 403);
		linha("POST em rota só de GET", "POST", null, c -> "/api/v1/termos/vigente", c -> "{}", 401, 403, 403, 403);
		linha("actuator fora da saúde", "GET", null, c -> "/actuator/env", null, 401, 403, 403, 403);
		linha("/error direto", "GET", null, c -> "/error", null, 401, 403, 403, 403);
		linha("sub-rota da saúde", "GET", null, c -> "/actuator/health/xyz", null, 401, 403, 403, 403);

		// ----- caminhos sensíveis (D127): nenhum abre, nem para o ADMIN. Anônimo 401; logado 403 (o ADMIN também) -----
		for (String sensivel : new String[] { "/actuator", "/actuator/env", "/actuator/heapdump", "/actuator/beans",
				"/actuator/mappings", "/actuator/configprops", "/actuator/loggers", "/actuator/metrics",
				"/actuator/threaddump", "/actuator/httpexchanges", "/h2-console", "/h2-console/", "/swagger-ui.html",
				"/swagger-ui/index.html", "/v3/api-docs", "/api-docs", "/.env", "/application.yml", "/config/application-local.yml",
				"/api/v1/admin", "/api/v1/admin/usuarios" }) {
			linha("caminho sensível " + sensivel, "GET", null, c -> sensivel, null, 401, 403, 403, 403);
		}
		linha("POST em /actuator/shutdown", "POST", null, c -> "/actuator/shutdown", c -> "{}", 401, 403, 403, 403);
		linha("POST em /h2-console", "POST", null, c -> "/h2-console", c -> "{}", 401, 403, 403, 403);
		linha("POST em /actuator/env", "POST", null, c -> "/actuator/env", c -> "{}", 401, 403, 403, 403);
		// /WEB-INF e /META-INF: o CONTAINER (Tomcat) responde 404 antes da cadeia de segurança, para qualquer perfil
		// (seção 7 do contrato). O corpo seguro é provado em CaminhosSensiveisVazamentoTest.
		linha("/WEB-INF/web.xml (o container responde antes da cadeia)", "GET", null, c -> "/WEB-INF/web.xml", null, 404,
				404, 404, 404);
		linha("/META-INF/MANIFEST.MF (o container responde antes da cadeia)", "GET", null, c -> "/META-INF/MANIFEST.MF",
				null, 404, 404, 404, 404);
	}

	public static List<Caso> casos() {
		return List.copyOf(CASOS);
	}

	/** Um UUID válido que não existe no banco. */
	public static String uuidInexistente() {
		return UUID.randomUUID().toString();
	}

}
