package br.simulado.etec.catalogo.dto;

import br.simulado.etec.catalogo.model.Prova;
import br.simulado.etec.shared.validacao.TamanhoEmPontosDeCodigo;
import br.simulado.etec.shared.validacao.UrlHttps;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Entrada do POST /admin/provas. Só estes campos entram: qualquer outro enviado (id, status, datas, programa) é ignorado (D27).
 *
 * <p>Os espaços das pontas dos textos são removidos ANTES da validação (D57, D104): um título só de espaços vira vazio e dá
 * 400. Banca e fonteUrl que sobram em branco viram {@code null}, igual a omitidos (D104). Os limites de texto contam pontos de
 * código (D98). Banca, ano e semestre são opcionais (D150); {@code quantidadeQuestoes} é dado declarado, de 1 a 300 (D151).
 */
@Schema(description = "Dados de uma prova. Campos de saída (id, status, datas) enviados no corpo são ignorados.")
public record ProvaRequest(

		@Schema(description = "Título da prova (1 a 150 caracteres, sem espaços nas pontas).")
		@NotNull(message = "O título é obrigatório.")
		@TamanhoEmPontosDeCodigo(min = 1, max = 150, message = "O título deve ter de 1 a 150 caracteres.")
		String titulo,

		@Schema(description = "Banca organizadora, opcional, até 80 caracteres. Em branco vira nula.")
		@TamanhoEmPontosDeCodigo(max = 80, message = "A banca deve ter no máximo 80 caracteres.")
		String banca,

		@Schema(description = "Ano da prova, opcional, de 2000 a 2100.")
		@Min(value = 2000, message = "O ano deve estar entre 2000 e 2100.")
		@Max(value = 2100, message = "O ano deve estar entre 2000 e 2100.")
		Integer ano,

		@Schema(description = "Semestre da prova, opcional: 1 ou 2.")
		@Min(value = 1, message = "O semestre deve ser 1 ou 2.")
		@Max(value = 2, message = "O semestre deve ser 1 ou 2.")
		Integer semestre,

		@Schema(description = "Duração da prova em minutos, de 1 a 600.")
		@NotNull(message = "A duração em minutos é obrigatória.")
		@Min(value = 1, message = "A duração deve estar entre 1 e 600 minutos.")
		@Max(value = 600, message = "A duração deve estar entre 1 e 600 minutos.")
		Integer duracaoMinutos,

		@Schema(description = "Quantidade de questões da prova, declarada na criação, de 1 a 300.")
		@NotNull(message = "A quantidade de questões é obrigatória.")
		@Min(value = 1, message = "A quantidade de questões deve estar entre 1 e 300.")
		@Max(value = Prova.QUANTIDADE_MAXIMA_DE_QUESTOES, message = "A quantidade de questões deve estar entre 1 e 300.")
		Integer quantidadeQuestoes,

		@Schema(description = "Endereço da fonte da prova, opcional: https, até 500 caracteres. Em branco vira nulo.")
		@TamanhoEmPontosDeCodigo(max = 500, message = "A URL da fonte deve ter no máximo 500 caracteres.")
		@UrlHttps(message = "A URL da fonte deve começar com https://.")
		String fonteUrl) {

	public ProvaRequest {
		titulo = (titulo == null) ? null : titulo.strip();
		banca = emBrancoViraNulo(banca);
		fonteUrl = emBrancoViraNulo(fonteUrl);
	}

	private static String emBrancoViraNulo(String texto) {
		return (texto == null || texto.isBlank()) ? null : texto.strip();
	}

}
