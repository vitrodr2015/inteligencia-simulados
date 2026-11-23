package br.simulado.etec.auth.controller;

import org.springframework.http.ResponseEntity;

import jakarta.servlet.http.HttpServletRequest;

import br.simulado.etec.auth.dto.RefreshRequest;
import br.simulado.etec.auth.dto.RegistroRequest;
import br.simulado.etec.auth.dto.SigninRequest;
import br.simulado.etec.auth.dto.TokensResponse;
import br.simulado.etec.shared.exception.ErrorResponse;
import br.simulado.etec.usuarios.dto.UsuarioResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Documentação OpenAPI da autenticação. As três rotas são públicas (não pedem Bearer) e têm limite de tentativas (429,
 * D128 a D130). O logout entra no Módulo 13B.
 */
@Tag(name = "Autenticação", description = "Cadastro, signin e renovação de tokens.")
@SecurityRequirements
public interface AuthControllerDocs {

	@Operation(summary = "Cadastra um aluno",
			description = "Cria sempre um STUDENT. Responde 201 com o usuário, sem tokens e sem Location: o aluno entra depois pelo signin.")
	@ApiResponse(responseCode = "201", description = "Aluno cadastrado.",
			content = @Content(schema = @Schema(implementation = UsuarioResponse.class)))
	@ApiResponse(responseCode = "400",
			description = "Corpo ilegível ou campos inválidos (VALIDACAO_FALHOU, com campos): nome, e-mail (ASCII), senha de 8 a 72 bytes, data de nascimento, termos.",
			content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	@ApiResponse(responseCode = "409", description = "Já existe um usuário com esse e-mail (EMAIL_DUPLICADO).",
			content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	@ApiResponse(responseCode = "429",
			description = "Limite de tentativas excedido (LIMITE_EXCEDIDO). Espere o tempo do cabeçalho Retry-After (segundos) e tente de novo; o bloqueio expira sozinho. A resposta é a mesma para e-mail existente e inexistente.",
			headers = @Header(name = "Retry-After", description = "Segundos até poder tentar de novo.",
					schema = @Schema(type = "integer")),
			content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	ResponseEntity<UsuarioResponse> registrar(RegistroRequest dados);

	@Operation(summary = "Entra com e-mail e senha",
			description = "Devolve o par de tokens. Qualquer falha (usuário inexistente, senha errada, conta excluída) dá o mesmo 401. A resposta leva Cache-Control: no-store.")
	@ApiResponse(responseCode = "200", description = "Par de tokens.",
			content = @Content(schema = @Schema(implementation = TokensResponse.class)))
	@ApiResponse(responseCode = "400", description = "E-mail ou senha ausente, nulo ou que não é texto.",
			content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	@ApiResponse(responseCode = "401", description = "E-mail ou senha inválidos (CREDENCIAIS_INVALIDAS).",
			content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	@ApiResponse(responseCode = "429",
			description = "Limite de tentativas excedido (LIMITE_EXCEDIDO). Espere o tempo do cabeçalho Retry-After (segundos) e tente de novo; o bloqueio expira sozinho. A resposta é a mesma para e-mail existente e inexistente.",
			headers = @Header(name = "Retry-After", description = "Segundos até poder tentar de novo.",
					schema = @Schema(type = "integer")),
			content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	ResponseEntity<TokensResponse> signin(SigninRequest dados, HttpServletRequest requisicao);

	@Operation(summary = "Troca o refresh token por um par novo",
			description = "O refresh vai só no corpo. Um access token enviado aqui dá 401. Até o Módulo 13 o refresh usado NÃO é invalidado. A resposta leva Cache-Control: no-store.")
	@ApiResponse(responseCode = "200", description = "Par de tokens novo.",
			content = @Content(schema = @Schema(implementation = TokensResponse.class)))
	@ApiResponse(responseCode = "400", description = "Refresh token ausente, nulo ou que não é texto.",
			content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	@ApiResponse(responseCode = "401",
			description = "Refresh inválido, vencido, de outro tipo ou de usuário excluído (TOKEN_INVALIDO).",
			content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	@ApiResponse(responseCode = "429",
			description = "Limite de tentativas excedido (LIMITE_EXCEDIDO). Espere o tempo do cabeçalho Retry-After (segundos) e tente de novo; o bloqueio expira sozinho. A resposta é a mesma para e-mail existente e inexistente.",
			headers = @Header(name = "Retry-After", description = "Segundos até poder tentar de novo.",
					schema = @Schema(type = "integer")),
			content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	ResponseEntity<TokensResponse> refresh(RefreshRequest dados);

}
