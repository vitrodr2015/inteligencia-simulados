package br.simulado.etec.shared.config;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Auditoria do Spring Data: preenche {@code @CreatedDate} e {@code @LastModifiedDate} com o
 * {@link Clock} da aplicação (UTC, injetável nos testes).
 */
@Configuration(proxyBeanMethods = false)
@EnableJpaAuditing(dateTimeProviderRef = "auditoriaDateTimeProvider")
public class AuditoriaConfig {

	/**
	 * Trunca para milissegundos: as colunas são DATETIME(3) e o MySQL ARREDONDA o que passa disso ao
	 * gravar, enquanto a API TRUNCA ao serializar. Sem isto, o POST devolvia {@code .850} e o GET
	 * seguinte {@code .851}.
	 */
	@Bean
	public DateTimeProvider auditoriaDateTimeProvider(Clock relogio) {
		return () -> Optional.of(relogio.instant().truncatedTo(ChronoUnit.MILLIS));
	}

}
