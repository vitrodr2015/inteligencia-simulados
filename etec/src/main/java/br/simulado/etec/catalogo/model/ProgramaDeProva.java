package br.simulado.etec.catalogo.model;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import br.simulado.etec.shared.programa.Programa;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * O programa de prova da tabela {@code programa} (D143). Hoje só existe a ETEC, criada pela migration V7. É interno: nunca
 * sai pela API. O {@code codigo} é o do {@link Programa} (mesmo formato, e a migration tem o CHECK equivalente).
 */
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "programa")
public class ProgramaDeProva {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	@Column(name = "public_id", nullable = false, updatable = false, unique = true)
	private UUID publicId;

	@Column(name = "codigo", nullable = false, updatable = false, length = 20, unique = true)
	private String codigo;

	@Column(name = "nome", nullable = false, length = 100)
	private String nome;

	@CreatedDate
	@Column(name = "criado_em", nullable = false, updatable = false)
	private Instant criadoEm;

	@LastModifiedDate
	@Column(name = "atualizado_em", nullable = false)
	private Instant atualizadoEm;

	protected ProgramaDeProva() {
		// exigido pelo JPA
	}

	public ProgramaDeProva(Programa programa, String nome) {
		this.publicId = UUID.randomUUID();
		this.codigo = programa.codigo();
		this.nome = nome;
	}

	public Long getId() {
		return id;
	}

	public UUID getPublicId() {
		return publicId;
	}

	public String getCodigo() {
		return codigo;
	}

	public String getNome() {
		return nome;
	}

	public Programa programa() {
		return Programa.de(codigo);
	}

}
