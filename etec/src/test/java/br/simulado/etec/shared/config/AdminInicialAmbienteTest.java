package br.simulado.etec.shared.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.env.MockEnvironment;

import br.simulado.etec.shared.config.AdminInicialAmbiente.Ausente;
import br.simulado.etec.shared.config.AdminInicialAmbiente.Invalida;
import br.simulado.etec.shared.config.AdminInicialAmbiente.Valida;

class AdminInicialAmbienteTest {

	private static final String SENHA_OK = "Senha-Do-Admin-123";

	private static MockEnvironment ambiente(String email, String senha) {
		MockEnvironment ambiente = new MockEnvironment();
		if (email != null) {
			ambiente.setProperty("ADMIN_EMAIL", email);
		}
		if (senha != null) {
			ambiente.setProperty("ADMIN_SENHA", senha);
		}
		return ambiente;
	}

	private static Invalida invalida(String email, String senha) {
		return (Invalida) AdminInicialAmbiente.ler(ambiente(email, senha));
	}

	@Test
	void deve_ser_ausente_quando_nenhuma_das_duas_esta_definida_ou_ambas_estao_em_branco() {
		assertThat(AdminInicialAmbiente.ler(ambiente(null, null))).isInstanceOf(Ausente.class);
		assertThat(AdminInicialAmbiente.ler(ambiente("", ""))).isInstanceOf(Ausente.class);
		assertThat(AdminInicialAmbiente.ler(ambiente("   ", "\t\n "))).isInstanceOf(Ausente.class);
	}

	@Test
	void deve_tratar_valor_em_branco_como_ausente_mesmo_com_a_outra_definida() {
		// uma variável vazia no ambiente sobrepõe o arquivo local: é assim que se simula "sem variáveis"
		assertThat(invalida("admin@exemplo.invalid", "").motivos()).singleElement().asString()
				.contains("ADMIN_SENHA não foi definida");
		assertThat(invalida("", SENHA_OK).motivos()).singleElement().asString().contains("ADMIN_EMAIL não foi definida");
	}

	@Test
	void deve_exigir_as_duas_variaveis_juntas() {
		assertThat(invalida(null, SENHA_OK).motivos()).hasSize(1);
		assertThat(invalida("admin@exemplo.invalid", null).motivos()).hasSize(1);
	}

	@Test
	void deve_aceitar_e_normalizar_o_email_e_manter_a_senha_exatamente_como_veio() {
		Valida valida = (Valida) AdminInicialAmbiente.ler(ambiente("  Admin.Teste@Exemplo.INVALID  ", SENHA_OK));

		assertThat(valida.email()).isEqualTo("admin.teste@exemplo.invalid");
		assertThat(valida.senha()).isEqualTo(SENHA_OK);
	}

	@Test
	void deve_respeitar_os_limites_de_12_a_72_bytes_da_senha() {
		assertThat(AdminInicialAmbiente.ler(ambiente("a@exemplo.invalid", "a".repeat(11)))).isInstanceOf(Invalida.class);
		assertThat(AdminInicialAmbiente.ler(ambiente("a@exemplo.invalid", "a".repeat(12)))).isInstanceOf(Valida.class);
		assertThat(AdminInicialAmbiente.ler(ambiente("a@exemplo.invalid", "a".repeat(72)))).isInstanceOf(Valida.class);
		assertThat(AdminInicialAmbiente.ler(ambiente("a@exemplo.invalid", "a".repeat(73)))).isInstanceOf(Invalida.class);
	}

	@Test
	void deve_contar_a_senha_em_bytes_utf8_e_nao_em_caracteres() {
		assertThat(AdminInicialAmbiente.ler(ambiente("a@exemplo.invalid", "é".repeat(6)))).as("12 bytes")
				.isInstanceOf(Valida.class);
		assertThat(AdminInicialAmbiente.ler(ambiente("a@exemplo.invalid", "é".repeat(5)))).as("10 bytes")
				.isInstanceOf(Invalida.class);
		assertThat(AdminInicialAmbiente.ler(ambiente("a@exemplo.invalid", "é".repeat(36)))).as("72 bytes")
				.isInstanceOf(Valida.class);
		assertThat(AdminInicialAmbiente.ler(ambiente("a@exemplo.invalid", "é".repeat(37)))).as("74 bytes")
				.isInstanceOf(Invalida.class);
	}

	@ParameterizedTest
	@ValueSource(strings = { " Senha-Do-Admin-123", "Senha-Do-Admin-123 ", "Senha-Do-Admin-123\n",
			"Senha-Do-Admin-123\r\n", "\tSenha-Do-Admin-123", "Senha-Do-Admin-123\t" })
	void deve_recusar_espaco_tabulacao_ou_quebra_de_linha_nas_pontas_da_senha(String senha) {
		assertThat(invalida("a@exemplo.invalid", senha).motivos()).anyMatch(m -> m.contains("início ou no fim"));
	}

	@ParameterizedTest
	@ValueSource(strings = { "Senha-Do\nAdmin-123", "Senha-Do\rAdmin-123", "Senha-Do\r\nAdmin-123" })
	void deve_recusar_quebra_de_linha_em_qualquer_posicao_da_senha(String senha) {
		assertThat(invalida("a@exemplo.invalid", senha).motivos()).anyMatch(m -> m.contains("quebra de linha"));
	}

	@Test
	void deve_aceitar_espaco_no_meio_da_senha() {
		assertThat(AdminInicialAmbiente.ler(ambiente("a@exemplo.invalid", "frase longa com espacos")))
				.isInstanceOf(Valida.class);
	}

	@ParameterizedTest
	@ValueSource(strings = { "sem-arroba", "a@b", "josé@exemplo.invalid", "a b@exemplo.invalid", "@exemplo.invalid",
			"a@exemplo..invalid" })
	void deve_recusar_email_invalido_ou_fora_do_ascii_como_no_cadastro(String email) {
		assertThat(invalida(email, SENHA_OK).motivos()).singleElement().asString().contains("ADMIN_EMAIL");
	}

	@Test
	void deve_recusar_email_com_mais_de_255_caracteres() {
		assertThat(invalida("a".repeat(250) + "@exemplo.invalid", SENHA_OK).motivos()).singleElement().asString()
				.contains("255");
	}

	@Test
	void deve_nunca_por_o_valor_das_variaveis_nos_motivos_nem_no_texto_do_resultado() {
		String email = "valor-secreto-no-email";
		String senha = "curta";
		Invalida invalida = invalida(email, senha);

		assertThat(invalida.motivos().toString()).doesNotContain(email).doesNotContain(senha);
		assertThat(invalida.resumo()).doesNotContain(email).doesNotContain(senha);
		assertThat(invalida.toString()).doesNotContain(email).doesNotContain(senha);
		Valida valida = (Valida) AdminInicialAmbiente.ler(ambiente("quem@exemplo.invalid", SENHA_OK));
		assertThat(valida.toString()).doesNotContain("quem").doesNotContain(SENHA_OK).contains("***");
	}

	@Test
	void deve_listar_todos_os_motivos_juntos() {
		assertThat(invalida("sem-arroba", "curta\n").motivos()).hasSizeGreaterThanOrEqualTo(3);
	}

}
