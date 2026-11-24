package br.simulado.etec.auth.controller;

import java.time.Clock;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.simulado.etec.auth.dto.RefreshRequest;
import br.simulado.etec.auth.dto.RegistroRequest;
import br.simulado.etec.auth.dto.SigninRequest;
import br.simulado.etec.auth.dto.TokensResponse;
import br.simulado.etec.auth.service.AuthService;
import br.simulado.etec.shared.security.limite.OrigemDaRequisicao;
import br.simulado.etec.usuarios.dto.UsuarioResponse;
import br.simulado.etec.usuarios.mapper.UsuarioMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

/** Só HTTP: valida a entrada, chama o service e monta a resposta. A documentação fica em {@link AuthControllerDocs}. */
@RestController
@RequestMapping(path = "/api/v1/auth", produces = MediaType.APPLICATION_JSON_VALUE)
public class AuthController implements AuthControllerDocs {

	private final AuthService servico;

	private final Clock relogio;

	private final OrigemDaRequisicao origens;

	public AuthController(AuthService servico, Clock relogio, OrigemDaRequisicao origens) {
		this.servico = servico;
		this.relogio = relogio;
		this.origens = origens;
	}

	/** 201 com o usuário, sem tokens e SEM Location: o recurso só existe em /me (D23). */
	@Override
	@PostMapping(path = "/register", consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<UsuarioResponse> registrar(@Valid @RequestBody RegistroRequest dados) {
		UsuarioResponse corpo = UsuarioMapper.paraResponse(servico.registrar(dados), relogio);
		return ResponseEntity.status(HttpStatus.CREATED).body(corpo);
	}

	/** Cache-Control: no-store (D114): a resposta traz credenciais e não pode ser guardada por ninguém. */
	@Override
	@PostMapping(path = "/signin", consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<TokensResponse> signin(@Valid @RequestBody SigninRequest dados, HttpServletRequest requisicao) {
		return ResponseEntity.ok().cacheControl(CacheControl.noStore())
				.body(servico.signin(dados, origens.de(requisicao)));
	}

	/** 204 para qualquer texto presente (D15, D107, D135); 400 só se o refreshToken estiver ausente ou não for texto. */
	@Override
	@PostMapping(path = "/logout", consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<Void> logout(@Valid @RequestBody RefreshRequest dados) {
		servico.logout(dados);
		return ResponseEntity.noContent().build();
	}

	@Override
	@PostMapping(path = "/refresh", consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<TokensResponse> refresh(@Valid @RequestBody RefreshRequest dados) {
		return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(servico.refresh(dados));
	}

}
