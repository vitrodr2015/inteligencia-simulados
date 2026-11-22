package br.simulado.etec.shared.security;

import java.io.IOException;
import java.time.Clock;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import br.simulado.etec.shared.exception.CodigoErro;
import br.simulado.etec.shared.exception.ErrorResponse;
import br.simulado.etec.shared.exception.GlobalExceptionHandler;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

/**
 * Escreve o 401, o 403 e o 500 da cadeia de segurança no mesmo {@link ErrorResponse} do resto da API (D82, D120).
 * Esses erros nascem ANTES do Spring MVC, então não passam pelo {@code GlobalExceptionHandler} nem pelos
 * conversores de mensagem: o JSON é escrito direto, sempre {@code application/json} (sem charset), ignorando o
 * {@code Accept} do cliente (D71). Mensagens fixas e genéricas: nunca dizem por que o token foi recusado.
 */
@Component
public class SecurityErrorWriter implements AuthenticationEntryPoint, AccessDeniedHandler {

	static final String MENSAGEM_401 = "É necessário estar autenticado para acessar este recurso.";

	static final String MENSAGEM_403 = "Você não tem permissão para acessar este recurso.";

	private final JsonMapper jsonMapper;

	private final Clock relogio;

	public SecurityErrorWriter(JsonMapper jsonMapper, Clock relogio) {
		this.jsonMapper = jsonMapper;
		this.relogio = relogio;
	}

	/** Sem credencial válida em rota protegida (inclusive token vencido, adulterado ou de outro tipo). */
	@Override
	public void commence(HttpServletRequest requisicao, HttpServletResponse resposta,
			AuthenticationException excecao) throws IOException {
		escrever(resposta, HttpStatus.UNAUTHORIZED, CodigoErro.NAO_AUTENTICADO, MENSAGEM_401);
	}

	/** Autenticado, mas sem permissão (por exemplo, /actuator/** e /error, D113). */
	@Override
	public void handle(HttpServletRequest requisicao, HttpServletResponse resposta,
			AccessDeniedException excecao) throws IOException {
		escrever(resposta, HttpStatus.FORBIDDEN, CodigoErro.ACESSO_NEGADO, MENSAGEM_403);
	}

	/** Falha inesperada dentro da cadeia (por exemplo, o banco fora do ar no filtro): 500 genérico, sem detalhes. */
	void erroInterno(HttpServletResponse resposta) throws IOException {
		escrever(resposta, HttpStatus.INTERNAL_SERVER_ERROR, CodigoErro.ERRO_INTERNO,
				GlobalExceptionHandler.MENSAGEM_ERRO_INTERNO);
	}

	private void escrever(HttpServletResponse resposta, HttpStatus status, CodigoErro codigo, String mensagem)
			throws IOException {
		if (resposta.isCommitted()) {
			return;
		}
		byte[] corpo = jsonMapper.writeValueAsBytes(ErrorResponse.de(relogio, status.value(), codigo, mensagem, null, null));
		resposta.resetBuffer();
		resposta.setStatus(status.value());
		resposta.setContentType("application/json");
		resposta.setContentLength(corpo.length);
		resposta.getOutputStream().write(corpo);
		resposta.flushBuffer();
	}

}
