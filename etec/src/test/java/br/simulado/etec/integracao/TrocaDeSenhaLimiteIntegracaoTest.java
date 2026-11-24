package br.simulado.etec.integracao;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;
import java.time.Duration;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

import br.simulado.etec.shared.security.limite.RelogioDeTeste;
import io.restassured.response.Response;

/**
 * O limite da troca de senha (D139) por HTTP real, MySQL real e o relógio de teste: errar a senha atual conta num bucket
 * POR USUÁRIO (5 por 15 minutos), "conta antes" (o 429 vem antes do 400 de formato), zera no sucesso, e o signin continua
 * usando o limite do 13A.
 */
class TrocaDeSenhaLimiteIntegracaoTest {

	private static final String[] LIMITES = { "--app.rate-limit.troca-senha-usuario.limite=5",
			"--app.rate-limit.troca-senha-usuario.janela=15m", "--app.rate-limit.signin-email-origem.limite=5",
			"--app.rate-limit.signin-email-origem.janela=15m", "--app.rate-limit.signin-origem.limite=100000",
			"--app.rate-limit.auth-por-origem.limite=100000" };

	private static String access(AppDeLimite app, String email, String senha) {
		Response r = app.signin(email, senha);
		r.then().statusCode(200);
		return r.jsonPath().getString("accessToken");
	}

	private static Response trocar(AppDeLimite app, String access, String atual, String nova) {
		return app.http().header("Authorization", "Bearer " + access)
				.body("{\"senhaAtual\":\"" + atual + "\",\"novaSenha\":\"" + nova + "\"}").post("/api/v1/me/senha");
	}

	@Test
	void deve_dar_429_na_6a_falha_da_senha_atual_antes_do_400_voltar_depois_da_janela_e_zerar_no_sucesso() throws SQLException {
		try (AppDeLimite app = AppDeLimite.iniciar(AppDeLimite.novoSchema(), new RelogioDeTeste(), true, LIMITES)) {
			String email = app.criarAluno();
			String access = access(app, email, AppDeLimite.SENHA_DO_ALUNO);

			for (int i = 0; i < 5; i++) {
				trocar(app, access, "errada-" + i, "nova-senha-123").then().statusCode(400)
						.body("codigo", Matchers.equalTo("SENHA_ATUAL_INCORRETA"));
			}
			int comparacoesAteAqui = app.comparacoes.get();

			// a 6ª: 429 mesmo com a senha atual CERTA e até com corpo vazio (o 429 vem antes do 400 de formato)
			trocar(app, access, AppDeLimite.SENHA_DO_ALUNO, "nova-senha-123").then().statusCode(429).header("Retry-After", "900")
					.header("Cache-Control", "no-store").body("codigo", Matchers.equalTo("LIMITE_EXCEDIDO"));
			app.http().header("Authorization", "Bearer " + access).body("{}").post("/api/v1/me/senha").then().statusCode(429);
			assertThat(app.comparacoes.get()).as("o 429 não gasta BCrypt").isEqualTo(comparacoesAteAqui);

			// passada a janela, volta (o access de 15 min venceu junto: entra de novo) e o sucesso zera o contador
			app.relogio.avancar(Duration.ofSeconds(900));
			String novoAccess = access(app, email, AppDeLimite.SENHA_DO_ALUNO);
			trocar(app, novoAccess, AppDeLimite.SENHA_DO_ALUNO, "nova-senha-123").then().statusCode(204);
			String depois = access(app, email, "nova-senha-123");
			for (int i = 0; i < 5; i++) {
				trocar(app, depois, "errada-" + i, "outra-senha-456").then().statusCode(400);
			}
			trocar(app, depois, "errada-6", "outra-senha-456").then().statusCode(429);
		}
	}

	@Test
	void deve_contar_por_usuario_e_nao_por_origem_nem_afetar_o_login() throws SQLException {
		try (AppDeLimite app = AppDeLimite.iniciar(AppDeLimite.novoSchema(), new RelogioDeTeste(), true, LIMITES)) {
			String email = app.criarAluno();
			String outro = app.criarAluno();
			String access = access(app, email, AppDeLimite.SENHA_DO_ALUNO);
			String accessOutro = access(app, outro, AppDeLimite.SENHA_DO_ALUNO);
			for (int i = 0; i < 6; i++) {
				trocar(app, access, "errada-" + i, "nova-senha-123");
			}

			trocar(app, access, "errada", "nova-senha-123").then().statusCode(429);
			trocar(app, accessOutro, "errada", "nova-senha-123").then().statusCode(400);
			app.signin(email, AppDeLimite.SENHA_DO_ALUNO).then().statusCode(200);
		}
	}

	@Test
	void deve_manter_o_limite_do_signin_do_13a_funcionando() throws SQLException {
		try (AppDeLimite app = AppDeLimite.iniciar(AppDeLimite.novoSchema(), new RelogioDeTeste(), true, LIMITES)) {
			String email = app.criarAluno();

			for (int i = 0; i < 5; i++) {
				app.signin(email, "errada-" + i).then().statusCode(401);
			}

			app.signin(email, "errada-6").then().statusCode(429).header("Retry-After", "900");
			app.signin(email, AppDeLimite.SENHA_DO_ALUNO).then().statusCode(429);
		}
	}

}
