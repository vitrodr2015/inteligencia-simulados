package br.simulado.etec.catalogo.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import br.simulado.etec.catalogo.model.Questao;
import br.simulado.etec.catalogo.model.QuestaoVersao;
import br.simulado.etec.catalogo.model.StatusQuestao;

public interface QuestaoVersaoRepository extends JpaRepository<QuestaoVersao, Long> {

	Optional<QuestaoVersao> findByQuestaoAndVersao(Questao questao, int versao);

	/** No máximo uma versão RASCUNHO e uma PUBLICADA por questão (índices únicos das colunas geradas, V10). */
	Optional<QuestaoVersao> findByQuestaoAndStatus(Questao questao, StatusQuestao status);

}
