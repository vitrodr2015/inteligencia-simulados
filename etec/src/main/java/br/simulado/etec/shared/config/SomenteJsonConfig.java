package br.simulado.etec.shared.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverters;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Só JSON (contrato, seção 1). O Spring MVC registra por conta própria os conversores de XML (JAXB, que
 * o Hibernate traz) e de YAML (o jackson-dataformat-yaml do Jackson 2, que o springdoc traz) quando as
 * bibliotecas estão no classpath. Sem esta configuração, um controller que esquecesse o
 * {@code produces = application/json} responderia YAML ou XML a um {@code Accept} desses tipos.
 *
 * <p>Aqui eles são removidos: sem conversor, o pedido recebe 406 no formato padrão de erro.
 */
@Configuration(proxyBeanMethods = false)
public class SomenteJsonConfig implements WebMvcConfigurer {

	@Override
	public void configureMessageConverters(HttpMessageConverters.ServerBuilder construtor) {
		construtor.configureMessageConvertersList(conversores -> conversores.removeIf(SomenteJsonConfig::ehXmlOuYaml));
	}

	static boolean ehXmlOuYaml(HttpMessageConverter<?> conversor) {
		return conversor.getSupportedMediaTypes().stream().anyMatch(SomenteJsonConfig::ehXmlOuYaml);
	}

	private static boolean ehXmlOuYaml(MediaType tipo) {
		String subtipo = tipo.getSubtype().toLowerCase();
		return subtipo.equals("xml") || subtipo.endsWith("+xml") || subtipo.contains("yaml");
	}

}
