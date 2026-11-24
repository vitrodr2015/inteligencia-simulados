package br.simulado.etec.usuarios.model;

import java.time.Instant;
import java.time.LocalDate;
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
 * Usuário (contrato, seção 3). O {@code id} é interno e nunca sai pela API; o recurso é
 * exposto por {@code publicId}. Datas em UTC ({@link Instant}); {@code criadoEm} e
 * {@code atualizadoEm} vêm da auditoria do Spring Data, com o Clock da aplicação. A entidade
 * nunca é serializada: a API usa DTOs.
 */
@Entity
@EntityListeners(AuditingEntityListener.class)
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

	@CreatedDate
	@Column(name = "criado_em", nullable = false, updatable = false)
	private Instant criadoEm;

	@LastModifiedDate
	@Column(name = "atualizado_em", nullable = false)
	private Instant atualizadoEm;

	@Column(name = "excluido_em")
	private Instant excluidoEm;

	/**
	 * Versão do token (D136). SÓ-LEITURA para a entidade ({@code updatable = false}): quem a incrementa é o UPDATE atômico do
	 * repositório, para um {@code save} de outro campo nunca gravar de volta um valor velho.
	 */
	@Column(name = "versao_token", nullable = false, updatable = false)
	private int versaoToken;

	protected Usuario() {
		// exigido pelo JPA
	}

	public Usuario(String nome, String email, String senhaHash, LocalDate dataNascimento,
			String aceiteTermosVersao, Papel papel) {
		this.publicId = UUID.randomUUID();
		this.nome = nome;
		this.email = email;
		this.senhaHash = senhaHash;
		this.dataNascimento = dataNascimento;
		this.aceiteTermosVersao = aceiteTermosVersao;
		this.papel = papel;
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

	public int getVersaoToken() {
		return versaoToken;
	}

	/** Troca o hash da senha (D139). A versão do token NÃO muda aqui: quem encerra as sessões é o serviço de sessões. */
	public void trocarSenhaHash(String novoHash) {
		this.senhaHash = novoHash;
	}

}
