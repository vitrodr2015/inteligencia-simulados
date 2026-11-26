package br.simulado.etec.catalogo.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import br.simulado.etec.catalogo.model.Prova;
import br.simulado.etec.catalogo.model.Questao;

public interface QuestaoRepository extends JpaRepository<Questao, Long> {

	Optional<Questao> findByPublicId(UUID publicId);

	Optional<Questao> findByProvaAndNumero(Prova prova, int numero);

	/** Quantas questões (identidades, não versões) tem cada prova de {@code provaIds}; prova sem questão não aparece. */
	interface ContagemPorProva {

		Long getProvaId();

		long getTotal();

	}

	/** UMA consulta agrupada para todas as provas da página (sem N+1). {@code provaIds} não pode ser vazio. */
	@Query("select q.prova.id as provaId, count(q.id) as total from Questao q where q.prova.id in :provaIds group by q.prova.id")
	List<ContagemPorProva> contarPorProva(@Param("provaIds") Collection<Long> provaIds);

}
