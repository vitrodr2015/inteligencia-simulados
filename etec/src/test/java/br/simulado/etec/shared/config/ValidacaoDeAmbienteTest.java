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

}
