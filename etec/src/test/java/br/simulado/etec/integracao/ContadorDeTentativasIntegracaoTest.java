package br.simulado.etec.integracao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import br.simulado.etec.shared.security.limite.ContadorDeTentativas;
import br.simulado.etec.shared.security.limite.ContadorDeTentativas.Estado;
import br.simulado.etec.shared.security.limite.RelogioDeTeste;

/**
 * O contador contra MySQL REAL: o UPSERT (a ordem das atribuições do SET), o teto de limite + 1, a concorrência (cada
 * ocorrência recebe um número diferente) e a transação própria (o incremento grava mesmo que a transação de fora desfaça).
 * Todo instante é passado pelo teste (nenhum {@code Thread.sleep}, nenhum {@code NOW()}).
 */
class ContadorDeTentativasIntegracaoTest {

	private static final Duration JANELA = Duration.ofMinutes(15);

	private static final Instant T0 = Instant.parse("2026-11-23T10:00:00Z");

	private static AppDeLimite app;

	private static ContadorDeTentativas contador;

	private static JdbcTemplate jdbc;

	private String chave;

	@BeforeAll
	static void subir() throws SQLException {
		app = AppDeLimite.iniciar(AppDeLimite.novoSchema(), new RelogioDeTeste(T0), false);
		contador = app.contexto.getBean(ContadorDeTentativas.class);
		jdbc = app.jdbc();
	}

	@AfterAll
	static void descer() {
		app.close();
	}

	@BeforeEach
	void novaChave() {
		chave = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
	}

	private Map<String, Object> linha() {
		return jdbc.queryForMap("SELECT contador, janela_inicio, janela_fim FROM limite_tentativa WHERE chave = ?", chave);
	}

	private static Instant instante(Object valor) {
		return ((LocalDateTime) valor).toInstant(ZoneOffset.UTC);
	}

	@Test
	void deve_criar_a_linha_com_contador_1_e_a_janela_a_partir_de_agora() {
		Estado estado = contador.contar(chave, 5, JANELA, T0);

		assertThat(estado.contador()).isEqualTo(1);
		assertThat(estado.janelaFim()).isEqualTo(T0.plus(JANELA));
		assertThat(instante(linha().get("janela_inicio"))).isEqualTo(T0);
	}

	@Test
	void deve_somar_1_e_manter_a_janela_enquanto_ela_esta_viva() {
		contador.contar(chave, 5, JANELA, T0);

		Estado segundo = contador.contar(chave, 5, JANELA, T0.plusSeconds(60));
		Estado ultimoInstanteVivo = contador.contar(chave, 5, JANELA, T0.plus(JANELA).minusMillis(1));

		assertThat(segundo.contador()).isEqualTo(2);
		assertThat(ultimoInstanteVivo.contador()).as("1 ms antes de fechar ainda é a mesma janela").isEqualTo(3);
		assertThat(instante(linha().get("janela_inicio"))).as("a janela não anda").isEqualTo(T0);
		assertThat(instante(linha().get("janela_fim"))).isEqualTo(T0.plus(JANELA));
	}

	@Test
	void deve_reiniciar_o_contador_e_a_janela_quando_a_janela_venceu() {
		for (int i = 0; i < 4; i++) {
			contador.contar(chave, 3, JANELA, T0);
		}
		assertThat(linha().get("contador")).isEqualTo(4);
		Instant depois = T0.plus(JANELA); // exatamente no fim: a janela venceu

		Estado reiniciado = contador.contar(chave, 3, JANELA, depois);

		assertThat(reiniciado.contador()).as("vencida reinicia em 1").isEqualTo(1);
		assertThat(reiniciado.janelaFim()).isEqualTo(depois.plus(JANELA));
		assertThat(instante(linha().get("janela_inicio"))).isEqualTo(depois);
		assertThat(instante(linha().get("janela_fim"))).isEqualTo(depois.plus(JANELA));
	}

	@Test
	void deve_nunca_passar_de_limite_mais_1() {
		Estado ultimo = null;
		for (int i = 0; i < 20; i++) {
			ultimo = contador.contar(chave, 3, JANELA, T0);
		}

		assertThat(ultimo.contador()).isEqualTo(4);
		assertThat(linha().get("contador")).isEqualTo(4);
	}

	@Test
	void deve_devolver_uma_ocorrencia_sem_passar_de_zero_e_so_com_a_janela_viva() {
		contador.contar(chave, 5, JANELA, T0);
		contador.contar(chave, 5, JANELA, T0);

		contador.devolverUma(chave, T0);
		assertThat(linha().get("contador")).isEqualTo(1);
		contador.devolverUma(chave, T0);
		contador.devolverUma(chave, T0);
		contador.devolverUma(chave, T0);
		assertThat(linha().get("contador")).as("nunca abaixo de zero").isEqualTo(0);

		contador.contar(chave, 5, JANELA, T0);
		contador.devolverUma(chave, T0.plus(JANELA));
		assertThat(linha().get("contador")).as("janela vencida: nada a devolver").isEqualTo(1);
	}

	@Test
	void deve_apagar_a_linha_da_chave() {
		contador.contar(chave, 5, JANELA, T0);

		contador.apagar(chave);

		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM limite_tentativa WHERE chave = ?", Integer.class, chave))
				.isZero();
	}

	@Test
	void deve_limpar_so_as_linhas_vencidas_e_em_lotes() {
		jdbc.update("DELETE FROM limite_tentativa");
		for (int i = 0; i < 5; i++) {
			contador.contar("vencida-" + i + "-".repeat(40), 5, JANELA, T0);
		}
		contador.contar("viva-" + "-".repeat(40), 5, JANELA, T0.plus(Duration.ofHours(2)));
		Instant agora = T0.plus(Duration.ofHours(1));

		assertThat(contador.limparVencidas(agora, 3)).as("lote de 3").isEqualTo(3);
		assertThat(contador.limparVencidas(agora, 3)).isEqualTo(2);
		assertThat(contador.limparVencidas(agora, 3)).isZero();
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM limite_tentativa", Integer.class)).as("a viva fica").isEqualTo(1);
	}

	@Test
	void deve_dar_um_numero_diferente_a_cada_ocorrencia_sob_concorrencia_inclusive_na_criacao_da_linha() throws Exception {
		int threads = 40;
		ExecutorService pool = Executors.newFixedThreadPool(threads);
		CountDownLatch largada = new CountDownLatch(1);
		try {
			var futuros = IntStream.range(0, threads).mapToObj(i -> pool.submit(() -> {
				largada.await();
				return contador.contar(chave, 1000, JANELA, T0).contador();
			})).toList();
			largada.countDown();
			Set<Integer> numeros = futuros.stream().map(this::resultado).collect(Collectors.toSet());

			assertThat(numeros).as("40 ocorrências, 40 números distintos, de 1 a 40")
					.containsExactlyInAnyOrderElementsOf(IntStream.rangeClosed(1, threads).boxed().toList());
			assertThat(linha().get("contador")).isEqualTo(threads);
		}
		finally {
			pool.shutdownNow();
		}
	}

	@Test
	void deve_gravar_o_incremento_mesmo_quando_a_transacao_de_fora_e_desfeita() {
		TransactionTemplate externa = new TransactionTemplate(app.contexto.getBean(PlatformTransactionManager.class));

		assertThatThrownBy(() -> externa.executeWithoutResult(status -> {
			contador.contar(chave, 5, JANELA, T0);
			throw new IllegalStateException("a requisição terminou em erro");
		})).isInstanceOf(IllegalStateException.class);

		assertThat(linha().get("contador")).as("o rollback de fora não leva o contador").isEqualTo(1);
	}

	private Integer resultado(Future<Integer> futuro) {
		try {
			return futuro.get();
		}
		catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

}
