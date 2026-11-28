package br.simulado.etec.shared.security.limite;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import br.simulado.etec.auth.service.LimpezaDeSessoesAgendada;
import br.simulado.etec.integracao.AbstractIntegrationTest;

/**
 * As duas limpezas rodam de hora em hora (D131, D137). A prova lê o {@code @Scheduled} dos métodos, sem esperar. No contexto
 * dos testes os agendadores continuam desligados.
 */
class AgendamentoDasLimpezasIntegracaoTest extends AbstractIntegrationTest {

	@Autowired
	private ApplicationContext contexto;

	private static void exigirDeHoraEmHora(Class<?> classe) throws NoSuchMethodException {
		Method metodo = classe.getMethod("limpar");
		Scheduled[] agendamentos = metodo.getAnnotationsByType(Scheduled.class);
		assertThat(agendamentos).as("um só agendamento em %s", classe.getSimpleName()).hasSize(1);
		Scheduled agendado = agendamentos[0];
		assertThat(Duration.parse(agendado.fixedDelayString())).as("intervalo de %s", classe.getSimpleName())
				.isEqualTo(Duration.ofHours(1));
		assertThat(Duration.parse(agendado.initialDelayString())).as("a primeira rodada vem dentro da primeira hora")
				.isPositive().isLessThanOrEqualTo(Duration.ofHours(1));
		// nenhuma outra forma de intervalo concorre com o fixedDelayString
		assertThat(agendado.cron()).isEmpty();
		assertThat(agendado.fixedRateString()).isEmpty();
		assertThat(agendado.fixedDelay()).isEqualTo(-1);
		assertThat(agendado.fixedRate()).isEqualTo(-1);
	}

	@Test
	void deve_agendar_a_limpeza_das_sessoes_de_hora_em_hora() throws NoSuchMethodException {
		exigirDeHoraEmHora(LimpezaDeSessoesAgendada.class);
		assertThat(LimpezaDeSessoesAgendada.class.isAnnotationPresent(EnableScheduling.class)).isTrue();
	}

	@Test
	void deve_agendar_a_limpeza_dos_contadores_de_hora_em_hora() throws NoSuchMethodException {
		exigirDeHoraEmHora(LimpezaAgendada.class);
		assertThat(Arrays.stream(LimiteConfig.class.getDeclaredClasses())
				.filter(c -> c.isAnnotationPresent(EnableScheduling.class))
				.map(Class::getSimpleName)).containsExactly("LimpezaAgendadaConfig");
	}

	@Test
	void deve_manter_os_agendadores_desligados_no_contexto_dos_testes() {
		assertThat(contexto.getBeanNamesForType(LimpezaDeSessoesAgendada.class)).isEmpty();
		assertThat(contexto.getBeanNamesForType(LimpezaAgendada.class)).isEmpty();
	}

}
