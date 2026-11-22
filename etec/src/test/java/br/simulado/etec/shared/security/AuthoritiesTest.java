package br.simulado.etec.shared.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Modifier;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

import br.simulado.etec.usuarios.model.Papel;

class AuthoritiesTest {

	@Test
	void deve_ter_uma_constante_ROLE_para_cada_papel_e_o_mapeamento_conferir() throws Exception {
		for (Papel papel : Papel.values()) {
			var campo = Authorities.class.getField("ROLE_" + papel.name());

			assertThat(Modifier.isStatic(campo.getModifiers()) && Modifier.isFinal(campo.getModifiers())).isTrue();
			assertThat(campo.get(null)).isEqualTo("ROLE_" + papel.name()).isEqualTo(Authorities.de(papel));
		}
	}

	@Test
	void deve_nao_ter_constante_ROLE_sem_papel_correspondente() {
		long constantes = Arrays.stream(Authorities.class.getFields()).filter(c -> c.getName().startsWith("ROLE_")).count();

		assertThat(constantes).isEqualTo(Papel.values().length);
	}

}
