package br.simulado.etec.catalogo.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.TreeMap;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Uma versão do conteúdo de uma questão (contrato, seção 5). O subtópico é DA VERSÃO (D146). As colunas geradas
 * {@code rascunho_aberto} e {@code publicada_vigente} (índices únicos de "um rascunho e uma publicada por questão") existem só no
 * banco e não são mapeadas aqui. A versão 1 nasce no cadastro, em RASCUNHO; as seguintes são cópias da publicada (D181). A
 * publicada nunca muda de conteúdo: quando outra toma o lugar dela, vira ARQUIVADA e fica assim para sempre (D183).
 */
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "questao_versao", uniqueConstraints = @UniqueConstraint(name = "uk_questao_versao_questao_versao",
		columnNames = { "questao_id", "versao" }))
public class QuestaoVersao {

	/** Limite de textoApoio, enunciado e explicacao, em caracteres (contrato, seção 5). */
	public static final int TEXTO_MAXIMO = 10_000;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;

	@ManyToOne(optional = false)
	@JoinColumn(name = "questao_id", nullable = false, updatable = false)
	private Questao questao;

	@Column(name = "versao", nullable = false, updatable = false)
	private int versao;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 20)
	private StatusQuestao status;

	@ManyToOne(optional = false)
	@JoinColumn(name = "subtopico_id", nullable = false, foreignKey = @ForeignKey(name = "fk_questao_versao_subtopico"))
	private Subtopico subtopico;

	@JdbcTypeCode(SqlTypes.LONG32VARCHAR)
	@Column(name = "texto_apoio", columnDefinition = "text")
	private String textoApoio;

	@Column(name = "grupo_apoio")
	private Integer grupoApoio;

	@JdbcTypeCode(SqlTypes.LONG32VARCHAR)
	@Column(name = "enunciado", nullable = false, columnDefinition = "text")
	private String enunciado;

	@Column(name = "imagem_url", length = 500)
	private String imagemUrl;

	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(name = "gabarito", length = 1)
	private String gabarito;

	@Enumerated(EnumType.STRING)
	@Column(name = "dificuldade", length = 10)
	private Dificuldade dificuldade;

	@JdbcTypeCode(SqlTypes.LONG32VARCHAR)
	@Column(name = "explicacao", columnDefinition = "text")
	private String explicacao;

	@Column(name = "publicada_em")
	private Instant publicadaEm;

	@OneToMany(mappedBy = "versao", cascade = CascadeType.ALL, fetch = FetchType.EAGER)
	@OrderBy("letra ASC")
	private List<Alternativa> alternativas = new ArrayList<>();

	@CreatedDate
	@Column(name = "criado_em", nullable = false, updatable = false)
	private Instant criadoEm;

	@LastModifiedDate
	@Column(name = "atualizado_em", nullable = false)
	private Instant atualizadoEm;

	protected QuestaoVersao() {
		// exigido pelo JPA
	}

	/** A primeira versão de uma questão nova: versão 1, RASCUNHO, com as alternativas por letra (já validadas pelo service). */
	public QuestaoVersao(Questao questao, Subtopico subtopico, String textoApoio, Integer grupoApoio, String enunciado,
			String imagemUrl, Map<String, String> textoPorLetra, String gabarito, Dificuldade dificuldade, String explicacao) {
		this.questao = questao;
		this.versao = 1;
		this.status = StatusQuestao.RASCUNHO;
		this.subtopico = subtopico;
		this.textoApoio = textoApoio;
		this.grupoApoio = grupoApoio;
		this.enunciado = enunciado;
		this.imagemUrl = imagemUrl;
		this.gabarito = gabarito;
		this.dificuldade = dificuldade;
		this.explicacao = explicacao;
		new TreeMap<>(textoPorLetra).forEach((letra, texto) -> this.alternativas.add(new Alternativa(this, letra, texto)));
	}

	/**
	 * Substituição total do conteúdo do RASCUNHO (PUT, D111 e D171): o opcional omitido vira nulo, inclusive o gabarito. As
	 * alternativas mudam o texto das MESMAS 5 linhas: trocar a coleção faria o JPA inserir antes de apagar e bater no índice
	 * único (versão, letra). Sem mudança de valor, o JPA não grava nada. Mudar só o texto de uma alternativa não suja esta
	 * linha, por isso o {@code momento} da alteração é anotado aqui nesse caso. A identidade da questão, e com ela o hash,
	 * não é tocada (D51).
	 */
	public void editarRascunho(Subtopico subtopico, String textoApoio, Integer grupoApoio, String enunciado, String imagemUrl,
			Map<String, String> textoPorLetra, String gabarito, Dificuldade dificuldade, String explicacao, Instant momento) {
		exigirRascunho();
		this.subtopico = subtopico;
		this.textoApoio = textoApoio;
		this.grupoApoio = grupoApoio;
		this.enunciado = enunciado;
		this.imagemUrl = imagemUrl;
		this.gabarito = gabarito;
		this.dificuldade = dificuldade;
		this.explicacao = explicacao;
		boolean mudouAlternativa = false;
		for (Alternativa alternativa : this.alternativas) {
			mudouAlternativa |= alternativa.mudarTexto(textoPorLetra.get(alternativa.getLetra()));
		}
		if (mudouAlternativa) {
			this.atualizadoEm = momento;
		}
	}

	/** Define o gabarito do rascunho (lote, D174). Não existe apagar por aqui. Devolve se o valor mudou. */
	public boolean definirGabarito(String letra) {
		exigirRascunho();
		Objects.requireNonNull(letra, "gabarito");
		if (letra.equals(this.gabarito)) {
			return false;
		}
		this.gabarito = letra;
		return true;
	}

	/** Publica o rascunho (D178): status e data juntos, como o CHECK da V11 exige. Daqui em diante o conteúdo não muda mais. */
	public void publicar(Instant momento) {
		exigirRascunho();
		this.status = StatusQuestao.PUBLICADA;
		this.publicadaEm = Objects.requireNonNull(momento, "momento");
	}

	/** A publicada que dá lugar a outra vira ARQUIVADA e guarda a data em que foi publicada. */
	public void arquivar() {
		if (this.status != StatusQuestao.PUBLICADA) {
			throw new IllegalStateException("Só a versão PUBLICADA pode ser arquivada.");
		}
		this.status = StatusQuestao.ARQUIVADA;
	}

	/** Versão publicada ou arquivada nunca muda: é o que a tentativa referencia. O service barra antes, com o 409. */
	private void exigirRascunho() {
		if (this.status != StatusQuestao.RASCUNHO) {
			throw new IllegalStateException("Só a versão em RASCUNHO pode ser alterada.");
		}
	}

	/**
	 * Uma versão nova, em RASCUNHO, com TODO o conteúdo de outra (a publicada) em linhas novas, inclusive as 5 alternativas
	 * (D181). A de origem não é tocada. O subtópico vai como está, mesmo desativado: quem o confere é a publicação.
	 */
	public static QuestaoVersao copiaDe(QuestaoVersao origem, int numeroDaVersao) {
		Map<String, String> textoPorLetra = origem.alternativas.stream()
				.collect(Collectors.toMap(Alternativa::getLetra, Alternativa::getTexto));
		QuestaoVersao copia = new QuestaoVersao(origem.questao, origem.subtopico, origem.textoApoio, origem.grupoApoio,
				origem.enunciado, origem.imagemUrl, textoPorLetra, origem.gabarito, origem.dificuldade, origem.explicacao);
		copia.versao = numeroDaVersao;
		return copia;
	}

	public Long getId() {
		return id;
	}

	public Questao getQuestao() {
		return questao;
	}

	public int getVersao() {
		return versao;
	}

	public StatusQuestao getStatus() {
		return status;
	}

	public Subtopico getSubtopico() {
		return subtopico;
	}

	public String getTextoApoio() {
		return textoApoio;
	}

	public Integer getGrupoApoio() {
		return grupoApoio;
	}

	public String getEnunciado() {
		return enunciado;
	}

	public String getImagemUrl() {
		return imagemUrl;
	}

	public String getGabarito() {
		return gabarito;
	}

	public Dificuldade getDificuldade() {
		return dificuldade;
	}

	public String getExplicacao() {
		return explicacao;
	}

	public Instant getPublicadaEm() {
		return publicadaEm;
	}

	/** As alternativas, de A a E. */
	public List<Alternativa> getAlternativas() {
		return List.copyOf(alternativas);
	}

}
