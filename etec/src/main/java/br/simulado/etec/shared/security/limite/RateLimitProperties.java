package br.simulado.etec.shared.security.limite;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Limites de abuso ({@code app.rate-limit.*}, D128). Nenhum número vive no código: tudo vem da configuração e é validado
 * na subida (inteiro positivo; janela de 1 segundo a 1 hora, o teto que mantém a retenção das linhas em menos de 1 dia).
 * Sem valor padrão: a propriedade ausente derruba a subida.
 *
 * @param authPorOrigem toda requisição POST de /auth/* por origem (só proteção contra enxurrada)
 * @param signinEmailOrigem falhas de signin por e-mail e origem
 * @param signinOrigem falhas de signin por origem
 * @param trocaSenhaUsuario erros da senha atual na troca de senha, por usuário (D139)
 * @param limpezaAtiva liga a limpeza agendada das linhas vencidas (desligada no perfil de teste)
 */
@ConfigurationProperties("app.rate-limit")
public record RateLimitProperties(Regra authPorOrigem, Regra signinEmailOrigem, Regra signinOrigem,
		Regra trocaSenhaUsuario, @DefaultValue("true") boolean limpezaAtiva) {

	public static final int LIMITE_MAXIMO = 1_000_000;

	public static final Duration JANELA_MINIMA = Duration.ofSeconds(1);

	public static final Duration JANELA_MAXIMA = Duration.ofHours(1);

	public RateLimitProperties {
		exigir(authPorOrigem, "auth-por-origem");
		exigir(signinEmailOrigem, "signin-email-origem");
		exigir(signinOrigem, "signin-origem");
		exigir(trocaSenhaUsuario, "troca-senha-usuario");
	}

	private static void exigir(Regra regra, String nome) {
		if (regra == null) {
			throw new IllegalArgumentException("app.rate-limit." + nome + " não está definido.");
		}
	}

	/** Um limite: no máximo {@code limite} no intervalo {@code janela}. */
	public record Regra(int limite, Duration janela) {

		public Regra {
			if (limite < 1 || limite > LIMITE_MAXIMO) {
				throw new IllegalArgumentException(
						"app.rate-limit: o limite precisa ser um inteiro de 1 a " + LIMITE_MAXIMO + ".");
			}
			if (janela == null || janela.compareTo(JANELA_MINIMA) < 0 || janela.compareTo(JANELA_MAXIMA) > 0) {
				throw new IllegalArgumentException("app.rate-limit: a janela precisa ficar entre 1 segundo e 1 hora.");
			}
		}

	}

}
