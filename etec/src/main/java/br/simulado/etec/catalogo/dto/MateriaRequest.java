package br.simulado.etec.catalogo.dto;

import br.simulado.etec.shared.validacao.TamanhoEmPontosDeCodigo;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * Entrada de POST e PUT de matéria. Só {@code nome} e {@code descricao} entram: qualquer outro
 * campo enviado (id, ativo, datas) é ignorado (D27).
 *
 * <p>Os espaços das pontas dos DOIS textos são removidos ANTES da validação (D57, D104): um nome só de
 * espaços vira vazio e dá 400. Uma descrição que sobra em branco depois disso vira {@code null}, igual a
 * uma descrição omitida (decisão da Tarefa 09: o contrato manda aparar e não diz o que fazer com o vazio).
 * Os limites contam pontos de código, não unidades UTF-16 (D98).
 */
@Schema(description = "Dados de uma matéria. Campos de saída (id, ativo, datas) enviados no corpo são ignorados.")
public record MateriaRequest(

		@Schema(description = "Nome da matéria (2 a 100 caracteres, sem espaços nas pontas). Único, sem distinguir maiúsculas e acentos.")
		@NotNull(message = "O nome é obrigatório.")
		@TamanhoEmPontosDeCodigo(min = 2, max = 100, message = "O nome deve ter de 2 a 100 caracteres.")
		String nome,

		@Schema(description = "Descrição opcional, de até 255 caracteres. Os espaços das pontas são removidos; em branco vira nula.")
		@TamanhoEmPontosDeCodigo(max = 255, message = "A descrição deve ter no máximo 255 caracteres.")
		String descricao) {

	public MateriaRequest {
		nome = (nome == null) ? null : nome.strip();
		descricao = (descricao == null || descricao.isBlank()) ? null : descricao.strip();
	}

}
