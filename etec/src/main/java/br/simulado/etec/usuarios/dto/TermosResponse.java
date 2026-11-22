package br.simulado.etec.usuarios.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import io.swagger.v3.oas.annotations.media.Schema;

/** Termos de uso vigentes (D23, D94): a versão que o cadastro precisa aceitar e onde lê-la. */
@JsonPropertyOrder({ "versao", "url" })
@JsonInclude(JsonInclude.Include.ALWAYS)
@Schema(description = "Termos de uso vigentes.")
public record TermosResponse(

		@Schema(description = "Versão vigente; é o valor de aceiteTermosVersao no cadastro.")
		String versao,

		@Schema(description = "Endereço do texto dos termos.")
		String url) {

}
