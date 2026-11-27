package br.simulado.etec.admin.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.simulado.etec.admin.dto.GabaritoRequest;
import br.simulado.etec.admin.dto.GabaritoRequest.ItemDoGabaritoRequest;
import br.simulado.etec.admin.dto.GabaritoResponse;
import br.simulado.etec.catalogo.model.Prova;
import br.simulado.etec.catalogo.service.QuestaoService;
import br.simulado.etec.shared.exception.CodigoErro;
import br.simulado.etec.shared.exception.ConflictException;
import br.simulado.etec.shared.exception.ErrorResponse.CampoInvalido;
import br.simulado.etec.shared.exception.InvalidRequestException;

/**
 * Gabarito em lote (D174): um invólucro TUDO OU NADA. A regra de editar uma questão mora no catálogo
 * ({@link QuestaoService#aplicarGabarito}); aqui ficam só as do lote. Este domínio só conversa com o SERVIÇO do catálogo, nunca
 * com os repositórios dele.
 *
 * <p>Ordem: numero repetido no lote (400, antes de tocar o banco) → trava a prova, UMA vez (404 se não existe; 409
 * PROVA_NAO_EDITAVEL se não é rascunho) → percorre todos os itens → 400 com TODOS os numeros que não existem na prova → 409
 * QUESTAO_SEM_RASCUNHO com TODOS os que não têm rascunho aberto. Qualquer exceção sai da transação e desfaz tudo, inclusive o
 * que já tinha sido alterado no laço.
 */
@Service
public class GabaritoService {

	private final QuestaoService questoes;

	public GabaritoService(QuestaoService questoes) {
		this.questoes = questoes;
	}

	@Transactional
	public GabaritoResponse aplicar(UUID provaId, GabaritoRequest lote) {
		List<ItemDoGabaritoRequest> itens = lote.itens();
		exigirNumerosSemRepeticao(itens);
		// A trava é a primeira leitura desta transação (ver QuestaoService.travarProvaEditavel): nada pode ler antes dela.
		Prova prova = questoes.travarProvaEditavel(provaId);

		int atualizadas = 0;
		List<CampoInvalido> inexistentes = new ArrayList<>();
		List<Integer> semRascunho = new ArrayList<>();
		for (int indice = 0; indice < itens.size(); indice++) {
			ItemDoGabaritoRequest item = itens.get(indice);
			switch (questoes.aplicarGabarito(prova, item.numero(), item.gabarito())) {
				case ALTERADO -> atualizadas++;
				case SEM_MUDANCA -> {
					// já tinha essa letra: não conta
				}
				case SEM_RASCUNHO -> semRascunho.add(item.numero());
				case QUESTAO_INEXISTENTE -> inexistentes.add(new CampoInvalido("itens[" + indice + "].numero",
						"A prova não tem a questão de número " + item.numero() + "."));
			}
		}
		if (!inexistentes.isEmpty()) {
			throw InvalidRequestException.camposInvalidos(inexistentes);
		}
		if (!semRascunho.isEmpty()) {
			throw new ConflictException(CodigoErro.QUESTAO_SEM_RASCUNHO,
					"Há questões sem rascunho aberto. Crie uma nova versão delas. Nada foi alterado.",
					Map.of("numerosSemRascunho", List.copyOf(semRascunho)));
		}
		return new GabaritoResponse(atualizadas);
	}

	/** Cada repetição (da segunda ocorrência em diante) é apontada pelo próprio índice. */
	private static void exigirNumerosSemRepeticao(List<ItemDoGabaritoRequest> itens) {
		Set<Integer> vistos = new HashSet<>();
		List<CampoInvalido> repetidos = new ArrayList<>();
		for (int indice = 0; indice < itens.size(); indice++) {
			if (!vistos.add(itens.get(indice).numero())) {
				repetidos.add(new CampoInvalido("itens[" + indice + "].numero", "O número está repetido no lote."));
			}
		}
		if (!repetidos.isEmpty()) {
			throw InvalidRequestException.camposInvalidos(repetidos);
		}
	}

}
