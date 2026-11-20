package br.simulado.etec.usuarios.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import br.simulado.etec.integracao.AbstractIntegrationTest;
import br.simulado.etec.usuarios.model.Papel;
import br.simulado.etec.usuarios.model.Usuario;

/**
 * Repositório de Usuario contra o MySQL REAL, com a migration V1 de verdade. Prova o que o H2 não
 * prova: unicidade sem distinguir maiúsculas (collation utf8mb4_unicode_ci), public_id único, CHECK
 * do papel e a auditoria sem arredondamento de milissegundo.
 */
class UsuarioRepositoryIntegracaoTest extends AbstractIntegrationTest {

	@Autowired
	private UsuarioRepository repositorio;

	@Autowired
	private JdbcTemplate jdbc;

	private static Usuario usuario(String email) {
		return new Usuario("Usuário de Teste", email, "hash-falso-nao-e-real", LocalDate.of(2008, 5, 10), "v1",
				Papel.STUDENT);
	}

	@Test
	void deve_violar_a_unicidade_quando_o_email_difere_so_na_caixa() {
		String sufixo = sufixo();
		repositorio.saveAndFlush(usuario("A" + sufixo + "@x.com"));

		assertThatThrownBy(() -> repositorio.saveAndFlush(usuario("a" + sufixo + "@x.com")))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void deve_encontrar_o_usuario_quando_a_busca_por_email_difere_so_na_caixa() {
		String sufixo = sufixo();
		Usuario salvo = repositorio.saveAndFlush(usuario("busca" + sufixo + "@x.com"));

		assertThat(repositorio.findByEmail("BUSCA" + sufixo + "@X.COM")).isPresent().get()
				.extracting(Usuario::getPublicId).isEqualTo(salvo.getPublicId());
	}

	@Test
	void deve_manter_o_public_id_unico_quando_outra_linha_reaproveita_o_valor() {
		String sufixo = sufixo();
		repositorio.saveAndFlush(usuario("original" + sufixo + "@x.com"));
		byte[] publicId = jdbc.queryForObject("select public_id from usuario where email = ?", byte[].class,
				"original" + sufixo + "@x.com");

		assertThatThrownBy(() -> inserirLinha(publicId, "copia" + sufixo + "@x.com", "STUDENT"))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void deve_recusar_o_papel_quando_o_valor_esta_fora_da_lista_do_check() {
		// O Spring não traduz o erro 3819 (CHECK) do MySQL para DataIntegrityViolationException: vira
		// UncategorizedSQLException. O que prova o CHECK é a causa raiz.
		assertThatThrownBy(() -> inserirLinha(new byte[16], "papel" + sufixo() + "@x.com", "SUPERUSUARIO"))
				.hasRootCauseInstanceOf(SQLException.class)
				.rootCause().hasMessageContaining("ck_usuario_papel");
	}

	@Test
	void deve_preencher_as_datas_na_auditoria_e_devolver_o_mesmo_valor_ao_ler_do_banco() {
		String email = "audit" + sufixo() + "@x.com";

		Usuario salvo = repositorio.saveAndFlush(usuario(email));
		Usuario lido = repositorio.findByEmail(email).orElseThrow();

		assertThat(salvo.getCriadoEm()).isNotNull();
		assertThat(salvo.getAtualizadoEm()).isNotNull();
		// Sem arredondamento: o que a aplicação gerou é exatamente o que o MySQL guardou (DATETIME(3)).
		assertThat(lido.getCriadoEm()).isEqualTo(salvo.getCriadoEm());
		assertThat(lido.getAtualizadoEm()).isEqualTo(salvo.getAtualizadoEm());
		assertThat(salvo.getCriadoEm().getNano() % 1_000_000).as("nanossegundos além do milissegundo").isZero();
	}

	@Test
	void deve_atribuir_um_public_id_diferente_para_cada_usuario() {
		Usuario primeiro = repositorio.saveAndFlush(usuario("u1" + sufixo() + "@x.com"));
		Usuario segundo = repositorio.saveAndFlush(usuario("u2" + sufixo() + "@x.com"));

		assertThat(primeiro.getPublicId()).isNotNull().isNotEqualTo(segundo.getPublicId());
	}

	private void inserirLinha(byte[] publicId, String email, String papel) {
		Timestamp agora = Timestamp.from(Instant.now());
		jdbc.update("insert into usuario (public_id, nome, email, senha_hash, data_nascimento, aceite_termos_versao,"
				+ " papel, criado_em, atualizado_em) values (?, ?, ?, ?, ?, ?, ?, ?, ?)", publicId, "Nome", email,
				"hash-falso", java.sql.Date.valueOf(LocalDate.of(2008, 5, 10)), "v1", papel, agora, agora);
	}

}
