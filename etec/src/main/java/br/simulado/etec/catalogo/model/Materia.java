package br.simulado.etec.catalogo.model;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Matéria (contrato, seção 4.1). O {@code id} é interno e nunca sai pela API; o recurso é
 * exposto por {@code publicId}. O nome é único no banco (a collation utf8mb4_unicode_ci ignora
 * maiúsculas e acentos). Nasce ativa; só muda por {@link #desativar()} e {@link #ativar()}.
 */
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "materia")
public class Materia {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	@Column(name = "public_id", nullable = false, updatable = false, unique = true)
	private UUID publicId;

	@Column(name = "nome", nullable = false, length = 100, unique = true)
	private String nome;

	@Column(name = "descricao", length = 255)
	private String descricao;

	@Column(name = "ativo", nullable = false)
	private boolean ativo;

	@CreatedDate
	@Column(name = "criado_em", nullable = false, updatable = false)
	private Instant criadoEm;

	@LastModifiedDate
	@Column(name = "atualizado_em", nullable = false)
	private Instant atualizadoEm;

	protected Materia() {
		// exigido pelo JPA
	}

	public Materia(String nome, String descricao) {
		this.publicId = UUID.randomUUID();
		this.nome = nome;
		this.descricao = descricao;
		this.ativo = true;
	}

	/** Troca nome e descrição; nunca mexe no estado ativo. */
	public void atualizar(String nome, String descricao) {
		this.nome = nome;
		this.descricao = descricao;
	}

	public void desativar() {
		this.ativo = false;
	}

	public void ativar() {
		this.ativo = true;
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

	public String getDescricao() {
		return descricao;
	}

	/** O STUDENT só vê matéria ativa (D28). */
	public boolean visivelParaAluno() {
		return ativo;
	}

	public boolean isAtivo() {
		return ativo;
	}

	public Instant getCriadoEm() {
		return criadoEm;
	}

	public Instant getAtualizadoEm() {
		return atualizadoEm;
	}

}
