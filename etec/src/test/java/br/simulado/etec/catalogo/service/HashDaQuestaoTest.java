package br.simulado.etec.catalogo.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/**
 * O hash da questão (D51, D90) com VETORES FIXOS, calculados à parte por uma implementação independente (Node, crypto.createHash):
 * se alguém mudar a forma canônica, TODO hash já gravado muda, e estes testes quebram.
 */
class HashDaQuestaoTest {

	private static final UUID PROVA = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");

	private static Map<String, String> alternativas() {
		return new HashMap<>(Map.of("A", "um", "B", "dois", "C", "três", "D", "quatro", "E", "cinco"));
	}

	@Test
	void deve_dar_o_hash_fixo_quando_so_o_obrigatorio_esta_preenchido() {
		assertThat(HashDaQuestao.calcular(PROVA, 1, null, null, "Quanto é 1+1?", null, alternativas()))
				.isEqualTo("14b798c0da0b070fa40d26cac1b63002eeb7aa45550589e38b642274d69bc281");
	}

	@Test
	void deve_dar_o_hash_fixo_quando_todos_os_campos_estao_preenchidos() {
		assertThat(HashDaQuestao.calcular(PROVA, 7, "Leia o texto.", 3, "Enunciado com acento: ação",
				"https://imagens.exemplo.invalid/a.png", alternativas()))
				.isEqualTo("2fd7e3b2c4083a2a32373d40986de47a971fcdc06b80a97b0aa46f2d9e36b971");
	}

	@Test
	void deve_ter_64_caracteres_hexadecimais_minusculos() {
		assertThat(HashDaQuestao.calcular(PROVA, 1, null, null, "x", null, alternativas())).matches("[0-9a-f]{64}");
	}

	@Test
	void deve_tratar_nulo_vazio_e_espacos_nas_pontas_como_a_mesma_coisa() {
		String esperado = "14b798c0da0b070fa40d26cac1b63002eeb7aa45550589e38b642274d69bc281";

		assertThat(HashDaQuestao.calcular(PROVA, 1, "  ", null, "  Quanto é 1+1?  ", "", alternativas())).isEqualTo(esperado);
		assertThat(HashDaQuestao.calcular(PROVA, 1, "", null, "\tQuanto é 1+1?\n", "   ", alternativas())).isEqualTo(esperado);
	}

	@Test
	void deve_normalizar_as_quebras_de_linha_para_o_mesmo_hash() {
		String esperado = "a72b00a8de3852766895f279a0b211277afef217d629679b95959c7c923cd19a";

		assertThat(HashDaQuestao.calcular(PROVA, 1, null, null, "linha1\r\nlinha2", null, alternativas())).isEqualTo(esperado);
		assertThat(HashDaQuestao.calcular(PROVA, 1, null, null, "linha1\nlinha2", null, alternativas())).isEqualTo(esperado);
		assertThat(HashDaQuestao.calcular(PROVA, 1, null, null, "linha1\rlinha2", null, alternativas())).isEqualTo(esperado);
	}

	@Test
	void deve_usar_o_id_publico_da_prova_no_hash() {
		assertThat(HashDaQuestao.calcular(UUID.fromString("223e4567-e89b-12d3-a456-426614174000"), 1, null, null,
				"Quanto é 1+1?", null, alternativas()))
				.isEqualTo("1417fecb874cf258ceddd61cb94a93bc1c43f852230f14fca37b447458e1b26a");
	}

	@Test
	void deve_nao_colidir_quando_o_texto_muda_de_um_campo_para_o_seguinte() {
		Map<String, String> a = alternativas();
		a.put("A", "c");
		Map<String, String> b = alternativas();
		b.put("A", "bc");

		String hashA = HashDaQuestao.calcular(PROVA, 1, null, null, "ab", null, a);
		String hashB = HashDaQuestao.calcular(PROVA, 1, null, null, "a", null, b);

		assertThat(hashA).isEqualTo("78552838ac5727d3b4715eb291f260ae440da0656610db2aeb37f79200bb8e98");
		assertThat(hashB).isEqualTo("90e73c97f54ea2578555ed2aa861e9d7e9487b3871292609df792a0be87e9c19");
		assertThat(hashA).isNotEqualTo(hashB);
	}

	@Test
	void deve_mudar_quando_qualquer_campo_do_hash_muda() {
		String base = HashDaQuestao.calcular(PROVA, 1, "apoio", 2, "enunciado", "https://x.invalid/i.png", alternativas());

		assertThat(HashDaQuestao.calcular(PROVA, 2, "apoio", 2, "enunciado", "https://x.invalid/i.png", alternativas())).isNotEqualTo(base);
		assertThat(HashDaQuestao.calcular(PROVA, 1, "outro", 2, "enunciado", "https://x.invalid/i.png", alternativas())).isNotEqualTo(base);
		assertThat(HashDaQuestao.calcular(PROVA, 1, "apoio", 3, "enunciado", "https://x.invalid/i.png", alternativas())).isNotEqualTo(base);
		assertThat(HashDaQuestao.calcular(PROVA, 1, "apoio", null, "enunciado", "https://x.invalid/i.png", alternativas())).isNotEqualTo(base);
		assertThat(HashDaQuestao.calcular(PROVA, 1, "apoio", 2, "outro", "https://x.invalid/i.png", alternativas())).isNotEqualTo(base);
		assertThat(HashDaQuestao.calcular(PROVA, 1, "apoio", 2, "enunciado", "https://x.invalid/j.png", alternativas())).isNotEqualTo(base);
		for (String letra : new String[] { "A", "B", "C", "D", "E" }) {
			Map<String, String> mudada = alternativas();
			mudada.put(letra, "texto diferente");
			assertThat(HashDaQuestao.calcular(PROVA, 1, "apoio", 2, "enunciado", "https://x.invalid/i.png", mudada))
					.as("alternativa %s", letra).isNotEqualTo(base);
		}
	}

	@Test
	void deve_ser_o_mesmo_hash_independente_da_ordem_em_que_as_alternativas_foram_informadas() {
		Map<String, String> invertida = new java.util.LinkedHashMap<>();
		for (String letra : new String[] { "E", "D", "C", "B", "A" }) {
			invertida.put(letra, alternativas().get(letra));
		}

		assertThat(HashDaQuestao.calcular(PROVA, 1, null, null, "Quanto é 1+1?", null, invertida))
				.isEqualTo("14b798c0da0b070fa40d26cac1b63002eeb7aa45550589e38b642274d69bc281");
	}

}
