package br.simulado.etec.catalogo.controller;

import java.net.URI;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.simulado.etec.catalogo.dto.SubtopicoAtualizacaoRequest;
import br.simulado.etec.catalogo.dto.SubtopicoCriacaoRequest;
import br.simulado.etec.catalogo.dto.SubtopicoResponse;
import br.simulado.etec.catalogo.mapper.SubtopicoMapper;
import br.simulado.etec.catalogo.model.Subtopico;
import br.simulado.etec.catalogo.service.SubtopicoService;
import br.simulado.etec.shared.paginacao.ConversorDePaginacao;
import br.simulado.etec.shared.paginacao.Filtros;
import br.simulado.etec.shared.paginacao.PaginaResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

/** Só HTTP: valida a entrada, chama o service e monta a resposta. A documentação fica em {@link SubtopicoControllerDocs}. */
@RestController
@RequestMapping(path = "/api/v1/subtopicos", produces = MediaType.APPLICATION_JSON_VALUE)
public class SubtopicoController implements SubtopicoControllerDocs {

	private final SubtopicoService servico;

	public SubtopicoController(SubtopicoService servico) {
		this.servico = servico;
	}

	@Override
	@PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<SubtopicoResponse> criar(@Valid @RequestBody SubtopicoCriacaoRequest dados) {
		Subtopico criado = servico.criar(dados);
		URI local = URI.create("/api/v1/subtopicos/" + criado.getPublicId());
		return ResponseEntity.created(local).body(SubtopicoMapper.paraResponse(criado));
	}

	@Override
	@GetMapping
	public ResponseEntity<PaginaResponse<SubtopicoResponse>> listar(@RequestParam(required = false) UUID materiaId,
			@RequestParam(required = false) String nome, @RequestParam(required = false) String ativo,
			@RequestParam(required = false) String page, @RequestParam(required = false) String size,
			HttpServletRequest requisicao) {
		// page, size e ativo chegam como texto de propósito: a validação é nossa e devolve 400. O materiaId malformado
		// vira 400 PARAMETRO_INVALIDO pela conversão do Spring. O sort vem CRU: parâmetro repetido = 400 (D101).
		Pageable paginacao = ConversorDePaginacao.converterRequisicao(page, size, requisicao.getParameterValues("sort"),
				SubtopicoService.CAMPOS_ORDENAVEIS);
		Boolean filtroAtivo = Filtros.booleano("ativo", ativo);
		Page<Subtopico> pagina = servico.listar(materiaId, nome, filtroAtivo, paginacao);
		return ResponseEntity.ok(PaginaResponse.de(pagina, SubtopicoMapper::paraResponse));
	}

	@Override
	@GetMapping("/{id}")
	public ResponseEntity<SubtopicoResponse> buscar(@PathVariable UUID id) {
		return ResponseEntity.ok(SubtopicoMapper.paraResponse(servico.buscar(id)));
	}

	@Override
	@PutMapping(path = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<SubtopicoResponse> atualizar(@PathVariable UUID id,
			@Valid @RequestBody SubtopicoAtualizacaoRequest dados) {
		return ResponseEntity.ok(SubtopicoMapper.paraResponse(servico.atualizar(id, dados)));
	}

	@Override
	@PatchMapping("/{id}/disable")
	public ResponseEntity<SubtopicoResponse> desativar(@PathVariable UUID id) {
		return ResponseEntity.ok(SubtopicoMapper.paraResponse(servico.desativar(id)));
	}

	@Override
	@PatchMapping("/{id}/enable")
	public ResponseEntity<SubtopicoResponse> ativar(@PathVariable UUID id) {
		return ResponseEntity.ok(SubtopicoMapper.paraResponse(servico.ativar(id)));
	}

	@Override
	@DeleteMapping("/{id}")
	public ResponseEntity<Void> apagar(@PathVariable UUID id) {
		servico.apagar(id);
		return ResponseEntity.noContent().build();
	}

}
