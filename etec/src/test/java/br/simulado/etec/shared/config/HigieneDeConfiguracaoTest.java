package br.simulado.etec.shared.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.FileSystemResource;

/**
 * Lê o application.yml REAL (src/main/resources), e não o do classpath de teste, que o sombreia.
 * Chave sensível não pode ter valor padrão: precisa ser exatamente ${VARIAVEL}.
 */
class HigieneDeConfiguracaoTest {

	private static final Path PASTA_DE_RECURSOS_REAL = Path.of("src/main/resources");

	private static final Path APPLICATION_YML_REAL = PASTA_DE_RECURSOS_REAL.resolve("application.yml");

	private static final List<String> PALAVRAS_SENSIVEIS = List.of("password", "senha", "secret", "token", "api-key",
			"private");

	/** Única exceção explícita: a senha do admin inicial pode ter padrão vazio. */
	private static final Set<String> EXCECOES = Set.of("app.admin-bootstrap.senha");

	/** Placeholder sem padrão: ${VAR}, sem dois-pontos. */
	private static final Pattern PLACEHOLDER_SEM_PADRAO = Pattern.compile("^\\$\\{[^:}]+}$");

	@Test
	void deve_nao_ter_valor_padrao_em_chave_sensivel_quando_le_o_application_yml_real() {
		assertThat(APPLICATION_YML_REAL).as("application.yml real").exists();

		Properties propriedades = carregar(APPLICATION_YML_REAL);

		assertThat(violacoes(propriedades)).as("chaves sensíveis com valor padrão ou literal").isEmpty();
		assertThat(chavesSensiveis(propriedades))
				.as("o teste precisa enxergar as chaves sensíveis conhecidas, senão passaria vazio")
				.contains("spring.datasource.password", "security.jwt.secret", "app.admin-bootstrap.senha");
	}

	@Test
	void deve_nao_ter_valor_padrao_em_chave_sensivel_quando_varre_todos_os_application_yml_reais() throws IOException {
		List<Path> arquivos = arquivosDeConfiguracaoReais();

		assertThat(arquivos).as("o application.yml principal precisa estar na varredura").contains(APPLICATION_YML_REAL);

		List<String> todasAsViolacoes = new ArrayList<>();
		for (Path arquivo : arquivos) {
			violacoes(carregar(arquivo)).forEach(v -> todasAsViolacoes.add(arquivo.getFileName() + ": " + v));
		}
		assertThat(todasAsViolacoes).as("chaves sensíveis com valor padrão ou literal em qualquer perfil").isEmpty();
	}

	/** application*.yml e application*.yaml reais. Os .example ficam de fora (extensão diferente e outra pasta). */
	private static List<Path> arquivosDeConfiguracaoReais() throws IOException {
		try (Stream<Path> arquivos = Files.list(PASTA_DE_RECURSOS_REAL)) {
			return arquivos
					.filter(Files::isRegularFile)
					.filter(a -> a.getFileName().toString().matches("application.*\\.ya?ml"))
					.sorted()
					.toList();
		}
	}

	@Test
	void deve_reprovar_quando_segredo_tem_valor_padrao() {
		Properties propriedades = new Properties();
		propriedades.setProperty("security.jwt.secret", "${JWT_SECRET:abc}");

		assertThat(violacoes(propriedades)).hasSize(1).first().asString().contains("security.jwt.secret");
	}

	@Test
	void deve_reprovar_quando_segredo_e_valor_literal() {
		Properties propriedades = new Properties();
		propriedades.setProperty("spring.datasource.password", "minha-senha");

		assertThat(violacoes(propriedades)).hasSize(1);
	}

	@Test
	void deve_reprovar_quando_senha_tem_padrao_vazio_fora_da_excecao() {
		Properties propriedades = new Properties();
		propriedades.setProperty("spring.datasource.password", "${DB_PASSWORD:}");

		assertThat(violacoes(propriedades)).hasSize(1);
	}

	@Test
	void deve_aceitar_quando_segredo_e_placeholder_sem_padrao() {
		Properties propriedades = new Properties();
		propriedades.setProperty("security.jwt.secret", "${JWT_SECRET}");

		assertThat(violacoes(propriedades)).isEmpty();
	}

	@Test
	void deve_aceitar_quando_senha_do_admin_inicial_tem_padrao_vazio() {
		Properties propriedades = new Properties();
		propriedades.setProperty("app.admin-bootstrap.senha", "${ADMIN_SENHA:}");

		assertThat(violacoes(propriedades)).isEmpty();
	}

	@Test
	void deve_aceitar_quando_chave_de_token_e_de_tempo() {
		Properties propriedades = new Properties();
		propriedades.setProperty("security.jwt.access-token-expiration", "15m");
		propriedades.setProperty("security.jwt.refresh-token-expiration", "24h");

		assertThat(violacoes(propriedades)).isEmpty();
	}

	private static Properties carregar(Path arquivo) {
		YamlPropertiesFactoryBean fabrica = new YamlPropertiesFactoryBean();
		fabrica.setResources(new FileSystemResource(arquivo));
		return fabrica.getObject();
	}

	private static boolean ehSensivel(String chave) {
		String minuscula = chave.toLowerCase(Locale.ROOT);
		if (minuscula.endsWith("expiration")) {
			return false;
		}
		return PALAVRAS_SENSIVEIS.stream().anyMatch(minuscula::contains);
	}

	private static Set<String> chavesSensiveis(Properties propriedades) {
		Set<String> sensiveis = new TreeSet<>();
		for (String chave : propriedades.stringPropertyNames()) {
			if (ehSensivel(chave)) {
				sensiveis.add(chave);
			}
		}
		return sensiveis;
	}

	private static List<String> violacoes(Properties propriedades) {
		return chavesSensiveis(propriedades).stream()
				.filter(chave -> !EXCECOES.contains(chave))
				.filter(chave -> !PLACEHOLDER_SEM_PADRAO.matcher(propriedades.getProperty(chave).strip()).matches())
				.map(chave -> chave + " deve ser ${VARIAVEL} sem valor padrão")
				.toList();
	}

}
