package br.simulado.etec.catalogo.mapper;

import br.simulado.etec.catalogo.dto.QuestaoResponse;
import br.simulado.etec.catalogo.dto.QuestaoResponse.AlternativaResponse;
import br.simulado.etec.catalogo.model.QuestaoVersao;

/** Conversão entre a versão da questão e o DTO do ADMIN, escrita à mão. O programa e o tipo (internos) não são copiados. */
public final class QuestaoMapper {

	private QuestaoMapper() {
	}

	public static QuestaoResponse paraResponse(QuestaoVersao versao) {
		return new QuestaoResponse(
				versao.getQuestao().getPublicId(),
				versao.getVersao(),
				versao.getQuestao().getProva().getPublicId(),
				versao.getQuestao().getNumero(),
				versao.getSubtopico().getPublicId(),
				versao.getTextoApoio(),
				versao.getGrupoApoio(),
				versao.getEnunciado(),
				versao.getImagemUrl(),
				versao.getAlternativas().stream().map(a -> new AlternativaResponse(a.getLetra(), a.getTexto())).toList(),
				versao.getGabarito(),
				versao.getDificuldade(),
				versao.getExplicacao(),
				versao.getStatus());
	}

}
