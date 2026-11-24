package br.simulado.etec.auth.repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import br.simulado.etec.auth.model.MotivoRevogacao;
import br.simulado.etec.auth.model.RefreshToken;

/**
 * Todas as mudanças de estado são UPDATE condicional (atômico): o que decide "quem ganhou" é o número de linhas afetadas,
 * nunca uma leitura anterior. O {@code atualizado_em} é gravado à mão (UPDATE em lote não passa pela auditoria).
 */
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

	Optional<RefreshToken> findByJti(UUID jti);

	/**
	 * Revoga o refresh SE ele ainda está ativo (não revogado e não vencido). Devolve 1 para quem ganhou e 0 para todos os
	 * outros, inclusive duas requisições simultâneas com o mesmo token: exatamente uma ganha.
	 */
	@Transactional
	@Modifying(flushAutomatically = true)
	@Query("update RefreshToken r set r.revogadoEm = :agora, r.motivoRevogacao = :motivo, r.atualizadoEm = :agora "
			+ "where r.jti = :jti and r.revogadoEm is null and r.expiraEm > :agora")
	int revogarSeAtivo(@Param("jti") UUID jti, @Param("agora") Instant agora, @Param("motivo") MotivoRevogacao motivo);

	/** Revoga todos os refresh ainda não revogados do usuário (D49). Devolve quantos revogou. */
	@Transactional
	@Modifying(flushAutomatically = true)
	@Query("update RefreshToken r set r.revogadoEm = :agora, r.motivoRevogacao = :motivo, r.atualizadoEm = :agora "
			+ "where r.usuarioId = :usuarioId and r.revogadoEm is null")
	int revogarTodosDoUsuario(@Param("usuarioId") Long usuarioId, @Param("agora") Instant agora,
			@Param("motivo") MotivoRevogacao motivo);

	/** Quantos refresh do usuário ainda estão ativos (não revogados e não vencidos). */
	@Query("select count(r) from RefreshToken r where r.usuarioId = :usuarioId and r.revogadoEm is null and r.expiraEm > :agora")
	long contarAtivos(@Param("usuarioId") Long usuarioId, @Param("agora") Instant agora);

	/** Apaga, em lote, as linhas vencidas antes do instante dado. Linha revogada e ainda não vencida NÃO entra (detecta o reuso). */
	@Transactional
	@Modifying
	@Query(value = "DELETE FROM refresh_token WHERE expira_em < :limite LIMIT :lote", nativeQuery = true)
	int apagarVencidasAntesDe(@Param("limite") Instant limite, @Param("lote") int lote);

}
