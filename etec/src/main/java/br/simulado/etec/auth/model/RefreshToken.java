package br.simulado.etec.auth.model;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Um refresh token emitido (D133). Guarda SÓ o {@code jti}, nunca o token. A revogação (rotação, logout, reuso, troca de senha)
 * é feita por UPDATE condicional no repositório, nunca por "ler, alterar e salvar", para valer sob concorrência. O
 * {@code usuarioId} é o id interno (o domínio {@code auth} não depende da entidade do usuário); a chave estrangeira está na
 * migration.
 */
@Entity
@Table(name = "refresh_token")
@EntityListeners(AuditingEntityListener.class)
public class RefreshToken {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "jti", nullable = false, updatable = false, unique = true)
	private UUID jti;

	@Column(name = "usuario_id", nullable = false, updatable = false)
	private Long usuarioId;

	@Column(name = "expira_em", nullable = false, updatable = false)
	private Instant expiraEm;

	@Column(name = "revogado_em")
	private Instant revogadoEm;

	@Enumerated(EnumType.STRING)
	@Column(name = "motivo_revogacao", length = 20)
	private MotivoRevogacao motivoRevogacao;

	@CreatedDate
	@Column(name = "criado_em", nullable = false, updatable = false)
	private Instant criadoEm;

	@LastModifiedDate
	@Column(name = "atualizado_em", nullable = false)
	private Instant atualizadoEm;

	protected RefreshToken() {
		// exigido pelo JPA
	}

	public RefreshToken(UUID jti, Long usuarioId, Instant expiraEm) {
		this.jti = jti;
		this.usuarioId = usuarioId;
		this.expiraEm = expiraEm;
	}

	public UUID getJti() {
		return jti;
	}

	public Long getUsuarioId() {
		return usuarioId;
	}

	public Instant getExpiraEm() {
		return expiraEm;
	}

	public Instant getRevogadoEm() {
		return revogadoEm;
	}

	public MotivoRevogacao getMotivoRevogacao() {
		return motivoRevogacao;
	}

	/** Nunca mostra o jti inteiro: o identificador de um refresh não vai para log nem para mensagem. */
	@Override
	public String toString() {
		return "RefreshToken[usuarioId=" + usuarioId + ", revogado=" + (revogadoEm != null) + "]";
	}

}
