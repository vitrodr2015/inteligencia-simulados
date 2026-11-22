package br.simulado.etec.auth.service;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import br.simulado.etec.auth.dto.RefreshRequest;
import br.simulado.etec.auth.dto.RegistroRequest;
import br.simulado.etec.auth.dto.SigninRequest;
import br.simulado.etec.auth.dto.TokensResponse;
import br.simulado.etec.shared.exception.CredenciaisInvalidasException;
import br.simulado.etec.shared.exception.InvalidTokenException;
import br.simulado.etec.shared.security.JwtTokenProvider;
import br.simulado.etec.shared.security.JwtTokenProvider.ClaimsDoToken;
import br.simulado.etec.shared.security.JwtTokenProvider.TipoToken;
import br.simulado.etec.usuarios.model.Usuario;
import br.simulado.etec.usuarios.service.UsuarioService;

/**
 * Cadastro, signin e refresh (contrato, seção 3). Sem persistência de refresh: a rotação, o logout e a revogação
 * por reuso (D39, D49, D15) são do Módulo 13; até lá o refresh usado NÃO é invalidado.
 *
 * <p><b>Signin sem pistas.</b> Usuário inexistente, conta excluída, e-mail de formato inválido, senha errada, senha
 * vazia e senha acima de 72 bytes terminam na mesma exceção, e TODAS passam por UMA comparação de BCrypt (com um
 * hash fictício quando não há usuário válido), para o tempo de resposta não revelar qual foi a falha. A senha acima
 * de 72 bytes nunca é comparada com o hash real: o BCrypt a truncaria em silêncio e um sufixo qualquer entraria.
 */
@Service
public class AuthService {

	/** Limite do BCrypt, em bytes UTF-8 (o contrato manda 72). */
	static final int TAMANHO_MAXIMO_DA_SENHA_EM_BYTES = 72;

	/** Tamanho máximo do e-mail (coluna); acima disso nem se consulta o banco. */
	static final int TAMANHO_MAXIMO_DO_EMAIL = 255;

	/** Texto usado na comparação quando a senha enviada não pode ser comparada (acima de 72 bytes). */
	private static final String SENHA_DE_COMPARACAO = "texto-so-para-gastar-o-mesmo-tempo";

	private final UsuarioService usuarios;

	private final PasswordEncoder encoder;

	private final JwtTokenProvider tokens;

	/** Hash de uma senha aleatória descartada, gerado com o mesmo encoder (e portanto o mesmo custo) dos reais. */
	private final String hashFicticio;

	public AuthService(UsuarioService usuarios, PasswordEncoder encoder, JwtTokenProvider tokens) {
		this.usuarios = usuarios;
		this.encoder = encoder;
		this.tokens = tokens;
		this.hashFicticio = encoder.encode(UUID.randomUUID().toString());
	}

	/** Cria o aluno. O e-mail já chega normalizado e todos os campos já foram validados no formato. */
	public Usuario registrar(RegistroRequest dados) {
		return usuarios.criarAluno(dados.nome(), dados.email(), dados.senha(), dados.dataNascimento(),
				dados.aceiteTermosVersao());
	}

	public TokensResponse signin(SigninRequest dados) {
		String email = dados.email();
		String senha = dados.senha();

		Optional<Usuario> usuario = (email.length() > TAMANHO_MAXIMO_DO_EMAIL) ? Optional.empty()
				: usuarios.buscarPorEmail(email);
		boolean usuarioValido = usuario.isPresent() && usuario.get().getExcluidoEm() == null;
		boolean senhaCabeNoBcrypt = senha.getBytes(StandardCharsets.UTF_8).length <= TAMANHO_MAXIMO_DA_SENHA_EM_BYTES;
		boolean comparaComOHashReal = usuarioValido && senhaCabeNoBcrypt;

		// Sempre exatamente UMA comparação, em todos os caminhos.
		String hash = comparaComOHashReal ? usuario.get().getSenhaHash() : hashFicticio;
		String candidata = senhaCabeNoBcrypt ? senha : SENHA_DE_COMPARACAO;
		boolean confere = encoder.matches(candidata, hash);

		if (!(comparaComOHashReal && confere)) {
			throw new CredenciaisInvalidasException();
		}
		return emitirPar(usuario.get());
	}

	/**
	 * Troca um refresh válido por um par novo. O usuário é recarregado do banco (excluído = 401) e o papel do novo
	 * token vem dele. Um access token enviado aqui dá 401 (D107).
	 */
	public TokensResponse refresh(RefreshRequest dados) {
		ClaimsDoToken claims = tokens.validar(dados.refreshToken(), TipoToken.REFRESH);
		Usuario usuario = usuarios.buscarAtivoPorPublicId(claims.usuarioId()).orElseThrow(InvalidTokenException::new);
		return emitirPar(usuario);
	}

	private TokensResponse emitirPar(Usuario usuario) {
		String papel = usuario.getPapel().name();
		String access = tokens.emitir(TipoToken.ACCESS, usuario.getPublicId(), papel).valor();
		String refresh = tokens.emitir(TipoToken.REFRESH, usuario.getPublicId(), papel).valor();
		return new TokensResponse(access, refresh, TokensResponse.TIPO, tokens.validadeDoAccessEmSegundos());
	}

}
