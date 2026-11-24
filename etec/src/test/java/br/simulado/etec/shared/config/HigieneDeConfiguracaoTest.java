package br.simulado.etec.shared.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.FileSystemResource;

/**
 * Guardas que leem os arquivos REAIS de configuração (sem depender do diretório de trabalho, ver {@link RaizDoProjeto}):
 *
 * <ul>
 * <li>nenhum segredo tem valor padrão nos {@code application*.yml}, inclusive a forma {@code ${NOME:padrão}} e o padrão
 * vazio; nenhuma chave do admin inicial (o admin é lido do ambiente, não do yml);</li>
 * <li>nos {@code application-*.yml.example} só valem marcadores ({@code <defina-aqui>}) ou comentários: valor com cara de
 * real falha.</li>
 * </ul>
 *
 * O {@code config/application-local.yml} (do desenvolvedor, ignorado pelo Git, com valores reais) NUNCA é lido por
 * este teste: na pasta {@code config/} só entram os arquivos {@code .example}.
 */
class HigieneDeConfiguracaoTest {

	private static final Path PASTA_DE_RECURSOS_REAL = RaizDoProjeto.resolver("src/main/resources");

	private static final Path PASTA_DE_CONFIG = RaizDoProjeto.resolver("config");

	private static final Path APPLICATION_YML_REAL = PASTA_DE_RECURSOS_REAL.resolve("application.yml");

	private static final List<String> PALAVRAS_SENSIVEIS = List.of("password", "senha", "secret", "token", "api-key",
			"apikey", "private", "credential", "chave");

	/**
	 * Chaves que CONTÊM uma palavra sensível mas NÃO são segredo, cada uma com o motivo. A lista é curta de propósito, e o
	 * valor de cada uma precisa ser uma duração ({@code 15m}, {@code 24h}), para a exceção não esconder um segredo.
	 */
	private static final Map<String, String> NAO_SAO_SEGREDO = Map.of(
			"security.jwt.access-token-expiration", "tempo de vida do access token (uma duração), não o token",
			"security.jwt.refresh-token-expiration", "tempo de vida do refresh token (uma duração), não o token",
			"app.rate-limit.troca-senha-usuario.limite", "quantas vezes se pode errar a senha atual (um número), não uma senha",
			"app.rate-limit.troca-senha-usuario.janela", "janela do limite de erros da senha atual (uma duração), não uma senha");

	private static final Pattern DURACAO = Pattern.compile("^\\d+(ms|s|m|h|d)$");

	/** Placeholder sem padrão: ${VAR}, sem dois-pontos. */
	private static final Pattern PLACEHOLDER_SEM_PADRAO = Pattern.compile("^\\$\\{[^:}]+}$");

	/** Marcador de valor a preencher: {@code <defina-aqui>}. */
	private static final Pattern MARCADOR = Pattern.compile("^<[^<>\\s][^<>]*>$");

	/** {@code ${NOME_SENSIVEL:...}}: um padrão (mesmo vazio) em variável de segredo, qualquer que seja a chave. */
	private static final Pattern PADRAO_EM_VARIAVEL_SENSIVEL = Pattern.compile(
			"\\$\\{[A-Za-z0-9_.-]*(?i:senha|secret|password|token|chave|key|credential)[A-Za-z0-9_.-]*:");

	/** Texto parecido com um segredo de verdade (longo, sem espaço, só letras, dígitos e símbolos de Base64). */
	private static final Pattern PARECE_SEGREDO = Pattern.compile("^[A-Za-z0-9+/=_-]{32,}$");

	// ----- yml reais -----

	@Test
	void deve_nao_ter_valor_padrao_em_chave_sensivel_quando_le_o_application_yml_real() throws IOException {
		assertThat(APPLICATION_YML_REAL).as("application.yml real").exists();

		Properties propriedades = carregar(APPLICATION_YML_REAL);

		assertThat(violacoesDoYml(propriedades, Files.readString(APPLICATION_YML_REAL)))
				.as("chaves sensíveis com valor padrão ou literal").isEmpty();
		assertThat(chavesSensiveis(propriedades))
				.as("o teste precisa enxergar as chaves sensíveis conhecidas, senão passaria vazio")
				.contains("spring.datasource.password", "security.jwt.secret");
	}

	@Test
	void deve_nao_ter_valor_padrao_em_chave_sensivel_quando_varre_todos_os_application_yml_reais() throws IOException {
		List<Path> arquivos = ymlReais();

		assertThat(arquivos).as("o application.yml principal precisa estar na varredura").contains(APPLICATION_YML_REAL);

		List<String> todasAsViolacoes = new ArrayList<>();
		for (Path arquivo : arquivos) {
			violacoesDoYml(carregar(arquivo), Files.readString(arquivo))
					.forEach(v -> todasAsViolacoes.add(arquivo.getFileName() + ": " + v));
		}
		assertThat(todasAsViolacoes).as("chaves sensíveis com valor padrão ou literal em qualquer perfil").isEmpty();
	}

	@Test
	void deve_nao_ter_nenhuma_chave_do_admin_inicial_no_yml_porque_o_admin_vem_so_do_ambiente() throws IOException {
		for (Path arquivo : ymlReais()) {
			String texto = semComentarios(Files.readString(arquivo));

			assertThat(texto).as(arquivo.getFileName().toString()).doesNotContainIgnoringCase("admin-bootstrap")
					.doesNotContain("ADMIN_EMAIL").doesNotContain("ADMIN_SENHA");
			assertThat(carregar(arquivo).stringPropertyNames()).as(arquivo.getFileName().toString())
					.noneMatch(chave -> chave.toLowerCase(Locale.ROOT).contains("admin"));
		}
	}

	/** application*.yml e application*.yaml reais de {@code src/main/resources}. */
	private static List<Path> ymlReais() throws IOException {
		try (Stream<Path> arquivos = Files.list(PASTA_DE_RECURSOS_REAL)) {
			return arquivos.filter(Files::isRegularFile)
					.filter(a -> a.getFileName().toString().matches("application.*\\.ya?ml")).sorted().toList();
		}
	}

	// ----- .example -----

	@Test
	void deve_ter_so_marcadores_ou_comentarios_nos_valores_sensiveis_dos_arquivos_example() throws IOException {
		List<Path> exemplos = exemplos();

		assertThat(exemplos).as("o application-local.yml.example precisa estar na varredura").isNotEmpty();

		List<String> todasAsViolacoes = new ArrayList<>();
		for (Path arquivo : exemplos) {
			violacoesDoExample(carregar(arquivo), Files.readString(arquivo))
					.forEach(v -> todasAsViolacoes.add(arquivo.getFileName() + ": " + v));
		}
		assertThat(todasAsViolacoes).as("valor que não é marcador nos .example").isEmpty();
	}

	/** Só os {@code application-*.yml.example}: o {@code application-local.yml} real do desenvolvedor NUNCA é lido. */
	private static List<Path> exemplos() throws IOException {
		try (Stream<Path> arquivos = Files.list(PASTA_DE_CONFIG)) {
			return arquivos.filter(Files::isRegularFile)
					.filter(a -> a.getFileName().toString().matches("application.*\\.ya?ml\\.example")).sorted().toList();
		}
	}

	// ----- as regras, provadas com entradas falsas (as sabotagens dos dois lados) -----

	@Test
	void deve_reprovar_quando_segredo_tem_valor_padrao() {
		Properties propriedades = new Properties();
		propriedades.setProperty("security.jwt.secret", "${JWT_SECRET:abc}");

		assertThat(violacoesDoYml(propriedades, "security.jwt.secret: ${JWT_SECRET:abc}")).isNotEmpty()
				.anyMatch(v -> v.contains("security.jwt.secret"));
	}

	@Test
	void deve_aceitar_numero_so_nas_chaves_nomeadas_da_lista_de_excecoes_e_nao_afrouxar_as_demais() {
		Properties propriedades = new Properties();
		propriedades.setProperty("app.rate-limit.troca-senha-usuario.limite", "5");
		propriedades.setProperty("app.rate-limit.troca-senha-usuario.janela", "15m");
		assertThat(violacoesDoYml(propriedades, "")).as("as duas chaves nomeadas, com número e duração").isEmpty();

		// a exceção é POR CHAVE: um número em outra chave sensível continua reprovado
		Properties outras = new Properties();
		outras.setProperty("spring.datasource.password", "12345678");
		outras.setProperty("app.admin.senha", "5");
		outras.setProperty("security.jwt.secret", "123456789012345678901234567890123");
		assertThat(violacoesDoYml(outras, "")).as("número em chave não nomeada").hasSize(3);

		// e a chave nomeada só aceita número ou duração, nunca texto
		Properties texto = new Properties();
		texto.setProperty("app.rate-limit.troca-senha-usuario.limite", "minha-senha-secreta");
		assertThat(violacoesDoYml(texto, "")).as("texto na chave nomeada").hasSize(1);
	}

	@Test
	void deve_reprovar_quando_segredo_e_valor_literal() {
		Properties propriedades = new Properties();
		propriedades.setProperty("spring.datasource.password", "minha-senha");

		assertThat(violacoesDoYml(propriedades, "")).hasSize(1);
	}

	@Test
	void deve_reprovar_quando_senha_tem_padrao_vazio_inclusive_a_do_admin() {
		Properties banco = new Properties();
		banco.setProperty("spring.datasource.password", "${DB_PASSWORD:}");
		Properties admin = new Properties();
		admin.setProperty("app.admin-bootstrap.senha", "${ADMIN_SENHA:}");

		assertThat(violacoesDoYml(banco, "")).hasSize(1);
		assertThat(violacoesDoYml(admin, "")).as("sem exceção para a senha do admin").hasSize(1);
	}

	@Test
	void deve_reprovar_padrao_em_variavel_de_segredo_mesmo_quando_a_chave_nao_parece_segredo() {
		Properties propriedades = new Properties();
		propriedades.setProperty("app.alguma.coisa", "${MINHA_SENHA:padrao}");

		assertThat(violacoesDoYml(propriedades, "app.alguma.coisa: ${MINHA_SENHA:padrao}")).isNotEmpty();
	}

	@Test
	void deve_ignorar_padrao_escrito_so_em_comentario() {
		assertThat(violacoesDoYml(new Properties(), "# exemplo antigo: ${JWT_SECRET:abc}\nchave: valor")).isEmpty();
	}

	@Test
	void deve_aceitar_quando_segredo_e_placeholder_sem_padrao() {
		Properties propriedades = new Properties();
		propriedades.setProperty("security.jwt.secret", "${JWT_SECRET}");

		assertThat(violacoesDoYml(propriedades, "security.jwt.secret: ${JWT_SECRET}")).isEmpty();
	}

	@Test
	void deve_aceitar_so_as_chaves_de_tempo_da_lista_curta_e_so_com_valor_de_duracao() {
		Properties tempo = new Properties();
		tempo.setProperty("security.jwt.access-token-expiration", "15m");
		tempo.setProperty("security.jwt.refresh-token-expiration", "24h");
		Properties disfarcado = new Properties();
		disfarcado.setProperty("security.jwt.access-token-expiration", "abc123SEGREDO");
		Properties naoListada = new Properties();
		naoListada.setProperty("security.jwt.outro-token-expiration", "15m");

		assertThat(violacoesDoYml(tempo, "")).isEmpty();
		assertThat(violacoesDoYml(disfarcado, "")).as("exceção não esconde segredo").isNotEmpty();
		assertThat(violacoesDoYml(naoListada, "")).as("só a lista explícita é isenta").isNotEmpty();
		assertThat(NAO_SAO_SEGREDO.values()).allMatch(motivo -> !motivo.isBlank());
	}

	@Test
	void deve_reprovar_valor_com_cara_de_real_no_example_e_aceitar_marcador_e_comentario() {
		Properties real = new Properties();
		real.setProperty("DB_PASSWORD", "x7Kq9ZmR2vLw4TnB8cYd6HsPaE3uFj5G");
		Properties curtoMasReal = new Properties();
		curtoMasReal.setProperty("JWT_SECRET", "root");
		Properties marcador = new Properties();
		marcador.setProperty("DB_PASSWORD", "<defina-aqui>");
		marcador.setProperty("TERMOS_VERSAO", "v1");

		assertThat(violacoesDoExample(real, "")).isNotEmpty();
		assertThat(violacoesDoExample(curtoMasReal, "")).as("qualquer valor que não seja marcador").isNotEmpty();
		assertThat(violacoesDoExample(marcador, "# ADMIN_SENHA: <defina-aqui>\n")).isEmpty();
		assertThat(violacoesDoExample(new Properties(), "# ADMIN_SENHA: Valor-Real-123\n")).as("comentário com valor real")
				.isNotEmpty();
		Properties longo = new Properties();
		longo.setProperty("ALGUMA_COISA", "abcdefghijklmnopqrstuvwxyz0123456789ABCD");
		assertThat(violacoesDoExample(longo, "")).as("texto longo parecido com segredo").isNotEmpty();
	}

	// ----- as regras -----

	private static Properties carregar(Path arquivo) {
		YamlPropertiesFactoryBean fabrica = new YamlPropertiesFactoryBean();
		fabrica.setResources(new FileSystemResource(arquivo));
		return fabrica.getObject();
	}

	private static boolean ehSensivel(String chave) {
		String minuscula = chave.toLowerCase(Locale.ROOT);
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

	private static String semComentarios(String texto) {
		StringBuilder limpo = new StringBuilder();
		for (String linha : texto.split("\\R")) {
			int comentario = linha.indexOf('#');
			limpo.append(comentario >= 0 ? linha.substring(0, comentario) : linha).append('\n');
		}
		return limpo.toString();
	}

	/** Violações de um yml real: chave sensível sem {@code ${VAR}} puro, e padrão em variável de segredo (qualquer chave). */
	static List<String> violacoesDoYml(Properties propriedades, String textoBruto) {
		List<String> violacoes = new ArrayList<>();
		for (String chave : chavesSensiveis(propriedades)) {
			String valor = propriedades.getProperty(chave).strip();
			if (NAO_SAO_SEGREDO.containsKey(chave)) {
				if (!DURACAO.matcher(valor).matches() && !valor.matches("\\d+")) {
					violacoes.add(chave + " está na lista de exceções (tempo ou contagem) mas o valor não é uma duração nem um número");
				}
			}
			else if (!PLACEHOLDER_SEM_PADRAO.matcher(valor).matches()) {
				violacoes.add(chave + " deve ser ${VARIAVEL} sem valor padrão");
			}
		}
		if (PADRAO_EM_VARIAVEL_SENSIVEL.matcher(semComentarios(textoBruto)).find()) {
			violacoes.add("variável de segredo com valor padrão (${NOME:padrão}) no texto do arquivo");
		}
		return violacoes;
	}

	/** Violações de um .example: valor sensível que não seja marcador, comentário com valor, ou texto parecido com segredo. */
	static List<String> violacoesDoExample(Properties propriedades, String textoBruto) {
		List<String> violacoes = new ArrayList<>();
		for (String chave : propriedades.stringPropertyNames()) {
			String valor = propriedades.getProperty(chave).strip();
			if (ehSensivel(chave) && !MARCADOR.matcher(valor).matches()) {
				violacoes.add(chave + " deve ser um marcador como <defina-aqui>");
			}
			else if (PARECE_SEGREDO.matcher(valor).matches()) {
				violacoes.add(chave + " tem valor com cara de segredo real");
			}
		}
		Pattern comentadoComValor = Pattern.compile("^#\\s*([A-Za-z0-9_.-]+):\\s*(.+)$");
		for (String linha : textoBruto.split("\\R")) {
			var m = comentadoComValor.matcher(linha.strip());
			if (m.matches() && ehSensivel(m.group(1)) && !MARCADOR.matcher(m.group(2).strip()).matches()) {
				violacoes.add("comentário com valor que não é marcador: " + m.group(1));
			}
		}
		return violacoes;
	}

}
