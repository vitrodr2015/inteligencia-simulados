package br.simulado.etec.usuarios.mapper;

import java.time.Clock;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneOffset;

import br.simulado.etec.usuarios.dto.UsuarioResponse;
import br.simulado.etec.usuarios.model.Usuario;

/** Conversão entre a entidade e o DTO, escrita à mão. */
public final class UsuarioMapper {

	/** Maioridade (D106): menor é quem tem menos de 18 anos completos. */
	static final int MAIORIDADE = 18;

	private UsuarioMapper() {
	}

	public static UsuarioResponse paraResponse(Usuario usuario, Clock relogio) {
		return new UsuarioResponse(
				usuario.getPublicId(),
				usuario.getNome(),
				usuario.getEmail(),
				usuario.getDataNascimento(),
				usuario.getPapel(),
				ehMenor(usuario.getDataNascimento(), relogio),
				usuario.getCriadoEm());
	}

	/** Idade em anos completos, em UTC, pelo relógio da aplicação. */
	static boolean ehMenor(LocalDate dataNascimento, Clock relogio) {
		LocalDate hoje = LocalDate.now(relogio.withZone(ZoneOffset.UTC));
		return Period.between(dataNascimento, hoje).getYears() < MAIORIDADE;
	}

}
