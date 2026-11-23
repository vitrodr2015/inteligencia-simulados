package br.simulado.etec.shared.security.limite;

import java.io.IOException;
import java.util.Set;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

import br.simulado.etec.shared.exception.LimiteExcedidoException;
import br.simulado.etec.shared.security.SecurityErrorWriter;

/**
 * O limite por ORIGEM de {@code /api/v1/auth/*} (D128): toda requisição POST das quatro rotas conta, e passada do limite
 * a resposta é 429 ANTES de ler o corpo (portanto antes do 400, do 401 e de qualquer BCrypt). É só proteção contra
 * enxurrada. Só contam as rotas POST de verdade: o preflight de CORS (OPTIONS) e qualquer outro método ficam de fora.
 *
 * <p>Criado com {@code new} na SecurityConfig, NÃO como {@code @Component} (rodaria duas vezes). Fica DEPOIS do filtro
 * de CORS, então o 429 leva os mesmos cabeçalhos de CORS do 401 e do 403. Falha do banco é 500 genérico, nunca "deixa
 * passar": sem contador não há limite.
 */
public class LimiteDeOrigemFilter extends OncePerRequestFilter {

	private static final Logger LOG = LoggerFactory.getLogger(LimiteDeOrigemFilter.class);

	/** As rotas POST de /auth que contam (a de logout entra com o Módulo 13B; contá-la antes não tem efeito). */
	static final Set<String> ROTAS = Set.of("/api/v1/auth/register", "/api/v1/auth/signin", "/api/v1/auth/refresh",
			"/api/v1/auth/logout");

	private final LimiteDeTentativas limite;

	private final RateLimitProperties propriedades;

	private final OrigemDaRequisicao origens;

	private final SecurityErrorWriter escritorDeErros;

	public LimiteDeOrigemFilter(LimiteDeTentativas limite, RateLimitProperties propriedades, OrigemDaRequisicao origens,
			SecurityErrorWriter escritorDeErros) {
		this.limite = limite;
		this.propriedades = propriedades;
		this.origens = origens;
		this.escritorDeErros = escritorDeErros;
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest requisicao) {
		return !"POST".equals(requisicao.getMethod()) || !ROTAS.contains(requisicao.getRequestURI());
	}

	@Override
	protected void doFilterInternal(HttpServletRequest requisicao, HttpServletResponse resposta, FilterChain cadeia)
			throws ServletException, IOException {
		try {
			limite.consumir(Escopo.AUTH_ORIGEM, origens.de(requisicao), propriedades.authPorOrigem());
		}
		catch (LimiteExcedidoException e) {
			escritorDeErros.limiteExcedido(resposta, e.getRetryAfterSegundos());
			return;
		}
		catch (RuntimeException e) {
			LOG.error("Falha ao conferir o limite de origem", e);
			escritorDeErros.erroInterno(resposta);
			return;
		}
		cadeia.doFilter(requisicao, resposta);
	}

}
