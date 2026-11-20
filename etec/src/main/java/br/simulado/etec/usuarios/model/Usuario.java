package br.simulado.etec.usuarios.model;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/**
 * Usuário (contrato, seção 3). O {@code id} é interno e nunca sai pela API; o recurso é
 * exposto por {@code publicId}. Datas em UTC ({@link Instant}). A entidade nunca é
 * serializada: a API usa DTOs.
 */
@Entity
@Table(name = "usuario")
public class Usuario {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	@Column(name = "public_id", nullable = false, updatable = false, unique = true)
	private UUID publicId;

	@Column(name = "nome", nullable = false, length = 100)
	private String nome;

	@Column(name = "email", nullable = false, length = 255, unique = true)
	private String email;

	@Column(name = "senha_hash", nullable = false, length = 100)
	private String senhaHash;

	@Column(name = "data_nascimento", nullable = false)
	private LocalDate dataNascimento;

	@Column(name = "aceite_termos_versao", nullable = false, length = 20)
	private String aceiteTermosVersao;

	@Enumerated(EnumType.STRING)
	@Column(name = "papel", nullable = false, length = 20)
	private Papel papel;

	@Column(name = "criado_em", nullable = false, updatable = false)
	private Instant criadoEm;

	@Column(name = "atualizado_em", nullable = false)
	private Instant atualizadoEm;

	@Column(name = "excluido_em")
	private Instant excluidoEm;

	protected Usuario() {
		// exigido pelo JPA
	}

	public Usuario(String nome, String email, String senhaHash, LocalDate dataNascimento,
			String aceiteTermosVersao, Papel papel) {
		this.nome = nome;
		this.email = email;
		this.senhaHash = senhaHash;
		this.dataNascimento = dataNascimento;
		this.aceiteTermosVersao = aceiteTermosVersao;
		this.papel = papel;
	}

	/** Preenche o que a aplicação gera (o banco não tem default). O service pode definir antes, com o Clock. */
	@PrePersist
	void aoCriar() {
		Instant agora = Instant.now();
		if (publicId == null) {
			publicId = UUID.randomUUID();
		}
		if (criadoEm == null) {
			criadoEm = agora;
		}
		if (atualizadoEm == null) {
			atualizadoEm = agora;
		}
	}

	@PreUpdate
	void aoAtualizar() {
		atualizadoEm = Instant.now();
	}

	public Long getId() {
		return id;
	}

	public UUID getPublicId() {
		return publicId;
	}

	public String getNome() {
		return nome;
	}

	public String getEmail() {
		return email;
	}

	public String getSenhaHash() {
		return senhaHash;
	}

	public LocalDate getDataNascimento() {
		return dataNascimento;
	}

	public String getAceiteTermosVersao() {
		return aceiteTermosVersao;
	}

	public Papel getPapel() {
		return papel;
	}

	public Instant getCriadoEm() {
		return criadoEm;
	}

	public Instant getAtualizadoEm() {
		return atualizadoEm;
	}

	public Instant getExcluidoEm() {
		return excluidoEm;
	}

}
