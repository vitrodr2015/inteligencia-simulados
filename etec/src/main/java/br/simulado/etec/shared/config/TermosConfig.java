package br.simulado.etec.shared.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Liga as propriedades dos termos de uso. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(TermosProperties.class)
public class TermosConfig {

}
