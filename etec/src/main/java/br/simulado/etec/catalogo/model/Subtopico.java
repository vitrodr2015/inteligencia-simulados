package br.simulado.etec.catalogo.model;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Subtópico (contrato, seção 4.2). O {@code id} é interno e nunca sai pela API; o recurso é exposto por
 * {@code publicId}. O nome é único DENTRO da matéria (a collation utf8mb4_unicode_ci ignora maiúsculas e
 * acentos). Nasce ativo; só muda por {@link #desativar()} e {@link #ativar()}. A matéria não muda depois de
 * criado (D48): a coluna é {@code updatable = false} e não há método que a troque.
 */
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "subtopico",
		uniqueConstraints = @UniqueConstraint(name = "uk_subtopico_materia_nome", columnNames = { "materia_id", "nome" }))
public class Subtopico {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	@Column(name = "public_id", nullable = false, updatable = false, unique = true)
	private UUID publicId;

	/** LAZY de propósito; as consultas que devolvem subtópicos para a API carregam a matéria junto (EntityGraph). */
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "materia_id", nullable = false, updatable = false,
			foreignKey = @ForeignKey(name = "fk_subtopico_materia"))
	private Materia materia;

	@Column(name = "nome", nullable = false, length = 100)
	private String nome;

	@Column(name = "ativo", nullable = false)
	private boolean ativo;

	@CreatedDate
	@Column(name = "criado_em", nullable = false, updatable = false)
	private Instant criadoEm;

	@LastModifiedDate
	@Column(name = "atualizado_em", nullable = false)
	private Instant atualizadoEm;

	protected Subtopico() {
		// exigido pelo JPA
	}

	public Subtopico(Materia materia, String nome) {
		this.publicId = UUID.randomUUID();
		this.materia = materia;
		this.nome = nome;
		this.ativo = true;
	}

	/** Só o nome muda (PUT); a matéria é imutável. */
	public void renomear(String nome) {
		this.nome = nome;
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

	public Materia getMateria() {
		return materia;
	}

	public String getNome() {
		return nome;
	}

	/** O STUDENT só vê subtópico ativo de matéria ativa (D28, D103). */
	public boolean visivelParaAluno() {
		return ativo && materia.isAtivo();
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
