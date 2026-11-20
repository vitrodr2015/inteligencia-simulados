package br.simulado.etec.catalogo.service;

import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.simulado.etec.catalogo.dto.MateriaRequest;
import br.simulado.etec.catalogo.model.Materia;
import br.simulado.etec.catalogo.repository.MateriaRepository;
import br.simulado.etec.shared.exception.CodigoErro;
import br.simulado.etec.shared.exception.ConflictException;
import br.simulado.etec.shared.exception.ResourceNotFoundException;

/** Regras da matéria (contrato, seção 4.1). Leituras em transação somente leitura. */
@Service
@Transactional(readOnly = true)
public class MateriaService {

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

	/** Atômico: uma instrução só. Se não apagou nada, a matéria não existia. */
	@Transactional
	public void apagar(UUID id) {
		if (repositorio.apagarPorPublicId(id) == 0) {
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
