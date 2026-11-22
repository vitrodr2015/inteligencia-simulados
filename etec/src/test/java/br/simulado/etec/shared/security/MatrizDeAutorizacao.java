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
	public record Ctx(String materiaId, String subtopicoId, String materiaVaziaId, String idInexistente, String sufixo) {
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

	private static String json(String chave, String valor) {
		return "{\"" + chave + "\":\"" + valor + "\"}";
	}

	static {
		// ----- públicas (o status é o da própria rota: nenhuma é barrada pela segurança) -----
		linha("signin com credencial errada", "POST", "/api/v1/auth/signin", c -> "/api/v1/auth/signin",
				c -> "{\"email\":\"ninguem@exemplo.invalid\",\"senha\":\"qualquer-123\"}", 401, 401, 401, 401);
		linha("refresh inválido", "POST", "/api/v1/auth/refresh", c -> "/api/v1/auth/refresh",
				c -> "{\"refreshToken\":\"lixo\"}", 401, 401, 401, 401);
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
