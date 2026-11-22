package br.simulado.etec.shared.security;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;

import br.simulado.etec.shared.exception.InvalidTokenException;

/**
 * Emite e valida os tokens (contrato, seção 3.1; D39, D118). HS256, claims {@code iss, sub, papel, tipo, jti, iat,
 * exp}. Só criptografia e claims: NÃO consulta o banco (quem confirma que o usuário existe é o
 * {@link AutenticadorDeToken}). O claim {@code papel} é só informativo (D87).
 *
 * <p>A validação exige assinatura HS256 (qualquer outro algoritmo, inclusive {@code none}, é recusado), emissor,
 * {@code exp}, {@code sub} (UUID), {@code jti} e {@code tipo}; qualquer falha vira {@link InvalidTokenException}
 * sem detalhes. O prazo é conferido pelo {@link Clock} injetado, sem tolerância.
 */
public class JwtTokenProvider {

	/** Tamanho máximo aceito para um token, antes de qualquer processamento. */
	static final int TAMANHO_MAXIMO_DO_TOKEN = 4096;

	static final String CLAIM_PAPEL = "papel";

	static final String CLAIM_TIPO = "tipo";

	private final Algorithm algoritmo;

	private final JWTVerifier verificador;

	private final String emissor;

	private final Clock relogio;

	private final Duration validadeDoAccess;

	private final Duration validadeDoRefresh;

	public JwtTokenProvider(JwtProperties propriedades, Clock relogio) {
		this.algoritmo = Algorithm.HMAC256(propriedades.secret().getBytes(StandardCharsets.UTF_8));
		this.emissor = propriedades.issuer();
		this.relogio = relogio;
		this.validadeDoAccess = propriedades.accessTokenExpiration();
		this.validadeDoRefresh = propriedades.refreshTokenExpiration();
		// build(Clock) existe só na implementação (BaseVerification), não na interface Verification.
		this.verificador = ((JWTVerifier.BaseVerification) JWT.require(algoritmo)
				.withIssuer(emissor)
				.withClaimPresence("exp")
				.withClaimPresence("sub")
				.withClaimPresence("jti")
				.withClaimPresence(CLAIM_TIPO))
				.build(relogio);
	}

	/** Tipo do token: o access abre as rotas; o refresh só serve para renovar. */
	public enum TipoToken {
		ACCESS("access"), REFRESH("refresh");

		private final String valor;

		TipoToken(String valor) {
			this.valor = valor;
		}

		String valor() {
			return valor;
		}
	}

	/** Token recém-emitido. {@code expiraEm} já está truncado em segundos, como no token. */
	public record TokenEmitido(String valor, Instant expiraEm, String jti) {

		/** Nunca mostra o token. */
		@Override
		public String toString() {
			return "TokenEmitido[jti=" + jti + ", expiraEm=" + expiraEm + "]";
		}
	}

	/** Claims de um token válido. */
	public record ClaimsDoToken(UUID usuarioId, TipoToken tipo, String jti, String papel, Instant expiraEm) {
	}

	public TokenEmitido emitir(TipoToken tipo, UUID usuarioId, String papel) {
		Instant agora = relogio.instant();
		Instant expiraEm = agora.plus(tipo == TipoToken.ACCESS ? validadeDoAccess : validadeDoRefresh);
		String jti = UUID.randomUUID().toString();
		String valor = JWT.create()
				.withIssuer(emissor)
				.withSubject(usuarioId.toString())
				.withClaim(CLAIM_PAPEL, papel)
				.withClaim(CLAIM_TIPO, tipo.valor())
				.withJWTId(jti)
				.withIssuedAt(agora)
				.withExpiresAt(expiraEm)
				.sign(algoritmo);
		return new TokenEmitido(valor, expiraEm.truncatedTo(java.time.temporal.ChronoUnit.SECONDS), jti);
	}

	/** Validade do access token, em segundos (o {@code expiresIn} do signin). */
	public long validadeDoAccessEmSegundos() {
		return validadeDoAccess.toSeconds();
	}

	/**
	 * @throws InvalidTokenException para qualquer falha, sem dizer qual
	 */
	public ClaimsDoToken validar(String token, TipoToken esperado) {
		if (token == null || token.isBlank() || token.length() > TAMANHO_MAXIMO_DO_TOKEN) {
			throw new InvalidTokenException();
		}
		try {
			DecodedJWT decodificado = verificador.verify(token);
			String tipoDoToken = decodificado.getClaim(CLAIM_TIPO).asString();
			if (!esperado.valor().equals(tipoDoToken)) {
				throw new InvalidTokenException();
			}
			UUID usuarioId = UUID.fromString(decodificado.getSubject());
			String jti = decodificado.getId();
			if (jti == null || jti.isBlank() || decodificado.getExpiresAtAsInstant() == null) {
				throw new InvalidTokenException();
			}
			return new ClaimsDoToken(usuarioId, esperado, jti, decodificado.getClaim(CLAIM_PAPEL).asString(),
					decodificado.getExpiresAtAsInstant());
		}
		catch (InvalidTokenException e) {
			throw e;
		}
		catch (RuntimeException e) {
			// JWTVerificationException, subject que não é UUID, claim de tipo inesperado etc.
			throw new InvalidTokenException(e);
		}
	}

}
