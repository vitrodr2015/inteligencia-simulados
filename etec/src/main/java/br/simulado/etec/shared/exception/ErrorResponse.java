package br.simulado.etec.shared.exception;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Formato único de erro da API (contrato, seção 1), igual em todo status de erro.
 *
 * <p>{@code dataHora} é texto em UTC com SEMPRE três casas de milissegundos, mesmo quando elas
 * são zero (o formato padrão do {@link Instant} omitiria). {@code campos} (só em 400 de validação)
 * e {@code detalhes} (objeto livre) não aparecem no JSON quando nulos.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
		String dataHora,
		int status,
		String erro,
		CodigoErro codigo,
		String mensagem,
		List<CampoInvalido> campos,
		Map<String, Object> detalhes) {

	private static final DateTimeFormatter FORMATO_DATA_HORA =
			DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);

	/** Um campo inválido em erro 400 de validação. */
	public record CampoInvalido(String campo, String mensagem) {
	}

	/** Formata em UTC com exatamente três casas de milissegundos (trunca o que passar disso). */
	public static String formatar(Instant instante) {
		return FORMATO_DATA_HORA.format(instante);
	}

	/** Monta o erro com a data e hora do {@link Clock} informado e o erro HTTP padrão em inglês ("Conflict"). */
	public static ErrorResponse de(Clock relogio, int status, CodigoErro codigo, String mensagem,
			List<CampoInvalido> campos, Map<String, Object> detalhes) {
		HttpStatus http = HttpStatus.resolve(status);
		String erro = (http != null) ? http.getReasonPhrase() : "Error";
		return new ErrorResponse(formatar(relogio.instant()), status, erro, codigo, mensagem, campos, detalhes);
	}

}
