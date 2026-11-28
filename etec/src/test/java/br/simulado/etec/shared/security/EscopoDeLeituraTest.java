package br.simulado.etec.shared.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;

import br.simulado.etec.shared.exception.AcessoNegadoException;
import br.simulado.etec.shared.exception.CodigoErro;
import br.simulado.etec.shared.exception.InvalidRequestException;
import br.simulado.etec.usuarios.model.Papel;

class EscopoDeLeituraTest {

	private final EscopoDeLeituraArgumentResolver resolvedor = new EscopoDeLeituraArgumentResolver();

	@AfterEach
	void limpar() {
		SecurityContextHolder.clearContext();
	}

	private void autenticarComo(Object principal) {
		SecurityContextHolder.setContext(
				new SecurityContextImpl(UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of())));
	}

	@Test
	void deve_dar_tudo_ao_admin_e_so_ativos_ao_student() {
		assertThat(EscopoDeLeitura.de(Papel.ADMIN)).isEqualTo(EscopoDeLeitura.TUDO);
		assertThat(EscopoDeLeitura.de(Papel.STUDENT)).isEqualTo(EscopoDeLeitura.SO_ATIVOS);
		assertThat(EscopoDeLeitura.TUDO.veInativos()).isTrue();
		assertThat(EscopoDeLeitura.SO_ATIVOS.veInativos()).isFalse();
	}

	@Test
	void deve_falhar_fechado_com_403_para_o_guardian() {
		assertThatThrownBy(() -> EscopoDeLeitura.de(Papel.GUARDIAN)).isInstanceOfSatisfying(AcessoNegadoException.class,
				e -> assertThat(e.getCodigo()).isEqualTo(CodigoErro.ACESSO_NEGADO));
	}

	@Test
	void deve_recusar_ativo_false_so_para_o_student_com_400_parametro_invalido() {
		assertThatThrownBy(() -> EscopoDeLeitura.SO_ATIVOS.validarFiltroAtivo(false))
				.isInstanceOfSatisfying(InvalidRequestException.class,
						e -> assertThat(e.getCodigo()).isEqualTo(CodigoErro.PARAMETRO_INVALIDO));
		assertThatCode(() -> EscopoDeLeitura.SO_ATIVOS.validarFiltroAtivo(true)).doesNotThrowAnyException();
		assertThatCode(() -> EscopoDeLeitura.SO_ATIVOS.validarFiltroAtivo(null)).doesNotThrowAnyException();
		assertThatCode(() -> EscopoDeLeitura.TUDO.validarFiltroAtivo(false)).doesNotThrowAnyException();
		assertThatCode(() -> EscopoDeLeitura.TUDO.validarFiltroAtivo(true)).doesNotThrowAnyException();
	}

	@Test
	void deve_deixar_so_o_admin_ver_as_provas_em_rascunho() {
		assertThat(EscopoDeLeitura.TUDO.veRascunhos()).isTrue();
		assertThat(EscopoDeLeitura.SO_ATIVOS.veRascunhos()).isFalse();
	}

	@Test
	void deve_recusar_o_filtro_status_do_student_com_qualquer_valor_com_400_parametro_invalido() {
		for (String valor : new String[] { "PUBLICADA", "RASCUNHO", "qualquer", "" }) {
			assertThatThrownBy(() -> EscopoDeLeitura.SO_ATIVOS.validarFiltroDeStatus(valor)).as(valor)
					.isInstanceOfSatisfying(InvalidRequestException.class,
							e -> assertThat(e.getCodigo()).isEqualTo(CodigoErro.PARAMETRO_INVALIDO));
			assertThatCode(() -> EscopoDeLeitura.TUDO.validarFiltroDeStatus(valor)).as(valor).doesNotThrowAnyException();
		}
		assertThatCode(() -> EscopoDeLeitura.SO_ATIVOS.validarFiltroDeStatus(null)).doesNotThrowAnyException();
	}

	@Test
	void deve_resolver_o_escopo_pelo_papel_do_principal() throws Exception {
		autenticarComo(new UsuarioAutenticado(UUID.randomUUID(), Papel.STUDENT));
		assertThat(resolvedor.resolveArgument(null, null, null, null)).isEqualTo(EscopoDeLeitura.SO_ATIVOS);

		autenticarComo(new UsuarioAutenticado(UUID.randomUUID(), Papel.ADMIN));
		assertThat(resolvedor.resolveArgument(null, null, null, null)).isEqualTo(EscopoDeLeitura.TUDO);
	}

	@Test
	void deve_falhar_fechado_sem_principal_com_principal_de_outro_tipo_ou_guardian() {
		assertThatThrownBy(() -> resolvedor.resolveArgument(null, null, null, null))
				.isInstanceOf(AcessoNegadoException.class);

		autenticarComo("um-texto-qualquer");
		assertThatThrownBy(() -> resolvedor.resolveArgument(null, null, null, null))
				.isInstanceOf(AcessoNegadoException.class);

		autenticarComo(new UsuarioAutenticado(UUID.randomUUID(), Papel.GUARDIAN));
		assertThatThrownBy(() -> resolvedor.resolveArgument(null, null, null, null))
				.isInstanceOf(AcessoNegadoException.class);
	}

}
