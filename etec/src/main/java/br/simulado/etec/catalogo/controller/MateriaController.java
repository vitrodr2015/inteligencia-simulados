package br.simulado.etec.catalogo.controller;

import java.net.URI;
import java.util.UUID;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.simulado.etec.catalogo.dto.MateriaRequest;
import br.simulado.etec.catalogo.dto.MateriaResponse;
import br.simulado.etec.catalogo.mapper.MateriaMapper;
import br.simulado.etec.catalogo.model.Materia;
import br.simulado.etec.catalogo.service.MateriaService;
import jakarta.validation.Valid;

/** Só HTTP: valida a entrada, chama o service e monta a resposta. A documentação fica em {@link MateriaControllerDocs}. */
@RestController
@RequestMapping(path = "/api/v1/materias", produces = MediaType.APPLICATION_JSON_VALUE)
public class MateriaController implements MateriaControllerDocs {

	private final MateriaService servico;

	public MateriaController(MateriaService servico) {
		this.servico = servico;
	}

	@Override
	@PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<MateriaResponse> criar(@Valid @RequestBody MateriaRequest dados) {
		Materia criada = servico.criar(dados);
		URI local = URI.create("/api/v1/materias/" + criada.getPublicId());
		return ResponseEntity.created(local).body(MateriaMapper.paraResponse(criada));
	}

	@Override
	@GetMapping("/{id}")
	public ResponseEntity<MateriaResponse> buscar(@PathVariable UUID id) {
		return ResponseEntity.ok(MateriaMapper.paraResponse(servico.buscar(id)));
	}

	@Override
	@PutMapping(path = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<MateriaResponse> atualizar(@PathVariable UUID id, @Valid @RequestBody MateriaRequest dados) {
		return ResponseEntity.ok(MateriaMapper.paraResponse(servico.atualizar(id, dados)));
	}

	@Override
	@PatchMapping("/{id}/disable")
	public ResponseEntity<MateriaResponse> desativar(@PathVariable UUID id) {
		return ResponseEntity.ok(MateriaMapper.paraResponse(servico.desativar(id)));
	}

	@Override
	@PatchMapping("/{id}/enable")
	public ResponseEntity<MateriaResponse> ativar(@PathVariable UUID id) {
		return ResponseEntity.ok(MateriaMapper.paraResponse(servico.ativar(id)));
	}

	@Override
	@DeleteMapping("/{id}")
	public ResponseEntity<Void> apagar(@PathVariable UUID id) {
		servico.apagar(id);
		return ResponseEntity.noContent().build();
	}

}
