package br.simulado.etec.catalogo.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import br.simulado.etec.catalogo.model.ProgramaDeProva;
import br.simulado.etec.catalogo.model.Prova;

public interface ProvaRepository extends JpaRepository<Prova, Long>, JpaSpecificationExecutor<Prova> {

	Optional<Prova> findByPublicId(UUID publicId);

	/**
	 * A prova com a linha TRAVADA para escrita (SELECT ... FOR UPDATE) até o fim da transação: serializa os cadastros de questão
	 * da mesma prova, e é isso que torna a idempotência do numero repetido (D153) correta sob concorrência.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select p from Prova p where p.publicId = :publicId")
	Optional<Prova> buscarComTrava(@Param("publicId") UUID publicId);

	/** Checagem prévia da unicidade (D150): só faz sentido com banca, ano e semestre preenchidos. A banca compara pela collation da coluna. */
	boolean existsByProgramaAndBancaAndAnoAndSemestre(ProgramaDeProva programa, String banca, Integer ano, Integer semestre);

	/** Para o PUT: a própria prova pode manter a banca, o ano e o semestre dela. */
	boolean existsByProgramaAndBancaAndAnoAndSemestreAndPublicIdNot(ProgramaDeProva programa, String banca, Integer ano,
			Integer semestre, UUID publicId);

}
