package br.simulado.etec.shared.exception;

import java.util.List;
import java.util.Map;

import br.simulado.etec.shared.exception.ErrorResponse.CampoInvalido;

/** Requisição inválida por regra do nosso código (400). */
public class InvalidRequestException extends ApiException {

	private final List<CampoInvalido> campos;

	public InvalidRequestException(String mensagem) {
		super(CodigoErro.VALIDACAO_FALHOU, mensagem, null);
		this.campos = null;
	}

	public InvalidRequestException(CodigoErro codigo, String mensagem) {
		super(codigo, mensagem, null);
		this.campos = null;
	}

	public InvalidRequestException(CodigoErro codigo, String mensagem, Map<String, Object> detalhes) {
		super(codigo, mensagem, detalhes);
		this.campos = null;
	}

	private InvalidRequestException(CodigoErro codigo, String mensagem, List<CampoInvalido> campos) {
		super(codigo, mensagem, null);
		this.campos = List.copyOf(campos);
	}

	/**
	 * 400 de validação que aponta UM campo do corpo (VALIDACAO_FALHOU, com {@code campos}). Serve para regra que só o
	 * service sabe checar, como "o corpo referencia um recurso que não existe" (D14).
	 */
	public static InvalidRequestException campoInvalido(String campo, String mensagem) {
		return new InvalidRequestException(CodigoErro.VALIDACAO_FALHOU, "Há campos inválidos na requisição.",
				List.of(new CampoInvalido(campo, mensagem)));
	}

	/** 400 de validação que aponta VÁRIOS campos do corpo (VALIDACAO_FALHOU, com {@code campos}). */
	public static InvalidRequestException camposInvalidos(List<CampoInvalido> campos) {
		return new InvalidRequestException(CodigoErro.VALIDACAO_FALHOU, "Há campos inválidos na requisição.", campos);
	}

	/**
	 * O mesmo erro de validação, com o caminho de cada campo precedido por {@code prefixo} (por exemplo {@code questoes[3].}):
	 * é assim que o erro de um item de um lote diz de qual item é. Sem {@code campos}, devolve o próprio erro.
	 */
	public InvalidRequestException comPrefixoNosCampos(String prefixo) {
		if (campos == null) {
			return this;
		}
		return camposInvalidos(campos.stream().map(c -> new CampoInvalido(prefixo + c.campo(), c.mensagem())).toList());
	}

	/** Campos inválidos a mostrar no ErrorResponse, ou {@code null} quando o erro não aponta campo. */
	public List<CampoInvalido> getCampos() {
		return campos;
	}

}
