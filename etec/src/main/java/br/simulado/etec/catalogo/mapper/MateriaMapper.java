package br.simulado.etec.catalogo.mapper;

import br.simulado.etec.catalogo.dto.MateriaResponse;
import br.simulado.etec.catalogo.model.Materia;

/** Conversão entre a entidade e o DTO, escrita à mão. */
public final class MateriaMapper {

	private MateriaMapper() {
	}

	public static MateriaResponse paraResponse(Materia materia) {
		return new MateriaResponse(
				materia.getPublicId(),
				materia.getNome(),
				materia.getDescricao(),
				materia.isAtivo(),
				materia.getCriadoEm(),
				materia.getAtualizadoEm());
	}

}
