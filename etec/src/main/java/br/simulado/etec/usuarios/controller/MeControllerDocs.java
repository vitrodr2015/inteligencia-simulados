package br.simulado.etec.usuarios.controller;

import org.springframework.http.ResponseEntity;

import br.simulado.etec.shared.exception.ErrorResponse;
import br.simulado.etec.shared.security.UsuarioAutenticado;
import br.simulado.etec.usuarios.dto.UsuarioResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

/** Documentação OpenAPI do usuário logado. Pede Bearer (esquema global {@code bearerAuth}). */
@Tag(name = "Usuário logado", description = "Dados de quem está chamando.")
public interface MeControllerDocs {

	@Operation(summary = "Devolve o usuário logado", description = "O mesmo corpo do 201 do cadastro.")
	@ApiResponse(responseCode = "200", description = "Usuário logado.",
			content = @Content(schema = @Schema(implementation = UsuarioResponse.class)))
	@ApiResponse(responseCode = "401", description = "Sem token válido (NAO_AUTENTICADO).",
			content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	ResponseEntity<UsuarioResponse> me(@Parameter(hidden = true) UsuarioAutenticado autenticado);

}
