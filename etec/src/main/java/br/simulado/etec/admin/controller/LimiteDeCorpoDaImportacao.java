package br.simulado.etec.admin.controller;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;

import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;

import br.simulado.etec.shared.exception.CorpoMuitoGrandeException;

/**
 * Limite de {@value #LIMITE_EM_BYTES} bytes (2 MiB) para o corpo da importação, e só dela. Roda DEPOIS da cadeia de segurança
 * (anônimo e sem papel recebem 401 e 403 sem o corpo ser lido) e depois de o tipo do conteúdo ser aceito (o 415 vem antes), e
 * ANTES de o Jackson ler:
 * <ol>
 * <li>se o {@code Content-Length} declarado passa do limite, recusa sem ler nada;</li>
 * <li>senão (inclusive quando não há tamanho declarado, no envio em partes), lê no máximo limite + 1 BYTES para a memória; se
 * vier esse byte a mais, recusa. Só um corpo dentro do limite chega ao Jackson, a partir da memória.</li>
 * </ol>
 * Assim o 413 nunca nasce no meio da leitura do Jackson, que o embrulharia num 400 CORPO_ILEGIVEL, e um corpo grande com um JSON
 * pequeno no começo também é recusado. O que sobra sem ler é descartado pelo Tomcat até {@code server.tomcat.max-swallow-size}
 * (application.yml): acima disso ele fecha a conexão, e o cliente pode não ver o 413.
 */
@ControllerAdvice(assignableTypes = ImportacaoController.class)
public class LimiteDeCorpoDaImportacao extends RequestBodyAdviceAdapter {

	public static final int LIMITE_EM_BYTES = 2 * 1024 * 1024;

	static final String MENSAGEM = "O corpo da importação passa de 2 MiB (2.097.152 bytes). Divida o lote em partes menores.";

	@Override
	public boolean supports(MethodParameter parametro, Type tipo, Class<? extends HttpMessageConverter<?>> conversor) {
		return true;
	}

	@Override
	public HttpInputMessage beforeBodyRead(HttpInputMessage entrada, MethodParameter parametro, Type tipo,
			Class<? extends HttpMessageConverter<?>> conversor) throws IOException {
		if (entrada.getHeaders().getContentLength() > LIMITE_EM_BYTES) {
			throw new CorpoMuitoGrandeException(MENSAGEM);
		}
		byte[] corpo = entrada.getBody().readNBytes(LIMITE_EM_BYTES + 1);
		if (corpo.length > LIMITE_EM_BYTES) {
			throw new CorpoMuitoGrandeException(MENSAGEM);
		}
		HttpHeaders cabecalhos = entrada.getHeaders();
		return new HttpInputMessage() {
			@Override
			public InputStream getBody() {
				return new ByteArrayInputStream(corpo);
			}

			@Override
			public HttpHeaders getHeaders() {
				return cabecalhos;
			}
		};
	}

}
