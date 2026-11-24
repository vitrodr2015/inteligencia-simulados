package br.simulado.etec.shared.validacao;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import br.simulado.etec.shared.validacao.PoliticaDeSenha.Violacao;

class PoliticaDeSenhaTest {

	@Test
	void deve_aceitar_do_aluno_de_8_a_72_bytes_e_so_olhar_o_tamanho() {
		assertThat(PoliticaDeSenha.verificarDoAluno("1234567")).containsExactly(Violacao.MUITO_CURTA);
		assertThat(PoliticaDeSenha.verificarDoAluno("12345678")).isEmpty();
		assertThat(PoliticaDeSenha.verificarDoAluno("a".repeat(72))).isEmpty();
		assertThat(PoliticaDeSenha.verificarDoAluno("a".repeat(73))).containsExactly(Violacao.MUITO_LONGA);
		assertThat(PoliticaDeSenha.verificarDoAluno(" com espaço nas pontas ")).as("o aluno não tem regra de pontas").isEmpty();
	}

	@Test
	void deve_contar_em_bytes_utf8_e_nao_em_caracteres() {
		assertThat(PoliticaDeSenha.verificarDoAluno("é".repeat(4))).as("8 bytes").isEmpty();
		assertThat(PoliticaDeSenha.verificarDoAluno("é".repeat(3))).as("6 bytes").containsExactly(Violacao.MUITO_CURTA);
		assertThat(PoliticaDeSenha.verificarDoAluno("é".repeat(37))).as("74 bytes").containsExactly(Violacao.MUITO_LONGA);
	}

	@Test
	void deve_exigir_do_admin_de_12_a_72_bytes_pontas_sem_quebra_de_linha_e_sem_marcador() {
		assertThat(PoliticaDeSenha.verificarDoAdmin("a".repeat(11))).containsExactly(Violacao.MUITO_CURTA);
		assertThat(PoliticaDeSenha.verificarDoAdmin("a".repeat(12))).isEmpty();
		assertThat(PoliticaDeSenha.verificarDoAdmin("a".repeat(73))).containsExactly(Violacao.MUITO_LONGA);
		assertThat(PoliticaDeSenha.verificarDoAdmin(" senha-com-espaco")).containsExactly(Violacao.ESPACO_NAS_PONTAS);
		assertThat(PoliticaDeSenha.verificarDoAdmin("senha-com-espaco\t")).containsExactly(Violacao.ESPACO_NAS_PONTAS);
		assertThat(PoliticaDeSenha.verificarDoAdmin("senha-com\nquebra-1")).containsExactly(Violacao.QUEBRA_DE_LINHA);
		assertThat(PoliticaDeSenha.verificarDoAdmin("troque-a-senha-do-admin")).containsExactly(Violacao.MARCADOR_DE_EXEMPLO);
		assertThat(PoliticaDeSenha.verificarDoAdmin("<defina-aqui-ja>")).containsExactly(Violacao.MARCADOR_DE_EXEMPLO);
		assertThat(PoliticaDeSenha.verificarDoAdmin("Senha-troque-123456")).isEmpty();
	}

	@Test
	void deve_nao_quebrar_com_a_senha_vazia() {
		assertThat(PoliticaDeSenha.verificarDoAluno("")).containsExactly(Violacao.MUITO_CURTA);
		assertThat(PoliticaDeSenha.verificarDoAdmin("")).containsExactly(Violacao.MUITO_CURTA);
	}

}
