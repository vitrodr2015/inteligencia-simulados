package br.simulado.etec.catalogo.model;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
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
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Questão, a IDENTIDADE (contrato, seção 5): prova, numero, tipo e hash do conteúdo; o conteúdo mora nas versões
 * ({@link QuestaoVersao}). O {@code id} é interno; o recurso é exposto por {@code publicId} (estável entre versões). O numero e o
 * hash não mudam (D42, D51): as colunas são {@code updatable = false} e não há método que as troque. O programa vem da prova (D143).
 */
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "questao", uniqueConstraints = @UniqueConstraint(name = "uk_questao_prova_numero",
		columnNames = { "prova_id", "numero" }))
public class Questao {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	@Column(name = "public_id", nullable = false, updatable = false, unique = true)
	private UUID publicId;

	@ManyToOne(optional = false)
	@JoinColumn(name = "prova_id", nullable = false, updatable = false)
	private Prova prova;

	@JdbcTypeCode(SqlTypes.SMALLINT)
	@Column(name = "numero", nullable = false, updatable = false)
	private int numero;

	@Enumerated(EnumType.STRING)
	@Column(name = "tipo", nullable = false, updatable = false, length = 30)
	private TipoQuestao tipo;

	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(name = "hash_conteudo", nullable = false, updatable = false, length = 64)
	private String hashConteudo;

	@CreatedDate
	@Column(name = "criado_em", nullable = false, updatable = false)
	private Instant criadoEm;

	@LastModifiedDate
	@Column(name = "atualizado_em", nullable = false)
	private Instant atualizadoEm;

	protected Questao() {
		// exigido pelo JPA
	}

	public Questao(Prova prova, int numero, String hashConteudo) {
		this.publicId = UUID.randomUUID();
		this.prova = prova;
		this.numero = numero;
		this.hashConteudo = hashConteudo;
		// D145: o tipo é atribuído AQUI e em nenhum outro lugar.
		this.tipo = TipoQuestao.MULTIPLA_ESCOLHA;
	}

	public Long getId() {
		return id;
	}

	public UUID getPublicId() {
		return publicId;
	}

	public Prova getProva() {
		return prova;
	}

	public int getNumero() {
		return numero;
	}

	public TipoQuestao getTipo() {
		return tipo;
	}

	public String getHashConteudo() {
		return hashConteudo;
	}

	public Instant getCriadoEm() {
		return criadoEm;
	}

	public Instant getAtualizadoEm() {
		return atualizadoEm;
	}

}
