package br.simulado.etec.shared.config;

import java.beans.PropertyEditorSupport;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.InitBinder;

/**
 * UUID de rota e de parâmetro, ESTRITO. O {@code UUID.fromString} do Java aceita grupos curtos (por exemplo
 * {@code 11111111-1111-1111-1111-11111111111}, 35 caracteres), e o Spring o usa por padrão: um id assim virava 404 em vez de 400.
 * Aqui só vale o formato canônico, 8-4-4-4-12 dígitos hexadecimais; qualquer outro texto falha na conversão e o tratador de erros
 * devolve 400 PARAMETRO_INVALIDO. Vale para TODOS os controllers (um editor próprio do binder tem precedência sobre o conversor
 * padrão do Spring, que um {@code Converter} registrado no MVC não conseguiu substituir).
 */
@ControllerAdvice
public class UuidEstritoConfig {

	private static final Pattern FORMATO = Pattern
			.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

	/** O parser, público para os testes. */
	public static UUID paraUuid(String texto) {
		if (!FORMATO.matcher(texto).matches()) {
			throw new IllegalArgumentException("UUID fora do formato canônico.");
		}
		return UUID.fromString(texto);
	}

	@InitBinder
	void registrarUuidEstrito(WebDataBinder binder) {
		binder.registerCustomEditor(UUID.class, new PropertyEditorSupport() {
			@Override
			public void setAsText(String texto) {
				setValue(paraUuid(texto));
			}
		});
	}

}
