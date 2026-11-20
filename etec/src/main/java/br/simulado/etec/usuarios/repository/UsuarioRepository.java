package br.simulado.etec.usuarios.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import br.simulado.etec.usuarios.model.Usuario;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

	Optional<Usuario> findByEmail(String email);

}
