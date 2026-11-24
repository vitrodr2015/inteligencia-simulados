package br.simulado.etec.auth.dto;

import java.time.LocalDate;
import java.util.Locale;

import br.simulado.etec.shared.validacao.DataDeNascimento;
import br.simulado.etec.shared.validacao.EmailAscii;
import br.simulado.etec.shared.validacao.PoliticaDeSenha;
import br.simulado.etec.shared.validacao.TamanhoEmBytes;
import br.simulado.etec.shared.validacao.TamanhoEmPontosDeCodigo;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * Entrada do cadastro (contrato, seção 3). Nome e e-mail têm os espaços das pontas removidos ANTES de validar, e o
 * e-mail vai para minúsculas (D57, D105). {@code papel}, {@code id}, datas e qualquer outro campo enviado são
 * ignorados: o cadastro sempre cria STUDENT.
 */
@Schema(description = "Dados do cadastro de aluno. Campos de saída (id, papel, datas) enviados no corpo são ignorados.")
public record RegistroRequest(

		@Schema(description = "Nome (2 a 100 caracteres, sem espaços nas pontas).")
		@NotNull(message = "O nome é obrigatório.")
		@TamanhoEmPontosDeCodigo(min = 2, max = 100, message = "O nome deve ter de 2 a 100 caracteres.")
		String nome,

		@Schema(description = "E-mail em formato válido, só com caracteres ASCII, até 255 caracteres. Vai para minúsculas.")
		@NotNull(message = "O e-mail é obrigatório.")
		@TamanhoEmPontosDeCodigo(max = 255, message = "O e-mail deve ter no máximo 255 caracteres.")
		@EmailAscii(message = "Informe um e-mail válido, só com caracteres ASCII.")
		String email,

		@Schema(description = "Senha de 8 a 72 bytes (UTF-8). Só entra; nunca sai.")
		@NotNull(message = "A senha é obrigatória.")
		@TamanhoEmBytes(min = PoliticaDeSenha.MINIMO_DO_ALUNO_EM_BYTES, max = PoliticaDeSenha.MAXIMO_EM_BYTES, message = "A senha deve ter de 8 a 72 bytes.")
		String senha,

		@Schema(description = "Data de nascimento (aaaa-mm-dd), no passado (UTC) e a partir de 1900-01-01.", example = "2008-05-10")
		@NotNull(message = "A data de nascimento é obrigatória.")
		@DataDeNascimento(message = "A data de nascimento deve estar no passado e a partir de 1900-01-01.")
		LocalDate dataNascimento,

		@Schema(description = "Versão dos termos de uso aceita; precisa ser igual à de GET /termos/vigente.")
		@NotNull(message = "O aceite dos termos é obrigatório.")
		String aceiteTermosVersao) {

	public RegistroRequest {
		nome = (nome == null) ? null : nome.strip();
		email = (email == null) ? null : email.strip().toLowerCase(Locale.ROOT);
	}

	/** Nunca mostra a senha (um log ou uma mensagem de erro com o objeto não pode vazá-la). */
	@Override
	public String toString() {
		return "RegistroRequest[nome=" + nome + ", email=" + email + ", senha=***, dataNascimento=" + dataNascimento
				+ ", aceiteTermosVersao=" + aceiteTermosVersao + "]";
	}

}
