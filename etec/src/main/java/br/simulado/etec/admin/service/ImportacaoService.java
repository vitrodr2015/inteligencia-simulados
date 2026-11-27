package br.simulado.etec.admin.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.simulado.etec.admin.dto.ImportacaoRequest;
import br.simulado.etec.admin.dto.ImportacaoResponse;
import br.simulado.etec.catalogo.dto.QuestaoRequest;
import br.simulado.etec.catalogo.model.Prova;
import br.simulado.etec.catalogo.service.QuestaoService;
import br.simulado.etec.catalogo.service.QuestaoService.ResultadoDoCadastro;
import br.simulado.etec.shared.exception.CodigoErro;
import br.simulado.etec.shared.exception.ConflictException;
import br.simulado.etec.shared.exception.ErrorResponse.CampoInvalido;
import br.simulado.etec.shared.exception.InvalidRequestException;
import br.simulado.etec.shared.exception.ResourceNotFoundException;

/**
 * Importação em lote: um invólucro TUDO OU NADA sobre o cadastro de questão ({@link QuestaoService}). Nenhuma regra de questão
 * mora aqui; as do lote são: numero sem repetição dentro dele, a prova-alvo referenciada no corpo e a forma de dizer qual item
 * falhou. Este domínio só conversa com o SERVIÇO do catálogo, nunca com os repositórios dele.
 *
 * <p>Ordem: numero repetido no lote (400, antes de tocar o banco) → trava a prova, UMA vez (inexistente = 400 em
 * campos[provaId], porque a referência está no corpo; fora de rascunho = 409) → itens na ordem do lote. Vale o primeiro item que
 * falha, EXCETO o conflito de conteúdo no numero: esses são juntados e saem num único 409 no fim. Qualquer exceção sai da
 * transação e desfaz tudo, inclusive o que já tinha sido gravado no laço.
 */
@Service
public class ImportacaoService {

	private final QuestaoService questoes;

	public ImportacaoService(QuestaoService questoes) {
		this.questoes = questoes;
	}

	@Transactional
	public ImportacaoResponse importar(ImportacaoRequest lote) {
		exigirNumerosSemRepeticao(lote.questoes());
		// A trava é a primeira leitura desta transação (ver QuestaoService.travarProvaEditavel): nada pode ler antes dela.
		Prova prova = travarAProva(lote);

		int criadas = 0;
		int ignoradas = 0;
		List<Integer> numerosEmConflito = new ArrayList<>();
		int indiceDoPrimeiroConflito = -1;
		for (int indice = 0; indice < lote.questoes().size(); indice++) {
			QuestaoRequest item = lote.questoes().get(indice);
			ResultadoDoCadastro resultado = cadastrarItem(prova, item, indice);
			switch (resultado.situacao()) {
				case CRIADA -> criadas++;
				case JA_EXISTIA -> ignoradas++;
				case CONFLITO -> {
					if (numerosEmConflito.isEmpty()) {
						indiceDoPrimeiroConflito = indice;
					}
					numerosEmConflito.add(item.numero());
				}
			}
		}
		if (!numerosEmConflito.isEmpty()) {
			Map<String, Object> detalhes = detalhesDoItem(indiceDoPrimeiroConflito, numerosEmConflito.getFirst());
			detalhes.put("numerosEmConflito", List.copyOf(numerosEmConflito));
			throw new ConflictException(CodigoErro.QUESTAO_NUMERO_EM_CONFLITO,
					"Já existem questões com esses números e conteúdo diferente. Nada foi importado.", detalhes);
		}
		return new ImportacaoResponse(lote.provaId(), criadas, ignoradas);
	}

	private Prova travarAProva(ImportacaoRequest lote) {
		try {
			return questoes.travarProvaEditavel(lote.provaId());
		}
		catch (ResourceNotFoundException ex) {
			throw InvalidRequestException.campoInvalido("provaId", "A prova informada não existe.");
		}
	}

	/** Cadastra o item; a recusa dele ganha a identificação do item (prefixo nos campos, ou detalhes no 409). */
	private ResultadoDoCadastro cadastrarItem(Prova prova, QuestaoRequest item, int indice) {
		try {
			return questoes.cadastrarNaProva(prova, item);
		}
		catch (InvalidRequestException ex) {
			throw ex.comPrefixoNosCampos("questoes[" + indice + "].");
		}
		catch (ConflictException ex) {
			throw new ConflictException(ex.getCodigo(), ex.getMessage(), detalhesDoItem(indice, item.numero()));
		}
	}

	private static Map<String, Object> detalhesDoItem(int indice, int numero) {
		Map<String, Object> detalhes = new LinkedHashMap<>();
		detalhes.put("indice", indice);
		detalhes.put("numero", numero);
		return detalhes;
	}

	/** Cada repetição (da segunda ocorrência em diante) é apontada pelo próprio índice. */
	private static void exigirNumerosSemRepeticao(List<QuestaoRequest> itens) {
		Set<Integer> vistos = new HashSet<>();
		List<CampoInvalido> repetidos = new ArrayList<>();
		for (int indice = 0; indice < itens.size(); indice++) {
			if (!vistos.add(itens.get(indice).numero())) {
				repetidos.add(new CampoInvalido("questoes[" + indice + "].numero", "O número está repetido no lote."));
			}
		}
		if (!repetidos.isEmpty()) {
			throw InvalidRequestException.camposInvalidos(repetidos);
		}
	}

}
