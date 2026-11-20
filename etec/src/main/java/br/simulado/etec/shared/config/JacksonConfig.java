package br.simulado.etec.shared.config;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.annotation.JsonFormat;

import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.module.SimpleModule;

/**
 * Formato global de datas da API (contrato, seção 1), valendo para todos os DTOs, sem anotar
 * campo a campo:
 * <ul>
 * <li>{@code Instant} sai SEMPRE em UTC com exatamente três casas de milissegundos
 * ({@code 2026-11-20T15:04:05.000Z}), inclusive quando são zero; o que passar disso é truncado.
 * O formato padrão do Instant omite as casas quando os milissegundos são zero. Só a saída é
 * alterada; a leitura de Instant segue o padrão do Jackson.</li>
 * <li>{@code LocalDate} é lida de forma ESTRITA no formato {@code uuuu-MM-dd}: {@code 2000-02-30}
 * e {@code 2000-2-3} falham (viram 400 pelo tratador padrão) e nunca são corrigidas em silêncio.
 * O formatador padrão de padrões do Java corrige datas (resolução SMART).</li>
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
		return construtor -> construtor
				.addModule(moduloDeInstant)
				.withConfigOverride(LocalDate.class, configuracao -> configuracao
						.setFormat(JsonFormat.Value.forPattern("uuuu-MM-dd").withLenient(false)));
	}

	/** Saída de Instant: UTC, três casas de milissegundos, mesmo quando são zero. */
	static final class InstantSerializador extends ValueSerializer<Instant> {

		@Override
		public void serialize(Instant valor, JsonGenerator gerador, SerializationContext contexto) {
			gerador.writeString(INSTANTE_UTC_COM_MILISSEGUNDOS.format(valor));
		}

	}

}
