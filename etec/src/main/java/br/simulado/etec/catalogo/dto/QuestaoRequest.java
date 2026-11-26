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
 * Entrada do cadastro unitário de questão (POST /admin/provas/{id}/questoes). A prova vem da rota; o programa e o tipo são internos e
 * não entram. Qualquer outro campo enviado (id, versao, status, provaId) é ignorado (D27).
 *
 * <p>Os espaços das pontas dos textos são removidos ANTES da validação (D57, D104); textoApoio, imagemUrl e explicacao em branco
 * viram {@code null}. Os limites de texto contam pontos de código (D98). Os textos aceitam só a marcação mínima (D12, D32).
 */
@Schema(description = "Dados de uma questão nova. Campos de saída (id, versao, provaId, status) enviados no corpo são ignorados.")
public record QuestaoRequest(

		@Schema(description = "Número da questão na prova, de 1 até a quantidadeQuestoes da prova. Único dentro da prova e imutável.")
		@NotNull(message = "O número é obrigatório.")
		@Min(value = 1, message = "O número deve ser de 1 até a quantidade de questões da prova.")
		@Max(value = Prova.QUANTIDADE_MAXIMA_DE_QUESTOES, message = "O número deve ser de 1 até a quantidade de questões da prova.")
		Integer numero,

		@Schema(description = "Id público do subtópico. Precisa existir, ser do programa da prova e estar ativo (de matéria ativa).")
		@NotNull(message = "O subtópico é obrigatório.")
		UUID subtopicoId,

		@Schema(description = "Texto de apoio opcional (até 10.000 caracteres). Em branco vira nulo.")
		@TamanhoEmPontosDeCodigo(max = QuestaoVersao.TEXTO_MAXIMO, message = "O texto de apoio deve ter no máximo 10000 caracteres.")
		@MarcacaoMinima(message = "O texto de apoio só pode usar as marcações b, u, i, sup, sub e br.")
		String textoApoio,

		@Schema(description = "Grupo do texto de apoio, opcional, a partir de 1: questões da mesma prova com o mesmo valor compartilham o texto de apoio.")
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

		@Schema(description = "Letra do gabarito (A a E), opcional no cadastro; obrigatória só para publicar.")
		@Pattern(regexp = "[A-E]", message = "O gabarito deve ser A, B, C, D ou E.")
		String gabarito,

		@Schema(description = "Dificuldade opcional: FACIL, MEDIA ou DIFICIL.")
		Dificuldade dificuldade,

		@Schema(description = "Explicação opcional (até 10.000 caracteres). Em branco vira nula.")
		@TamanhoEmPontosDeCodigo(max = QuestaoVersao.TEXTO_MAXIMO, message = "A explicação deve ter no máximo 10000 caracteres.")
		@MarcacaoMinima(message = "A explicação só pode usar as marcações b, u, i, sup, sub e br.")
		String explicacao) {

	public QuestaoRequest {
		textoApoio = emBrancoViraNulo(textoApoio);
		enunciado = (enunciado == null) ? null : enunciado.strip();
		imagemUrl = emBrancoViraNulo(imagemUrl);
		explicacao = emBrancoViraNulo(explicacao);
	}

	private static String emBrancoViraNulo(String texto) {
		return (texto == null || texto.isBlank()) ? null : texto.strip();
	}

}
