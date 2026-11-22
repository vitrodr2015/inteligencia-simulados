package br.simulado.etec.shared.paginacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import br.simulado.etec.shared.exception.CodigoErro;
import br.simulado.etec.shared.exception.InvalidRequestException;

/** Validação dos parâmetros de paginação, sem Spring: nada aqui depende do contexto nem do banco. */
class ConversorDePaginacaoTest {

	private static final CamposOrdenaveis CAMPOS = CamposOrdenaveis.de(Sort.by(Sort.Order.asc("nome")), "nome",
			"criadoEm");

	private static Pageable converter(String page, String size, String sort) {
		return ConversorDePaginacao.converter(page, size, sort, CAMPOS);
	}

	private static List<String> ordens(Pageable pageable) {
		return pageable.getSort().stream().map(ordem -> ordem.getProperty() + " " + ordem.getDirection()).toList();
	}

	// ----- padrões e constantes (uma só fonte da verdade) -----

	@Test
	void deve_usar_pagina_0_tamanho_20_e_nome_crescente_quando_nenhum_parametro_e_informado() {
		Pageable pageable = converter(null, null, null);

		assertThat(pageable.getPageNumber()).isZero();
		assertThat(pageable.getPageSize()).isEqualTo(20);
		assertThat(ordens(pageable)).containsExactly("nome ASC", "id ASC");
	}

	@Test
	void deve_ter_tamanho_padrao_20_e_maximo_50_nas_constantes() {
		assertThat(ConversorDePaginacao.TAMANHO_PADRAO).isEqualTo(20);
		assertThat(ConversorDePaginacao.TAMANHO_MAXIMO).isEqualTo(50);
	}

	// ----- size -----

	@ParameterizedTest
	@ValueSource(strings = { "51", "100", "100000", "2147483647", "99999999999999999999999999" })
	void deve_reduzir_ao_maximo_sem_erro_quando_o_size_passa_de_50(String size) {
		assertThat(converter(null, size, null).getPageSize()).isEqualTo(50);
	}

	@ParameterizedTest
	@ValueSource(strings = { "1", "7", "007", "50" })
	void deve_aceitar_o_size_quando_esta_entre_1_e_50(String size) {
		assertThat(converter(null, size, null).getPageSize()).isEqualTo(Integer.parseInt(size));
	}

	@ParameterizedTest
	@ValueSource(strings = { "-1", "0", "00", "abc", "1.5", "", " ", " 7", "7 ", "+7", "1e3", "0x10", "٧" })
	void deve_recusar_com_parametro_invalido_quando_o_size_e_zero_negativo_ou_nao_numerico(String size) {
		assertThatThrownBy(() -> converter(null, size, null))
				.isInstanceOfSatisfying(InvalidRequestException.class,
						erro -> assertThat(erro.getCodigo()).isEqualTo(CodigoErro.PARAMETRO_INVALIDO))
				.hasMessageContaining("'size'");
	}

	// ----- page -----

	@ParameterizedTest
	@ValueSource(strings = { "0", "1", "3", "000", "1000" })
	void deve_aceitar_o_page_quando_e_inteiro_a_partir_de_0(String page) {
		assertThat(converter(page, null, null).getPageNumber()).isEqualTo(Integer.parseInt(page));
	}

	@ParameterizedTest
	@ValueSource(strings = { "2147483647", "2147483648", "99999999999999999999999999" })
	void deve_limitar_o_page_gigante_para_o_deslocamento_caber_num_inteiro_sem_erro(String page) {
		for (String size : new String[] { null, "1", "7", "50", "100000" }) {
			Pageable pageable = converter(page, size, null);

			assertThat(pageable.getOffset()).as("deslocamento com size=%s", size).isLessThanOrEqualTo(Integer.MAX_VALUE);
			assertThat(pageable.getPageNumber()).isEqualTo(Integer.MAX_VALUE / pageable.getPageSize());
		}
	}

	@ParameterizedTest
	@ValueSource(strings = { "-1", "abc", "1.5", "", " ", " 1", "1 ", "+1", "1e3", "0x10", "١" })
	void deve_recusar_com_parametro_invalido_quando_o_page_e_negativo_ou_nao_numerico(String page) {
		assertThatThrownBy(() -> converter(page, null, null))
				.isInstanceOfSatisfying(InvalidRequestException.class,
						erro -> assertThat(erro.getCodigo()).isEqualTo(CodigoErro.PARAMETRO_INVALIDO))
				.hasMessageContaining("'page'");
	}

	// ----- sort -----

	@Test
	void deve_converter_o_sort_e_acrescentar_o_desempate_por_id_quando_o_sort_e_valido() {
		assertThat(ordens(converter(null, null, "nome,desc"))).containsExactly("nome DESC", "id ASC");
		assertThat(ordens(converter(null, null, "nome,asc"))).containsExactly("nome ASC", "id ASC");
		assertThat(ordens(converter(null, null, "criadoEm,desc"))).containsExactly("criadoEm DESC", "id ASC");
		assertThat(ordens(converter(null, null, "criadoEm,asc"))).containsExactly("criadoEm ASC", "id ASC");
	}

	@Test
	void deve_assumir_crescente_e_aceitar_a_direcao_em_qualquer_caixa() {
		assertThat(ordens(converter(null, null, "criadoEm"))).containsExactly("criadoEm ASC", "id ASC");
		assertThat(ordens(converter(null, null, "nome,DESC"))).containsExactly("nome DESC", "id ASC");
	}

	@Test
	void deve_terminar_sempre_com_o_desempate_por_id_crescente() {
		for (String sort : new String[] { null, "nome,asc", "nome,desc", "criadoEm,asc", "criadoEm,desc" }) {
			List<String> ordens = ordens(converter(null, null, sort));
			assertThat(ordens).as("ordenação para sort=%s", sort).hasSize(2).last().isEqualTo("id ASC");
		}
	}

	@ParameterizedTest
	@ValueSource(strings = { "nome;drop table x", "nome,asc;drop table materia", "nome; drop table x --",
			"id", "ativo", "xyz", "senha", "publicId", "nome,up", "nome,asc,desc", ",asc", "nome,", ",", "", " ",
			"criadoem", "NOME", "nome asc", "nome, asc", "nome,asc;", "(select 1)", "nome,asc) union select 1",
			"nome%2Cdesc", "../etc/passwd", "nome.criadoEm", "nome,desc,nome", "nome'--", "nome\"" })
	void deve_recusar_com_ordenacao_invalida_quando_o_sort_e_malformado_ou_fora_da_lista(String sort) {
		assertThatThrownBy(() -> converter(null, null, sort))
				.isInstanceOfSatisfying(InvalidRequestException.class,
						erro -> assertThat(erro.getCodigo()).isEqualTo(CodigoErro.ORDENACAO_INVALIDA))
				.hasMessageContaining("nome").hasMessageContaining("criadoEm");
	}

	@Test
	void deve_nao_repetir_o_valor_recebido_na_mensagem_quando_o_sort_e_invalido() {
		assertThatThrownBy(() -> converter(null, null, "nome;drop table x"))
				.hasMessageNotContaining("drop").hasMessageNotContaining("table");
	}


	// ----- converterRequisicao: valores crus do sort (D101) -----

	private static Pageable converterRequisicao(String... sorts) {
		return ConversorDePaginacao.converterRequisicao(null, null, sorts, CAMPOS);
	}

	@Test
	void deve_usar_o_sort_padrao_quando_a_requisicao_nao_traz_o_parametro_sort() {
		assertThat(ordens(ConversorDePaginacao.converterRequisicao(null, null, null, CAMPOS)))
				.containsExactly("nome ASC", "id ASC");
		assertThat(ordens(converterRequisicao())).containsExactly("nome ASC", "id ASC");
	}

	@Test
	void deve_aceitar_um_unico_sort_cru_com_campo_e_direcao() {
		assertThat(ordens(converterRequisicao("criadoEm,DESC"))).containsExactly("criadoEm DESC", "id ASC");
	}

	@ParameterizedTest
	@ValueSource(strings = { "nome|criadoEm", "nome,asc|criadoEm,desc", "nome|nome", "nome|desc", "senha|nome", "nome|" })
	void deve_recusar_com_ordenacao_invalida_quando_a_requisicao_traz_mais_de_um_sort(String valores) {
		String[] sorts = valores.split("\\|", -1);

		assertThat(sorts.length).isGreaterThan(1);
		assertThatThrownBy(() -> converterRequisicao(sorts)).isInstanceOfSatisfying(InvalidRequestException.class,
				erro -> assertThat(erro.getCodigo()).isEqualTo(CodigoErro.ORDENACAO_INVALIDA));
	}

	@Test
	void deve_diferenciar_do_texto_juntado_que_seria_valido() {
		// O mesmo "nome,desc" é válido quando vem num só parâmetro e inválido quando vem em dois (nome e desc).
		assertThat(ordens(converterRequisicao("nome,desc"))).containsExactly("nome DESC", "id ASC");
		assertThatThrownBy(() -> converterRequisicao("nome", "desc")).isInstanceOf(InvalidRequestException.class);
	}

}
