package br.simulado.etec.catalogo.dto;

import java.util.List;
import java.util.UUID;

import br.simulado.etec.catalogo.model.Dificuldade;
import br.simulado.etec.catalogo.model.Prova;
import br.simulado.etec.catalogo.model.QuestaoVersao;
import br.simulado.etec.shared.validacao.HostDeImagemPermitido;
import br.simulado.etec.shared.validacao.MarcacaoMinima;
import br.simulado.etec.shared.validacao.TamanhoEmPontosDeCodigo;
import br.simulado.etec.shared.validacao.UrlHttps;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * Entrada do PUT de questão (D171): substituição total do rascunho. É um record PRÓPRIO porque aqui o numero e o provaId são
 * opcionais (só servem para comparar: diferentes do atual dão CAMPO_IMUTAVEL), e no cadastro o numero é obrigatório e o
 * provaId do corpo é ignorado (D27). As demais regras são as MESMAS do {@link QuestaoRequest}, e
 * {@code QuestaoPostEPutParidadeIntegracaoTest} manda os mesmos corpos inválidos às duas rotas para elas não divergirem:
 * ao mudar uma anotação aqui, mude lá também.
 */
@Schema(description = "Conteúdo completo do rascunho de uma questão. O que for omitido é apagado, inclusive o gabarito.")
public record QuestaoAtualizacaoRequest(

		@Schema(description = "Opcional. Se enviado, tem de ser o id público da prova da questão; um valor diferente dá 400 (CAMPO_IMUTAVEL).")
		UUID provaId,

		@Schema(description = "Opcional. Se enviado, tem de ser o número atual da questão; um valor diferente dá 400 (CAMPO_IMUTAVEL).")
		@Min(value = 1, message = "O número deve ser de 1 até a quantidade de questões da prova.")
		@Max(value = Prova.QUANTIDADE_MAXIMA_DE_QUESTOES, message = "O número deve ser de 1 até a quantidade de questões da prova.")
		Integer numero,

		@Schema(description = "Id público do subtópico. Se for outro que não o atual, precisa existir, ser do programa da prova e estar ativo.")
		@NotNull(message = "O subtópico é obrigatório.")
		UUID subtopicoId,

		@Schema(description = "Texto de apoio opcional (até 10.000 caracteres). Em branco ou omitido, é apagado.")
		@TamanhoEmPontosDeCodigo(max = QuestaoVersao.TEXTO_MAXIMO, message = "O texto de apoio deve ter no máximo 10000 caracteres.")
		@MarcacaoMinima(message = "O texto de apoio só pode usar as marcações b, u, i, sup, sub e br.")
		String textoApoio,

		@Schema(description = "Grupo do texto de apoio, opcional, a partir de 1.")
		@Min(value = 1, message = "O grupo de apoio deve ser maior ou igual a 1.")
		Integer grupoApoio,

		@Schema(description = "Enunciado, de 1 a 10.000 caracteres. Só as marcações b, u, i, sup, sub e br.")
		@NotNull(message = "O enunciado é obrigatório.")
		@TamanhoEmPontosDeCodigo(min = 1, max = QuestaoVersao.TEXTO_MAXIMO, message = "O enunciado deve ter de 1 a 10000 caracteres.")
		@MarcacaoMinima(message = "O enunciado só pode usar as marcações b, u, i, sup, sub e br.")
		String enunciado,

		@Schema(description = "Endereço https da imagem, opcional (até 500 caracteres); o host precisa estar na lista de servidores permitidos.")
		@TamanhoEmPontosDeCodigo(max = 500, message = "A URL da imagem deve ter no máximo 500 caracteres.")
		@UrlHttps(message = "A URL da imagem deve começar com https://.")
		@HostDeImagemPermitido(message = "O endereço da imagem não está em um servidor permitido.")
		String imagemUrl,

		@Schema(description = "Exatamente 5 alternativas, com as letras A a E, cada uma uma vez e em qualquer ordem.")
		@NotNull(message = "As alternativas são obrigatórias.")
		@AlternativasValidas
		@Valid
		List<AlternativaRequest> alternativas,

		@Schema(description = "Letra do gabarito (A a E). Opcional no rascunho; OMITIR APAGA o gabarito que estiver gravado.")
		@Pattern(regexp = "[A-E]", message = "O gabarito deve ser A, B, C, D ou E.")
		String gabarito,

		@Schema(description = "Dificuldade opcional: FACIL, MEDIA ou DIFICIL.")
		Dificuldade dificuldade,

		@Schema(description = "Explicação opcional (até 10.000 caracteres). Em branco ou omitida, é apagada.")
		@TamanhoEmPontosDeCodigo(max = QuestaoVersao.TEXTO_MAXIMO, message = "A explicação deve ter no máximo 10000 caracteres.")
		@MarcacaoMinima(message = "A explicação só pode usar as marcações b, u, i, sup, sub e br.")
		String explicacao) {

	public QuestaoAtualizacaoRequest {
		textoApoio = QuestaoRequest.emBrancoViraNulo(textoApoio);
		enunciado = (enunciado == null) ? null : enunciado.strip();
		imagemUrl = QuestaoRequest.emBrancoViraNulo(imagemUrl);
		explicacao = QuestaoRequest.emBrancoViraNulo(explicacao);
	}

}
