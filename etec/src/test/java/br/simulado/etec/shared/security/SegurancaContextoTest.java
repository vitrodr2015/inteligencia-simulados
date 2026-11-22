package br.simulado.etec.shared.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

import jakarta.servlet.Filter;

/** A configuração de segurança como o Spring a montou (H2): sem usuário gerado, sem filtro duplicado. */
@SpringBootTest
class SegurancaContextoTest {

	@Autowired
	private ApplicationContext contexto;

	@Test
	void deve_nao_criar_o_usuario_user_com_senha_gerada_e_impressa_no_log() {
		assertThat(contexto.getBeansOfType(InMemoryUserDetailsManager.class)).isEmpty();
		assertThatThrownBy(() -> contexto.getBean(UserDetailsService.class).loadUserByUsername("user"))
				.isInstanceOf(UsernameNotFoundException.class);
	}

	@Test
	void deve_ter_uma_unica_cadeia_de_seguranca_e_o_filtro_jwt_fora_do_servlet() {
		Map<String, SecurityFilterChain> cadeias = contexto.getBeansOfType(SecurityFilterChain.class);

		assertThat(cadeias).hasSize(1);
		// criado com new na SecurityConfig, nunca como bean (senão o Boot o registraria também no servlet)
		assertThat(contexto.getBeansOfType(JwtTokenFilter.class)).isEmpty();
		assertThat(contexto.getBeansOfType(Filter.class).values()).noneMatch(JwtTokenFilter.class::isInstance);
		assertThat(cadeias.values().iterator().next().getFilters()).anyMatch(JwtTokenFilter.class::isInstance);
	}

	@Test
	void deve_usar_o_segredo_aleatorio_da_execucao_e_nao_um_valor_do_repositorio() {
		JwtProperties propriedades = contexto.getBean(JwtProperties.class);

		assertThat(propriedades.secret()).isEqualTo(SegredoDeTeste.DA_EXECUCAO).hasSizeGreaterThanOrEqualTo(32);
	}

}
