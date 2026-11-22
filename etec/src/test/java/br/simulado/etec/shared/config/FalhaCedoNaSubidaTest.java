package br.simulado.etec.shared.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.annotation.UserConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import br.simulado.etec.shared.security.JwtConfig;
import br.simulado.etec.shared.security.JwtProperties;
import br.simulado.etec.shared.security.SegredoDeTeste;

/**
 * "Falhar cedo, com mensagem clara, na subida": sem os termos de uso, ou com um segredo do JWT curto demais, o contexto
 * da aplicação nem sobe. Usa só as configurações envolvidas, sem banco.
 */
class FalhaCedoNaSubidaTest {

	@Configuration(proxyBeanMethods = false)
	static class RelogioDeTeste {

		@Bean
		Clock clock() {
			return Clock.systemUTC();
		}

	}

	private final ApplicationContextRunner termos = new ApplicationContextRunner()
			.withConfiguration(UserConfigurations.of(TermosConfig.class));

	private final ApplicationContextRunner jwt = new ApplicationContextRunner()
			.withConfiguration(UserConfigurations.of(JwtConfig.class, RelogioDeTeste.class));

	@Test
	void deve_subir_quando_os_termos_estao_definidos() {
		termos.withPropertyValues("app.termos.versao=v1", "app.termos.url=https://exemplo.invalid/termos")
				.run(contexto -> assertThat(contexto).hasNotFailed().getBean(TermosProperties.class).extracting("versao")
						.isEqualTo("v1"));
	}

	@Test
	void deve_falhar_na_subida_quando_falta_a_versao_ou_a_url_dos_termos() {
		termos.withPropertyValues("app.termos.url=https://exemplo.invalid/termos")
				.run(contexto -> assertThat(contexto).hasFailed());
		termos.withPropertyValues("app.termos.versao=v1").run(contexto -> assertThat(contexto).hasFailed());
		termos.run(contexto -> assertThat(contexto).hasFailed());
	}

	@Test
	void deve_falhar_na_subida_quando_a_versao_dos_termos_tem_mais_de_20_caracteres() {
		termos.withPropertyValues("app.termos.versao=" + "v".repeat(21), "app.termos.url=https://exemplo.invalid/termos")
				.run(contexto -> assertThat(contexto).hasFailed());
	}

	@Test
	void deve_subir_o_jwt_quando_o_segredo_tem_32_bytes_ou_mais() {
		jwt.withPropertyValues("security.jwt.secret=" + SegredoDeTeste.gerar(), "security.jwt.issuer=etec",
				"security.jwt.access-token-expiration=15m", "security.jwt.refresh-token-expiration=24h")
				.run(contexto -> assertThat(contexto).hasNotFailed().hasSingleBean(JwtProperties.class));
	}

	@Test
	void deve_falhar_na_subida_quando_o_segredo_do_jwt_e_curto_ou_nao_existe() {
		String curto = "segredo-curto-demais";
		jwt.withPropertyValues("security.jwt.secret=" + curto, "security.jwt.issuer=etec",
				"security.jwt.access-token-expiration=15m", "security.jwt.refresh-token-expiration=24h")
				.run(contexto -> {
					assertThat(contexto).hasFailed();
					assertThat(contexto.getStartupFailure()).hasStackTraceContaining("32 bytes")
							.hasStackTraceContaining("security.jwt.secret");
				});
		jwt.withPropertyValues("security.jwt.issuer=etec", "security.jwt.access-token-expiration=15m",
				"security.jwt.refresh-token-expiration=24h").run(contexto -> assertThat(contexto).hasFailed());
	}

	@Test
	void deve_nao_ter_valor_padrao_para_os_termos_no_application_yml_base() throws IOException {
		String yml = Files.readString(Path.of("src/main/resources/application.yml"));

		assertThat(yml).contains("versao: ${TERMOS_VERSAO}").contains("url: ${TERMOS_URL}")
				.doesNotContain("TERMOS_VERSAO:").doesNotContain("TERMOS_URL:");
	}

}
