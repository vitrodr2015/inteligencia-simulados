package br.simulado.etec.usuarios.controller;

import org.springframework.http.ResponseEntity;

import br.simulado.etec.usuarios.dto.TermosResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;

/** Documentação OpenAPI dos termos de uso. Rota pública (não pede Bearer). */
@Tag(name = "Termos de uso", description = "Versão vigente dos termos, aceita no cadastro.")
@SecurityRequirements
public interface TermosControllerDocs {

	@Operation(summary = "Devolve os termos de uso vigentes",
			description = "A versão devolvida é o valor que o cadastro precisa enviar em aceiteTermosVersao.")
	@ApiResponse(responseCode = "200", description = "Termos vigentes.",
			content = @Content(schema = @Schema(implementation = TermosResponse.class)))
	ResponseEntity<TermosResponse> vigente();

}
