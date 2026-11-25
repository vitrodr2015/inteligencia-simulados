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
import br.simulado.etec.catalogo.model.ProgramaDeProva;
import br.simulado.etec.catalogo.repository.MateriaRepository;
import br.simulado.etec.catalogo.repository.ProgramaRepository;
import br.simulado.etec.shared.exception.CodigoErro;
import br.simulado.etec.shared.exception.ConflictException;
import br.simulado.etec.shared.exception.ResourceNotFoundException;
import br.simulado.etec.shared.paginacao.CamposOrdenaveis;
import br.simulado.etec.shared.paginacao.Filtros;
import br.simulado.etec.shared.security.EscopoDeLeitura;
import br.simulado.etec.shared.persistencia.Restricoes;
import br.simulado.etec.shared.programa.Programa;

/** Regras da matéria (contrato, seção 4.1). Leituras em transação somente leitura. */
@Service
@Transactional(readOnly = true)
public class MateriaService {

	/** Campos pelos quais a listagem aceita ordenação (lista fixa); padrão: nome, crescente. */
	public static final CamposOrdenaveis CAMPOS_ORDENAVEIS = CamposOrdenaveis.de(Sort.by(Sort.Order.asc("nome")), "nome",
			"criadoEm");

	/** O índice único do nome da matéria, que vale POR PROGRAMA (D144). Só a violação DELE vira 409 de nome duplicado. */
	public static final String UK_NOME = "uk_materia_programa_nome";

	private final MateriaRepository repositorio;

	private final ProgramaRepository programas;

	public MateriaService(MateriaRepository repositorio, ProgramaRepository programas) {
		this.repositorio = repositorio;
		this.programas = programas;
	}

	@Transactional
	public Materia criar(MateriaRequest dados) {
		// O programa é interno (D143): na fase 1 toda matéria nasce no ETEC, lido da tabela a cada criação (sem cache).
		ProgramaDeProva programa = programaEtec();
		// Checagem prévia: dá a mensagem amigável no caso comum.
		if (repositorio.existsByProgramaAndNome(programa, dados.nome())) {
			throw nomeDuplicado();
		}
		try {
			return repositorio.saveAndFlush(new Materia(programa, dados.nome(), dados.descricao()));
		}
		catch (DataIntegrityViolationException ex) {
			// Corrida: dois pedidos passaram pela checagem e o índice único barrou o segundo. Só ESSA restrição vira 409;
			// qualquer outra (programa_id nulo, por exemplo) sobe como erro e não é escondida.
			if (Restricoes.ehUnicidade(ex, UK_NOME)) {
				throw nomeDuplicado();
			}
			throw ex;
		}
	}

	/**
	 * Lista com filtros opcionais: {@code nome} (contém, sem distinguir maiúsculas e acentos no MySQL com a
	 * collation utf8mb4_unicode_ci; %, _ e a barra invertida valem como texto) e {@code ativo} (nulo = todos).
	 */
	public Page<Materia> listar(EscopoDeLeitura escopo, String nome, Boolean ativo, Pageable paginacao) {
		escopo.validarFiltroAtivo(ativo);
		// STUDENT só vê matéria ativa (D28); ADMIN vê todas.
		Specification<Materia> visibilidade = escopo.veInativos() ? Filtros.<Materia>semFiltro()
				: Filtros.<Materia>igual("ativo", Boolean.TRUE);
		Specification<Materia> filtros = Specification.allOf(visibilidade, Filtros.<Materia>contem("nome", nome),
				Filtros.<Materia>igual("ativo", ativo));
		return repositorio.findAll(filtros, paginacao);
	}

	/** Leitura por id: para o STUDENT, matéria inativa dá o MESMO 404 da inexistente (D103). */
	public Materia buscar(UUID id, EscopoDeLeitura escopo) {
		Materia materia = carregar(id);
		if (!escopo.veInativos() && !materia.visivelParaAluno()) {
			throw naoEncontrada();
		}
		return materia;
	}

	/** Para as escritas (só ADMIN, a cadeia de segurança já barrou os demais): carrega sem filtro de visibilidade. */
	private Materia carregar(UUID id) {
		return repositorio.findByPublicId(id).orElseThrow(MateriaService::naoEncontrada);
	}

	@Transactional
	public Materia atualizar(UUID id, MateriaRequest dados) {
		Materia materia = carregar(id);
		if (repositorio.existsByProgramaAndNomeAndPublicIdNot(materia.getPrograma(), dados.nome(), id)) {
			throw nomeDuplicado();
		}
		materia.atualizar(dados.nome(), dados.descricao());
		try {
			return repositorio.saveAndFlush(materia);
		}
		catch (DataIntegrityViolationException ex) {
			if (Restricoes.ehUnicidade(ex, UK_NOME)) {
				throw nomeDuplicado();
			}
			throw ex;
		}
	}

	/** Idempotente: repetir devolve o mesmo resultado. */
	@Transactional
	public Materia desativar(UUID id) {
		Materia materia = carregar(id);
		materia.desativar();
		return repositorio.saveAndFlush(materia);
	}

	/** Idempotente: repetir devolve o mesmo resultado. */
	@Transactional
	public Materia ativar(UUID id) {
		Materia materia = carregar(id);
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

	private ProgramaDeProva programaEtec() {
		return programas.findByCodigo(Programa.ETEC.codigo()).orElseThrow(
				() -> new IllegalStateException("O programa ETEC não existe na tabela programa (migration V7)."));
	}

	private static ConflictException nomeDuplicado() {
		return new ConflictException(CodigoErro.MATERIA_NOME_DUPLICADO, "Já existe uma matéria com esse nome.");
	}

	private static ResourceNotFoundException naoEncontrada() {
		return new ResourceNotFoundException(CodigoErro.MATERIA_NAO_ENCONTRADA, "Matéria não encontrada.");
	}

}
