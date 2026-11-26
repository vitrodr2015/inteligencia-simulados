package br.simulado.etec.catalogo.mapper;

import br.simulado.etec.catalogo.dto.ProvaResponse;
import br.simulado.etec.catalogo.model.Prova;

/** Conversão entre a entidade e o DTO, escrita à mão. O programa (interno) não é copiado. */
public final class ProvaMapper {

	private ProvaMapper() {
	}

	public static ProvaResponse paraResponse(Prova prova) {
		return new ProvaResponse(
				prova.getPublicId(),
				prova.getTitulo(),
				prova.getBanca(),
				prova.getAno(),
				prova.getSemestre(),
				prova.getDuracaoMinutos(),
				prova.getQuantidadeQuestoes(),
				prova.getFonteUrl(),
				prova.getStatus(),
				prova.getPublicadaEm(),
				prova.getCriadoEm());
	}

}
