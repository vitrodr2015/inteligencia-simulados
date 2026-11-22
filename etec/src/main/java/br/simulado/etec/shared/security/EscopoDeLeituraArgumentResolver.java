package br.simulado.etec.shared.security;

import org.springframework.core.MethodParameter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import br.simulado.etec.shared.exception.AcessoNegadoException;

/**
 * Entrega o {@link EscopoDeLeitura} ao parâmetro do controller, calculado a partir do {@link UsuarioAutenticado} (cujo
 * papel veio do BANCO). Sem usuário autenticado, ou com um principal de outro tipo, falha FECHADO (403): nunca assume
 * "ver tudo" por falta de informação.
 */
public class EscopoDeLeituraArgumentResolver implements HandlerMethodArgumentResolver {

	@Override
	public boolean supportsParameter(MethodParameter parametro) {
		return EscopoDeLeitura.class.equals(parametro.getParameterType());
	}

	@Override
	public Object resolveArgument(MethodParameter parametro, ModelAndViewContainer conteiner,
			NativeWebRequest requisicao, WebDataBinderFactory fabrica) {
		Authentication autenticacao = SecurityContextHolder.getContext().getAuthentication();
		if (autenticacao == null || !autenticacao.isAuthenticated()
				|| !(autenticacao.getPrincipal() instanceof UsuarioAutenticado usuario)) {
			throw new AcessoNegadoException();
		}
		return EscopoDeLeitura.de(usuario.papel());
	}

}
