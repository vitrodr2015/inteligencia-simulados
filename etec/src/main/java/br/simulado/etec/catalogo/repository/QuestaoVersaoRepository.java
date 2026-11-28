package br.simulado.etec.catalogo.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import br.simulado.etec.catalogo.model.Prova;
import br.simulado.etec.catalogo.model.Questao;
import br.simulado.etec.catalogo.model.QuestaoVersao;
import br.simulado.etec.catalogo.model.StatusQuestao;

public interface QuestaoVersaoRepository extends JpaRepository<QuestaoVersao, Long> {

	Optional<QuestaoVersao> findByQuestaoAndVersao(Questao questao, int versao);

	/** No máximo uma versão RASCUNHO e uma PUBLICADA por questão (índices únicos das colunas geradas, V10). */
	Optional<QuestaoVersao> findByQuestaoAndStatus(Questao questao, StatusQuestao status);

	/** Há versão fora de {@code status}? Com RASCUNHO responde "esta questão já foi publicada alguma vez" (D184). */
	boolean existsByQuestaoAndStatusNot(Questao questao, StatusQuestao status);

	/** Os numeros, em ordem, das questões da prova que têm alguma versão PUBLICADA ou ARQUIVADA (D185). */
	@Query("select distinct q.numero from QuestaoVersao v join v.questao q where q.prova = :prova "
			+ "and v.status <> br.simulado.etec.catalogo.model.StatusQuestao.RASCUNHO order by q.numero")
	List<Integer> buscarNumerosJaPublicados(@Param("prova") Prova prova);

	/** O maior numero de versão da questão, contando as arquivadas. A questão sempre tem ao menos uma versão. */
	@Query("select max(v.versao) from QuestaoVersao v where v.questao = :questao")
	int buscarMaiorVersao(@Param("questao") Questao questao);

	/**
	 * Só a condição "esta é a versão EXIBIDA da questão" (D19, D56): o rascunho aberto, se houver, senão a publicada. Uma ARQUIVADA
	 * nunca é exibida. Os filtros opcionais (nulo = todos) agem sobre ESSA versão, depois da escolha: o subtópico é o dela (D146).
	 */
	String VERSAO_EXIBIDA = " from QuestaoVersao v join v.questao q "
			+ "where (v.status = br.simulado.etec.catalogo.model.StatusQuestao.RASCUNHO "
			+ "or (v.status = br.simulado.etec.catalogo.model.StatusQuestao.PUBLICADA and not exists "
			+ "(select 1 from QuestaoVersao r where r.questao = q and r.status = br.simulado.etec.catalogo.model.StatusQuestao.RASCUNHO))) "
			+ "and (:provaId is null or q.prova.publicId = :provaId) "
			+ "and (:subtopicoId is null or v.subtopico.publicId = :subtopicoId) "
			+ "and (:status is null or v.status = :status)";

	/**
	 * Primeira das duas consultas da listagem: só os ids internos das versões exibidas, já na ordem (numero crescente, desempate pelo
	 * id interno da questão, D77) e paginados no banco. A paginação recebida NÃO pode trazer sort: a ordem é fixa aqui.
	 */
	@Query(value = "select v.id" + VERSAO_EXIBIDA + " order by q.numero asc, q.id asc", countQuery = "select count(v.id)" + VERSAO_EXIBIDA)
	Page<Long> buscarIdsDasVersoesExibidas(@Param("provaId") UUID provaId, @Param("subtopicoId") UUID subtopicoId,
			@Param("status") StatusQuestao status, Pageable paginacao);

	/**
	 * Para a publicação da prova (D178): as versões em RASCUNHO e PUBLICADA das questões dela, com o subtópico, a matéria e as
	 * alternativas na mesma instrução, sem uma consulta por questão. A lista pode repetir a versão (uma linha por alternativa).
	 */
	@Query("select v from QuestaoVersao v join fetch v.questao q join fetch v.subtopico s join fetch s.materia "
			+ "left join fetch v.alternativas where q.prova = :prova "
			+ "and v.status <> br.simulado.etec.catalogo.model.StatusQuestao.ARQUIVADA")
	List<QuestaoVersao> buscarVivasDaProva(@Param("prova") Prova prova);

	/**
	 * Segunda consulta: as versões dos ids da página com TUDO o que a saída usa (questão, prova, subtópico e alternativas) em uma
	 * só instrução, sem N+1. A ordem da lista devolvida não é garantida: o service a reordena pelos ids.
	 */
	@Query("select v from QuestaoVersao v join fetch v.questao q join fetch q.prova join fetch v.subtopico "
			+ "left join fetch v.alternativas where v.id in :ids")
	List<QuestaoVersao> carregarCompletas(@Param("ids") Collection<Long> ids);

}
