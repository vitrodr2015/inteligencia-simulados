package br.simulado.etec.shared.security.limite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DeadlockLoserDataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import br.simulado.etec.shared.security.limite.ContadorDeTentativas.Estado;

/**
 * O esgotamento das repetições por deadlock, SEM depender de um deadlock de verdade: o número de tentativas é injetável e o
 * JDBC é substituído por um que sempre lança a exceção de deadlock.
 */
class ContadorDeTentativasEsgotamentoTest {

	private static final Instant AGORA = Instant.parse("2026-11-23T10:00:00Z");

	private final JdbcTemplate jdbc = mock(JdbcTemplate.class);

	private final PlatformTransactionManager transacoes = mock(PlatformTransactionManager.class);

	@BeforeEach
	void montar() {
		when(transacoes.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
	}

	@Test
	void deve_repetir_a_transacao_inteira_ate_o_limite_de_tentativas_e_so_entao_lancar() {
		when(jdbc.update(anyString(), any(Object[].class))).thenThrow(new DeadlockLoserDataAccessException("deadlock", null));
		ContadorDeTentativas contador = new ContadorDeTentativas(jdbc, transacoes, 3);

		assertThatThrownBy(() -> contador.contar("c".repeat(64), 5, Duration.ofMinutes(15), AGORA))
				.isInstanceOf(ConcurrencyFailureException.class);

		verify(transacoes, times(3)).getTransaction(any());
	}

	@Test
	void deve_dar_certo_quando_uma_repeticao_dentro_do_limite_passa() {
		when(jdbc.update(anyString(), any(Object[].class))).thenThrow(new DeadlockLoserDataAccessException("deadlock", null))
				.thenReturn(1);
		when(jdbc.queryForObject(anyString(), any(RowMapper.class), eq("c".repeat(64)))).thenReturn(new Estado(2, AGORA.plusSeconds(900)));
		ContadorDeTentativas contador = new ContadorDeTentativas(jdbc, transacoes, 3);

		assertThat(contador.contar("c".repeat(64), 5, Duration.ofMinutes(15), AGORA).contador()).isEqualTo(2);

		verify(transacoes, times(2)).getTransaction(any());
	}

	@Test
	void deve_repassar_sem_repetir_o_erro_que_nao_e_de_disputa_de_lock() {
		when(jdbc.update(anyString(), any(Object[].class))).thenThrow(new DataAccessResourceFailureException("banco fora"));
		ContadorDeTentativas contador = new ContadorDeTentativas(jdbc, transacoes, 3);

		assertThatThrownBy(() -> contador.contar("c".repeat(64), 5, Duration.ofMinutes(15), AGORA))
				.isInstanceOf(DataAccessResourceFailureException.class);

		verify(transacoes, times(1)).getTransaction(any());
	}

}
