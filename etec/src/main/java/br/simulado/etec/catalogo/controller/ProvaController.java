package br.simulado.etec.catalogo.controller;

import java.net.URI;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.simulado.etec.catalogo.dto.ProvaRequest;
import br.simulado.etec.catalogo.dto.ProvaResponse;
import br.simulado.etec.catalogo.mapper.ProvaMapper;
import br.simulado.etec.catalogo.model.Prova;
import br.simulado.etec.catalogo.model.StatusProva;
import br.simulado.etec.catalogo.service.ProvaService;
import br.simulado.etec.shared.paginacao.ConversorDePaginacao;
import br.simulado.etec.shared.paginacao.Filtros;
import br.simulado.etec.shared.paginacao.PaginaResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

/**
 * Só HTTP: valida a entrada, chama o service e monta a resposta. A documentação fica em {@link ProvaControllerDocs}. A criação
 * mora em {@code /admin/provas} (D149) e a leitura em {@code /provas}; as três rotas são só do ADMIN nesta fatia (D154).
 */
@RestController
@RequestMapping(path = "/api/v1", produces = MediaType.APPLICATION_JSON_VALUE)
public class ProvaController implements ProvaControllerDocs {

	private final ProvaService servico;

	public ProvaController(ProvaService servico) {
		this.servico = servico;
	}

	@Override
	@PostMapping(path = "/admin/provas", consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<ProvaResponse> criar(@Valid @RequestBody ProvaRequest dados) {
		Prova criada = servico.criar(dados);
		URI local = URI.create("/api/v1/provas/" + criada.getPublicId());
		return ResponseEntity.created(local).body(ProvaMapper.paraResponse(criada));
	}

	@Override
	@GetMapping("/provas")
	public ResponseEntity<PaginaResponse<ProvaResponse>> listar(@RequestParam(required = false) String ano,
			@RequestParam(required = false) String semestre, @RequestParam(required = false) String status,
			@RequestParam(required = false) String page, @RequestParam(required = false) String size,
			HttpServletRequest requisicao) {
		// Parâmetros recebidos como texto de propósito: a validação é nossa e devolve 400 (ver MateriaController).
		Pageable paginacao = ConversorDePaginacao.converterRequisicao(page, size, requisicao.getParameterValues("sort"),
				ProvaService.CAMPOS_ORDENAVEIS);
		Integer filtroAno = Filtros.inteiro("ano", ano, 2000, 2100);
		Integer filtroSemestre = Filtros.inteiro("semestre", semestre, 1, 2);
		StatusProva filtroStatus = Filtros.enumerado("status", status, StatusProva.class);
		Page<Prova> pagina = servico.listar(filtroAno, filtroSemestre, filtroStatus, paginacao);
		return ResponseEntity.ok(PaginaResponse.de(pagina, ProvaMapper::paraResponse));
	}

	@Override
	@GetMapping("/provas/{id}")
	public ResponseEntity<ProvaResponse> buscar(@PathVariable UUID id) {
		return ResponseEntity.ok(ProvaMapper.paraResponse(servico.buscar(id)));
	}

}
