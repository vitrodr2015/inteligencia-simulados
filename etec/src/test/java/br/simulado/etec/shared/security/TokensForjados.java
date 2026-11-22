package br.simulado.etec.shared.security;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.UUID;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;

import br.simulado.etec.shared.security.JwtTokenProvider.TipoToken;

/**
 * Fábrica de tokens de teste: os VÁLIDOS saem do provedor real, com o segredo da execução, e os FORJADOS estragam uma
 * coisa de cada vez (prazo, assinatura, segredo, algoritmo, claim). Nenhum segredo fixo: tudo vem do
 * {@link SegredoDeTeste}.
 */
public final class TokensForjados {

	public static final String EMISSOR = "etec-simulados";

	private static final Duration ACCESS = Duration.ofMinutes(15);

	private static final Duration REFRESH = Duration.ofHours(24);

	private TokensForjados() {
	}

	private static JwtTokenProvider provedor(String segredo, Instant agora) {
		return new JwtTokenProvider(new JwtProperties(segredo, EMISSOR, ACCESS, REFRESH),
				Clock.fixed(agora, ZoneOffset.UTC));
	}

	/** Válido em tudo: segredo da execução, emissor certo, prazo no futuro. */
	public static String valido(UUID usuario, String papel, TipoToken tipo) {
		return provedor(SegredoDeTeste.DA_EXECUCAO, Instant.now()).emitir(tipo, usuario, papel).valor();
	}

	/** Assinado com o segredo certo, mas já vencido (emitido há duas horas, com validade de 15 minutos). */
	public static String vencido(UUID usuario, String papel) {
		return provedor(SegredoDeTeste.DA_EXECUCAO, Instant.now().minus(Duration.ofHours(2)))
				.emitir(TipoToken.ACCESS, usuario, papel).valor();
	}

	/** Mesma estrutura de um token válido, mas assinado com OUTRO segredo. */
	public static String deOutroSegredo(UUID usuario, String papel) {
		return provedor(SegredoDeTeste.gerar(), Instant.now()).emitir(TipoToken.ACCESS, usuario, papel).valor();
	}

	/** Emissor diferente do da aplicação, assinado com o segredo certo. */
	public static String deOutroEmissor(UUID usuario, String papel) {
		return JWT.create().withIssuer("outro-emissor").withSubject(usuario.toString()).withClaim("papel", papel)
				.withClaim("tipo", "access").withJWTId(UUID.randomUUID().toString()).withIssuedAt(Instant.now())
				.withExpiresAt(Instant.now().plus(ACCESS))
				.sign(Algorithm.HMAC256(SegredoDeTeste.DA_EXECUCAO.getBytes(StandardCharsets.UTF_8)));
	}

	/** Cabeçalho com alg=none e sem assinatura. */
	public static String semAssinatura(UUID usuario, String papel) {
		String cabecalho = base64("{\"alg\":\"none\",\"typ\":\"JWT\"}");
		String corpo = base64("{\"iss\":\"" + EMISSOR + "\",\"sub\":\"" + usuario + "\",\"papel\":\"" + papel
				+ "\",\"tipo\":\"access\",\"jti\":\"" + UUID.randomUUID() + "\",\"exp\":"
				+ Instant.now().plus(ACCESS).getEpochSecond() + "}");
		return cabecalho + "." + corpo + ".";
	}

	/** Assinado com HS512 e o segredo certo (o servidor só aceita HS256). */
	public static String comOutroAlgoritmo(UUID usuario, String papel) {
		return JWT.create().withIssuer(EMISSOR).withSubject(usuario.toString()).withClaim("papel", papel)
				.withClaim("tipo", "access").withJWTId(UUID.randomUUID().toString()).withIssuedAt(Instant.now())
				.withExpiresAt(Instant.now().plus(ACCESS))
				.sign(Algorithm.HMAC512(SegredoDeTeste.DA_EXECUCAO.getBytes(StandardCharsets.UTF_8)));
	}

	/** Um token válido com o claim papel trocado NO TEXTO, sem reassinar: a assinatura deixa de conferir. */
	public static String comPapelEditadoSemReassinar(String tokenValido, String papelAtual, String novoPapel) {
		String[] partes = tokenValido.split("\\.");
		String corpo = new String(Base64.getUrlDecoder().decode(partes[1]), StandardCharsets.UTF_8);
		String editado = corpo.replace("\"papel\":\"" + papelAtual + "\"", "\"papel\":\"" + novoPapel + "\"");
		if (editado.equals(corpo)) {
			throw new IllegalStateException("O claim papel não foi encontrado no token.");
		}
		return partes[0] + "." + base64(editado) + "." + partes[2];
	}

	/** Troca um caractere da assinatura. */
	public static String comAssinaturaAdulterada(String tokenValido) {
		String[] partes = tokenValido.split("\\.");
		String assinatura = partes[2];
		int meio = assinatura.length() / 2;
		char trocado = (assinatura.charAt(meio) == 'A') ? 'B' : 'A';
		return partes[0] + "." + partes[1] + "." + assinatura.substring(0, meio) + trocado + assinatura.substring(meio + 1);
	}

	private static String base64(String texto) {
		return Base64.getUrlEncoder().withoutPadding().encodeToString(texto.getBytes(StandardCharsets.UTF_8));
	}

}
