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

import br.simulado.etec.catalogo.dto.MateriaRequest;
import br.simulado.etec.catalogo.dto.MateriaResponse;
import br.simulado.etec.catalogo.mapper.MateriaMapper;
import br.simulado.etec.catalogo.model.Materia;
import br.simulado.etec.catalogo.service.MateriaService;
import br.simulado.etec.shared.paginacao.ConversorDePaginacao;
import br.simulado.etec.shared.paginacao.Filtros;
import br.simulado.etec.shared.paginacao.PaginaResponse;
import jakarta.servlet.http.HttpServletRequest;
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
	@GetMapping
	public ResponseEntity<PaginaResponse<MateriaResponse>> listar(@RequestParam(required = false) String nome,
			@RequestParam(required = false) String ativo, @RequestParam(required = false) String page,
			@RequestParam(required = false) String size, HttpServletRequest requisicao) {
		// Parâmetros recebidos como texto de propósito: a validação é nossa, e devolve 400 (o resolvedor do
		// Spring corrigiria page e size inválidos em silêncio). O sort vem CRU da requisição: quando o parâmetro
		// se repete, o Spring juntaria os valores com vírgula e aceitaria o segundo em silêncio (D101).
		Pageable paginacao = ConversorDePaginacao.converterRequisicao(page, size, requisicao.getParameterValues("sort"),
				MateriaService.CAMPOS_ORDENAVEIS);
		Boolean filtroAtivo = Filtros.booleano("ativo", ativo);
		Page<Materia> pagina = servico.listar(nome, filtroAtivo, paginacao);
		return ResponseEntity.ok(PaginaResponse.de(pagina, MateriaMapper::paraResponse));
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
