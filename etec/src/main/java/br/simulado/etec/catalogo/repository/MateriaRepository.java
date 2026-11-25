package br.simulado.etec.catalogo.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import br.simulado.etec.catalogo.model.Materia;
import br.simulado.etec.catalogo.model.ProgramaDeProva;

public interface MateriaRepository extends JpaRepository<Materia, Long>, JpaSpecificationExecutor<Materia> {

	Optional<Materia> findByPublicId(UUID publicId);

	/** O nome é único POR PROGRAMA (D144). No MySQL a comparação usa a collation da coluna (ignora maiúsculas e acentos). */
	boolean existsByProgramaAndNome(ProgramaDeProva programa, String nome);

	/** Para o PUT: o mesmo registro com o próprio nome é permitido. */
	boolean existsByProgramaAndNomeAndPublicIdNot(ProgramaDeProva programa, String nome, UUID publicId);

	/**
	 * Apaga numa única instrução (atômico): nunca ler e depois apagar, senão pedidos simultâneos
	 * passam todos pela leitura e falham na hora de apagar.
	 *
	 * @return quantas linhas foram apagadas (0 = não existia)
	 */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("delete from Materia m where m.publicId = :publicId")
	int apagarPorPublicId(@Param("publicId") UUID publicId);

}
