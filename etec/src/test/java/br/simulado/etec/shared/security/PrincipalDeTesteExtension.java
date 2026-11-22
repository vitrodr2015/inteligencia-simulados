package br.simulado.etec.shared.security;

import java.util.UUID;

import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

/** Implementa {@link ComPrincipal}: põe o principal antes de cada teste e limpa o contexto depois (nunca vaza). */
public class PrincipalDeTesteExtension implements BeforeEachCallback, AfterEachCallback {

	@Override
	public void beforeEach(ExtensionContext contexto) {
		SecurityContextHolder.clearContext();
		ComPrincipal anotacao = contexto.getTestMethod().map(m -> m.getAnnotation(ComPrincipal.class)).orElse(null);
		if (anotacao == null) {
			anotacao = contexto.getRequiredTestClass().getAnnotation(ComPrincipal.class);
		}
		if (anotacao == null) {
			return;
		}
		UsuarioAutenticado usuario = new UsuarioAutenticado(UUID.randomUUID(), anotacao.value());
		SecurityContext seguranca = new SecurityContextImpl(UsernamePasswordAuthenticationToken.authenticated(usuario, null,
				List.of(new SimpleGrantedAuthority(Authorities.de(usuario.papel())))));
		SecurityContextHolder.setContext(seguranca);
	}

	@Override
	public void afterEach(ExtensionContext contexto) {
		SecurityContextHolder.clearContext();
	}

}
