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
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Prova (contrato, seção 4.3). O {@code id} é interno e nunca sai pela API; o recurso é exposto por {@code publicId}. O
 * programa é interno e nunca sai pela API (D143). Banca, ano e semestre são opcionais (D150): a unicidade por programa + banca +
 * ano + semestre só vale com os três preenchidos. {@code quantidadeQuestoes} é dado declarado na criação (D151). Nasce em
 * RASCUNHO e sem data de publicação.
 */
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "prova", uniqueConstraints = @UniqueConstraint(name = "uk_prova_programa_banca_ano_semestre",
		columnNames = { "programa_id", "banca", "ano", "semestre" }))
public class Prova {

	/** Teto de questões por prova (D151). Está também no CHECK da migration V8; mudar exige uma migration nova. */
	public static final int QUANTIDADE_MAXIMA_DE_QUESTOES = 300;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	@Column(name = "public_id", nullable = false, updatable = false, unique = true)
	private UUID publicId;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "programa_id", nullable = false, updatable = false)
	private ProgramaDeProva programa;

	@Column(name = "titulo", nullable = false, length = 150)
	private String titulo;

	@Column(name = "banca", length = 80)
	private String banca;

	@JdbcTypeCode(SqlTypes.SMALLINT)
	@Column(name = "ano")
	private Integer ano;

	@JdbcTypeCode(SqlTypes.TINYINT)
	@Column(name = "semestre")
	private Integer semestre;

	@JdbcTypeCode(SqlTypes.SMALLINT)
	@Column(name = "duracao_minutos", nullable = false)
	private int duracaoMinutos;

	@JdbcTypeCode(SqlTypes.SMALLINT)
	@Column(name = "quantidade_questoes", nullable = false)
	private int quantidadeQuestoes;

	@Column(name = "fonte_url", length = 500)
	private String fonteUrl;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 20)
	private StatusProva status;

	@Column(name = "publicada_em")
	private Instant publicadaEm;

	@CreatedDate
	@Column(name = "criado_em", nullable = false, updatable = false)
	private Instant criadoEm;

	@LastModifiedDate
	@Column(name = "atualizado_em", nullable = false)
	private Instant atualizadoEm;

	protected Prova() {
		// exigido pelo JPA
	}

	public Prova(ProgramaDeProva programa, String titulo, String banca, Integer ano, Integer semestre, int duracaoMinutos,
			int quantidadeQuestoes, String fonteUrl) {
		this.publicId = UUID.randomUUID();
		this.programa = programa;
		this.titulo = titulo;
		this.banca = banca;
		this.ano = ano;
		this.semestre = semestre;
		this.duracaoMinutos = duracaoMinutos;
		this.quantidadeQuestoes = quantidadeQuestoes;
		this.fonteUrl = fonteUrl;
		this.status = StatusProva.RASCUNHO;
	}

	public Long getId() {
		return id;
	}

	public UUID getPublicId() {
		return publicId;
	}

	public ProgramaDeProva getPrograma() {
		return programa;
	}

	public String getTitulo() {
		return titulo;
	}

	public String getBanca() {
		return banca;
	}

	public Integer getAno() {
		return ano;
	}

	public Integer getSemestre() {
		return semestre;
	}

	public int getDuracaoMinutos() {
		return duracaoMinutos;
	}

	public int getQuantidadeQuestoes() {
		return quantidadeQuestoes;
	}

	public String getFonteUrl() {
		return fonteUrl;
	}

	public StatusProva getStatus() {
		return status;
	}

	public Instant getPublicadaEm() {
		return publicadaEm;
	}

	public Instant getCriadoEm() {
		return criadoEm;
	}

	public Instant getAtualizadoEm() {
		return atualizadoEm;
	}

}
