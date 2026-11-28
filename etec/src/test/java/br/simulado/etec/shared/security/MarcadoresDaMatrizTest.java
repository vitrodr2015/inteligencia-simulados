package br.simulado.etec.shared.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import br.simulado.etec.shared.security.MatrizDeAutorizacao.Caso;
import br.simulado.etec.shared.security.MatrizDeAutorizacao.Ctx;

/** Teste unitário (sem Spring e sem banco) da criação sob demanda dos recursos da matriz: a fábrica é falsa e anota o que criou. */
class MarcadoresDaMatrizTest {

	/** Anota cada criação; os ids são únicos entre TODAS as instâncias do teste, como os do banco. */
	private static class FabricaFalsa implements MarcadoresDaMatriz.Fabrica {

		private static final AtomicInteger SEQUENCIA = new AtomicInteger();

		final List<String> criacoes = new ArrayList<>();

		private String criar(String o) {
			String id = o + "-" + SEQUENCIA.incrementAndGet();
			criacoes.add(id);
			return id;
		}

		@Override
		public String materia() {
			return criar("materia");
		}

		@Override
		public String subtopico(String materiaId) {
			assertThat(materiaId).startsWith("materia-");
			return criar("subtopico");
		}

		@Override
		public String materiaVazia() {
			return criar("vazia");
		}

		@Override
		public String prova() {
			return criar("prova");
		}

		@Override
		public String questao(String provaId, String materiaId) {
			assertThat(provaId).startsWith("prova-");
			assertThat(materiaId).startsWith("materia-");
			return criar("questao");
		}

		@Override
		public String provaCompleta(String materiaId) {
			assertThat(materiaId).startsWith("materia-");
			return criar("provacompleta");
		}

		List<String> tipos() {
			return criacoes.stream().map(id -> id.substring(0, id.indexOf('-'))).toList();
		}

	}

	private final FabricaFalsa fabrica = new FabricaFalsa();

	private final MarcadoresDaMatriz recursos = new MarcadoresDaMatriz(fabrica);

	// ----- só o que a linha usa, com as dependências -----

	@Test
	void deve_nao_criar_nada_quando_o_texto_nao_tem_marcador() {
		assertThat(recursos.resolver("/api/v1/materias")).isEqualTo("/api/v1/materias");
		assertThat(recursos.resolver("{}")).isEqualTo("{}");
		assertThat(recursos.resolver("{\"itens\":[]}")).isEqualTo("{\"itens\":[]}");
		assertThat(recursos.resolver("{\"nome\":{\"x\":1}}")).isEqualTo("{\"nome\":{\"x\":1}}");
		assertThat(recursos.resolver(null)).isNull();

		assertThat(fabrica.criacoes).isEmpty();
	}

	@Test
	void deve_criar_so_a_prova_quando_a_linha_usa_so_a_prova() {
		String caminho = recursos.resolver("/api/v1/provas/{provaId}");

		assertThat(caminho).startsWith("/api/v1/provas/prova-");
		assertThat(fabrica.tipos()).containsExactly("prova");
	}

	@Test
	void deve_criar_a_materia_antes_do_subtopico_quando_a_linha_usa_so_o_subtopico() {
		recursos.resolver("/api/v1/subtopicos/{subtopicoId}");

		assertThat(fabrica.tipos()).containsExactly("materia", "subtopico");
	}

	@Test
	void deve_criar_a_prova_e_a_materia_antes_da_questao() {
		recursos.resolver("/api/v1/admin/questoes/{questaoId}");

		assertThat(fabrica.tipos()).containsExactly("prova", "materia", "questao");
	}

	@Test
	void deve_criar_a_materia_vazia_sem_criar_a_materia_base() {
		recursos.resolver("/api/v1/materias/{materiaVaziaId}");

		assertThat(fabrica.tipos()).containsExactly("vazia");
	}

	@Test
	void deve_usar_o_mesmo_recurso_no_caminho_no_corpo_e_nas_dependencias_da_mesma_execucao() {
		String caminho = recursos.resolver("/api/v1/admin/provas/{provaId}/questoes?provaId={provaId}");
		String corpo = recursos.resolver("{\"subtopicoId\":\"{subtopicoId}\",\"materiaId\":\"{materiaId}\",\"q\":\"{questaoId}\"}");

		assertThat(fabrica.tipos()).containsExactly("prova", "materia", "subtopico", "questao");
		String prova = fabrica.criacoes.get(0);
		assertThat(caminho).isEqualTo("/api/v1/admin/provas/" + prova + "/questoes?provaId=" + prova);
		assertThat(corpo).contains(fabrica.criacoes.get(1)).contains(fabrica.criacoes.get(2)).contains(fabrica.criacoes.get(3))
				.doesNotContain("{subtopicoId}").doesNotContain("{materiaId}").doesNotContain("{questaoId}");
	}

	// ----- isolamento entre execuções -----

	@Test
	void deve_criar_recursos_novos_a_cada_execucao_sem_compartilhar_com_outra() {
		String texto = "/{materiaId}/{subtopicoId}/{materiaVaziaId}/{provaId}/{questaoId}";
		FabricaFalsa outraFabrica = new FabricaFalsa();

		String primeira = recursos.resolver(texto);
		String segunda = new MarcadoresDaMatriz(outraFabrica).resolver(texto);

		assertThat(outraFabrica.tipos()).as("a segunda execução cria os SEUS recursos")
				.containsExactlyInAnyOrderElementsOf(fabrica.tipos()).hasSize(5);
		assertThat(outraFabrica.criacoes).doesNotContainAnyElementsOf(fabrica.criacoes);
		for (String idDaPrimeira : fabrica.criacoes) {
			assertThat(segunda).doesNotContain("/" + idDaPrimeira + "/");
		}
		assertThat(segunda).isNotEqualTo(primeira);
	}

	@org.junit.jupiter.api.Test
	void deve_criar_a_prova_completa_como_recurso_proprio_so_com_a_materia() {
		String resolvido = recursos.resolver("/api/v1/provas/{provaCompletaId}/publish");

		assertThat(resolvido).contains("provacompleta-").doesNotContain("{");
		assertThat(fabrica.tipos()).containsExactly("materia", "provacompleta");
	}

	// ----- falha rápida -----

	@ParameterizedTest
	@CsvSource(delimiter = '|', quoteCharacter = '`', value = {
			"/api/v1/provas/{id}                         | {id}",
			"/api/v1/materias/{materiaid}                | {materiaid}",
			"/api/v1/materias/{MateriaId}                | {MateriaId}",
			"{\"provaId\":\"{outraProvaId}\"}            | {outraProvaId}",
			"/api/v1/admin/questoes?provaId={prova_id}   | {prova_id}",
			"/api/v1/provas/{provaId}/{desconhecido}     | {desconhecido}" })
	void deve_errar_com_mensagem_clara_quando_sobra_um_marcador_no_caminho_no_corpo_ou_nos_parametros(String texto,
			String sobra) {
		assertThatThrownBy(() -> recursos.resolver(texto)).isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("Marcador não resolvido").hasMessageContaining(sobra);
	}

	@ParameterizedTest
	@ValueSource(strings = { "", "   " })
	void deve_errar_quando_a_fabrica_nao_devolve_um_id(String vazio) {
		MarcadoresDaMatriz comFabricaQuebrada = new MarcadoresDaMatriz(new FabricaFalsa() {
			@Override
			public String prova() {
				return vazio;
			}
		});

		assertThatThrownBy(() -> comFabricaQuebrada.resolver("/api/v1/provas/{provaId}")).isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("{provaId}").hasMessageContaining("não foi criado");
	}

	@Test
	void deve_errar_em_vez_de_mandar_null_na_url_quando_a_fabrica_devolve_nulo() {
		MarcadoresDaMatriz comFabricaQuebrada = new MarcadoresDaMatriz(new FabricaFalsa() {
			@Override
			public String prova() {
				return null;
			}
		});

		assertThatThrownBy(() -> comFabricaQuebrada.resolver("/api/v1/provas/{provaId}")).isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("{provaId}");
	}

	// ----- as linhas de verdade da matriz -----

	@Test
	void deve_resolver_todas_as_linhas_da_matriz_sem_sobrar_marcador() {
		for (Caso caso : MatrizDeAutorizacao.casos()) {
			MarcadoresDaMatriz daLinha = new MarcadoresDaMatriz(new FabricaFalsa());
			Ctx ctx = MarcadoresDaMatriz.contexto("00000000-0000-4000-8000-000000000000", "sufixo");

			String caminho = daLinha.resolver(caso.caminho().apply(ctx));
			String corpo = daLinha.resolver((caso.corpo() != null) ? caso.corpo().apply(ctx) : null);

			assertThat(caminho).as(caso.nome()).doesNotContain("{").doesNotContain("null");
			if (corpo != null) {
				assertThat(corpo).as(caso.nome()).doesNotContainPattern("\\{[A-Za-z]");
			}
		}
	}

}
