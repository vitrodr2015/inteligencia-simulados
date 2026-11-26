package br.simulado.etec.catalogo.service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.simulado.etec.catalogo.dto.AlternativaRequest;
import br.simulado.etec.catalogo.dto.AlternativasValidasValidator;
import br.simulado.etec.catalogo.dto.QuestaoRequest;
import br.simulado.etec.catalogo.model.Prova;
import br.simulado.etec.catalogo.model.Questao;
import br.simulado.etec.catalogo.model.QuestaoVersao;
import br.simulado.etec.catalogo.model.StatusProva;
import br.simulado.etec.catalogo.model.StatusQuestao;
import br.simulado.etec.catalogo.model.Subtopico;
import br.simulado.etec.catalogo.repository.ProvaRepository;
import br.simulado.etec.catalogo.repository.QuestaoRepository;
import br.simulado.etec.catalogo.repository.QuestaoVersaoRepository;
import br.simulado.etec.catalogo.repository.SubtopicoRepository;
import br.simulado.etec.shared.exception.CodigoErro;
import br.simulado.etec.shared.exception.ConflictException;
import br.simulado.etec.shared.exception.InvalidRequestException;
import br.simulado.etec.shared.exception.ResourceNotFoundException;

/**
 * Regras da questão (contrato, seção 5). O cadastro unitário é o caminho PRIMÁRIO (D152): a importação da fatia 3 chamará
 * {@link #cadastrar} em laço, dentro de uma transação sua, sem duplicar nenhuma regra. Por isso este service não conhece HTTP.
 *
 * <p>Ordem do cadastro (D79, D153, D156, D157, D158): prova inexistente (404) → prova que não é RASCUNHO (409 PROVA_NAO_EDITAVEL) → numero
 * acima da quantidade da prova (400) → subtópico inexistente ou de outro programa (400) → subtópico inativo ou de matéria inativa
 * (409 SUBTOPICO_INATIVO) → numero já cadastrado: mesmo hash = devolve a questão gravada (idempotente), hash diferente = 409
 * QUESTAO_NUMERO_EM_CONFLITO → grava. A linha da prova fica TRAVADA (SELECT ... FOR UPDATE) do começo ao fim: os cadastros da mesma
 * prova se serializam, e a decisão "já existe?" nunca corre com outra transação.
 */
@Service
@Transactional(readOnly = true)
public class QuestaoService {

	/** O que o cadastro fez: criou a questão agora ({@code criada}) ou o numero já tinha a MESMA questão (idempotente, D153). */
	public record ResultadoDoCadastro(QuestaoVersao versao, boolean criada) {
	}

	private final ProvaRepository provas;

	private final QuestaoRepository questoes;

	private final QuestaoVersaoRepository versoes;

	private final SubtopicoRepository subtopicos;

	public QuestaoService(ProvaRepository provas, QuestaoRepository questoes, QuestaoVersaoRepository versoes,
			SubtopicoRepository subtopicos) {
		this.provas = provas;
		this.questoes = questoes;
		this.versoes = versoes;
		this.subtopicos = subtopicos;
	}

	@Transactional
	public ResultadoDoCadastro cadastrar(UUID provaPublicId, QuestaoRequest dados) {
		// As 5 alternativas A a E: o HTTP já validou, mas o service não depende dele (a importação o chama direto).
		if (!AlternativasValidasValidator.valida(dados.alternativas())) {
			throw InvalidRequestException.campoInvalido("alternativas",
					"A questão precisa de exatamente 5 alternativas, com as letras A, B, C, D e E, cada uma uma vez.");
		}
		Prova prova = provas.buscarComTrava(provaPublicId).orElseThrow(QuestaoService::provaNaoEncontrada);
		if (prova.getStatus() != StatusProva.RASCUNHO) {
			throw new ConflictException(CodigoErro.PROVA_NAO_EDITAVEL, "A prova não está em rascunho e não aceita novas questões.");
		}
		if (dados.numero() > prova.getQuantidadeQuestoes()) {
			throw InvalidRequestException.campoInvalido("numero",
					"O número deve ser de 1 até a quantidade de questões da prova (" + prova.getQuantidadeQuestoes() + ").");
		}
		Subtopico subtopico = subtopicos.findByPublicId(dados.subtopicoId()).filter(s -> mesmoPrograma(s, prova))
				.orElseThrow(() -> InvalidRequestException.campoInvalido("subtopicoId", "O subtópico informado não existe."));
		if (!subtopico.visivelParaAluno()) {
			throw new ConflictException(CodigoErro.SUBTOPICO_INATIVO, "O subtópico está desativado, ou é de uma matéria desativada.");
		}

		Map<String, String> textoPorLetra = dados.alternativas().stream()
				.collect(Collectors.toMap(AlternativaRequest::letra, AlternativaRequest::texto));
		String hash = HashDaQuestao.calcular(prova.getPublicId(), dados.numero(), dados.textoApoio(), dados.grupoApoio(),
				dados.enunciado(), dados.imagemUrl(), textoPorLetra);

		return questoes.findByProvaAndNumero(prova, dados.numero()).map(existente -> {
			if (!existente.getHashConteudo().equals(hash)) {
				throw new ConflictException(CodigoErro.QUESTAO_NUMERO_EM_CONFLITO,
						"Já existe uma questão com esse número e conteúdo diferente.",
						Map.of("numerosEmConflito", List.of(dados.numero())));
			}
			return new ResultadoDoCadastro(versaoExibida(existente), false);
		}).orElseGet(() -> {
			Questao questao = questoes.save(new Questao(prova, dados.numero(), hash));
			QuestaoVersao primeira = versoes.saveAndFlush(new QuestaoVersao(questao, subtopico, dados.textoApoio(),
					dados.grupoApoio(), dados.enunciado(), dados.imagemUrl(), textoPorLetra, dados.gabarito(),
					dados.dificuldade(), dados.explicacao()));
			return new ResultadoDoCadastro(primeira, true);
		});
	}

	/**
	 * A versão que o ADMIN vê: com {@code versao}, essa versão (qualquer status, inclusive ARQUIVADA, D41); sem ela, o rascunho aberto
	 * se houver, senão a última publicada (D19). Questão ou versão inexistente = 404 QUESTAO_NAO_ENCONTRADA.
	 */
	public QuestaoVersao buscar(UUID questaoPublicId, Integer versao) {
		Questao questao = questoes.findByPublicId(questaoPublicId).orElseThrow(QuestaoService::questaoNaoEncontrada);
		if (versao != null) {
			return versoes.findByQuestaoAndVersao(questao, versao).orElseThrow(QuestaoService::questaoNaoEncontrada);
		}
		return versaoExibida(questao);
	}

	private QuestaoVersao versaoExibida(Questao questao) {
		return versoes.findByQuestaoAndStatus(questao, StatusQuestao.RASCUNHO)
				.or(() -> versoes.findByQuestaoAndStatus(questao, StatusQuestao.PUBLICADA))
				.orElseThrow(QuestaoService::questaoNaoEncontrada);
	}

	/** O programa do subtópico é o da matéria dele; o da prova é o dela. Outro programa conta como inexistente (D157). */
	private static boolean mesmoPrograma(Subtopico subtopico, Prova prova) {
		return Objects.equals(subtopico.getMateria().getPrograma().getId(), prova.getPrograma().getId());
	}

	private static ResourceNotFoundException provaNaoEncontrada() {
		return new ResourceNotFoundException(CodigoErro.PROVA_NAO_ENCONTRADA, "Prova não encontrada.");
	}

	private static ResourceNotFoundException questaoNaoEncontrada() {
		return new ResourceNotFoundException(CodigoErro.QUESTAO_NAO_ENCONTRADA, "Questão não encontrada.");
	}

}
