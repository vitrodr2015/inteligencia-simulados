package br.simulado.etec.usuarios.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import br.simulado.etec.shared.config.AuditoriaConfig;
import br.simulado.etec.usuarios.model.Papel;
import br.simulado.etec.usuarios.model.Usuario;

/**
 * Teste rápido com H2. Atenção: a unicidade do e-mail sem distinção de maiúsculas e acentos
 * (contrato 4.1 e D29), o CHECK de papel e a migration só são provados contra MySQL real
 * (Módulo 7), porque o H2 não se comporta igual e aqui o esquema vem do Hibernate, não do Flyway.
 */
@DataJpaTest
@Import(AuditoriaConfig.class)
class UsuarioRepositoryTest {

	private static final Instant AGORA = Instant.parse("2026-11-20T15:04:05Z");

	/** Relógio fixo no lugar do ClockConfig: prova que a auditoria usa o Clock da aplicação. */
	@TestConfiguration
	static class RelogioFixo {

		@Bean
		Clock clock() {
			return Clock.fixed(AGORA, ZoneOffset.UTC);
		}

	}

	@Autowired
	private UsuarioRepository repositorio;

	@Autowired
	private JdbcTemplate jdbc;

	@Test
	void deve_encontrar_o_usuario_de_exemplo_quando_busca_por_email_do_data_sql() {
		Optional<Usuario> encontrado = repositorio.findByEmail("aluno.exemplo@exemplo.invalid");

		assertThat(encontrado).isPresent();
		assertThat(encontrado.get().getNome()).isEqualTo("Aluno de Exemplo");
		assertThat(encontrado.get().getPapel()).isEqualTo(Papel.STUDENT);
	}

	@Test
	void deve_salvar_e_buscar_por_email_quando_o_usuario_e_novo() {
		Usuario novo = new Usuario("Maria Teste", "maria.teste@exemplo.invalid", "hash-falso-nao-e-real",
				LocalDate.of(2009, 3, 20), "v1", Papel.STUDENT);

		Usuario salvo = repositorio.saveAndFlush(novo);

		assertThat(salvo.getId()).isNotNull();
		assertThat(salvo.getPublicId()).isNotNull();
		assertThat(salvo.getCriadoEm()).isNotNull();
		assertThat(salvo.getAtualizadoEm()).isNotNull();
		assertThat(salvo.getExcluidoEm()).isNull();

		Optional<Usuario> buscado = repositorio.findByEmail("maria.teste@exemplo.invalid");
		assertThat(buscado).isPresent();
		assertThat(buscado.get().getPublicId()).isEqualTo(salvo.getPublicId());
		assertThat(buscado.get().getDataNascimento()).isEqualTo(LocalDate.of(2009, 3, 20));
	}

	@Test
	void deve_preencher_criado_em_e_atualizado_em_com_o_clock_quando_salva_um_usuario() {
		Usuario novo = new Usuario("Joana Teste", "joana.teste@exemplo.invalid", "hash-falso-nao-e-real",
				LocalDate.of(2008, 8, 8), "v1", Papel.STUDENT);

		Usuario salvo = repositorio.saveAndFlush(novo);

		assertThat(salvo.getCriadoEm()).isEqualTo(AGORA);
		assertThat(salvo.getAtualizadoEm()).isEqualTo(AGORA);
	}

	@Test
	void deve_devolver_vazio_quando_o_email_nao_existe() {
		assertThat(repositorio.findByEmail("ninguem@exemplo.invalid")).isEmpty();
	}

	@Test
	void deve_encontrar_pelo_public_id_quando_o_usuario_nao_foi_excluido() {
		Usuario salvo = repositorio.saveAndFlush(new Usuario("Rita Teste", "rita.teste@exemplo.invalid",
				"hash-falso-nao-e-real", LocalDate.of(2008, 1, 2), "v1", Papel.STUDENT));

		assertThat(repositorio.findByPublicIdAndExcluidoEmIsNull(salvo.getPublicId())).isPresent();
	}

	@Test
	void deve_devolver_vazio_pelo_public_id_quando_o_usuario_foi_excluido_ou_nao_existe() {
		Usuario salvo = repositorio.saveAndFlush(new Usuario("Caio Teste", "caio.teste@exemplo.invalid",
				"hash-falso-nao-e-real", LocalDate.of(2008, 1, 2), "v1", Papel.STUDENT));
		jdbc.update("UPDATE usuario SET excluido_em = CURRENT_TIMESTAMP WHERE id = ?", salvo.getId());

		assertThat(repositorio.findByPublicIdAndExcluidoEmIsNull(salvo.getPublicId())).isEmpty();
		assertThat(repositorio.findByPublicIdAndExcluidoEmIsNull(java.util.UUID.randomUUID())).isEmpty();
	}

}
