package br.simulado.etec.shared.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class PasswordConfigTest {

	private final PasswordEncoder encoder = new PasswordConfig().passwordEncoder();

	@Test
	void deve_gerar_hash_com_prefixo_bcrypt_e_conferir_a_senha_certa() {
		String hash = encoder.encode("senha-de-teste-123");

		assertThat(hash).startsWith("{bcrypt}$2").hasSize(68).doesNotContain("senha-de-teste-123");
		assertThat(encoder.matches("senha-de-teste-123", hash)).isTrue();
		assertThat(encoder.matches("senha-de-teste-124", hash)).isFalse();
		assertThat(encoder.matches("", hash)).isFalse();
	}

	@Test
	void deve_gerar_hashes_diferentes_para_a_mesma_senha() {
		assertThat(encoder.encode("mesma-senha-1")).isNotEqualTo(encoder.encode("mesma-senha-1"));
	}

	@Test
	void deve_recusar_noop_md5_sha256_e_senha_em_texto_puro_sem_lancar_excecao() {
		assertThat(encoder.matches("segredo123", "{noop}segredo123")).isFalse();
		assertThat(encoder.matches("segredo123", "{md5}9b4b7d7f8e2f6a0c1d3e5f708192a3b4")).isFalse();
		assertThat(encoder.matches("segredo123", "{sha256}abc")).isFalse();
		assertThat(encoder.matches("segredo123", "{pbkdf2}abc")).isFalse();
		assertThat(encoder.matches("segredo123", "segredo123")).isFalse();
		assertThat(encoder.matches("segredo123", "hash-falso-nao-e-real")).isFalse();
		assertThat(encoder.matches("segredo123", "")).isFalse();
		assertThat(encoder.matches("segredo123", null)).isFalse();
		assertThat(encoder.matches("segredo123", "{bcrypt}lixo")).isFalse();
	}

	@Test
	void deve_recusar_o_bcrypt_sem_prefixo() {
		String semPrefixo = encoder.encode("segredo123").substring("{bcrypt}".length());

		assertThat(encoder.matches("segredo123", semPrefixo)).isFalse();
	}

	/**
	 * ACHADO: o BCrypt do Spring Security 7.2 NÃO recusa senha com mais de 72 bytes: ele trunca em silêncio. Uma senha de
	 * 73 bytes confere com o hash da sua versão de 72. Por isso o cadastro (400) e o signin (401, antes de comparar com o
	 * hash real) barram esse tamanho: sem a barreira, qualquer sufixo depois do 72º byte entraria.
	 */
	@Test
	void deve_truncar_em_silencio_a_senha_acima_de_72_bytes_e_por_isso_o_tamanho_e_barrado_antes() {
		String com72 = "a".repeat(72);
		String hash72 = encoder.encode(com72);

		assertThat(encoder.matches(com72, hash72)).isTrue();
		assertThat(encoder.matches(com72 + "QUALQUER-COISA", hash72)).isTrue();
	}

}
