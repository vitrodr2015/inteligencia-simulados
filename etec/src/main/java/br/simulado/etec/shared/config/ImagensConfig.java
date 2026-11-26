package br.simulado.etec.shared.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Registra {@link ImagensProperties}. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ImagensProperties.class)
public class ImagensConfig {

}
