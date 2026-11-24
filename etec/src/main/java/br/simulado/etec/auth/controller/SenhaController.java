package br.simulado.etec.auth.controller;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.simulado.etec.auth.dto.TrocaDeSenhaRequest;
import br.simulado.etec.auth.service.TrocaDeSenhaService;
import br.simulado.etec.shared.security.UsuarioAutenticado;

/**
 * Só HTTP. O corpo NÃO leva {@code @Valid}: o 429 vem antes do 400 de formato, então quem valida os campos é o service,
 * depois de contar a tentativa. Documentação em {@link SenhaControllerDocs}.
 */
@RestController
@RequestMapping(path = "/api/v1/me/senha", produces = MediaType.APPLICATION_JSON_VALUE)
public class SenhaController implements SenhaControllerDocs {

	private final TrocaDeSenhaService servico;

	public SenhaController(TrocaDeSenhaService servico) {
		this.servico = servico;
	}

	/** 204 sem tokens e com Cache-Control: no-store (D139). */
	@Override
	@PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<Void> trocar(@AuthenticationPrincipal UsuarioAutenticado autenticado,
			@RequestBody TrocaDeSenhaRequest dados) {
		servico.trocar(autenticado, dados);
		return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
	}

}
