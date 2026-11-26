package br.simulado.etec.catalogo.controller;

import java.net.URI;
import java.util.UUID;

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
import br.simulado.etec.catalogo.service.QuestaoService;
import br.simulado.etec.catalogo.service.QuestaoService.ResultadoDoCadastro;
import br.simulado.etec.shared.paginacao.Filtros;
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
	@GetMapping("/questoes/{id}")
	public ResponseEntity<QuestaoResponse> buscar(@PathVariable UUID id, @RequestParam(required = false) String versao) {
		// versao recebida como texto de propósito: valor inválido é 400 (e não o 400 genérico de conversão sem código claro)
		Integer numeroDaVersao = Filtros.inteiro("versao", versao, 1, 999_999_999);
		return ResponseEntity.ok(QuestaoMapper.paraResponse(servico.buscar(id, numeroDaVersao)));
	}

}
