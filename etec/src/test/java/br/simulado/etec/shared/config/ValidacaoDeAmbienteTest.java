package br.simulado.etec.shared.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class ValidacaoDeAmbienteTest {

	private static final String SEGREDO_FALSO_VALIDO = "segredo-de-teste-falso-nao-e-real-0123456789";
	private static final String URL_FALSA = "jdbc:mysql://localhost:3307/etec_teste";

	private MockEnvironment ambienteValido() {
		return new MockEnvironment()
				.withProperty("spring.datasource.url", URL_FALSA)
				.withProperty("DB_PASSWORD", "senha-de-teste-falsa")
				.withProperty("JWT_SECRET", SEGREDO_FALSO_VALIDO);
	}

	@Test
	void deve_aceitar_quando_ambiente_esta_correto() {
		assertThatCode(() -> ValidacaoDeAmbiente.validar(ambienteValido())).doesNotThrowAnyException();
	}

	@Test
	void deve_recusar_quando_db_password_nao_esta_definida() {
		MockEnvironment ambiente = new MockEnvironment()
				.withProperty("spring.datasource.url", URL_FALSA)
				.withProperty("JWT_SECRET", SEGREDO_FALSO_VALIDO);

		assertThatThrownBy(() -> ValidacaoDeAmbiente.validar(ambiente))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("DB_PASSWORD não está definida ou está vazia");
	}

	@Test
	void deve_recusar_quando_db_password_esta_vazia() {
		MockEnvironment ambiente = ambienteValido().withProperty("DB_PASSWORD", "   ");

		assertThatThrownBy(() -> ValidacaoDeAmbiente.validar(ambiente))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("DB_PASSWORD não está definida ou está vazia");
	}

	@Test
	void deve_nao_exigir_db_password_quando_datasource_url_nao_esta_definida() {
		MockEnvironment ambiente = new MockEnvironment().withProperty("JWT_SECRET", SEGREDO_FALSO_VALIDO);

		assertThatCode(() -> ValidacaoDeAmbiente.validar(ambiente)).doesNotThrowAnyException();
	}

	@Test
	void deve_recusar_quando_jwt_secret_nao_esta_definido() {
		MockEnvironment ambiente = new MockEnvironment()
				.withProperty("spring.datasource.url", URL_FALSA)
				.withProperty("DB_PASSWORD", "senha-de-teste-falsa");

		assertThatThrownBy(() -> ValidacaoDeAmbiente.validar(ambiente))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("JWT_SECRET não está definida ou está vazia");
	}

	@Test
	void deve_recusar_quando_jwt_secret_tem_menos_de_32_caracteres() {
		MockEnvironment ambiente = ambienteValido().withProperty("JWT_SECRET", "curto-demais");

		assertThatThrownBy(() -> ValidacaoDeAmbiente.validar(ambiente))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("JWT_SECRET precisa ter pelo menos 32 caracteres");
	}

	@Test
	void deve_aceitar_quando_jwt_secret_tem_exatamente_32_caracteres() {
		MockEnvironment ambiente = ambienteValido().withProperty("JWT_SECRET", "x".repeat(32));

		assertThatCode(() -> ValidacaoDeAmbiente.validar(ambiente)).doesNotThrowAnyException();
	}

	@Test
	void deve_recusar_quando_db_password_tem_espaco_nas_pontas() {
		MockEnvironment ambiente = ambienteValido().withProperty("DB_PASSWORD", " senha-de-teste-falsa");

		assertThatThrownBy(() -> ValidacaoDeAmbiente.validar(ambiente))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("DB_PASSWORD tem espaço ou quebra de linha no início ou no fim");
	}

	@Test
	void deve_recusar_quando_jwt_secret_tem_quebra_de_linha_no_fim() {
		MockEnvironment ambiente = ambienteValido().withProperty("JWT_SECRET", SEGREDO_FALSO_VALIDO + "\n");

		assertThatThrownBy(() -> ValidacaoDeAmbiente.validar(ambiente))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("JWT_SECRET tem espaço ou quebra de linha no início ou no fim");
	}

	@Test
	void deve_listar_todos_os_problemas_quando_ha_mais_de_um() {
		MockEnvironment ambiente = new MockEnvironment().withProperty("spring.datasource.url", URL_FALSA);

		assertThatThrownBy(() -> ValidacaoDeAmbiente.validar(ambiente))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("DB_PASSWORD não está definida")
				.hasMessageContaining("JWT_SECRET não está definida");
	}

	@Test
	void deve_nao_mostrar_o_valor_do_segredo_quando_recusa() {
		String valorSecreto = "valor-secreto-que-nunca-pode-aparecer";
		MockEnvironment ambiente = ambienteValido().withProperty("JWT_SECRET", valorSecreto + "\n");

		assertThatThrownBy(() -> ValidacaoDeAmbiente.validar(ambiente))
				.isInstanceOf(IllegalStateException.class)
				.satisfies(erro -> assertThat(erro.getMessage()).doesNotContain(valorSecreto));
	}


	// ----- administrador inicial (D45, D91, D125) -----

	private MockEnvironment emProducao(String email, String senha) {
		MockEnvironment ambiente = ambienteValido();
		ambiente.setActiveProfiles(Perfis.PRODUCAO);
		if (email != null) {
			ambiente.setProperty("ADMIN_EMAIL", email);
		}
		if (senha != null) {
			ambiente.setProperty("ADMIN_SENHA", senha);
		}
		return ambiente;
	}

	@Test
	void deve_aceitar_em_producao_as_variaveis_do_admin_validas_ou_ausentes() {
		assertThatCode(() -> ValidacaoDeAmbiente.validar(emProducao("admin@exemplo.invalid", "Senha-Do-Admin-123")))
				.doesNotThrowAnyException();
		// ausentes: a falta de admin depende do banco e é conferida depois (antes de o servidor aceitar conexões)
		assertThatCode(() -> ValidacaoDeAmbiente.validar(emProducao(null, null))).doesNotThrowAnyException();
		assertThatCode(() -> ValidacaoDeAmbiente.validar(emProducao("", ""))).doesNotThrowAnyException();
	}

	@Test
	void deve_recusar_em_producao_variavel_do_admin_invalida_sem_mostrar_valor() {
		String senhaCurta = "senha-curta";
		String emailRuim = "isto-nao-e-um-email";

		assertThatThrownBy(() -> ValidacaoDeAmbiente.validar(emProducao("admin@exemplo.invalid", senhaCurta)))
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("Administrador inicial")
				.hasMessageContaining("ADMIN_SENHA tem menos de 12 bytes").hasMessageNotContaining(senhaCurta);
		assertThatThrownBy(() -> ValidacaoDeAmbiente.validar(emProducao(emailRuim, "Senha-Do-Admin-123")))
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("ADMIN_EMAIL")
				.hasMessageNotContaining(emailRuim);
		assertThatThrownBy(() -> ValidacaoDeAmbiente.validar(emProducao("admin@exemplo.invalid", "Senha-Do-Admin-123\n")))
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("início ou no fim");
	}

	@Test
	void deve_recusar_em_producao_so_uma_das_duas_variaveis() {
		assertThatThrownBy(() -> ValidacaoDeAmbiente.validar(emProducao("admin@exemplo.invalid", null)))
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("ADMIN_SENHA não foi definida");
		assertThatThrownBy(() -> ValidacaoDeAmbiente.validar(emProducao(null, "Senha-Do-Admin-123")))
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("ADMIN_EMAIL não foi definida");
	}

	@Test
	void deve_nao_falhar_fora_de_producao_mesmo_com_variavel_do_admin_invalida() {
		MockEnvironment desenvolvimento = ambienteValido().withProperty("ADMIN_EMAIL", "ruim").withProperty("ADMIN_SENHA", "curta");
		MockEnvironment local = ambienteValido().withProperty("ADMIN_SENHA", "curta");
		local.setActiveProfiles("local");

		assertThatCode(() -> ValidacaoDeAmbiente.validar(desenvolvimento)).doesNotThrowAnyException();
		assertThatCode(() -> ValidacaoDeAmbiente.validar(local)).doesNotThrowAnyException();
	}

	@Test
	void deve_listar_junto_com_os_outros_problemas_do_ambiente_em_producao() {
		MockEnvironment ambiente = new MockEnvironment().withProperty("spring.datasource.url", URL_FALSA)
				.withProperty("ADMIN_SENHA", "curta");
		ambiente.setActiveProfiles(Perfis.PRODUCAO);

		assertThatThrownBy(() -> ValidacaoDeAmbiente.validar(ambiente)).hasMessageContaining("DB_PASSWORD")
				.hasMessageContaining("JWT_SECRET").hasMessageContaining("Administrador inicial");
	}


	// ----- origem da requisição: proxy confiável (D130) -----

	private MockEnvironment producaoComProxy(String cabecalho, String lista) {
		MockEnvironment ambiente = ambienteValido().withProperty("ADMIN_EMAIL", "admin@exemplo.invalid")
				.withProperty("ADMIN_SENHA", "Senha-Do-Admin-123");
		ambiente.setActiveProfiles(Perfis.PRODUCAO);
		if (cabecalho != null) {
			ambiente.setProperty("server.tomcat.remoteip.remote-ip-header", cabecalho);
		}
		if (lista != null) {
			ambiente.setProperty("server.tomcat.remoteip.internal-proxies", lista);
		}
		return ambiente;
	}

	@Test
	void deve_aceitar_em_producao_sem_proxy_configurado_so_com_aviso_no_log() {
		assertThatCode(() -> ValidacaoDeAmbiente.validar(producaoComProxy(null, null))).doesNotThrowAnyException();
	}

	@Test
	void deve_aceitar_em_producao_o_proxy_com_lista_explicita() {
		assertThatCode(() -> ValidacaoDeAmbiente.validar(producaoComProxy("X-Forwarded-For", "10\\.0\\.0\\.1|10\\.0\\.0\\.2")))
				.doesNotThrowAnyException();
	}

	@Test
	void deve_recusar_em_producao_o_proxy_ligado_com_lista_vazia_ou_permissiva_sem_mostrar_o_valor() {
		for (String lista : new String[] { null, "", ".*", "10\\.0\\.0\\.1|.*", "0.0.0.0/0" }) {
			assertThatThrownBy(() -> ValidacaoDeAmbiente.validar(producaoComProxy("X-Forwarded-For", lista)))
					.isInstanceOf(IllegalStateException.class).hasMessageContaining("Proxy confiável")
					.hasMessageContaining("explícita");
		}
	}

	@Test
	void deve_nao_exigir_a_lista_fora_de_producao() {
		MockEnvironment ambiente = ambienteValido().withProperty("server.tomcat.remoteip.remote-ip-header", "X-Forwarded-For");

		assertThatCode(() -> ValidacaoDeAmbiente.validar(ambiente)).doesNotThrowAnyException();
	}

}
