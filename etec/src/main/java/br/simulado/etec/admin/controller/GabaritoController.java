package br.simulado.etec.admin.controller;

import java.util.UUID;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.simulado.etec.admin.dto.GabaritoRequest;
import br.simulado.etec.admin.dto.GabaritoResponse;
import br.simulado.etec.admin.service.GabaritoService;
import jakarta.validation.Valid;

/** Só HTTP. A documentação fica em {@link GabaritoControllerDocs}. Rota só do ADMIN. */
@RestController
@RequestMapping(path = "/api/v1/admin/provas", produces = MediaType.APPLICATION_JSON_VALUE)
public class GabaritoController implements GabaritoControllerDocs {

	private final GabaritoService servico;

	public GabaritoController(GabaritoService servico) {
		this.servico = servico;
	}

	@Override
	@PutMapping(path = "/{id}/gabarito", consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<GabaritoResponse> aplicar(@PathVariable UUID id, @Valid @RequestBody GabaritoRequest lote) {
		return ResponseEntity.ok(servico.aplicar(id, lote));
	}

}
