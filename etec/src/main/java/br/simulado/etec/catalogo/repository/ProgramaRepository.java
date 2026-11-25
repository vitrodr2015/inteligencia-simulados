package br.simulado.etec.catalogo.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import br.simulado.etec.catalogo.model.ProgramaDeProva;

public interface ProgramaRepository extends JpaRepository<ProgramaDeProva, Long> {

	/** O código é único (no MySQL a collation ignora a caixa; o CHECK da migration exige a caixa exata). */
	Optional<ProgramaDeProva> findByCodigo(String codigo);

}
