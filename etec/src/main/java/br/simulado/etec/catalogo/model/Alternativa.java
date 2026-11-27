package br.simulado.etec.catalogo.model;

import java.time.Instant;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/** Uma das 5 alternativas (A a E) de uma versão de questão. Sem id público: nunca sai como recurso próprio. */
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "alternativa", uniqueConstraints = @UniqueConstraint(name = "uk_alternativa_versao_letra",
		columnNames = { "questao_versao_id", "letra" }))
public class Alternativa {

	/** Limite do texto da alternativa (D155). */
	public static final int TEXTO_MAXIMO = 2000;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	@ManyToOne(optional = false)
	@JoinColumn(name = "questao_versao_id", nullable = false, updatable = false)
	private QuestaoVersao versao;

	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(name = "letra", nullable = false, updatable = false, length = 1)
	private String letra;

	@Column(name = "texto", nullable = false, length = TEXTO_MAXIMO)
	private String texto;

	@CreatedDate
	@Column(name = "criado_em", nullable = false, updatable = false)
	private Instant criadoEm;

	@LastModifiedDate
	@Column(name = "atualizado_em", nullable = false)
	private Instant atualizadoEm;

	protected Alternativa() {
		// exigido pelo JPA
	}

	Alternativa(QuestaoVersao versao, String letra, String texto) {
		this.versao = versao;
		this.letra = letra;
		this.texto = texto;
	}

	/** Troca o texto NA MESMA linha (a letra não muda). Devolve se o texto mudou. */
	boolean mudarTexto(String novo) {
		if (this.texto.equals(novo)) {
			return false;
		}
		this.texto = novo;
		return true;
	}

	public String getLetra() {
		return letra;
	}

	public String getTexto() {
		return texto;
	}

}
