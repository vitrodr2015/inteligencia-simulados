package br.simulado.etec.usuarios.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import br.simulado.etec.usuarios.model.Papel;
import br.simulado.etec.usuarios.model.Usuario;

/**
 * Teste rápido com H2. Atenção: a unicidade do e-mail sem distinção de maiúsculas e acentos
 * (contrato 4.1 e D29), o CHECK de papel e a migration só são provados contra MySQL real
 * (Módulo 7), porque o H2 não se comporta igual e aqui o esquema vem do Hibernate, não do Flyway.
 */
@DataJpaTest
class UsuarioRepositoryTest {

	@Autowired
	private UsuarioRepository repositorio;

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
	void deve_devolver_vazio_quando_o_email_nao_existe() {
		assertThat(repositorio.findByEmail("ninguem@exemplo.invalid")).isEmpty();
	}

}
