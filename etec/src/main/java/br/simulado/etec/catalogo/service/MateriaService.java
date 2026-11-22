package br.simulado.etec.catalogo.service;

import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.simulado.etec.catalogo.dto.MateriaRequest;
import br.simulado.etec.catalogo.model.Materia;
import br.simulado.etec.catalogo.repository.MateriaRepository;
import br.simulado.etec.shared.exception.CodigoErro;
import br.simulado.etec.shared.exception.ConflictException;
import br.simulado.etec.shared.exception.ResourceNotFoundException;
import br.simulado.etec.shared.paginacao.CamposOrdenaveis;
import br.simulado.etec.shared.paginacao.Filtros;
import br.simulado.etec.shared.persistencia.Restricoes;

/** Regras da matéria (contrato, seção 4.1). Leituras em transação somente leitura. */
@Service
@Transactional(readOnly = true)
public class MateriaService {

	/** Campos pelos quais a listagem aceita ordenação (lista fixa); padrão: nome, crescente. */
	public static final CamposOrdenaveis CAMPOS_ORDENAVEIS = CamposOrdenaveis.de(Sort.by(Sort.Order.asc("nome")), "nome",
			"criadoEm");

	private final MateriaRepository repositorio;

	public MateriaService(MateriaRepository repositorio) {
		this.repositorio = repositorio;
	}

	@Transactional
	public Materia criar(MateriaRequest dados) {
		// Checagem prévia: dá a mensagem amigável no caso comum.
		if (repositorio.existsByNome(dados.nome())) {
			throw nomeDuplicado();
		}
		try {
			return repositorio.saveAndFlush(new Materia(dados.nome(), dados.descricao()));
		}
		catch (DataIntegrityViolationException ex) {
			// Corrida: dois pedidos passaram pela checagem e o índice único barrou o segundo.
			// As demais restrições da tabela já são garantidas pela validação do DTO.
			throw nomeDuplicado();
		}
	}

	/**
	 * Lista com filtros opcionais: {@code nome} (contém, sem distinguir maiúsculas e acentos no MySQL com a
	 * collation utf8mb4_unicode_ci; %, _ e a barra invertida valem como texto) e {@code ativo} (nulo = todos).
	 */
	public Page<Materia> listar(String nome, Boolean ativo, Pageable paginacao) {
		Specification<Materia> filtros = Specification.allOf(Filtros.<Materia>contem("nome", nome),
				Filtros.<Materia>igual("ativo", ativo));
		return repositorio.findAll(filtros, paginacao);
	}

	public Materia buscar(UUID id) {
		return repositorio.findByPublicId(id).orElseThrow(MateriaService::naoEncontrada);
	}

	@Transactional
	public Materia atualizar(UUID id, MateriaRequest dados) {
		Materia materia = buscar(id);
		if (repositorio.existsByNomeAndPublicIdNot(dados.nome(), id)) {
			throw nomeDuplicado();
		}
		materia.atualizar(dados.nome(), dados.descricao());
		try {
			return repositorio.saveAndFlush(materia);
		}
		catch (DataIntegrityViolationException ex) {
			throw nomeDuplicado();
		}
	}

	/** Idempotente: repetir devolve o mesmo resultado. */
	@Transactional
	public Materia desativar(UUID id) {
		Materia materia = buscar(id);
		materia.desativar();
		return repositorio.saveAndFlush(materia);
	}

	/** Idempotente: repetir devolve o mesmo resultado. */
	@Transactional
	public Materia ativar(UUID id) {
		Materia materia = buscar(id);
		materia.ativar();
		return repositorio.saveAndFlush(materia);
	}

	/**
	 * Atômico: uma instrução só. Se não apagou nada, a matéria não existia. Se ela tem subtópicos (ativos ou não), a chave
	 * estrangeira com ON DELETE RESTRICT do banco recusa o delete e isso vira 409 MATERIA_COM_SUBTOPICOS. Só essa
	 * restrição é traduzida: qualquer outra violação de integridade sobe como está, e não é escondida (D28).
	 */
	@Transactional
	public void apagar(UUID id) {
		int apagadas;
		try {
			apagadas = repositorio.apagarPorPublicId(id);
		}
		catch (DataIntegrityViolationException ex) {
			if (Restricoes.ehChaveEstrangeira(ex, SubtopicoService.FK_MATERIA)) {
				throw new ConflictException(CodigoErro.MATERIA_COM_SUBTOPICOS,
						"A matéria tem subtópicos e não pode ser apagada.");
			}
			throw ex;
		}
		if (apagadas == 0) {
			throw naoEncontrada();
		}
	}

	private static ConflictException nomeDuplicado() {
		return new ConflictException(CodigoErro.MATERIA_NOME_DUPLICADO, "Já existe uma matéria com esse nome.");
	}

	private static ResourceNotFoundException naoEncontrada() {
		return new ResourceNotFoundException(CodigoErro.MATERIA_NAO_ENCONTRADA, "Matéria não encontrada.");
	}

}
