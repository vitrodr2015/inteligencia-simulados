package br.simulado.etec.admin.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.simulado.etec.admin.dto.ImportacaoRequest;
import br.simulado.etec.admin.dto.ImportacaoResponse;
import br.simulado.etec.admin.service.ImportacaoService;
import jakarta.validation.Valid;

/** Só HTTP. A documentação fica em {@link ImportacaoControllerDocs}; o limite de corpo, em {@link LimiteDeCorpoDaImportacao}. */
@RestController
@RequestMapping(path = "/api/v1/admin", produces = MediaType.APPLICATION_JSON_VALUE)
public class ImportacaoController implements ImportacaoControllerDocs {

	private final ImportacaoService servico;

	public ImportacaoController(ImportacaoService servico) {
		this.servico = servico;
	}

	@Override
	@PostMapping(path = "/importacoes", consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<ImportacaoResponse> importar(@Valid @RequestBody ImportacaoRequest lote) {
		// 200 sempre, inclusive quando tudo já existia: a importação não é um recurso (sem 201 nem Location)
		return ResponseEntity.ok(servico.importar(lote));
	}

}
