package br.simulado.etec.catalogo.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import br.simulado.etec.catalogo.model.Materia;
import br.simulado.etec.catalogo.model.Subtopico;

/**
 * Subtópicos. As consultas que alimentam a API carregam a matéria junto ({@code EntityGraph}), porque o
 * DTO de saída traz o id público dela e a sessão já está fechada quando o controller monta a resposta.
 */
public interface SubtopicoRepository extends JpaRepository<Subtopico, Long>, JpaSpecificationExecutor<Subtopico> {

	@EntityGraph(attributePaths = "materia")
	Optional<Subtopico> findByPublicId(UUID publicId);

	@Override
	@EntityGraph(attributePaths = "materia")
	Page<Subtopico> findAll(Specification<Subtopico> filtros, Pageable paginacao);

	/** No MySQL a comparação usa a collation da coluna (ignora maiúsculas e acentos). */
	boolean existsByMateriaAndNome(Materia materia, String nome);

	/** Para o PUT: o mesmo registro com o próprio nome é permitido. */
	boolean existsByMateriaAndNomeAndPublicIdNot(Materia materia, String nome, UUID publicId);

	/** Atômico, como o da matéria: uma instrução só; 0 linhas = não existia. */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("delete from Subtopico s where s.publicId = :publicId")
	int apagarPorPublicId(@Param("publicId") UUID publicId);

}
