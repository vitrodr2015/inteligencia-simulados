package br.simulado.etec.auth.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.simulado.etec.auth.dto.TokensResponse;
import br.simulado.etec.auth.model.MotivoRevogacao;
import br.simulado.etec.auth.model.RefreshToken;
import br.simulado.etec.auth.repository.RefreshTokenRepository;
import br.simulado.etec.shared.exception.InvalidTokenException;
import br.simulado.etec.shared.security.JwtTokenProvider;
import br.simulado.etec.shared.security.JwtTokenProvider.ClaimsDoToken;
import br.simulado.etec.shared.security.JwtTokenProvider.TipoToken;
import br.simulado.etec.shared.security.JwtTokenProvider.TokenEmitido;
import br.simulado.etec.usuarios.model.Usuario;
import br.simulado.etec.usuarios.service.UsuarioService;

/**
 * O ciclo de vida do refresh token e das sessões (Módulo 13B, D133 a D137). Só o {@code jti} vai ao banco, nunca o token.
 * Tudo que decide "quem ganhou" é UPDATE condicional; nada daqui lança exceção de dentro de uma transação que desfaria uma
 * revogação: quem precisa do 401 depois de revogar em massa o faz FORA da transação (ver {@link AuthService#refresh}).
 *
 * <p>Nenhum método loga token, jti, e-mail ou senha.
 */
@Service
public class SessaoService {

	private static final Logger LOG = LoggerFactory.getLogger(SessaoService.class);

	/** Linhas vencidas só são apagadas depois deste prazo (a margem de segurança da limpeza). */
	static final Duration RETENCAO_APOS_VENCER = Duration.ofDays(1);

	/** Linhas apagadas por rodada da limpeza. */
	static final int LOTE_DA_LIMPEZA = 1000;

	private final RefreshTokenRepository refreshTokens;

	private final JwtTokenProvider tokens;

	private final UsuarioService usuarios;

	private final Clock relogio;

	public SessaoService(RefreshTokenRepository refreshTokens, JwtTokenProvider tokens, UsuarioService usuarios,
			Clock relogio) {
		this.refreshTokens = refreshTokens;
		this.tokens = tokens;
		this.usuarios = usuarios;
		this.relogio = relogio;
	}

	/**
	 * Emite um par novo para o usuário (signin) e grava o refresh. A gravação é a ÚLTIMA coisa: se falhar, nenhum token sai.
	 */
	public TokensResponse iniciar(Usuario usuario) {
		Par par = emitirPar(usuario);
		refreshTokens.save(par.registro(usuario));
		return par.resposta();
	}

	/**
	 * Rotação: revoga o refresh com UPDATE condicional (jti, não revogado, não vencido) e, na MESMA transação, insere o novo.
	 * Se a inserção falhar, o rollback devolve a validade ao antigo. Devolve vazio para quem PERDEU (token já usado,
	 * revogado, vencido ou desconhecido); nunca lança exceção por isso.
	 */
	@Transactional
	public Optional<TokensResponse> rotacionar(ClaimsDoToken claims, Usuario usuario) {
		UUID jti = UUID.fromString(claims.jti());
		if (refreshTokens.revogarSeAtivo(jti, relogio.instant(), MotivoRevogacao.ROTACAO) != 1) {
			return Optional.empty();
		}
		Par par = emitirPar(usuario);
		refreshTokens.save(par.registro(usuario));
		return Optional.of(par.resposta());
	}

	/**
	 * O refresh que não rotacionou é um REUSO? Só é se ele foi revogado POR ROTAÇÃO: reapresentar um refresh já rotacionado
	 * é o sinal de roubo (D49). Desconhecido, vencido ou revogado por outro motivo (logout, troca de senha) é só 401.
	 */
	@Transactional(readOnly = true)
	public boolean foiRotacionado(String jti) {
		return refreshTokens.findByJti(UUID.fromString(jti))
				.map(r -> r.getMotivoRevogacao() == MotivoRevogacao.ROTACAO).orElse(false);
	}

	/**
	 * Encerra TODAS as sessões do usuário: revoga todos os refresh ainda ativos e incrementa a versão do token (que derruba
	 * os access tokens vivos), de forma ATÔMICA (uma transação). Chamado pelo reuso, pela troca de senha e, no futuro, pela
	 * exclusão da conta. Participa da transação de quem chama; sozinho, abre a sua.
	 */
	@Transactional
	public void encerrarTodasAsSessoes(Long usuarioId, MotivoRevogacao motivo) {
		Instant agora = relogio.instant();
		int revogados = refreshTokens.revogarTodosDoUsuario(usuarioId, agora, motivo);
		usuarios.incrementarVersaoDoToken(usuarioId, agora);
		LOG.debug("Sessões encerradas (motivo {}): {} refresh revogados.", motivo, revogados);
	}

	/**
	 * Logout (D15, D107): revoga SÓ o refresh enviado, quando a assinatura é válida. Idempotente e sem erro para qualquer
	 * texto (malformado, desconhecido, vencido, já revogado ou de outro tipo). NÃO derruba o access token vivo (no máximo
	 * 15 minutos): o logout é anônimo e não sabe de qual sessão veio o access.
	 */
	public void logout(String refreshToken) {
		ClaimsDoToken claims;
		try {
			claims = tokens.validar(refreshToken, TipoToken.REFRESH);
		}
		catch (InvalidTokenException e) {
			return;
		}
		refreshTokens.revogarSeAtivo(UUID.fromString(claims.jti()), relogio.instant(), MotivoRevogacao.LOGOUT);
	}

	/** Apaga, em lotes, as linhas vencidas há mais de 1 dia. Devolve o total. */
	public int limparVencidas() {
		Instant limite = relogio.instant().minus(RETENCAO_APOS_VENCER);
		int total = 0;
		int apagadas;
		do {
			apagadas = refreshTokens.apagarVencidasAntesDe(limite, LOTE_DA_LIMPEZA);
			total += apagadas;
		}
		while (apagadas >= LOTE_DA_LIMPEZA);
		return total;
	}

	private Par emitirPar(Usuario usuario) {
		String papel = usuario.getPapel().name();
		int versao = usuario.getVersaoToken();
		String access = tokens.emitir(TipoToken.ACCESS, usuario.getPublicId(), papel, versao).valor();
		TokenEmitido refresh = tokens.emitir(TipoToken.REFRESH, usuario.getPublicId(), papel, versao);
		return new Par(new TokensResponse(access, refresh.valor(), TokensResponse.TIPO, tokens.validadeDoAccessEmSegundos()),
				refresh);
	}

	/** O par emitido e o refresh dele (jti e validade) para gravar. */
	private record Par(TokensResponse resposta, TokenEmitido refresh) {

		RefreshToken registro(Usuario usuario) {
			return new RefreshToken(UUID.fromString(refresh.jti()), usuario.getId(), refresh.expiraEm());
		}

	}

}
