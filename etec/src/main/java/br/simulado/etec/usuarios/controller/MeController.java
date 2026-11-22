package br.simulado.etec.usuarios.controller;

import java.time.Clock;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.simulado.etec.shared.exception.InvalidTokenException;
import br.simulado.etec.shared.security.UsuarioAutenticado;
import br.simulado.etec.usuarios.dto.UsuarioResponse;
import br.simulado.etec.usuarios.mapper.UsuarioMapper;
import br.simulado.etec.usuarios.service.UsuarioService;

/** Só HTTP. {@code PATCH} e {@code DELETE /me} entram em tarefas próprias. Documentação em {@link MeControllerDocs}. */
@RestController
@RequestMapping(path = "/api/v1/me", produces = MediaType.APPLICATION_JSON_VALUE)
public class MeController implements MeControllerDocs {

	private final UsuarioService usuarios;

	private final Clock relogio;

	public MeController(UsuarioService usuarios, Clock relogio) {
		this.usuarios = usuarios;
		this.relogio = relogio;
	}

	@Override
	@GetMapping
	public ResponseEntity<UsuarioResponse> me(@AuthenticationPrincipal UsuarioAutenticado autenticado) {
		// Relê do banco: o dado devolvido é sempre o atual. Se o usuário sumiu entre o filtro e aqui, é 401.
		return usuarios.buscarAtivoPorPublicId(autenticado.id())
				.map(usuario -> ResponseEntity.ok(UsuarioMapper.paraResponse(usuario, relogio)))
				.orElseThrow(InvalidTokenException::new);
	}

}
