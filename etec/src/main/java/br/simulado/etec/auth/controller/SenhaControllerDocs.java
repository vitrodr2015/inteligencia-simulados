package br.simulado.etec.auth.controller;

import org.springframework.http.ResponseEntity;

import br.simulado.etec.auth.dto.TrocaDeSenhaRequest;
import br.simulado.etec.shared.exception.ErrorResponse;
import br.simulado.etec.shared.security.UsuarioAutenticado;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

/** Documentação OpenAPI da troca de senha (Módulo 13B, D139). Exige Bearer. */
@Tag(name = "Autenticação", description = "Cadastro, signin, renovação de tokens, logout e troca de senha.")
public interface SenhaControllerDocs {

	@Operation(summary = "Troca a senha do usuário logado",
			description = "Exige a senha atual. Responde 204 sem tokens e com Cache-Control: no-store. Depois da troca, TODOS os tokens antigos (access e refresh) deixam de valer: é preciso entrar de novo com a senha nova. STUDENT: nova senha de 8 a 72 bytes. ADMIN: de 12 a 72 bytes, sem espaço nas pontas, sem quebra de linha e sem texto de exemplo. Errar a senha atual conta num limite por usuário.")
	@ApiResponse(responseCode = "204", description = "Senha trocada; todas as sessões encerradas.")
	@ApiResponse(responseCode = "400",
			description = "Corpo ilegível; campo ausente ou nova senha fora da política, ou igual à atual (VALIDACAO_FALHOU, com campos); ou senha atual incorreta (SENHA_ATUAL_INCORRETA).",
			content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	@ApiResponse(responseCode = "401", description = "Sem token válido (NAO_AUTENTICADO).",
			content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	@ApiResponse(responseCode = "403", description = "GUARDIAN não tem permissão (ACESSO_NEGADO).",
			content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	@ApiResponse(responseCode = "429",
			description = "Erros demais da senha atual (LIMITE_EXCEDIDO). Espere o tempo do cabeçalho Retry-After (segundos).",
			headers = @Header(name = "Retry-After", description = "Segundos até poder tentar de novo.",
					schema = @Schema(type = "integer")),
			content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	ResponseEntity<Void> trocar(UsuarioAutenticado autenticado, TrocaDeSenhaRequest dados);

}
