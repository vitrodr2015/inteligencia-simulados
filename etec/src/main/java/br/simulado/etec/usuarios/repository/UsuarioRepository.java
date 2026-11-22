package br.simulado.etec.usuarios.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import br.simulado.etec.usuarios.model.Usuario;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

	Optional<Usuario> findByEmail(String email);

	/** Usuário que existe e NÃO foi excluído (D26): é o que o filtro de autenticação confere a cada requisição. */
	Optional<Usuario> findByPublicIdAndExcluidoEmIsNull(UUID publicId);

}
