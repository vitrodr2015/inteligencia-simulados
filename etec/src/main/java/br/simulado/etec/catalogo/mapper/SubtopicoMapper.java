package br.simulado.etec.catalogo.mapper;

import br.simulado.etec.catalogo.dto.SubtopicoResponse;
import br.simulado.etec.catalogo.model.Subtopico;

/** Conversão entre a entidade e o DTO, escrita à mão. A matéria já vem carregada pela consulta. */
public final class SubtopicoMapper {

	private SubtopicoMapper() {
	}

	public static SubtopicoResponse paraResponse(Subtopico subtopico) {
		return new SubtopicoResponse(
				subtopico.getPublicId(),
				subtopico.getMateria().getPublicId(),
				subtopico.getNome(),
				subtopico.isAtivo(),
				subtopico.getCriadoEm(),
				subtopico.getAtualizadoEm());
	}

}
