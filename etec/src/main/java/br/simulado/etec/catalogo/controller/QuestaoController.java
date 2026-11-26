package br.simulado.etec.catalogo.controller;

import java.net.URI;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.simulado.etec.catalogo.dto.QuestaoRequest;
import br.simulado.etec.catalogo.dto.QuestaoResponse;
import br.simulado.etec.catalogo.mapper.QuestaoMapper;
import br.simulado.etec.catalogo.model.QuestaoVersao;
import br.simulado.etec.catalogo.model.StatusQuestao;
import br.simulado.etec.catalogo.service.QuestaoService;
import br.simulado.etec.catalogo.service.QuestaoService.ResultadoDoCadastro;
import br.simulado.etec.shared.exception.CodigoErro;
import br.simulado.etec.shared.exception.InvalidRequestException;
import br.simulado.etec.shared.paginacao.ConversorDePaginacao;
import br.simulado.etec.shared.paginacao.Filtros;
import br.simulado.etec.shared.paginacao.PaginaResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

/** Só HTTP. A documentação fica em {@link QuestaoControllerDocs}. Rotas só do ADMIN (D121: o resto de /admin/** é negado). */
@RestController
@RequestMapping(path = "/api/v1/admin", produces = MediaType.APPLICATION_JSON_VALUE)
public class QuestaoController implements QuestaoControllerDocs {

	private final QuestaoService servico;

	public QuestaoController(QuestaoService servico) {
		this.servico = servico;
	}

	@Override
	@PostMapping(path = "/provas/{id}/questoes", consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<QuestaoResponse> cadastrar(@PathVariable UUID id, @Valid @RequestBody QuestaoRequest dados) {
		ResultadoDoCadastro resultado = servico.cadastrar(id, dados);
		QuestaoResponse corpo = QuestaoMapper.paraResponse(resultado.versao());
		if (resultado.criada()) {
			return ResponseEntity.created(URI.create("/api/v1/admin/questoes/" + corpo.id())).body(corpo);
		}
		// D153: o numero já tem a mesma questão; 200 com o que está gravado (sem Location: nada foi criado)
		return ResponseEntity.ok(corpo);
	}

	@Override
	@GetMapping("/questoes")
	public ResponseEntity<PaginaResponse<QuestaoResponse>> listar(@RequestParam(required = false) UUID provaId,
			@RequestParam(required = false) UUID subtopicoId, @RequestParam(required = false) String status,
			@RequestParam(required = false) String page, @RequestParam(required = false) String size,
			HttpServletRequest requisicao) {
		Pageable paginacao = ConversorDePaginacao.converterRequisicao(page, size, requisicao.getParameterValues("sort"),
				QuestaoService.CAMPOS_ORDENAVEIS);
		exigirCrescente(paginacao);
		Page<QuestaoVersao> pagina = servico.listar(provaId, subtopicoId, filtroDeStatus(status), paginacao);
		return ResponseEntity.ok(PaginaResponse.de(pagina, QuestaoMapper::paraResponse));
	}

	/** A listagem de questões só ordena por numero CRESCENTE (D77 e a decisão da fatia 2b-ii): "desc" também é 400. */
	private static void exigirCrescente(Pageable paginacao) {
		if (paginacao.getSort().stream().anyMatch(Sort.Order::isDescending)) {
			throw new InvalidRequestException(CodigoErro.ORDENACAO_INVALIDA,
					"O parâmetro 'sort' é inválido. A listagem de questões só aceita numero,asc.");
		}
	}

	/** O filtro aceita só RASCUNHO e PUBLICADA; a ARQUIVADA se lê por GET /admin/questoes/{id}?versao=n (D41). */
	private static StatusQuestao filtroDeStatus(String valor) {
		if (valor == null) {
			return null;
		}
		if (StatusQuestao.RASCUNHO.name().equals(valor)) {
			return StatusQuestao.RASCUNHO;
		}
		if (StatusQuestao.PUBLICADA.name().equals(valor)) {
			return StatusQuestao.PUBLICADA;
		}
		throw new InvalidRequestException(CodigoErro.PARAMETRO_INVALIDO,
				"O parâmetro 'status' deve ser um destes valores: RASCUNHO, PUBLICADA.");
	}

	@Override
	@GetMapping("/questoes/{id}")
	public ResponseEntity<QuestaoResponse> buscar(@PathVariable UUID id, @RequestParam(required = false) String versao) {
		// versao recebida como texto de propósito: valor inválido é 400 (e não o 400 genérico de conversão sem código claro)
		Integer numeroDaVersao = Filtros.inteiro("versao", versao, 1, 999_999_999);
		return ResponseEntity.ok(QuestaoMapper.paraResponse(servico.buscar(id, numeroDaVersao)));
	}

}
