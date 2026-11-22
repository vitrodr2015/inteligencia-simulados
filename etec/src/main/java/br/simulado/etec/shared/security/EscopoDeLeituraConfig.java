package br.simulado.etec.shared.security;

import java.util.List;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Registra o resolvedor do {@link EscopoDeLeitura}. */
@Configuration(proxyBeanMethods = false)
public class EscopoDeLeituraConfig implements WebMvcConfigurer {

	@Override
	public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvedores) {
		resolvedores.add(new EscopoDeLeituraArgumentResolver());
	}

}
