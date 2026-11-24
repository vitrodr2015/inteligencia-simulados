package br.simulado.etec.usuarios.repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import br.simulado.etec.usuarios.model.Papel;
import br.simulado.etec.usuarios.model.Usuario;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

	Optional<Usuario> findByEmail(String email);

	/** Usuário que existe e NÃO foi excluído (D26): é o que o filtro de autenticação confere a cada requisição. */
	Optional<Usuario> findByPublicIdAndExcluidoEmIsNull(UUID publicId);

	/** Existe administrador em uso (D45, D81)? É a regra do bootstrap: com um admin ativo, as variáveis são ignoradas. */
	boolean existsByPapelAndExcluidoEmIsNull(Papel papel);

	/**
	 * Incrementa a versão do token de forma ATÔMICA (D136). O {@code atualizado_em} é gravado à mão porque o UPDATE em lote não
	 * passa pela auditoria.
	 */
	@Modifying(flushAutomatically = true)
	@Query("update Usuario u set u.versaoToken = u.versaoToken + 1, u.atualizadoEm = :agora where u.id = :id")
	int incrementarVersaoDoToken(@Param("id") Long id, @Param("agora") Instant agora);

}
