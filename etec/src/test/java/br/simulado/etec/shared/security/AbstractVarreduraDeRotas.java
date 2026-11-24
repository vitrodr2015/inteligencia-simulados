package br.simulado.etec.shared.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import br.simulado.etec.shared.security.MatrizDeAutorizacao.Caso;

/**
 * Varredura por reflexão dos mapeamentos do Spring: "rota nova nasce negada" E "rota nova nasce com teste". Lista TODAS
 * as rotas mapeadas e exige que cada uma do NOSSO código tenha linha na {@link MatrizDeAutorizacao} (e, sendo GET,
 * também a linha de HEAD) e que o anônimo receba 401 em toda rota que não esteja na lista explícita de públicas.
 *
 * <p>As rotas que NÃO são do nosso código têm tratamento explícito: o {@code /error} (BasicErrorController) tem de dar
 * 401 ao anônimo (D113), e as rotas do springdoc só existem com a documentação ligada, quando são públicas só para
 * GET e HEAD (D34). QUALQUER outra rota de terceiro (por exemplo, a do actuator no Módulo 18) quebra o teste até ser
 * classificada aqui, de propósito. A varredura não pode ser enfraquecida para passar: acrescente a rota à matriz.
 */
abstract class AbstractVarreduraDeRotas {

	/** As únicas rotas do nosso código que o anônimo pode chamar (D23, D94). */
	private static final Set<String> PUBLICAS = Set.of("POST /api/v1/auth/register", "POST /api/v1/auth/signin",
			"POST /api/v1/auth/refresh", "POST /api/v1/auth/logout",
			"GET /api/v1/termos/vigente");

	/** Prefixos de classes de terceiros que a varredura sabe tratar. */
	private static final String ERRO = "org.springframework.boot.webmvc.autoconfigure.error.";

	private static final String SPRINGDOC = "org.springdoc.";

	@Autowired
	private ApplicationContext contexto;

	@Autowired
	private MockMvc mockMvc;

	/** {@code true} quando a documentação do springdoc está ligada neste contexto. */
	abstract boolean documentacaoLigada();

	/** Uma rota mapeada: método, padrão e a classe do handler. */
	private record Rota(String metodo, String padrao, String classe) {
		String chave() {
			return metodo + " " + padrao;
		}
	}

	private List<Rota> rotasMapeadas() {
		List<Rota> rotas = new ArrayList<>();
		for (RequestMappingHandlerMapping mapeamento : contexto.getBeansOfType(RequestMappingHandlerMapping.class).values()) {
			for (Map.Entry<RequestMappingInfo, HandlerMethod> entrada : mapeamento.getHandlerMethods().entrySet()) {
				var padroes = entrada.getKey().getPathPatternsCondition().getPatternValues();
				var metodos = entrada.getKey().getMethodsCondition().getMethods();
				String classe = entrada.getValue().getBeanType().getName();
				for (String padrao : padroes) {
					if (metodos.isEmpty()) {
						rotas.add(new Rota("*", padrao, classe));
					}
					for (var metodo : metodos) {
						rotas.add(new Rota(metodo.name(), padrao, classe));
					}
				}
			}
		}
		return rotas;
	}

	private static boolean ehNossa(Rota rota) {
		return rota.classe().startsWith("br.simulado.etec.");
	}

	private static String concreto(String padrao) {
		return padrao.replace("{id}", UUID.randomUUID().toString());
	}

	private int statusAnonimo(String metodo, String caminho) throws Exception {
		return mockMvc.perform(request(HttpMethod.valueOf(metodo), caminho)).andReturn().getResponse().getStatus();
	}

	@Test
	void deve_ter_uma_linha_na_matriz_para_toda_rota_mapeada_do_nosso_codigo() {
		List<String> semLinha = new ArrayList<>();
		for (Rota rota : rotasMapeadas()) {
			if (!ehNossa(rota)) {
				continue;
			}
			assertThat(rota.metodo()).as("mapeamento sem método explícito: %s", rota).isNotEqualTo("*");
			if (naoTemLinha(rota.metodo(), rota.padrao())) {
				semLinha.add(rota.chave());
			}
			if ("GET".equals(rota.metodo()) && naoTemLinha("HEAD", rota.padrao())) {
				semLinha.add("HEAD " + rota.padrao());
			}
		}

		assertThat(semLinha).as("rotas mapeadas SEM linha na MatrizDeAutorizacao (acrescente a rota e a sua matriz)")
				.isEmpty();
	}

	private static boolean naoTemLinha(String metodo, String padrao) {
		return MatrizDeAutorizacao.casos().stream()
				.noneMatch(c -> metodo.equals(c.metodo()) && padrao.equals(c.padrao()));
	}

	@Test
	void deve_dar_401_ao_anonimo_em_toda_rota_do_nosso_codigo_que_nao_e_publica() throws Exception {
		List<String> erradas = new ArrayList<>();
		for (Rota rota : rotasMapeadas()) {
			if (!ehNossa(rota) || PUBLICAS.contains(rota.chave())) {
				continue;
			}
			int status = statusAnonimo(rota.metodo(), concreto(rota.padrao()));
			if (status != 401) {
				erradas.add(rota.chave() + " => " + status);
			}
		}

		assertThat(erradas).as("rotas protegidas que o anônimo NÃO recebeu 401").isEmpty();
	}

	@Test
	void deve_ter_na_matriz_401_para_o_anonimo_em_toda_linha_que_nao_e_publica() {
		for (Caso caso : MatrizDeAutorizacao.casos()) {
			// o HEAD de uma rota GET pública também é público
			String metodo = "HEAD".equals(caso.metodo()) ? "GET" : caso.metodo();
			if (caso.padrao() == null || PUBLICAS.contains(metodo + " " + caso.padrao())
					|| caso.padrao().startsWith("/actuator/health")) {
				continue;
			}
			assertThat(caso.anonimo()).as("a matriz precisa dar 401 ao anônimo em %s", caso).isEqualTo(401);
		}
	}

	@Test
	void deve_exigir_regra_para_toda_rota_publica_listada_e_so_elas() {
		Set<String> publicasNaMatriz = new TreeSet<>();
		for (Caso caso : MatrizDeAutorizacao.casos()) {
			if (caso.padrao() != null && caso.anonimo() != 401 && !caso.padrao().startsWith("/actuator/health")
					&& !"HEAD".equals(caso.metodo())) {
				publicasNaMatriz.add(caso.metodo() + " " + caso.padrao());
			}
		}
		// signin e refresh dão 401 pela credencial, mas continuam públicos: entram pela lista explícita.
		assertThat(publicasNaMatriz).isSubsetOf(PUBLICAS);
	}

	@Test
	void deve_tratar_explicitamente_toda_rota_que_nao_e_do_nosso_codigo() throws Exception {
		List<String> naoClassificadas = new ArrayList<>();
		boolean viuSpringdoc = false;
		for (Rota rota : rotasMapeadas()) {
			if (ehNossa(rota)) {
				continue;
			}
			if (rota.classe().startsWith(ERRO)) {
				// /error direto: 401 ao anônimo (D113).
				for (String metodo : new String[] { "GET", "POST" }) {
					assertThat(statusAnonimo(metodo, concreto(rota.padrao()))).as("%s %s", metodo, rota.padrao())
							.isEqualTo(401);
				}
			}
			else if (rota.classe().startsWith(SPRINGDOC)) {
				viuSpringdoc = true;
				if (!rota.padrao().contains("{") && !"*".equals(rota.metodo())) {
					int status = statusAnonimo("GET", rota.padrao());
					if (documentacaoLigada()) {
						assertThat(status).as("documentação ligada é pública: %s", rota).isNotIn(401, 403);
					}
				}
			}
			else {
				naoClassificadas.add(rota.chave() + " (" + rota.classe() + ")");
			}
		}

		assertThat(naoClassificadas).as("rotas de terceiros que a varredura não conhece: classifique aqui").isEmpty();
		assertThat(viuSpringdoc).as("rotas do springdoc só existem com a documentação ligada").isEqualTo(documentacaoLigada());
	}

}
