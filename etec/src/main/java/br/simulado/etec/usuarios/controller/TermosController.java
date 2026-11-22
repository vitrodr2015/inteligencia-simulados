package br.simulado.etec.usuarios.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.simulado.etec.shared.config.TermosProperties;
import br.simulado.etec.usuarios.dto.TermosResponse;
import br.simulado.etec.usuarios.service.UsuarioService;

/** Termos de uso vigentes (D23, D94). Rota pública. */
@RestController
@RequestMapping(path = "/api/v1/termos", produces = MediaType.APPLICATION_JSON_VALUE)
public class TermosController implements TermosControllerDocs {

	private final UsuarioService usuarios;

	public TermosController(UsuarioService usuarios) {
		this.usuarios = usuarios;
	}

	@Override
	@GetMapping("/vigente")
	public ResponseEntity<TermosResponse> vigente() {
		TermosProperties termos = usuarios.termosVigentes();
		return ResponseEntity.ok(new TermosResponse(termos.versao(), termos.url()));
	}

}
