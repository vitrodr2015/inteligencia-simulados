package br.simulado.etec.catalogo.service;

import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.simulado.etec.catalogo.dto.SubtopicoAtualizacaoRequest;
import br.simulado.etec.catalogo.dto.SubtopicoCriacaoRequest;
import br.simulado.etec.catalogo.model.Materia;
import br.simulado.etec.catalogo.model.Subtopico;
import br.simulado.etec.catalogo.repository.MateriaRepository;
import br.simulado.etec.catalogo.repository.SubtopicoRepository;
import br.simulado.etec.shared.exception.CodigoErro;
import br.simulado.etec.shared.exception.ConflictException;
import br.simulado.etec.shared.exception.InvalidRequestException;
import br.simulado.etec.shared.exception.ResourceNotFoundException;
import br.simulado.etec.shared.paginacao.CamposOrdenaveis;
import br.simulado.etec.shared.paginacao.Filtros;
import br.simulado.etec.shared.persistencia.Restricoes;

/**
 * Regras do subtópico (contrato, seção 4.2). Ordem de validação (D79): formato (400, antes de chegar aqui),
 * existência (404), conflito de estado (409) e, por último, o 400 CAMPO_IMUTAVEL.
 *
 * <p>Matéria e subtópico são do mesmo domínio (catálogo), então este service lê a matéria pelo repositório dela.
 * A matéria desativada só impede CRIAR subtópico e ATIVAR um subtópico inativo (D109); renomear e desativar
 * continuam permitidos.
 */
@Service
@Transactional(readOnly = true)
public class SubtopicoService {

	/** Campos pelos quais a listagem aceita ordenação (lista fixa); padrão: nome, crescente (D77). */
	public static final CamposOrdenaveis CAMPOS_ORDENAVEIS = CamposOrdenaveis.de(Sort.by(Sort.Order.asc("nome")), "nome",
			"criadoEm");

	/** Nome da chave estrangeira do subtópico para a matéria (migration V3). */
	static final String FK_MATERIA = "fk_subtopico_materia";

	private final SubtopicoRepository repositorio;

	private final MateriaRepository materias;

	public SubtopicoService(SubtopicoRepository repositorio, MateriaRepository materias) {
		this.repositorio = repositorio;
		this.materias = materias;
	}

	/**
	 * Matéria inexistente no corpo = 400 (D14: o corpo aponta para um recurso que não existe); matéria desativada =
	 * 409 MATERIA_INATIVA (D54); nome repetido na matéria = 409 SUBTOPICO_NOME_DUPLICADO. Se as duas condições de 409
	 * valerem juntas, vale a da matéria (o estado do pai vem antes do nome).
	 */
	@Transactional
	public Subtopico criar(SubtopicoCriacaoRequest dados) {
		Materia materia = materias.findByPublicId(dados.materiaId()).orElseThrow(SubtopicoService::materiaInexistente);
		if (!materia.isAtivo()) {
			throw materiaInativa();
		}
		// Checagem prévia: dá a mensagem amigável no caso comum.
		if (repositorio.existsByMateriaAndNome(materia, dados.nome())) {
			throw nomeDuplicado();
		}
		try {
			return repositorio.saveAndFlush(new Subtopico(materia, dados.nome()));
		}
		catch (DataIntegrityViolationException ex) {
			throw traduzirFalhaDeGravacao(ex);
		}
	}

	/**
	 * Lista com filtros opcionais: {@code materiaId} (matéria do subtópico), {@code nome} (contém, sem distinguir
	 * maiúsculas e acentos no MySQL; %, _ e a barra invertida valem como texto) e {@code ativo} (nulo = todos). Um
	 * {@code materiaId} sem matéria correspondente é só um filtro que não casa com nada: devolve lista vazia.
	 */
	public Page<Subtopico> listar(UUID materiaId, String nome, Boolean ativo, Pageable paginacao) {
		Specification<Subtopico> filtros = Specification.allOf(daMateria(materiaId),
				Filtros.<Subtopico>contem("nome", nome), Filtros.<Subtopico>igual("ativo", ativo));
		return repositorio.findAll(filtros, paginacao);
	}

	public Subtopico buscar(UUID id) {
		return repositorio.findByPublicId(id).orElseThrow(SubtopicoService::naoEncontrado);
	}

	/**
	 * Troca o nome (substituição total, D61). 404, depois 409 de nome repetido na matéria, e por último o 400
	 * CAMPO_IMUTAVEL se vier um {@code materiaId} diferente do atual (D48, D79). Permitido com a matéria desativada.
	 */
	@Transactional
	public Subtopico atualizar(UUID id, SubtopicoAtualizacaoRequest dados) {
		Subtopico subtopico = buscar(id);
		if (repositorio.existsByMateriaAndNomeAndPublicIdNot(subtopico.getMateria(), dados.nome(), id)) {
			throw nomeDuplicado();
		}
		if (dados.materiaId() != null && !dados.materiaId().equals(subtopico.getMateria().getPublicId())) {
			throw new InvalidRequestException(CodigoErro.CAMPO_IMUTAVEL,
					"A matéria de um subtópico não pode ser alterada (materiaId).");
		}
		subtopico.renomear(dados.nome());
		try {
			return repositorio.saveAndFlush(subtopico);
		}
		catch (DataIntegrityViolationException ex) {
			throw traduzirFalhaDeGravacao(ex);
		}
	}

	/** Idempotente: já inativo devolve o mesmo resultado, sem tocar no registro (atualizadoEm não muda, D62). */
	@Transactional
	public Subtopico desativar(UUID id) {
		Subtopico subtopico = buscar(id);
		if (subtopico.isAtivo()) {
			subtopico.desativar();
			return repositorio.saveAndFlush(subtopico);
		}
		return subtopico;
	}

	/**
	 * Idempotente: já ativo devolve 200 sem efeito, mesmo com a matéria desativada (D62, D109). O 409 só ocorre quando
	 * o enable de fato ativaria um subtópico inativo e a matéria está desativada.
	 */
	@Transactional
	public Subtopico ativar(UUID id) {
		Subtopico subtopico = buscar(id);
		if (subtopico.isAtivo()) {
			return subtopico;
		}
		if (!subtopico.getMateria().isAtivo()) {
			throw materiaInativa();
		}
		subtopico.ativar();
		return repositorio.saveAndFlush(subtopico);
	}

	/**
	 * Atômico: uma instrução só; 0 linhas = não existia. O 409 de "tem questões" (contrato 4.2) entra com a tabela
	 * de questões e a chave estrangeira dela; hoje não há nada que dependa de um subtópico.
	 */
	@Transactional
	public void apagar(UUID id) {
		if (repositorio.apagarPorPublicId(id) == 0) {
			throw naoEncontrado();
		}
	}

	private static Specification<Subtopico> daMateria(UUID materiaId) {
		if (materiaId == null) {
			return Filtros.semFiltro();
		}
		return (raiz, consulta, construtor) -> construtor.equal(raiz.get("materia").get("publicId"), materiaId);
	}

	/** Só traduz as duas restrições esperadas; qualquer outra violação de integridade sobe como está (vira 500). */
	private static RuntimeException traduzirFalhaDeGravacao(DataIntegrityViolationException ex) {
		if (Restricoes.ehUnicidade(ex)) {
			// Corrida: dois pedidos passaram pela checagem e o índice único (materia_id, nome) barrou o segundo.
			return nomeDuplicado();
		}
		if (Restricoes.ehChaveEstrangeira(ex, FK_MATERIA)) {
			// Corrida: a matéria foi apagada entre a leitura e a gravação.
			return materiaInexistente();
		}
		return ex;
	}

	private static InvalidRequestException materiaInexistente() {
		return InvalidRequestException.campoInvalido("materiaId", "A matéria informada não existe.");
	}

	private static ConflictException materiaInativa() {
		return new ConflictException(CodigoErro.MATERIA_INATIVA, "A matéria está desativada.");
	}

	private static ConflictException nomeDuplicado() {
		return new ConflictException(CodigoErro.SUBTOPICO_NOME_DUPLICADO, "Já existe um subtópico com esse nome nessa matéria.");
	}

	private static ResourceNotFoundException naoEncontrado() {
		return new ResourceNotFoundException(CodigoErro.SUBTOPICO_NAO_ENCONTRADO, "Subtópico não encontrado.");
	}

}
