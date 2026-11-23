package br.simulado.etec.shared.security.limite;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Acesso à tabela {@code limite_tentativa} por SQL atômico. CADA operação roda numa transação PRÓPRIA e curta
 * ({@code REQUIRES_NEW}), por duas razões:
 * <ol>
 * <li>o incremento grava mesmo quando a requisição termina em 401 (a exceção do service não leva o contador junto no
 * rollback);</li>
 * <li>o lock da linha dura só o tempo de uma instrução, nunca o do BCrypt.</li>
 * </ol>
 * <p><b>Não envolva o signin (nem qualquer chamador) em {@code @Transactional}.</b> O {@code REQUIRES_NEW} pega uma SEGUNDA
 * conexão do pool: com uma transação de fora aberta, um pool esgotado travaria as duas pontas, e, pior, uma transação de
 * fora que desfaz não pode levar o contador junto (ele existe para contar justamente as requisições que terminam em erro).
 *
 * O SQL é o mesmo para MySQL e H2 (UPDATE, depois INSERT se a linha não existe, e de novo UPDATE se outra requisição
 * inseriu antes). Todo instante vem do chamador (o {@code Clock}), em UTC e em milissegundos; nada usa {@code NOW()}.
 *
 * <p><b>Ordem das atribuições.</b> No UPDATE do MySQL as colunas do SET são avaliadas da esquerda para a direita, e uma
 * já alterada vale para as seguintes. Por isso {@code contador} e {@code janela_inicio} vêm ANTES de {@code janela_fim}:
 * todas decidem "a janela venceu?" pela janela_fim ANTIGA, e a janela_fim só muda por último.
 */
public class ContadorDeTentativas {

	/** Quantas vezes se repete o UPDATE quando a linha some no meio (limpeza ou sucesso concorrente). */
	private static final int TENTATIVAS_DO_UPSERT = 3;

	/**
	 * Quantas vezes a transação inteira se repete quando o MySQL escolhe uma delas como vítima de deadlock (ou estoura a
	 * espera de lock). Com muitas requisições criando a MESMA linha ao mesmo tempo isso acontece de verdade: o INSERT que dá
	 * chave duplicada pega um lock compartilhado e o UPDATE seguinte pede o exclusivo. A vítima foi desfeita por inteiro, e
	 * repetir é seguro (o UPDATE agora acha a linha).
	 */
	static final int TENTATIVAS_PADRAO_DA_TRANSACAO = 5;

	/** Estado do contador depois de contar: quanto vale e quando a janela fecha. */
	public record Estado(int contador, Instant janelaFim) {
	}

	private final JdbcTemplate jdbc;

	private final TransactionTemplate transacao;

	private final int tentativasDaTransacao;

	public ContadorDeTentativas(JdbcTemplate jdbc, PlatformTransactionManager gerenteDeTransacao) {
		this(jdbc, gerenteDeTransacao, TENTATIVAS_PADRAO_DA_TRANSACAO);
	}

	/** O número de repetições é injetável só para o teste provocar o esgotamento sem depender de um deadlock de verdade. */
	ContadorDeTentativas(JdbcTemplate jdbc, PlatformTransactionManager gerenteDeTransacao, int tentativasDaTransacao) {
		this.tentativasDaTransacao = tentativasDaTransacao;
		this.jdbc = jdbc;
		this.transacao = new TransactionTemplate(gerenteDeTransacao);
		this.transacao.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
	}

	/**
	 * Conta mais uma ocorrência na chave. Janela vencida reinicia o contador em 1; janela viva soma 1, e o contador NUNCA
	 * passa de {@code limite + 1}.
	 */
	public Estado contar(String chave, int limite, Duration janela, Instant agora) {
		LocalDateTime t = utc(agora);
		LocalDateTime fim = utc(agora.plus(janela));
		return executar(status -> {
			for (int i = 0; i < TENTATIVAS_DO_UPSERT; i++) {
				int alteradas = jdbc.update("UPDATE limite_tentativa SET "
						+ "contador = CASE WHEN janela_fim <= ? THEN 1 ELSE LEAST(contador + 1, ?) END, "
						+ "janela_inicio = CASE WHEN janela_fim <= ? THEN ? ELSE janela_inicio END, "
						+ "janela_fim = CASE WHEN janela_fim <= ? THEN ? ELSE janela_fim END, "
						+ "atualizado_em = ? WHERE chave = ?", t, limite + 1, t, t, t, fim, t, chave);
				if (alteradas == 0) {
					try {
						jdbc.update("INSERT INTO limite_tentativa (chave, contador, janela_inicio, janela_fim, criado_em, "
								+ "atualizado_em) VALUES (?, 1, ?, ?, ?, ?)", chave, t, fim, t, t);
					}
					catch (DuplicateKeyException e) {
						continue; // outra requisição inseriu antes: agora o UPDATE acha a linha
					}
				}
				return jdbc.queryForObject("SELECT contador, janela_fim FROM limite_tentativa WHERE chave = ?",
						(rs, n) -> new Estado(rs.getInt(1),
								rs.getObject(2, LocalDateTime.class).toInstant(ZoneOffset.UTC)),
						chave);
			}
			throw new IllegalStateException("Não foi possível atualizar o contador do limite.");
		});
	}

	/** Devolve uma ocorrência, sem passar de zero, e só se a janela ainda está viva. */
	public void devolverUma(String chave, Instant agora) {
		LocalDateTime t = utc(agora);
		executar(status -> jdbc.update(
				"UPDATE limite_tentativa SET contador = GREATEST(contador - 1, 0), atualizado_em = ? "
						+ "WHERE chave = ? AND janela_fim > ?",
				t, chave, t));
	}

	/** Apaga o contador da chave (o sucesso do login zera o e-mail e origem). */
	public void apagar(String chave) {
		executar(status -> jdbc.update("DELETE FROM limite_tentativa WHERE chave = ?", chave));
	}

	/** Apaga até {@code lote} linhas de janela vencida; devolve quantas apagou. */
	public int limparVencidas(Instant agora, int lote) {
		LocalDateTime t = utc(agora);
		Integer apagadas = executar(
				status -> jdbc.update("DELETE FROM limite_tentativa WHERE janela_fim < ? LIMIT " + lote, t));
		return (apagadas == null) ? 0 : apagadas;
	}

	/** Roda numa transação própria e a repete se o MySQL a escolheu como vítima de deadlock. */
	private <T> T executar(TransactionCallback<T> acao) {
		ConcurrencyFailureException ultima = null;
		for (int i = 0; i < tentativasDaTransacao; i++) {
			try {
				return transacao.execute(acao);
			}
			catch (ConcurrencyFailureException e) {
				ultima = e;
			}
		}
		throw ultima;
	}

	private static LocalDateTime utc(Instant instante) {
		return LocalDateTime.ofInstant(instante.truncatedTo(ChronoUnit.MILLIS), ZoneOffset.UTC);
	}

}
