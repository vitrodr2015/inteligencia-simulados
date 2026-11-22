package br.simulado.etec.shared.security;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.web.filter.OncePerRequestFilter;

import br.simulado.etec.shared.exception.InvalidTokenException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Autentica pelo cabeçalho {@code Authorization: Bearer <access token>}. Criado com {@code new} na
 * {@link SecurityConfig}, NÃO como {@code @Component}: assim o Spring Boot não o registra também como filtro do
 * servlet (rodaria duas vezes).
 *
 * <p>Token ausente, malformado, vencido, adulterado, de outro tipo ou de usuário excluído NÃO derruba a requisição:
 * ela segue SEM autenticação, e é a rota protegida que responde 401. Isso mantém as rotas públicas funcionando mesmo
 * com um token velho no cabeçalho. A autoridade vem do papel gravado no BANCO (D87). Falha de infraestrutura (o banco
 * fora do ar) NÃO é "token ruim": vira 500 genérico, nunca um 401 nem uma autenticação concedida por engano.
 * O token nunca é logado.
 */
public class JwtTokenFilter extends OncePerRequestFilter {

	private static final Logger LOG = LoggerFactory.getLogger(JwtTokenFilter.class);

	private static final String PREFIXO = "bearer ";

	private final AutenticadorDeToken autenticador;

	private final SecurityErrorWriter escritorDeErros;

	private final SecurityContextHolderStrategy estrategia = SecurityContextHolder.getContextHolderStrategy();

	private final RequestAttributeSecurityContextRepository repositorio = new RequestAttributeSecurityContextRepository();

	public JwtTokenFilter(AutenticadorDeToken autenticador, SecurityErrorWriter escritorDeErros) {
		this.autenticador = autenticador;
		this.escritorDeErros = escritorDeErros;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest requisicao, HttpServletResponse resposta, FilterChain cadeia)
			throws ServletException, IOException {
		String token = extrairToken(requisicao);
		if (token != null) {
			try {
				UsuarioAutenticado usuario = autenticador.autenticar(token);
				SecurityContext contexto = estrategia.createEmptyContext();
				contexto.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(usuario, null,
						List.of(new SimpleGrantedAuthority(Authorities.de(usuario.papel())))));
				estrategia.setContext(contexto);
				repositorio.saveContext(contexto, requisicao, resposta);
			}
			catch (InvalidTokenException e) {
				// Segue sem autenticação; o 401 é da rota protegida. Nada do token no log.
				LOG.debug("Token recusado; a requisição segue sem autenticação.");
			}
			catch (RuntimeException e) {
				LOG.error("Falha ao conferir o usuário do token", e);
				escritorDeErros.erroInterno(resposta);
				return;
			}
		}
		cadeia.doFilter(requisicao, resposta);
	}

	/**
	 * O token, se houver EXATAMENTE um cabeçalho Authorization com o esquema Bearer (sem distinguir caixa); senão
	 * {@code null}. Mais de um cabeçalho é ambíguo e é ignorado.
	 */
	static String extrairToken(HttpServletRequest requisicao) {
		List<String> cabecalhos = Collections.list(requisicao.getHeaders("Authorization"));
		if (cabecalhos.size() != 1) {
			return null;
		}
		String valor = cabecalhos.get(0);
		if (valor == null || valor.length() <= PREFIXO.length()
				|| !valor.regionMatches(true, 0, PREFIXO, 0, PREFIXO.length())) {
			return null;
		}
		String token = valor.substring(PREFIXO.length()).strip();
		return token.isEmpty() ? null : token;
	}

}
