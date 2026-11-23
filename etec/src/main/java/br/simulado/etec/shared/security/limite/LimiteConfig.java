package br.simulado.etec.shared.security.limite;

import java.time.Clock;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.PlatformTransactionManager;

import br.simulado.etec.shared.security.JwtProperties;

/** Liga as propriedades do limite e cria as peças (D128 a D130). */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(RateLimitProperties.class)
public class LimiteConfig {

	@Bean
	public ChavesDoLimite chavesDoLimite(JwtProperties jwt) {
		return new ChavesDoLimite(jwt);
	}

	@Bean
	public ContadorDeTentativas contadorDeTentativas(JdbcTemplate jdbc, PlatformTransactionManager gerenteDeTransacao) {
		return new ContadorDeTentativas(jdbc, gerenteDeTransacao);
	}

	@Bean
	public LimiteDeTentativas limiteDeTentativas(ContadorDeTentativas contadores, ChavesDoLimite chaves, Clock relogio) {
		return new LimiteDeTentativas(contadores, chaves, relogio);
	}

	@Bean
	public OrigemDaRequisicao origemDaRequisicao() {
		return new OrigemDaRequisicao();
	}

	/** A limpeza agendada só existe com {@code app.rate-limit.limpeza-ativa=true} (desligada no perfil de teste). */
	@Configuration(proxyBeanMethods = false)
	@EnableScheduling
	@ConditionalOnProperty(prefix = "app.rate-limit", name = "limpeza-ativa", havingValue = "true",
			matchIfMissing = true)
	static class LimpezaAgendadaConfig {

		@Bean
		LimpezaAgendada limpezaAgendada(LimiteDeTentativas limite) {
			return new LimpezaAgendada(limite);
		}

	}

}
