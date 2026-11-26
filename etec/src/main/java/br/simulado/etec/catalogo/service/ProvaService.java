package br.simulado.etec.catalogo.service;

import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.simulado.etec.catalogo.dto.ProvaRequest;
import br.simulado.etec.catalogo.model.ProgramaDeProva;
import br.simulado.etec.catalogo.model.Prova;
import br.simulado.etec.catalogo.model.StatusProva;
import br.simulado.etec.catalogo.repository.ProgramaRepository;
import br.simulado.etec.catalogo.repository.ProvaRepository;
import br.simulado.etec.shared.exception.CodigoErro;
import br.simulado.etec.shared.exception.ConflictException;
import br.simulado.etec.shared.exception.ResourceNotFoundException;
import br.simulado.etec.shared.paginacao.CamposOrdenaveis;
import br.simulado.etec.shared.paginacao.Filtros;
import br.simulado.etec.shared.persistencia.Restricoes;
import br.simulado.etec.shared.programa.Programa;

/**
 * Regras da prova (contrato, seção 4.3). Nesta fatia: criar (sempre em RASCUNHO), listar e buscar, só para o ADMIN (D154).
 * Leituras em transação somente leitura.
 */
@Service
@Transactional(readOnly = true)
public class ProvaService {

	/**
	 * Campos pelos quais a listagem aceita ordenação (lista fixa); padrão: ano decrescente (D102). No MySQL, os anos nulos ficam
	 * no FIM no padrão decrescente e no INÍCIO no ascendente (o NULL é o menor valor). O desempate pelo id é do conversor.
	 */
	public static final CamposOrdenaveis CAMPOS_ORDENAVEIS = CamposOrdenaveis.de(Sort.by(Sort.Order.desc("ano")), "ano",
			"semestre");

	/** O índice único de programa + banca + ano + semestre (D150). Só a violação DELE vira 409 PROVA_DUPLICADA. */
	public static final String UK_PROVA = "uk_prova_programa_banca_ano_semestre";

	private final ProvaRepository repositorio;

	private final ProgramaRepository programas;

	public ProvaService(ProvaRepository repositorio, ProgramaRepository programas) {
		this.repositorio = repositorio;
		this.programas = programas;
	}

	@Transactional
	public Prova criar(ProvaRequest dados) {
		// O programa é interno (D143): na fase 1 toda prova nasce no ETEC, lido da tabela a cada criação (sem cache).
		ProgramaDeProva programa = programaEtec();
		boolean identificada = dados.banca() != null && dados.ano() != null && dados.semestre() != null;
		// Checagem prévia: dá a mensagem amigável no caso comum. Sem os três campos NÃO há unicidade (D150).
		if (identificada && repositorio.existsByProgramaAndBancaAndAnoAndSemestre(programa, dados.banca(), dados.ano(),
				dados.semestre())) {
			throw provaDuplicada();
		}
		Prova nova = new Prova(programa, dados.titulo(), dados.banca(), dados.ano(), dados.semestre(),
				dados.duracaoMinutos(), dados.quantidadeQuestoes(), dados.fonteUrl());
		try {
			return repositorio.saveAndFlush(nova);
		}
		catch (DataIntegrityViolationException ex) {
			// Corrida: dois pedidos passaram pela checagem e o índice único barrou o segundo. Só ESSA restrição vira 409;
			// qualquer outra (um CHECK, a chave estrangeira) sobe como erro e não é escondida.
			if (Restricoes.ehUnicidade(ex, UK_PROVA)) {
				throw provaDuplicada();
			}
			throw ex;
		}
	}

	/** Lista com filtros opcionais: {@code ano}, {@code semestre} e {@code status} (nulo = todos). */
	public Page<Prova> listar(Integer ano, Integer semestre, StatusProva status, Pageable paginacao) {
		Specification<Prova> filtros = Specification.allOf(Filtros.<Prova>igual("ano", ano),
				Filtros.<Prova>igual("semestre", semestre), Filtros.<Prova>igual("status", status));
		return repositorio.findAll(filtros, paginacao);
	}

	public Prova buscar(UUID id) {
		return repositorio.findByPublicId(id).orElseThrow(ProvaService::naoEncontrada);
	}

	private ProgramaDeProva programaEtec() {
		return programas.findByCodigo(Programa.ETEC.codigo()).orElseThrow(
				() -> new IllegalStateException("O programa ETEC não existe na tabela programa (migration V7)."));
	}

	private static ConflictException provaDuplicada() {
		return new ConflictException(CodigoErro.PROVA_DUPLICADA, "Já existe uma prova com essa banca, ano e semestre.");
	}

	private static ResourceNotFoundException naoEncontrada() {
		return new ResourceNotFoundException(CodigoErro.PROVA_NAO_ENCONTRADA, "Prova não encontrada.");
	}

}
