package br.simulado.etec.catalogo.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import br.simulado.etec.catalogo.model.ProgramaDeProva;
import br.simulado.etec.catalogo.model.Prova;

public interface ProvaRepository extends JpaRepository<Prova, Long>, JpaSpecificationExecutor<Prova> {

	Optional<Prova> findByPublicId(UUID publicId);

	/** Checagem prévia da unicidade (D150): só faz sentido com banca, ano e semestre preenchidos. A banca compara pela collation da coluna. */
	boolean existsByProgramaAndBancaAndAnoAndSemestre(ProgramaDeProva programa, String banca, Integer ano, Integer semestre);

}
