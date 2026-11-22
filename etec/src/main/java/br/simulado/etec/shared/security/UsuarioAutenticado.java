package br.simulado.etec.shared.security;

import java.util.UUID;

import br.simulado.etec.usuarios.model.Papel;

/**
 * Quem está autenticado na requisição. O papel vem do BANCO (D87), nunca do claim do token.
 */
public record UsuarioAutenticado(UUID id, Papel papel) {
}
