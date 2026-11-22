package br.simulado.etec.auth.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

/** O {@code toString} automático dos records imprimiria a senha e os tokens; os DTOs o sobrescrevem. */
class DtosDeEntradaToStringTest {

	private static final String SENHA = "SENHA-SECRETA-DE-TESTE-9";

	private static final String TOKEN = "eyJ-TOKEN-SECRETO-DE-TESTE.payload.assinatura";

	@Test
	void deve_esconder_a_senha_no_texto_do_registro() {
		var dados = new RegistroRequest("Ana", "ana@exemplo.invalid", SENHA, LocalDate.of(2008, 5, 10), "v1");

		assertThat(dados.toString()).doesNotContain(SENHA).contains("senha=***").contains("ana@exemplo.invalid");
		assertThat(String.valueOf(dados)).doesNotContain(SENHA);
		assertThat(("log: " + dados)).doesNotContain(SENHA);
	}

	@Test
	void deve_esconder_a_senha_no_texto_do_signin() {
		var dados = new SigninRequest("ana@exemplo.invalid", SENHA);

		assertThat(dados.toString()).doesNotContain(SENHA).contains("senha=***");
	}

	@Test
	void deve_esconder_o_token_no_texto_do_refresh() {
		assertThat(new RefreshRequest(TOKEN).toString()).doesNotContain(TOKEN).contains("***");
	}

	@Test
	void deve_esconder_os_dois_tokens_no_texto_da_resposta() {
		var resposta = new TokensResponse(TOKEN + "-access", TOKEN + "-refresh", "Bearer", 900);

		assertThat(resposta.toString()).doesNotContain("TOKEN-SECRETO").contains("Bearer").contains("900");
	}

	@Test
	void deve_normalizar_nome_e_email_e_nunca_a_senha() {
		var dados = new RegistroRequest("  Ana  ", "  ANA@Exemplo.Invalid ", "  senha com espacos  ",
				LocalDate.of(2008, 5, 10), "v1");

		assertThat(dados.nome()).isEqualTo("Ana");
		assertThat(dados.email()).isEqualTo("ana@exemplo.invalid");
		assertThat(dados.senha()).isEqualTo("  senha com espacos  ");
		assertThat(new SigninRequest("  ANA@Exemplo.Invalid ", "  x ").senha()).isEqualTo("  x ");
	}

}
