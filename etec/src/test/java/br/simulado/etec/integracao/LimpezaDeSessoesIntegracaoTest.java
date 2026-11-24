package br.simulado.etec.integracao;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import br.simulado.etec.auth.dto.TokensResponse;
import br.simulado.etec.auth.service.SessaoService;
import br.simulado.etec.shared.security.limite.RelogioDeTeste;
import br.simulado.etec.usuarios.service.UsuarioService;

/**
 * A limpeza dos refresh tokens (D137) contra MySQL real e o relógio de teste: só apaga o que venceu há MAIS de 1 dia; a linha
 * revogada e ainda não vencida fica (é ela que detecta o reuso). A limpeza agendada não roda sozinha nos testes.
 */
class LimpezaDeSessoesIntegracaoTest {

	private static final Instant T0 = Instant.parse("2026-11-23T10:00:00Z");

	private static long linhas(JdbcTemplate jdbc) {
		return jdbc.queryForObject("SELECT COUNT(*) FROM refresh_token", Long.class);
	}

	@Test
	void deve_apagar_so_as_vencidas_ha_mais_de_1_dia_e_manter_a_revogada_ainda_nao_vencida() throws SQLException {
		RelogioDeTeste relogio = new RelogioDeTeste(T0);
		try (AppDeLimite app = AppDeLimite.iniciar(AppDeLimite.novoSchema(), relogio, false)) {
			String email = app.criarAluno();
			SessaoService sessoes = app.contexto.getBean(SessaoService.class);
			var usuario = app.contexto.getBean(UsuarioService.class).buscarPorEmail(email).orElseThrow();
			TokensResponse ativo = sessoes.iniciar(usuario);
			TokensResponse revogado = sessoes.iniciar(usuario);
			sessoes.logout(revogado.refreshToken());
			JdbcTemplate jdbc = app.jdbc();
			assertThat(linhas(jdbc)).isEqualTo(2);
			assertThat(ativo.refreshToken()).isNotBlank();

			relogio.avancar(Duration.ofHours(2));
			assertThat(sessoes.limparVencidas()).as("nenhuma venceu: a revogada fica").isZero();
			assertThat(linhas(jdbc)).isEqualTo(2);

			relogio.avancar(Duration.ofHours(24)); // vencidas há ~2 h
			assertThat(sessoes.limparVencidas()).as("venceu há menos de 1 dia: fica").isZero();
			assertThat(linhas(jdbc)).isEqualTo(2);

			relogio.avancar(Duration.ofHours(24)); // vencidas há ~26 h
			assertThat(sessoes.limparVencidas()).as("venceu há mais de 1 dia: apaga").isEqualTo(2);
			assertThat(linhas(jdbc)).isZero();
		}
	}

}
