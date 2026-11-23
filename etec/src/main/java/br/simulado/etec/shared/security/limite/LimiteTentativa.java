package br.simulado.etec.shared.security.limite;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Só o MAPEAMENTO da tabela {@code limite_tentativa}: no MySQL o Hibernate confere, na subida ({@code ddl-auto: validate}),
 * que a migration V4 bate com ela; nos testes com H2 ela cria a tabela. NINGUÉM lê nem grava esta entidade: o acesso é o
 * {@link ContadorDeTentativas}, por SQL atômico (o contador não pode passar por "ler, somar e salvar").
 */
@Entity
@Table(name = "limite_tentativa")
public class LimiteTentativa {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "chave", nullable = false, length = 64, unique = true)
	private String chave;

	@Column(name = "contador", nullable = false)
	private int contador;

	@Column(name = "janela_inicio", nullable = false)
	private LocalDateTime janelaInicio;

	@Column(name = "janela_fim", nullable = false)
	private LocalDateTime janelaFim;

	@Column(name = "criado_em", nullable = false)
	private LocalDateTime criadoEm;

	@Column(name = "atualizado_em", nullable = false)
	private LocalDateTime atualizadoEm;

	protected LimiteTentativa() {
		// só para o JPA
	}

}
