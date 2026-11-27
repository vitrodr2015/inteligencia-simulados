package br.simulado.etec.catalogo.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import br.simulado.etec.catalogo.model.Prova;
import br.simulado.etec.catalogo.model.Questao;

public interface QuestaoRepository extends JpaRepository<Questao, Long> {

	Optional<Questao> findByPublicId(UUID publicId);

	Optional<Questao> findByProvaAndNumero(Prova prova, int numero);

	/**
	 * O id público da prova de uma questão, lido ANTES da transação de escrita das rotas por id de questão (D175): a prova de uma
	 * questão nunca muda, então é seguro. A transação própria e curta é obrigatória aqui: ela devolve a conexão ao pool antes
	 * de a escrita pegar a dela. Sem isso cada pedido seguraria duas conexões, e muitos pedidos juntos esgotariam o pool.
	 */
	@Transactional(readOnly = true)
	@Query("select q.prova.publicId from Questao q where q.publicId = :publicId")
	Optional<UUID> buscarProvaDaQuestao(@Param("publicId") UUID publicId);

	/** O maior numero de questão cadastrado na prova (vazio se não há questão). */
	@Query("select max(q.numero) from Questao q where q.prova = :prova")
	Optional<Integer> buscarMaiorNumero(@Param("prova") Prova prova);

	/** Quantas questões (identidades, não versões) tem cada prova de {@code provaIds}; prova sem questão não aparece. */
	interface ContagemPorProva {

		Long getProvaId();

		long getTotal();

	}

	/** UMA consulta agrupada para todas as provas da página (sem N+1). {@code provaIds} não pode ser vazio. */
	@Query("select q.prova.id as provaId, count(q.id) as total from Questao q where q.prova.id in :provaIds group by q.prova.id")
	List<ContagemPorProva> contarPorProva(@Param("provaIds") Collection<Long> provaIds);

}
