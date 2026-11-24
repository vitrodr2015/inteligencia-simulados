package br.simulado.etec.shared.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTCreator;
import com.auth0.jwt.algorithms.Algorithm;

import br.simulado.etec.shared.exception.CodigoErro;
import br.simulado.etec.shared.exception.InvalidTokenException;
import br.simulado.etec.shared.security.JwtTokenProvider.ClaimsDoToken;
import br.simulado.etec.shared.security.JwtTokenProvider.TipoToken;
import br.simulado.etec.shared.security.JwtTokenProvider.TokenEmitido;

/**
 * Matriz de recusa do provedor (Módulo 10). Relógio fixo: "vencido" não usa sleep. O segredo é aleatório a cada
 * execução; os tokens forjados aqui são assinados direto com a java-jwt, sem passar pelo provedor.
 */
class JwtTokenProviderTests {

	private static final Instant AGORA = Instant.parse("2026-11-23T12:00:00Z");

	private static final String EMISSOR = "etec-teste";

	private static final Duration ACCESS = Duration.ofMinutes(15);

	private static final Duration REFRESH = Duration.ofHours(24);

	private final String segredo = SegredoDeTeste.gerar();

	private final UUID usuario = UUID.randomUUID();

	private final JwtTokenProvider provedor = provedor(segredo, EMISSOR, AGORA);

	private static JwtTokenProvider provedor(String segredo, String emissor, Instant agora) {
		return new JwtTokenProvider(new JwtProperties(segredo, emissor, ACCESS, REFRESH),
				Clock.fixed(agora, ZoneOffset.UTC));
	}

	/** Token forjado válido em tudo; cada teste estraga uma coisa. */
	private JWTCreator.Builder base(TipoToken tipo) {
		return JWT.create().withIssuer(EMISSOR).withSubject(usuario.toString()).withClaim("papel", "STUDENT")
				.withClaim("tipo", tipo == TipoToken.ACCESS ? "access" : "refresh").withJWTId(UUID.randomUUID().toString())
				.withClaim("versao", 0).withIssuedAt(AGORA).withExpiresAt(AGORA.plus(ACCESS));
	}

	private Algorithm hs256() {
		return Algorithm.HMAC256(segredo.getBytes(StandardCharsets.UTF_8));
	}

	private void deveRecusar(String token, TipoToken tipo) {
		assertThatThrownBy(() -> provedor.validar(token, tipo)).isInstanceOf(InvalidTokenException.class)
				.hasMessage(InvalidTokenException.MENSAGEM)
				.extracting(e -> ((InvalidTokenException) e).getCodigo()).isEqualTo(CodigoErro.TOKEN_INVALIDO);
	}

	// ----- caminho feliz -----

	@Test
	void deve_emitir_e_validar_o_access_token_com_todos_os_claims() {
		TokenEmitido emitido = provedor.emitir(TipoToken.ACCESS, usuario, "STUDENT", 0);

		ClaimsDoToken claims = provedor.validar(emitido.valor(), TipoToken.ACCESS);

		assertThat(claims.usuarioId()).isEqualTo(usuario);
		assertThat(claims.tipo()).isEqualTo(TipoToken.ACCESS);
		assertThat(claims.papel()).isEqualTo("STUDENT");
		assertThat(claims.jti()).isEqualTo(emitido.jti());
		assertThat(claims.expiraEm()).isEqualTo(AGORA.plus(ACCESS)).isEqualTo(emitido.expiraEm());
	}

	@Test
	void deve_emitir_o_refresh_com_24_horas_e_validar_como_refresh() {
		TokenEmitido emitido = provedor.emitir(TipoToken.REFRESH, usuario, "ADMIN", 0);

		ClaimsDoToken claims = provedor.validar(emitido.valor(), TipoToken.REFRESH);

		assertThat(claims.expiraEm()).isEqualTo(AGORA.plus(REFRESH));
		assertThat(claims.tipo()).isEqualTo(TipoToken.REFRESH);
	}

	@Test
	void deve_gravar_emissor_e_emitido_em_no_token() {
		String token = provedor.emitir(TipoToken.ACCESS, usuario, "STUDENT", 0).valor();

		var decodificado = JWT.decode(token);

		assertThat(decodificado.getIssuer()).isEqualTo(EMISSOR);
		assertThat(decodificado.getIssuedAtAsInstant()).isEqualTo(AGORA);
		assertThat(decodificado.getAlgorithm()).isEqualTo("HS256");
	}

	@Test
	void deve_gerar_jti_diferente_a_cada_emissao() {
		String a = provedor.emitir(TipoToken.ACCESS, usuario, "STUDENT", 0).jti();
		String b = provedor.emitir(TipoToken.ACCESS, usuario, "STUDENT", 0).jti();

		assertThat(a).isNotEqualTo(b);
	}

	@Test
	void deve_informar_a_validade_do_access_em_segundos() {
		assertThat(provedor.validadeDoAccessEmSegundos()).isEqualTo(900);
	}

	@Test
	void deve_nunca_mostrar_o_token_no_texto_do_token_emitido() {
		TokenEmitido emitido = provedor.emitir(TipoToken.ACCESS, usuario, "STUDENT", 0);

		assertThat(emitido.toString()).doesNotContain(emitido.valor());
	}

	// ----- recusas -----

	@Test
	void deve_recusar_token_vencido() {
		String token = provedor.emitir(TipoToken.ACCESS, usuario, "STUDENT", 0).valor();
		JwtTokenProvider depois = provedor(segredo, EMISSOR, AGORA.plus(ACCESS).plusSeconds(1));

		assertThatThrownBy(() -> depois.validar(token, TipoToken.ACCESS)).isInstanceOf(InvalidTokenException.class);
		// um segundo antes do fim ainda vale
		JwtTokenProvider quaseNoFim = provedor(segredo, EMISSOR, AGORA.plus(ACCESS).minusSeconds(1));
		assertThat(quaseNoFim.validar(token, TipoToken.ACCESS).usuarioId()).isEqualTo(usuario);
	}

	@Test
	void deve_recusar_token_adulterado_no_corpo_ou_na_assinatura() {
		String token = provedor.emitir(TipoToken.ACCESS, usuario, "STUDENT", 0).valor();
		String[] partes = token.split("\\.");

		deveRecusar(partes[0] + "." + trocarUmCaractere(partes[1]) + "." + partes[2], TipoToken.ACCESS);
		deveRecusar(partes[0] + "." + partes[1] + "." + trocarUmCaractere(partes[2]), TipoToken.ACCESS);
		deveRecusar(partes[0] + "." + partes[1], TipoToken.ACCESS);
		deveRecusar(token + "x", TipoToken.ACCESS);
	}

	@Test
	void deve_recusar_token_assinado_com_outro_segredo() {
		String token = base(TipoToken.ACCESS).sign(Algorithm.HMAC256(SegredoDeTeste.gerar()));

		deveRecusar(token, TipoToken.ACCESS);
	}

	@Test
	void deve_recusar_token_de_outro_emissor() {
		String token = base(TipoToken.ACCESS).withIssuer("outro-emissor").sign(hs256());

		deveRecusar(token, TipoToken.ACCESS);
	}

	@Test
	void deve_recusar_token_sem_emissor() {
		String token = JWT.create().withSubject(usuario.toString()).withClaim("tipo", "access")
				.withJWTId(UUID.randomUUID().toString()).withExpiresAt(AGORA.plus(ACCESS)).sign(hs256());

		deveRecusar(token, TipoToken.ACCESS);
	}

	@Test
	void deve_recusar_refresh_como_access_e_access_como_refresh() {
		String refresh = provedor.emitir(TipoToken.REFRESH, usuario, "STUDENT", 0).valor();
		String access = provedor.emitir(TipoToken.ACCESS, usuario, "STUDENT", 0).valor();

		deveRecusar(refresh, TipoToken.ACCESS);
		deveRecusar(access, TipoToken.REFRESH);
	}

	@Test
	void deve_recusar_tipo_ausente_ou_desconhecido() {
		String semTipo = JWT.create().withIssuer(EMISSOR).withSubject(usuario.toString())
				.withJWTId(UUID.randomUUID().toString()).withExpiresAt(AGORA.plus(ACCESS)).sign(hs256());
		String tipoEstranho = base(TipoToken.ACCESS).withClaim("tipo", "admin").sign(hs256());
		String tipoNaoTexto = base(TipoToken.ACCESS).withClaim("tipo", 1).sign(hs256());

		deveRecusar(semTipo, TipoToken.ACCESS);
		deveRecusar(tipoEstranho, TipoToken.ACCESS);
		deveRecusar(tipoNaoTexto, TipoToken.ACCESS);
	}

	@Test
	void deve_recusar_papel_trocado_sem_reassinar_o_token() {
		String token = provedor.emitir(TipoToken.ACCESS, usuario, "STUDENT", 0).valor();
		String[] partes = token.split("\\.");
		String corpo = new String(Base64.getUrlDecoder().decode(partes[1]), StandardCharsets.UTF_8);
		assertThat(corpo).contains("\"papel\":\"STUDENT\"");
		String corpoAdulterado = Base64.getUrlEncoder().withoutPadding()
				.encodeToString(corpo.replace("STUDENT", "ADMIN").getBytes(StandardCharsets.UTF_8));

		deveRecusar(partes[0] + "." + corpoAdulterado + "." + partes[2], TipoToken.ACCESS);
	}

	@Test
	void deve_aceitar_o_papel_reassinado_com_o_segredo_certo_e_so_informar_o_claim() {
		// O claim é informativo (D87): quem decide é o banco. O provedor devolve o que está no token.
		String token = base(TipoToken.ACCESS).withClaim("papel", "ADMIN").sign(hs256());

		ClaimsDoToken claims = provedor.validar(token, TipoToken.ACCESS);

		assertThat(claims.papel()).isEqualTo("ADMIN");
	}

	@Test
	void deve_recusar_algoritmo_none() {
		String cabecalho = codificar("{\"alg\":\"none\",\"typ\":\"JWT\"}");
		String corpo = codificar("{\"iss\":\"" + EMISSOR + "\",\"sub\":\"" + usuario + "\",\"tipo\":\"access\","
				+ "\"jti\":\"" + UUID.randomUUID() + "\",\"exp\":" + AGORA.plus(ACCESS).getEpochSecond() + "}");

		deveRecusar(cabecalho + "." + corpo + ".", TipoToken.ACCESS);
		deveRecusar(cabecalho + "." + corpo, TipoToken.ACCESS);
		deveRecusar(cabecalho + "." + corpo + "." + codificar("x"), TipoToken.ACCESS);
	}

	@Test
	void deve_recusar_outros_algoritmos_hmac_mesmo_com_o_segredo_certo() {
		byte[] chave = segredo.getBytes(StandardCharsets.UTF_8);

		deveRecusar(base(TipoToken.ACCESS).sign(Algorithm.HMAC384(chave)), TipoToken.ACCESS);
		deveRecusar(base(TipoToken.ACCESS).sign(Algorithm.HMAC512(chave)), TipoToken.ACCESS);
	}

	@Test
	void deve_recusar_token_sem_exp() {
		String token = JWT.create().withIssuer(EMISSOR).withSubject(usuario.toString()).withClaim("tipo", "access")
				.withJWTId(UUID.randomUUID().toString()).sign(hs256());

		deveRecusar(token, TipoToken.ACCESS);
	}

	@Test
	void deve_recusar_token_sem_sub_ou_com_sub_que_nao_e_uuid() {
		String semSub = JWT.create().withIssuer(EMISSOR).withClaim("tipo", "access")
				.withJWTId(UUID.randomUUID().toString()).withExpiresAt(AGORA.plus(ACCESS)).sign(hs256());
		String subInvalido = base(TipoToken.ACCESS).withSubject("123").sign(hs256());

		deveRecusar(semSub, TipoToken.ACCESS);
		deveRecusar(subInvalido, TipoToken.ACCESS);
	}

	@Test
	void deve_recusar_token_sem_jti() {
		String token = JWT.create().withIssuer(EMISSOR).withSubject(usuario.toString()).withClaim("tipo", "access")
				.withExpiresAt(AGORA.plus(ACCESS)).sign(hs256());

		deveRecusar(token, TipoToken.ACCESS);
	}

	@Test
	void deve_recusar_token_ainda_nao_valido_ou_emitido_no_futuro() {
		deveRecusar(base(TipoToken.ACCESS).withNotBefore(AGORA.plusSeconds(60)).sign(hs256()), TipoToken.ACCESS);
		deveRecusar(base(TipoToken.ACCESS).withIssuedAt(AGORA.plusSeconds(60)).sign(hs256()), TipoToken.ACCESS);
	}

	@Test
	void deve_recusar_texto_que_nao_e_token() {
		deveRecusar(null, TipoToken.ACCESS);
		deveRecusar("", TipoToken.ACCESS);
		deveRecusar("   ", TipoToken.ACCESS);
		deveRecusar("lixo", TipoToken.ACCESS);
		deveRecusar("a.b.c", TipoToken.ACCESS);
		deveRecusar("....", TipoToken.ACCESS);
		deveRecusar("x".repeat(JwtTokenProvider.TAMANHO_MAXIMO_DO_TOKEN + 1), TipoToken.ACCESS);
	}

	@Test
	void deve_nunca_por_o_token_na_mensagem_da_excecao() {
		String token = base(TipoToken.ACCESS).withIssuer("outro").sign(hs256());

		assertThatThrownBy(() -> provedor.validar(token, TipoToken.ACCESS)).hasMessageNotContaining(token)
				.hasMessageNotContaining("outro");
	}

	private static String codificar(String texto) {
		return Base64.getUrlEncoder().withoutPadding().encodeToString(texto.getBytes(StandardCharsets.UTF_8));
	}

	private static String trocarUmCaractere(String texto) {
		char meio = texto.charAt(texto.length() / 2);
		char outro = (meio == 'A') ? 'B' : 'A';
		return texto.substring(0, texto.length() / 2) + outro + texto.substring(texto.length() / 2 + 1);
	}


	@Test
	void deve_levar_a_versao_no_claim_e_devolve_la_na_validacao() {
		String token = provedor.emitir(TipoToken.ACCESS, usuario, "STUDENT", 5).valor();

		assertThat(provedor.validar(token, TipoToken.ACCESS).versao()).isEqualTo(5);
	}

	@Test
	void deve_recusar_o_token_sem_o_claim_de_versao() {
		String semVersao = JWT.create().withIssuer(EMISSOR).withSubject(usuario.toString()).withClaim("papel", "STUDENT")
				.withClaim("tipo", "access").withJWTId(UUID.randomUUID().toString()).withIssuedAt(AGORA)
				.withExpiresAt(AGORA.plus(ACCESS)).sign(hs256());

		deveRecusar(semVersao, TipoToken.ACCESS);
	}
}
