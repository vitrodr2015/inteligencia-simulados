package br.simulado.etec.catalogo.service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import br.simulado.etec.catalogo.dto.AlternativaRequest;
import br.simulado.etec.catalogo.dto.AlternativasValidasValidator;
import br.simulado.etec.catalogo.dto.QuestaoAtualizacaoRequest;
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
import br.simulado.etec.shared.config.ImagensProperties;
import br.simulado.etec.shared.exception.CodigoErro;
import br.simulado.etec.shared.exception.ConflictException;
import br.simulado.etec.shared.exception.InvalidRequestException;
import br.simulado.etec.shared.exception.ResourceNotFoundException;
import br.simulado.etec.shared.paginacao.CamposOrdenaveis;
import br.simulado.etec.shared.persistencia.Restricoes;
import br.simulado.etec.shared.validacao.HostDeImagemPermitidoValidator;

/**
 * Regras da questão (contrato, seção 5). O cadastro unitário é o caminho PRIMÁRIO (D152). O cadastro tem duas partes,
 * {@link #travarProvaEditavel} e {@link #cadastrarNaProva}, que o unitário ({@link #cadastrar}) e a importação em lote usam,
 * sem duplicar nenhuma regra. Este service não conhece HTTP.
 *
 * <p>Ordem do cadastro (D79, D153, D156 a D158 e a D169): prova inexistente (404) → prova que não é RASCUNHO (409
 * PROVA_NAO_EDITAVEL) → numero acima da quantidade da prova (400) → subtópico inexistente ou de outro programa (400) → numero já
 * cadastrado: mesmo hash = a questão gravada (idempotente), hash diferente = conflito (409 QUESTAO_NUMERO_EM_CONFLITO) → só
 * quando vai gravar: subtópico inativo ou de matéria inativa (409 SUBTOPICO_INATIVO) → grava. A linha da prova fica TRAVADA
 * (SELECT ... FOR UPDATE) até o fim da transação: os cadastros da mesma prova se serializam, e a decisão "já existe?" nunca
 * corre com outra transação.
 */
@Service
@Transactional(readOnly = true)
public class QuestaoService {

	/** A listagem só ordena por numero (D77); padrão: numero crescente. */
	public static final CamposOrdenaveis CAMPOS_ORDENAVEIS = CamposOrdenaveis.de(Sort.by(Sort.Order.asc("numero")), "numero");

	/** O que aconteceu com uma questão enviada ao cadastro. */
	public enum Situacao {
		/** Gravada agora. */
		CRIADA,
		/** O numero já tinha a MESMA questão (mesmo hash): nada foi gravado (D153). */
		JA_EXISTIA,
		/** O numero já tem questão de conteúdo diferente: nada foi gravado. */
		CONFLITO
	}

	/** O resultado do cadastro; {@code versao} é a gravada (ou a que já estava lá) e é {@code null} no CONFLITO. */
	public record ResultadoDoCadastro(Situacao situacao, QuestaoVersao versao) {

		public boolean criada() {
			return situacao == Situacao.CRIADA;
		}

	}

	/** O que aconteceu ao aplicar um item do gabarito em lote (D174). */
	public enum SituacaoDoGabarito {
		/** O gabarito do rascunho mudou. */
		ALTERADO,
		/** O rascunho já tinha esse gabarito: nada foi gravado. */
		SEM_MUDANCA,
		/** A questão existe, mas não tem rascunho aberto: nada foi gravado. */
		SEM_RASCUNHO,
		/** A prova não tem questão com esse numero. */
		QUESTAO_INEXISTENTE
	}

	/** Por que uma questão impede a publicação da prova (D178). Os nomes saem na API, em detalhes.questoesPendentes[].motivo. */
	public enum MotivoDePendencia {
		/** A prova não tem questão com esse numero. */
		QUESTAO_AUSENTE,
		/** A questão não tem rascunho aberto para publicar. Defensivo: numa prova em rascunho a API não publica questão avulsa. */
		QUESTAO_SEM_RASCUNHO,
		SEM_GABARITO,
		/** O subtópico, ou a matéria dele, está desativado. */
		SUBTOPICO_INATIVO,
		/** Defensivo: o cadastro e o PUT já recusam subtópico de outro programa. */
		SUBTOPICO_DE_OUTRO_PROGRAMA,
		/** O host da imagem não está na lista de hoje (a lista é configuração e pode ter mudado). */
		IMAGEM_NAO_PERMITIDA
	}

	/** Uma pendência de publicação. A mesma questão aparece uma vez por motivo. */
	public record Pendencia(int numero, MotivoDePendencia motivo) {
	}

	private final ProvaRepository provas;

	private final QuestaoRepository questoes;

	private final QuestaoVersaoRepository versoes;

	private final SubtopicoRepository subtopicos;

	private final TransactionTemplate transacao;

	private final Clock relogio;

	private final ImagensProperties imagens;

	public QuestaoService(ProvaRepository provas, QuestaoRepository questoes, QuestaoVersaoRepository versoes,
			SubtopicoRepository subtopicos, PlatformTransactionManager gerenteDeTransacao, Clock relogio,
			ImagensProperties imagens) {
		this.provas = provas;
		this.questoes = questoes;
		this.versoes = versoes;
		this.subtopicos = subtopicos;
		this.transacao = new TransactionTemplate(gerenteDeTransacao);
		this.relogio = relogio;
		this.imagens = imagens;
	}

	/** O cadastro unitário: trava a prova, cadastra nela e traduz o conflito de conteúdo no 409. */
	@Transactional
	public ResultadoDoCadastro cadastrar(UUID provaPublicId, QuestaoRequest dados) {
		exigirAlternativas(dados.alternativas());
		ResultadoDoCadastro resultado = gravarNaProva(travarProvaEditavel(provaPublicId), dados);
		if (resultado.situacao() == Situacao.CONFLITO) {
			throw new ConflictException(CodigoErro.QUESTAO_NUMERO_EM_CONFLITO,
					"Já existe uma questão com esse número e conteúdo diferente.",
					Map.of("numerosEmConflito", List.of(dados.numero())));
		}
		return resultado;
	}

	/**
	 * Primeira parte do cadastro: TRAVA a linha da prova (SELECT ... FOR UPDATE) até o fim da transação e exige que ela esteja em
	 * RASCUNHO (404 se não existe; 409 PROVA_NAO_EDITAVEL se não é rascunho).
	 *
	 * <p>Precisa ser a PRIMEIRA leitura da transação de quem chama. No REPEATABLE READ do MySQL, uma leitura comum antes da trava
	 * fixa o snapshot: quem esperou a trava não enxergaria o que a transação anterior gravou, decidiria que o numero está livre e
	 * terminaria em erro no índice único.
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public Prova travarProvaEditavel(UUID provaPublicId) {
		Prova prova = provas.buscarComTrava(provaPublicId).orElseThrow(QuestaoService::provaNaoEncontrada);
		ProvaService.exigirRascunho(prova);
		return prova;
	}

	/**
	 * PUT de uma questão (D171): substituição total do RASCUNHO ABERTO. Só o status da versão decide: sem rascunho aberto é 409
	 * QUESTAO_SEM_RASCUNHO, e o status da prova não importa (editar a questão não altera a prova, D173). O hash nunca muda (D51).
	 *
	 * <p>Trava (D175): ver {@code comAProvaDaQuestaoTravada}.
	 *
	 * <p>Ordem (D79): 404 → 409 sem rascunho → 400 do subtópico e 409 SUBTOPICO_INATIVO (só se o subtópico for TROCADO, D172) →
	 * 400 CAMPO_IMUTAVEL (numero ou provaId diferentes do atual, mesmo que o provaId enviado não exista).
	 */
	@Transactional(propagation = Propagation.NEVER)
	public QuestaoVersao atualizar(UUID questaoPublicId, QuestaoAtualizacaoRequest dados) {
		exigirAlternativas(dados.alternativas());
		return comAProvaDaQuestaoTravada(questaoPublicId, (prova, questao) -> atualizarRascunho(prova, questao, dados));
	}

	/**
	 * Abre uma versão nova da questão (D181): cópia COMPLETA da publicada, em RASCUNHO, com a versão 1 acima da maior que existe.
	 * O subtópico é copiado mesmo desativado (quem confere é a publicação). numero, prova e hash são da identidade e não mudam
	 * (D48, D51). O status da prova não importa. Ordem: 404 → 409 QUESTAO_COM_RASCUNHO → 409 CONFLITO quando não há publicada
	 * para copiar (defensivo: a API não deixa uma questão sem rascunho e sem publicada).
	 */
	@Transactional(propagation = Propagation.NEVER)
	public QuestaoVersao criarVersao(UUID questaoPublicId) {
		return comAProvaDaQuestaoTravada(questaoPublicId, (prova, questao) -> {
			if (versoes.findByQuestaoAndStatus(questao, StatusQuestao.RASCUNHO).isPresent()) {
				throw new ConflictException(CodigoErro.QUESTAO_COM_RASCUNHO,
						"A questão já tem um rascunho aberto. Altere ou publique esse rascunho.");
			}
			QuestaoVersao publicada = versoes.findByQuestaoAndStatus(questao, StatusQuestao.PUBLICADA)
					.orElseThrow(() -> new ConflictException("A questão não tem versão publicada para copiar."));
			return gravar(QuestaoVersao.copiaDe(publicada, versoes.buscarMaiorVersao(questao) + 1));
		});
	}

	/**
	 * Publica UMA questão (D182). Só vale em prova PUBLICADA (numa prova em rascunho quem publica é a cascata) e só publica o
	 * rascunho aberto; as conferências são as MESMAS da cascata. Ordem: 404 → 409 PROVA_NAO_PUBLICADA → 409
	 * QUESTAO_SEM_RASCUNHO → 409 QUESTAO_COM_PENDENCIAS. A publicada anterior vira ARQUIVADA na mesma transação. A prova
	 * não muda (D173).
	 */
	@Transactional(propagation = Propagation.NEVER)
	public QuestaoVersao publicar(UUID questaoPublicId) {
		return comAProvaDaQuestaoTravada(questaoPublicId, (prova, questao) -> {
			if (prova.getStatus() != StatusProva.PUBLICADA) {
				throw new ConflictException(CodigoErro.PROVA_NAO_PUBLICADA,
						"A prova ainda não foi publicada. Publique a prova, que publica as questões dela.");
			}
			QuestaoVersao rascunho = versoes.findByQuestaoAndStatus(questao, StatusQuestao.RASCUNHO)
					.orElseThrow(QuestaoService::semRascunho);
			List<Pendencia> pendencias = pendenciasDe(rascunho, prova);
			if (!pendencias.isEmpty()) {
				throw recusaPorPendencias(CodigoErro.QUESTAO_COM_PENDENCIAS,
						"A questão não pode ser publicada: há pendências. Nada foi publicado.", pendencias);
			}
			List<QuestaoVersao> anteriores = versoes.findByQuestaoAndStatus(questao, StatusQuestao.PUBLICADA).stream().toList();
			publicarNoLugarDas(anteriores, List.of(rascunho), relogio.instant().truncatedTo(ChronoUnit.MILLIS));
			return rascunho;
		});
	}

	/**
	 * Apaga (D184). Só RASCUNHO se apaga: versão PUBLICADA ou ARQUIVADA, e a identidade que a tem, ficam para sempre (D183).
	 *
	 * <p>Questão que já foi publicada: some só o rascunho aberto, e a questão volta ao estado publicado; sem rascunho, 409
	 * QUESTAO_SEM_RASCUNHO. Questão NUNCA publicada: some inteira (alternativas, versão e identidade), e o numero fica livre
	 * para cadastrar ou reimportar; isso só vale com a prova em RASCUNHO (senão 409 PROVA_NAO_EDITAVEL, defensivo: numa
	 * prova publicada a API não deixa uma questão sem publicada). A prova não muda.
	 */
	@Transactional(propagation = Propagation.NEVER)
	public void apagar(UUID questaoPublicId) {
		comAProvaDaQuestaoTravada(questaoPublicId, (prova, questao) -> {
			Optional<QuestaoVersao> rascunho = versoes.findByQuestaoAndStatus(questao, StatusQuestao.RASCUNHO);
			if (versoes.existsByQuestaoAndStatusNot(questao, StatusQuestao.RASCUNHO)) {
				versoes.delete(rascunho.orElseThrow(() -> new ConflictException(CodigoErro.QUESTAO_SEM_RASCUNHO,
						"A questão já foi publicada e não tem rascunho aberto: não há o que apagar.")));
			}
			else {
				ProvaService.exigirRascunho(prova);
				rascunho.ifPresent(versoes::delete);
				versoes.flush();
				questoes.delete(questao);
			}
			return null;
		});
	}

	/**
	 * Apaga todas as questões da prova que {@link ProvaService} travou, na transação dela (D185). Se alguma já foi publicada,
	 * não apaga nada e devolve os numeros delas. Só rascunhos são escolhidos para apagar. Se a conferência falhasse, a exclusão
	 * não passaria: o Hibernate recusa apagar a questão que uma versão carregada (a publicada) ainda referencia, e a chave
	 * estrangeira RESTRICT barra a que não é carregada (a arquivada). Isso NÃO protege contra leitura velha: quem garante que o
	 * que parece rascunho ainda é rascunho é a trava da prova. De baixo para cima, como as chaves RESTRICT pedem, com um flush
	 * entre as tabelas.
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public List<Integer> apagarQuestoesDaProva(Prova provaTravada) {
		List<Integer> jaPublicados = versoes.buscarNumerosJaPublicados(provaTravada);
		if (!jaPublicados.isEmpty()) {
			return jaPublicados;
		}
		versoes.deleteAll(versoes.buscarVivasDaProva(provaTravada).stream()
				.filter(versao -> versao.getStatus() == StatusQuestao.RASCUNHO).toList());
		versoes.flush();
		questoes.deleteAll(questoes.findByProva(provaTravada));
		questoes.flush();
		return List.of();
	}

	/**
	 * A escrita pelas rotas que trazem o id da QUESTÃO (D175). A prova de uma questão nunca muda, então o id dela é lido ANTES,
	 * numa transação própria e curta; depois a transação de escrita trava a prova como PRIMEIRA leitura e lê a questão de novo
	 * (se sumiu enquanto esperava, 404). Ler qualquer coisa antes da trava dentro da mesma transação fixaria o snapshot do
	 * REPEATABLE READ e esconderia o que a transação anterior gravou; como o UPDATE grava todas as colunas, isso desfaria em
	 * silêncio uma publicação. Quem chama é {@code NEVER}: não pode estar dentro de outra transação.
	 */
	private <T> T comAProvaDaQuestaoTravada(UUID questaoPublicId, BiFunction<Prova, Questao, T> escrita) {
		UUID provaPublicId = questoes.buscarProvaDaQuestao(questaoPublicId).orElseThrow(QuestaoService::questaoNaoEncontrada);
		return transacao.execute(estado -> {
			// PRIMEIRA leitura da transação.
			Prova prova = provas.buscarComTrava(provaPublicId).orElseThrow(QuestaoService::questaoNaoEncontrada);
			Questao questao = questoes.findByPublicId(questaoPublicId).orElseThrow(QuestaoService::questaoNaoEncontrada);
			return escrita.apply(prova, questao);
		});
	}

	private QuestaoVersao atualizarRascunho(Prova prova, Questao questao, QuestaoAtualizacaoRequest dados) {
		QuestaoVersao rascunho = versoes.findByQuestaoAndStatus(questao, StatusQuestao.RASCUNHO)
				.orElseThrow(QuestaoService::semRascunho);
		Subtopico subtopico = rascunho.getSubtopico();
		if (!subtopico.getPublicId().equals(dados.subtopicoId())) {
			subtopico = subtopicoDoPrograma(dados.subtopicoId(), prova);
			exigirVisivel(subtopico);
		}
		exigirImutaveis(questao, prova, dados);
		rascunho.editarRascunho(subtopico, dados.textoApoio(), dados.grupoApoio(), dados.enunciado(), dados.imagemUrl(),
				textoPorLetra(dados.alternativas()), dados.gabarito(), dados.dificuldade(), dados.explicacao(),
				relogio.instant().truncatedTo(ChronoUnit.MILLIS));
		return gravar(rascunho);
	}

	/** numero e provaId são opcionais no PUT; enviados diferentes do atual dão 400 CAMPO_IMUTAVEL, por último (D42, D48, D79). */
	private static void exigirImutaveis(Questao questao, Prova prova, QuestaoAtualizacaoRequest dados) {
		if (dados.numero() != null && dados.numero() != questao.getNumero()) {
			throw new InvalidRequestException(CodigoErro.CAMPO_IMUTAVEL, "O número de uma questão não pode ser alterado (numero).");
		}
		if (dados.provaId() != null && !dados.provaId().equals(prova.getPublicId())) {
			throw new InvalidRequestException(CodigoErro.CAMPO_IMUTAVEL, "A prova de uma questão não pode ser alterada (provaId).");
		}
	}

	/**
	 * UM item do gabarito em lote, na prova que {@link #travarProvaEditavel} devolveu e na mesma transação (D174). A regra de
	 * editar a questão mora aqui: só o rascunho aberto muda, inclusive o de uma questão já publicada que ganhou versão nova.
	 * Nada é lançado: o lote precisa juntar todos os problemas e desfazer tudo no fim.
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public SituacaoDoGabarito aplicarGabarito(Prova provaTravada, int numero, String gabarito) {
		Optional<Questao> questao = questoes.findByProvaAndNumero(provaTravada, numero);
		if (questao.isEmpty()) {
			return SituacaoDoGabarito.QUESTAO_INEXISTENTE;
		}
		Optional<QuestaoVersao> rascunho = versoes.findByQuestaoAndStatus(questao.get(), StatusQuestao.RASCUNHO);
		if (rascunho.isEmpty()) {
			return SituacaoDoGabarito.SEM_RASCUNHO;
		}
		return rascunho.get().definirGabarito(gabarito) ? SituacaoDoGabarito.ALTERADO : SituacaoDoGabarito.SEM_MUDANCA;
	}

	/**
	 * A parte das questões na publicação da prova (D178), na prova que quem chama TRAVOU (como primeira leitura da transação) e na
	 * mesma transação. Primeiro confere TODAS as questões de 1 a quantidadeQuestoes e junta todas as pendências, em ordem de
	 * numero, sem gravar nada; havendo alguma, devolve a lista. Sem nenhuma, publica todos os rascunhos com o mesmo
	 * {@code momento} e devolve a lista vazia.
	 *
	 * <p>Se uma questão já tiver versão publicada além do rascunho (a API não produz isso numa prova em rascunho), a
	 * publicada é arquivada e isso vai ao banco ANTES de o rascunho ser publicado: o índice único de "uma publicada por questão"
	 * é conferido a cada instrução, e o JPA não garante a ordem das instruções sem o flush.
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public List<Pendencia> publicarQuestoesDaProva(Prova provaTravada, Instant momento) {
		Set<Integer> numeros = questoes.findByProva(provaTravada).stream().map(Questao::getNumero).collect(Collectors.toSet());
		Map<Integer, QuestaoVersao> rascunhos = new HashMap<>();
		Map<Integer, QuestaoVersao> publicadas = new HashMap<>();
		for (QuestaoVersao versao : versoes.buscarVivasDaProva(provaTravada)) {
			(versao.getStatus() == StatusQuestao.RASCUNHO ? rascunhos : publicadas).put(versao.getQuestao().getNumero(), versao);
		}

		List<Pendencia> pendencias = new ArrayList<>();
		List<QuestaoVersao> aPublicar = new ArrayList<>();
		for (int numero = 1; numero <= provaTravada.getQuantidadeQuestoes(); numero++) {
			if (!numeros.contains(numero)) {
				pendencias.add(new Pendencia(numero, MotivoDePendencia.QUESTAO_AUSENTE));
				continue;
			}
			QuestaoVersao rascunho = rascunhos.get(numero);
			if (rascunho == null) {
				pendencias.add(new Pendencia(numero, MotivoDePendencia.QUESTAO_SEM_RASCUNHO));
				continue;
			}
			pendencias.addAll(pendenciasDe(rascunho, provaTravada));
			aPublicar.add(rascunho);
		}
		if (!pendencias.isEmpty()) {
			return pendencias;
		}
		publicarNoLugarDas(aPublicar.stream().map(rascunho -> publicadas.get(rascunho.getQuestao().getNumero()))
				.filter(Objects::nonNull).toList(), aPublicar, momento);
		return List.of();
	}

	/**
	 * A troca da publicada, num ponto só para a cascata e a publicação avulsa: arquiva as anteriores, manda isso ao banco e só
	 * então publica os rascunhos. O índice único de "uma publicada por questão" é conferido a cada instrução, e o JPA não
	 * garante a ordem das instruções sem o flush. Tudo na transação de quem chama: se publicar falhar, o arquivamento se desfaz.
	 */
	private void publicarNoLugarDas(List<QuestaoVersao> anteriores, List<QuestaoVersao> rascunhos, Instant momento) {
		anteriores.forEach(QuestaoVersao::arquivar);
		versoes.flush();
		rascunhos.forEach(rascunho -> rascunho.publicar(momento));
	}

	/** As pendências de UM rascunho, as mesmas na cascata e na publicação avulsa: o único lugar da regra do que publica. */
	private List<Pendencia> pendenciasDe(QuestaoVersao rascunho, Prova prova) {
		int numero = rascunho.getQuestao().getNumero();
		return impedimentos(rascunho, prova).stream().map(motivo -> new Pendencia(numero, motivo)).toList();
	}

	/**
	 * O 409 de publicação recusada, montado num lugar só, com o mesmo corpo nas duas rotas
	 * (detalhes.questoesPendentes = {numero, motivo}). Só o codigo muda: o da prova e o da questão avulsa (D187).
	 */
	static ConflictException recusaPorPendencias(CodigoErro codigo, String mensagem, List<Pendencia> pendencias) {
		return new ConflictException(codigo, mensagem, Map.of("questoesPendentes", pendencias));
	}

	/** Tudo o que impede ESTE rascunho de ser publicado, na ordem dos motivos. */
	private List<MotivoDePendencia> impedimentos(QuestaoVersao rascunho, Prova prova) {
		List<MotivoDePendencia> motivos = new ArrayList<>();
		if (rascunho.getGabarito() == null) {
			motivos.add(MotivoDePendencia.SEM_GABARITO);
		}
		if (!rascunho.getSubtopico().visivelParaAluno()) {
			motivos.add(MotivoDePendencia.SUBTOPICO_INATIVO);
		}
		if (!mesmoPrograma(rascunho.getSubtopico(), prova)) {
			motivos.add(MotivoDePendencia.SUBTOPICO_DE_OUTRO_PROGRAMA);
		}
		if (!HostDeImagemPermitidoValidator.hostPermitido(rascunho.getImagemUrl(), imagens)) {
			motivos.add(MotivoDePendencia.IMAGEM_NAO_PERMITIDA);
		}
		return motivos;
	}

	/**
	 * Segunda parte: cadastra UMA questão na prova que {@link #travarProvaEditavel} devolveu, na mesma transação. Usada pelo
	 * cadastro unitário e pelo lote.
	 *
	 * <p>O conflito de conteúdo no numero é DEVOLVIDO ({@link Situacao#CONFLITO}, sem nada gravado), e não lançado: uma exceção que
	 * cruza o proxy transacional marca a transação para rollback, e o lote precisa continuar para juntar todos os conflitos. As
	 * demais recusas são exceções.
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public ResultadoDoCadastro cadastrarNaProva(Prova provaTravada, QuestaoRequest dados) {
		exigirAlternativas(dados.alternativas());
		return gravarNaProva(provaTravada, dados);
	}

	/** As 5 alternativas A a E: o HTTP já validou, mas o service não depende dele. */
	private static void exigirAlternativas(List<AlternativaRequest> alternativas) {
		if (!AlternativasValidasValidator.valida(alternativas)) {
			throw InvalidRequestException.campoInvalido("alternativas",
					"A questão precisa de exatamente 5 alternativas, com as letras A, B, C, D e E, cada uma uma vez.");
		}
	}

	/**
	 * Ordem: numero acima da prova (400) → subtópico inexistente ou de outro programa (400) → hash: o numero já tem questão? igual =
	 * JA_EXISTIA, diferente = CONFLITO → só então o estado do subtópico (409 SUBTOPICO_INATIVO) → grava. Quando nada será gravado,
	 * o subtópico estar desativado não importa.
	 */
	private ResultadoDoCadastro gravarNaProva(Prova prova, QuestaoRequest dados) {
		if (dados.numero() > prova.getQuantidadeQuestoes()) {
			throw InvalidRequestException.campoInvalido("numero",
					"O número deve ser de 1 até a quantidade de questões da prova (" + prova.getQuantidadeQuestoes() + ").");
		}
		Subtopico subtopico = subtopicoDoPrograma(dados.subtopicoId(), prova);

		Map<String, String> textoPorLetra = textoPorLetra(dados.alternativas());
		String hash = HashDaQuestao.calcular(prova.getPublicId(), dados.numero(), dados.textoApoio(), dados.grupoApoio(),
				dados.enunciado(), dados.imagemUrl(), textoPorLetra);

		Optional<Questao> existente = questoes.findByProvaAndNumero(prova, dados.numero());
		if (existente.isPresent()) {
			return existente.get().getHashConteudo().equals(hash)
					? new ResultadoDoCadastro(Situacao.JA_EXISTIA, versaoExibida(existente.get()))
					: new ResultadoDoCadastro(Situacao.CONFLITO, null);
		}
		exigirVisivel(subtopico);
		Questao questao = questoes.save(new Questao(prova, dados.numero(), hash));
		QuestaoVersao primeira = gravar(new QuestaoVersao(questao, subtopico, dados.textoApoio(),
				dados.grupoApoio(), dados.enunciado(), dados.imagemUrl(), textoPorLetra, dados.gabarito(),
				dados.dificuldade(), dados.explicacao()));
		return new ResultadoDoCadastro(Situacao.CRIADA, primeira);
	}

	/** O subtópico precisa existir e ser do programa da prova (D157); senão, 400 no campo. */
	private Subtopico subtopicoDoPrograma(UUID subtopicoId, Prova prova) {
		return subtopicos.findByPublicId(subtopicoId).filter(s -> mesmoPrograma(s, prova))
				.orElseThrow(QuestaoService::subtopicoInexistente);
	}

	/** Para ENTRAR numa questão, o subtópico precisa estar ativo, e a matéria dele também (D156, D172). */
	private static void exigirVisivel(Subtopico subtopico) {
		if (!subtopico.visivelParaAluno()) {
			throw new ConflictException(CodigoErro.SUBTOPICO_INATIVO, "O subtópico está desativado, ou é de uma matéria desativada.");
		}
	}

	/**
	 * Grava a versão e já manda ao banco. A trava da prova não cobre o subtópico: se ele for apagado entre a leitura e a
	 * gravação, a chave estrangeira recusa e isso é o mesmo 400 do subtópico inexistente (D177). Só ESSA restrição é
	 * traduzida; qualquer outra violação sobe como está.
	 */
	private QuestaoVersao gravar(QuestaoVersao versao) {
		try {
			return versoes.saveAndFlush(versao);
		}
		catch (DataIntegrityViolationException ex) {
			if (Restricoes.ehChaveEstrangeira(ex, SubtopicoService.FK_QUESTAO_VERSAO)) {
				throw subtopicoInexistente();
			}
			throw ex;
		}
	}

	private static Map<String, String> textoPorLetra(List<AlternativaRequest> alternativas) {
		return alternativas.stream().collect(Collectors.toMap(AlternativaRequest::letra, AlternativaRequest::texto));
	}

	private static InvalidRequestException subtopicoInexistente() {
		return InvalidRequestException.campoInvalido("subtopicoId", "O subtópico informado não existe.");
	}

	private static ConflictException semRascunho() {
		return new ConflictException(CodigoErro.QUESTAO_SEM_RASCUNHO,
				"A questão não tem rascunho aberto. Crie uma nova versão para alterá-la.");
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

	/**
	 * A listagem do ADMIN: de cada questão, só a versão EXIBIDA (rascunho aberto, senão a publicada; D19, D56), em ordem de numero
	 * (desempate pelo id interno, D77). Filtros opcionais (nulo = todos), todos sobre a versão exibida; um provaId ou subtopicoId que
	 * não existe não é erro: só não casa com nada (lista vazia). São duas consultas fixas, não importa o tamanho da página.
	 */
	public Page<QuestaoVersao> listar(UUID provaId, UUID subtopicoId, StatusQuestao status, Pageable paginacao) {
		// a ordem é fixa na consulta: a paginação vai sem sort, senão o Spring Data acrescentaria outro order by
		Pageable semOrdem = PageRequest.of(paginacao.getPageNumber(), paginacao.getPageSize());
		Page<Long> ids = versoes.buscarIdsDasVersoesExibidas(provaId, subtopicoId, status, semOrdem);
		if (ids.isEmpty()) {
			return new PageImpl<>(List.of(), semOrdem, ids.getTotalElements());
		}
		Map<Long, QuestaoVersao> porId = versoes.carregarCompletas(ids.getContent()).stream()
				.collect(Collectors.toMap(QuestaoVersao::getId, Function.identity(), (a, b) -> a));
		return ids.map(porId::get);
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
