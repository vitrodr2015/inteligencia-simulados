package br.simulado.etec;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationListener;

import br.simulado.etec.shared.config.ValidacaoDeAmbiente;

@SpringBootApplication
public class EtecApplication {

	public static void main(String[] args) {
		SpringApplication aplicacao = new SpringApplication(EtecApplication.class);
		aplicacao.addListeners((ApplicationListener<ApplicationEnvironmentPreparedEvent>) evento ->
				ValidacaoDeAmbiente.validar(evento.getEnvironment()));
		aplicacao.run(args);
	}

}
