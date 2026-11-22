package br.simulado.etec.shared.security;

import org.springframework.stereotype.Component;

import br.simulado.etec.shared.exception.InvalidTokenException;
import br.simulado.etec.shared.security.JwtTokenProvider.ClaimsDoToken;
import br.simulado.etec.shared.security.JwtTokenProvider.TipoToken;
import br.simulado.etec.usuarios.model.Usuario;
import br.simulado.etec.usuarios.service.UsuarioService;

/**
 * Transforma um access token em "quem está chamando". Valida o token pelo {@link JwtTokenProvider} e RECARREGA o
 * usuário do banco a cada chamada (D26, D87): o token não é a fonte da verdade. Usuário que não existe mais ou foi
 * excluído derruba o token na requisição seguinte, e o papel que vale é o do banco, nunca o do claim.
 */
@Component
public class AutenticadorDeToken {

	private final JwtTokenProvider tokens;

	private final UsuarioService usuarios;

	public AutenticadorDeToken(JwtTokenProvider tokens, UsuarioService usuarios) {
		this.tokens = tokens;
		this.usuarios = usuarios;
	}

	/**
	 * @throws InvalidTokenException token inválido por qualquer motivo, ou usuário inexistente ou excluído
	 */
	public UsuarioAutenticado autenticar(String accessToken) {
		ClaimsDoToken claims = tokens.validar(accessToken, TipoToken.ACCESS);
		Usuario usuario = usuarios.buscarAtivoPorPublicId(claims.usuarioId()).orElseThrow(InvalidTokenException::new);
		return new UsuarioAutenticado(usuario.getPublicId(), usuario.getPapel());
	}

}
