package br.simulado.etec.usuarios.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import br.simulado.etec.usuarios.model.Papel;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Saída de usuário: o corpo do 201 do cadastro e do {@code GET /me} (contrato, seção 3). Nunca traz senha, hash,
 * versão dos termos nem o id numérico. A ordem dos campos é a da tabela do contrato (D72); {@code menor} é
 * calculado na leitura (D106).
 */
@JsonPropertyOrder({ "id", "nome", "email", "dataNascimento", "papel", "menor", "criadoEm" })
@JsonInclude(JsonInclude.Include.ALWAYS)
@Schema(description = "Usuário.")
public record UsuarioResponse(

		@Schema(description = "Identificador público (UUID).")
		UUID id,

		@Schema(description = "Nome do usuário.")
		String nome,

		@Schema(description = "E-mail, em minúsculas.")
		String email,

		@Schema(description = "Data de nascimento (aaaa-mm-dd).", example = "2008-05-10")
		LocalDate dataNascimento,

		@Schema(description = "Papel. O cadastro sempre cria STUDENT.")
		Papel papel,

		@Schema(description = "Menor de 18 anos, calculado na leitura. Na fase 1 é só uma marca.")
		boolean menor,

		@Schema(description = "Criação, em UTC com três casas de milissegundos.")
		Instant criadoEm) {

}
