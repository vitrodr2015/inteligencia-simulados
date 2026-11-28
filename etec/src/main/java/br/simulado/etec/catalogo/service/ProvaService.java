package br.simulado.etec.catalogo.service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

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
import br.simulado.etec.catalogo.repository.QuestaoRepository;
import br.simulado.etec.catalogo.repository.QuestaoRepository.ContagemPorProva;
import br.simulado.etec.catalogo.service.QuestaoService.Pendencia;
import br.simulado.etec.shared.exception.CodigoErro;
import br.simulado.etec.shared.exception.ConflictException;
import br.simulado.etec.shared.exception.ResourceNotFoundException;
import br.simulado.etec.shared.paginacao.CamposOrdenaveis;
import br.simulado.etec.shared.paginacao.Filtros;
import br.simulado.etec.shared.persistencia.Restricoes;
import br.simulado.etec.shared.programa.Programa;

/**
 * Regras da prova (contrato, seção 4.3): criar (sempre em RASCUNHO), editar o rascunho (D170), publicar (D178), listar e buscar,
 * só para o ADMIN (D154).
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

	private final QuestaoRepository questoes;

	private final QuestaoService servicoDeQuestoes;

	private final Clock relogio;

	public ProvaService(ProvaRepository repositorio, ProgramaRepository programas, QuestaoRepository questoes,
			QuestaoService servicoDeQuestoes, Clock relogio) {
		this.repositorio = repositorio;
		this.programas = programas;
		this.questoes = questoes;
		this.servicoDeQuestoes = servicoDeQuestoes;
		this.relogio = relogio;
	}

	/**
	 * Publica a prova e, em cascata, as questões dela (D178), numa transação só: tudo ou nada. A linha da prova é TRAVADA como
	 * primeira leitura, então nenhum cadastro, importação, edição ou gabarito corre no meio. Ordem (D79): 404 → 409
	 * PROVA_NAO_EDITAVEL (não é rascunho, inclusive a já publicada) → 409 PROVA_COM_QUESTOES_PENDENTES, com TODAS as
	 * pendências. A prova e todas as versões levam o mesmo publicada_em.
	 */
	@Transactional
	public Prova publicar(UUID id) {
		Prova prova = repositorio.buscarComTrava(id).orElseThrow(ProvaService::naoEncontrada);
		exigirRascunho(prova);
		Instant momento = relogio.instant().truncatedTo(ChronoUnit.MILLIS);
		List<Pendencia> pendencias = servicoDeQuestoes.publicarQuestoesDaProva(prova, momento);
		if (!pendencias.isEmpty()) {
			throw QuestaoService.recusaPorPendencias(CodigoErro.PROVA_COM_QUESTOES_PENDENTES,
					"A prova não pode ser publicada: há questões pendentes. Nada foi publicado.", pendencias);
		}
		prova.publicar(momento);
		return repositorio.saveAndFlush(prova);
	}

	/**
	 * Apaga a prova em RASCUNHO e, antes dela, todas as questões dela (D185), numa transação só: tudo ou nada. A linha da prova
	 * é TRAVADA como primeira leitura; quem esperava a trava não a encontra mais. Ordem (D79): 404 → 409 PROVA_NAO_EDITAVEL →
	 * 409 PROVA_COM_QUESTOES_PUBLICADAS com detalhes.numerosPublicados (defesa de invariante: a publicação é em cascata, então
	 * a API não deixa uma prova em rascunho com questão publicada).
	 */
	@Transactional
	public void apagar(UUID id) {
		Prova prova = repositorio.buscarComTrava(id).orElseThrow(ProvaService::naoEncontrada);
		exigirRascunho(prova);
		List<Integer> jaPublicados = servicoDeQuestoes.apagarQuestoesDaProva(prova);
		if (!jaPublicados.isEmpty()) {
			throw new ConflictException(CodigoErro.PROVA_COM_QUESTOES_PUBLICADAS,
					"A prova tem questão que já foi publicada e não pode ser apagada. Nada foi apagado.",
					Map.of("numerosPublicados", jaPublicados));
		}
		repositorio.delete(prova);
		repositorio.flush();
	}

	/**
	 * Quantas questões (identidades, em qualquer status, nunca versões) cada prova tem, pela chave interna: UMA consulta agrupada
	 * para todas as provas pedidas. Prova sem questão não vem no mapa (vale 0).
	 */
	public Map<Long, Long> contarQuestoes(Collection<Prova> provas) {
		if (provas.isEmpty()) {
			return Map.of();
		}
		return questoes.contarPorProva(provas.stream().map(Prova::getId).toList()).stream()
				.collect(Collectors.toMap(ContagemPorProva::getProvaId, ContagemPorProva::getTotal));
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

	/**
	 * PUT da prova (D170): substituição total, só em RASCUNHO, inclusive da quantidadeQuestoes. A linha da prova fica TRAVADA, e
	 * a trava é a primeira leitura da transação: um cadastro de questão simultâneo ou já está gravado (e conta no maior numero)
	 * ou espera e passa a valer a quantidade nova.
	 *
	 * <p>Ordem (D79): 404 → 409 PROVA_NAO_EDITAVEL → 409 PROVA_DUPLICADA → 409 PROVA_COM_QUESTOES_ACIMA_DA_QUANTIDADE. As
	 * checagens vêm ANTES de alterar a entidade, senão uma consulta faria o JPA gravar a alteração no meio delas.
	 */
	@Transactional
	public Prova atualizar(UUID id, ProvaRequest dados) {
		Prova prova = repositorio.buscarComTrava(id).orElseThrow(ProvaService::naoEncontrada);
		exigirRascunho(prova);
		boolean identificada = dados.banca() != null && dados.ano() != null && dados.semestre() != null;
		if (identificada && repositorio.existsByProgramaAndBancaAndAnoAndSemestreAndPublicIdNot(prova.getPrograma(),
				dados.banca(), dados.ano(), dados.semestre(), id)) {
			throw provaDuplicada();
		}
		questoes.buscarMaiorNumero(prova).filter(maior -> maior > dados.quantidadeQuestoes()).ifPresent(maior -> {
			throw new ConflictException(CodigoErro.PROVA_COM_QUESTOES_ACIMA_DA_QUANTIDADE,
					"A prova tem questão cadastrada com número maior que a quantidade informada.",
					Map.of("maiorNumero", maior));
		});
		prova.editar(dados.titulo(), dados.banca(), dados.ano(), dados.semestre(), dados.duracaoMinutos(),
				dados.quantidadeQuestoes(), dados.fonteUrl());
		try {
			return repositorio.saveAndFlush(prova);
		}
		catch (DataIntegrityViolationException ex) {
			// Corrida: outra prova pegou a mesma identidade entre a checagem e a gravação.
			if (Restricoes.ehUnicidade(ex, UK_PROVA)) {
				throw provaDuplicada();
			}
			throw ex;
		}
	}

	/** Só a prova em RASCUNHO se altera (409 PROVA_NAO_EDITAVEL); a regra e a mensagem moram aqui. */
	static void exigirRascunho(Prova prova) {
		if (prova.getStatus() != StatusProva.RASCUNHO) {
			throw new ConflictException(CodigoErro.PROVA_NAO_EDITAVEL, "A prova não está em rascunho e não pode ser alterada.");
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
