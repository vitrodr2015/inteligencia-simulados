package br.simulado.etec.catalogo.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import br.simulado.etec.catalogo.model.Prova;
import br.simulado.etec.catalogo.model.Questao;

public interface QuestaoRepository extends JpaRepository<Questao, Long> {

	Optional<Questao> findByPublicId(UUID publicId);

	Optional<Questao> findByProvaAndNumero(Prova prova, int numero);

}
