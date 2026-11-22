package br.simulado.etec.shared.security;

import java.io.IOException;

import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.web.cors.DefaultCorsProcessor;

/**
 * Origem não permitida (ou método ou cabeçalho fora da lista): o {@link DefaultCorsProcessor} responderia 403 com o texto
 * simples "Invalid CORS request". Aqui sai o 403 ACESSO_NEGADO em {@code ErrorResponse}, como todo erro da API (D82,
 * D123), sem dizer qual regra de CORS falhou.
 */
public class CorsProcessorComErrorResponse extends DefaultCorsProcessor {

	private final SecurityErrorWriter escritorDeErros;

	public CorsProcessorComErrorResponse(SecurityErrorWriter escritorDeErros) {
		this.escritorDeErros = escritorDeErros;
	}

	@Override
	protected void rejectRequest(ServerHttpResponse resposta) throws IOException {
		escritorDeErros.negarOrigem(((ServletServerHttpResponse) resposta).getServletResponse());
	}

}
