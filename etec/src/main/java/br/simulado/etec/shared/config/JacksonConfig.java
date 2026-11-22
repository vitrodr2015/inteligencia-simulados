package br.simulado.etec.shared.config;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.regex.Pattern;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.annotation.JsonFormat;

import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.cfg.CoercionAction;
import tools.jackson.databind.cfg.CoercionInputShape;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.type.LogicalType;

/**
 * Formato global de datas da API (contrato, seção 1), valendo para todos os DTOs, sem anotar
 * campo a campo:
 * <ul>
 * <li>{@code Instant} sai SEMPRE em UTC com exatamente três casas de milissegundos
 * ({@code 2026-11-20T15:04:05.000Z}), inclusive quando são zero; o que passar disso é truncado.
 * O formato padrão do Instant omite as casas quando os milissegundos são zero. Só a saída é
 * alterada; a leitura de Instant segue o padrão do Jackson.</li>
 * <li>{@code LocalDate} é lida de forma ESTRITA no formato {@code uuuu-MM-dd}: {@code 2000-02-30}
 * e {@code 2000-2-3} falham (o tratador de erros os devolve como 400 VALIDACAO_FALHOU, D60) e nunca são
 * corrigidas em silêncio. O formatador padrão de padrões do Java corrige datas (resolução SMART).</li>
 * <li>Campo de texto só aceita texto: número e booleano no lugar de uma String falham (D86), em vez de virarem
 * texto em silêncio.</li>
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
public class JacksonConfig {

	private static final DateTimeFormatter INSTANTE_UTC_COM_MILISSEGUNDOS =
			DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);

	@Bean
	JsonMapperBuilderCustomizer formatoDeDatas() {
		SimpleModule moduloDeInstant = new SimpleModule("InstantUtcComMilissegundos");
		moduloDeInstant.addSerializer(Instant.class, new InstantSerializador());
		moduloDeInstant.addDeserializer(LocalDate.class, new LocalDateEstrito());
		return construtor -> construtor
				.addModule(moduloDeInstant)
				.withConfigOverride(LocalDate.class, configuracao -> configuracao
						.setFormat(JsonFormat.Value.forPattern("uuuu-MM-dd").withLenient(false)))
				// D86: campo de texto só aceita texto. Sem isto, {"senha":123} virava "123" em silêncio.
				.withCoercionConfig(LogicalType.Textual, configuracao -> configuracao
						.setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
						.setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
						.setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail));
	}

	/**
	 * Leitura estrita de LocalDate (D60, D86): só texto, exatamente {@code aaaa-mm-dd} (quatro dígitos, dois, dois),
	 * sem espaços nas pontas, sem hora, sem sinal e sem ano de cinco dígitos, e a data precisa existir. Texto fora disso
	 * vira InvalidFormatException (o tratador de erros devolve 400 VALIDACAO_FALHOU com o campo); valor que não é texto
	 * (número, lista, objeto, booleano) vira MismatchedInputException (CORPO_ILEGIVEL). Nunca corrige em silêncio.
	 */
	static final class LocalDateEstrito extends ValueDeserializer<LocalDate> {

		private static final Pattern FORMATO = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");

		private static final DateTimeFormatter LEITURA = DateTimeFormatter.ofPattern("uuuu-MM-dd")
				.withResolverStyle(ResolverStyle.STRICT);

		@Override
		public LocalDate deserialize(JsonParser parser, DeserializationContext contexto) {
			if (!parser.hasToken(JsonToken.VALUE_STRING)) {
				return (LocalDate) contexto.handleUnexpectedToken(LocalDate.class, parser);
			}
			String texto = parser.getString();
			if (!FORMATO.matcher(texto).matches()) {
				throw contexto.weirdStringException(texto, LocalDate.class, "Use o formato aaaa-mm-dd.");
			}
			try {
				return LocalDate.parse(texto, LEITURA);
			}
			catch (DateTimeParseException e) {
				throw contexto.weirdStringException(texto, LocalDate.class, "Data inexistente.");
			}
		}

	}

	/** Saída de Instant: UTC, três casas de milissegundos, mesmo quando são zero. */
	static final class InstantSerializador extends ValueSerializer<Instant> {

		@Override
		public void serialize(Instant valor, JsonGenerator gerador, SerializationContext contexto) {
			gerador.writeString(INSTANTE_UTC_COM_MILISSEGUNDOS.format(valor));
		}

	}

}
