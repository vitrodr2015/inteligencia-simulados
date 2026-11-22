package br.simulado.etec.shared.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

/**
 * O nome do perfil de produção é UM só ({@link Perfis#PRODUCAO}) e todo lugar que depende dele (CORS só com https hoje; o
 * bootstrap do admin, D45, e a ValidacaoDeAmbiente quando precisarem) usa a constante. Estes testes quebram se o nome
 * divergir: texto solto no código, arquivo {@code application-<perfil>.yml} com outro nome de produção, ou a documentação
 * falando de um perfil diferente.
 */
class PerfisTest {

	private static final Path CODIGO = Path.of("src/main/java");

	private static final List<Path> PASTAS_DE_CONFIGURACAO = List.of(Path.of("src/main/resources"), Path.of("config"));

	/** Perfis que existem de fato: o de desenvolvimento local e o de produção. */
	private static final Set<String> PERFIS_CONHECIDOS = Set.of("local", Perfis.PRODUCAO);

	@Test
	void deve_reconhecer_o_perfil_de_producao_pela_constante() {
		MockEnvironment producao = new MockEnvironment();
		producao.setActiveProfiles(Perfis.PRODUCAO);
		MockEnvironment local = new MockEnvironment();
		local.setActiveProfiles("local");

		assertThat(Perfis.emProducao(producao)).isTrue();
		assertThat(Perfis.emProducao(local)).isFalse();
		assertThat(Perfis.emProducao(new MockEnvironment())).isFalse();
		assertThat(Perfis.PRODUCAO).isEqualTo("prod");
	}

	@Test
	void deve_nao_ter_o_nome_do_perfil_de_producao_escrito_fora_da_classe_perfis() throws IOException {
		try (Stream<Path> arquivos = Files.walk(CODIGO)) {
			List<String> culpados = arquivos.filter(a -> a.toString().endsWith(".java"))
					.filter(a -> !a.getFileName().toString().equals("Perfis.java")).filter(a -> {
						String texto = ler(a);
						return texto.contains("\"" + Perfis.PRODUCAO + "\"") || texto.contains("\"production\"")
								|| texto.contains("\"producao\"") || texto.contains("\"prd\"")
								|| texto.contains("Profiles.of(\"") || texto.contains("setActiveProfiles(\"");
					}).map(Path::toString).toList();

			assertThat(culpados).as("só a classe Perfis escreve o nome do perfil de produção").isEmpty();
		}
	}

	@Test
	void deve_so_usar_o_perfil_de_producao_pela_classe_perfis_no_codigo_que_depende_dele() throws IOException {
		String cors = ler(CODIGO.resolve("br/simulado/etec/shared/config/CorsConfig.java"));

		assertThat(cors).contains("Perfis.emProducao(");
	}

	@Test
	void deve_nao_ter_application_yml_de_perfil_com_nome_de_producao_diferente() throws IOException {
		for (Path pasta : PASTAS_DE_CONFIGURACAO) {
			try (Stream<Path> arquivos = Files.list(pasta)) {
				for (Path arquivo : arquivos.toList()) {
					String nome = arquivo.getFileName().toString();
					if (nome.startsWith("application-") && (nome.endsWith(".yml") || nome.endsWith(".yaml")
							|| nome.endsWith(".properties") || nome.endsWith(".example") || nome.contains(".yml."))) {
						String perfil = nome.substring("application-".length()).replaceAll("\\.(yml|yaml|properties)(\\..*)?$", "");
						assertThat(PERFIS_CONHECIDOS).as("perfil do arquivo %s", arquivo).contains(perfil);
					}
				}
			}
		}
	}

	@Test
	void deve_nao_ativar_blocos_por_perfil_com_nome_de_producao_diferente_no_application_yml() throws IOException {
		String yml = ler(Path.of("src/main/resources/application.yml"));

		assertThat(yml).doesNotContain("on-profile: production").doesNotContain("on-profile: producao")
				.doesNotContain("on-profile: prd");
		assertThat(yml).doesNotContainPattern("on-profile:\\s*(?!local\\b|" + Perfis.PRODUCAO + "\\b)\\S+");
	}

	@Test
	void deve_falar_do_mesmo_perfil_de_producao_no_yml_no_readme_e_no_contrato() throws IOException {
		String esperado = "perfil " + Perfis.PRODUCAO;
		String esperadoCrase = "perfil `" + Perfis.PRODUCAO + "`";

		assertThat(ler(Path.of("src/main/resources/application.yml"))).contains(esperado);
		assertThat(ler(Path.of("../README.md"))).contains(esperadoCrase);
		assertThat(ler(Path.of("docs/contrato-api-fase1.md"))).contains("(perfil " + Perfis.PRODUCAO + ")");
	}

	private static String ler(Path arquivo) {
		try {
			return Files.readString(arquivo);
		}
		catch (IOException e) {
			throw new IllegalStateException("não consegui ler " + arquivo, e);
		}
	}

}
