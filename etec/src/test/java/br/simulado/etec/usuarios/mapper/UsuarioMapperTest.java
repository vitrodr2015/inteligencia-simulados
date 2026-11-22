package br.simulado.etec.usuarios.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import br.simulado.etec.usuarios.dto.UsuarioResponse;
import br.simulado.etec.usuarios.model.Papel;
import br.simulado.etec.usuarios.model.Usuario;

class UsuarioMapperTest {

	private static final Clock RELOGIO = Clock.fixed(Instant.parse("2026-11-23T02:00:00Z"), ZoneOffset.UTC);

	@Test
	void deve_ser_menor_ate_o_dia_anterior_ao_aniversario_de_18_anos() {
		assertThat(UsuarioMapper.ehMenor(LocalDate.of(2008, 11, 24), RELOGIO)).isTrue();
		assertThat(UsuarioMapper.ehMenor(LocalDate.of(2008, 11, 23), RELOGIO)).isFalse();
		assertThat(UsuarioMapper.ehMenor(LocalDate.of(2000, 1, 1), RELOGIO)).isFalse();
		assertThat(UsuarioMapper.ehMenor(LocalDate.of(2026, 11, 22), RELOGIO)).isTrue();
	}

	@Test
	void deve_calcular_a_idade_em_utc_e_nao_no_fuso_do_sistema() {
		// 02:00 UTC de 23/11 ainda é 22/11 em São Paulo; o contrato manda UTC.
		Clock saoPaulo = Clock.fixed(Instant.parse("2026-11-23T02:00:00Z"), ZoneOffset.ofHours(-3));

		assertThat(UsuarioMapper.ehMenor(LocalDate.of(2008, 11, 23), saoPaulo)).isFalse();
	}

	@Test
	void deve_montar_a_resposta_sem_nenhum_dado_de_credencial() {
		Usuario usuario = new Usuario("Maria Teste", "maria@exemplo.invalid", "{bcrypt}hash-que-nao-pode-sair",
				LocalDate.of(2008, 5, 10), "v1", Papel.STUDENT);

		UsuarioResponse resposta = UsuarioMapper.paraResponse(usuario, RELOGIO);

		assertThat(resposta.id()).isEqualTo(usuario.getPublicId());
		assertThat(resposta.email()).isEqualTo("maria@exemplo.invalid");
		assertThat(resposta.papel()).isEqualTo(Papel.STUDENT);
		assertThat(resposta.menor()).isFalse();
		assertThat(resposta.toString()).doesNotContain("hash-que-nao-pode-sair");
	}

}
