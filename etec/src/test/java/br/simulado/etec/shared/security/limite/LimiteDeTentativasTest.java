package br.simulado.etec.shared.security.limite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import br.simulado.etec.shared.exception.LimiteExcedidoException;
import br.simulado.etec.shared.security.JwtProperties;
import br.simulado.etec.shared.security.limite.ContadorDeTentativas.Estado;
import br.simulado.etec.shared.security.limite.RateLimitProperties.Regra;

class LimiteDeTentativasTest {

	private static final Regra REGRA = new Regra(5, Duration.ofMinutes(15));

	private final RelogioDeTeste relogio = new RelogioDeTeste(Instant.parse("2026-11-23T10:00:00Z"));

	private final ContadorDeTentativas contadores = mock(ContadorDeTentativas.class);

	private final LimiteDeTentativas limite = new LimiteDeTentativas(contadores,
			new ChavesDoLimite(new JwtProperties("segredo-de-teste-falso-nao-e-real-0123456789", "emissor",
					Duration.ofMinutes(15), Duration.ofHours(24))),
			relogio);

	private void contadorEm(int valor, Instant fim) {
		when(contadores.contar(anyString(), eq(5), eq(Duration.ofMinutes(15)), any())).thenReturn(new Estado(valor, fim));
	}

	@Test
	void deve_deixar_passar_ate_o_limite_inclusive() {
		contadorEm(5, relogio.instant().plusSeconds(900));

		assertThatCode(() -> limite.consumir(Escopo.SIGNIN_ORIGEM, "v4:10.0.0.1", REGRA)).doesNotThrowAnyException();
	}

	@Test
	void deve_recusar_acima_do_limite_com_o_tempo_ate_a_janela_fechar() {
		contadorEm(6, relogio.instant().plusSeconds(900));

		assertThatThrownBy(() -> limite.consumir(Escopo.SIGNIN_ORIGEM, "v4:10.0.0.1", REGRA))
				.isExactlyInstanceOf(LimiteExcedidoException.class)
				.extracting(e -> ((LimiteExcedidoException) e).getRetryAfterSegundos()).isEqualTo(900L);
	}

	@Test
	void deve_arredondar_o_retry_after_para_cima_e_nunca_devolver_menos_de_1() {
		Instant agora = Instant.parse("2026-11-23T10:00:00Z");

		assertThat(LimiteDeTentativas.segundosAte(agora.plusMillis(1), agora)).isEqualTo(1);
		assertThat(LimiteDeTentativas.segundosAte(agora.plusMillis(1000), agora)).isEqualTo(1);
		assertThat(LimiteDeTentativas.segundosAte(agora.plusMillis(1001), agora)).isEqualTo(2);
		assertThat(LimiteDeTentativas.segundosAte(agora.plusSeconds(900), agora)).isEqualTo(900);
		assertThat(LimiteDeTentativas.segundosAte(agora, agora)).isEqualTo(1);
		assertThat(LimiteDeTentativas.segundosAte(agora.minusSeconds(5), agora)).isEqualTo(1);
	}

	@Test
	void deve_usar_o_relogio_injetado_e_chave_que_nao_contem_o_valor() {
		contadorEm(1, relogio.instant().plusSeconds(900));

		limite.consumir(Escopo.SIGNIN_EMAIL_ORIGEM, "v4:10.0.0.1|ana@exemplo.invalid", REGRA);

		verify(contadores).contar(org.mockito.ArgumentMatchers.argThat(chave -> chave.matches("[0-9a-f]{64}")), eq(5),
				eq(Duration.ofMinutes(15)), eq(Instant.parse("2026-11-23T10:00:00Z")));
	}

	@Test
	void deve_limpar_em_lotes_ate_esvaziar() {
		when(contadores.limparVencidas(any(), anyInt())).thenReturn(1000, 1000, 37);

		assertThat(limite.limparVencidas()).isEqualTo(2037);
	}


	// ----- falha FECHADA quando o contador não consegue gravar (deadlock que esgotou as repetições) -----

	@Test
	void deve_tratar_como_limite_excedido_com_retry_after_curto_e_fixo_quando_o_contador_esgota_as_repeticoes() {
		when(contadores.contar(anyString(), eq(5), eq(Duration.ofMinutes(15)), any()))
				.thenThrow(new org.springframework.dao.DeadlockLoserDataAccessException("deadlock", null));

		assertThatThrownBy(() -> limite.consumir(Escopo.SIGNIN_ORIGEM, "v4:10.0.0.1", REGRA))
				.as("nunca passa sem contar e nunca vira 500")
				.isExactlyInstanceOf(LimiteExcedidoException.class)
				.hasMessage(LimiteExcedidoException.MENSAGEM)
				.extracting(e -> ((LimiteExcedidoException) e).getRetryAfterSegundos())
				.isEqualTo(LimiteDeTentativas.RETRY_AFTER_POR_CONTENCAO_SEGUNDOS);
	}

	@Test
	void deve_deixar_o_erro_que_nao_e_de_lock_seguir_como_erro_interno_e_nao_como_429() {
		when(contadores.contar(anyString(), eq(5), eq(Duration.ofMinutes(15)), any()))
				.thenThrow(new org.springframework.dao.DataAccessResourceFailureException("banco fora"));

		assertThatThrownBy(() -> limite.consumir(Escopo.SIGNIN_ORIGEM, "v4:10.0.0.1", REGRA))
				.isInstanceOf(org.springframework.dao.DataAccessResourceFailureException.class);
	}

	@Test
	void deve_ignorar_a_falha_de_lock_ao_zerar_ou_devolver_porque_a_ocorrencia_ja_foi_contada() {
		org.mockito.Mockito.doThrow(new org.springframework.dao.CannotAcquireLockException("lock")).when(contadores)
				.apagar(anyString());
		org.mockito.Mockito.doThrow(new org.springframework.dao.CannotAcquireLockException("lock")).when(contadores)
				.devolverUma(anyString(), any());

		assertThatCode(() -> limite.zerar(Escopo.SIGNIN_EMAIL_ORIGEM, "x")).doesNotThrowAnyException();
		assertThatCode(() -> limite.devolver(Escopo.SIGNIN_ORIGEM, "x")).doesNotThrowAnyException();
	}

}
