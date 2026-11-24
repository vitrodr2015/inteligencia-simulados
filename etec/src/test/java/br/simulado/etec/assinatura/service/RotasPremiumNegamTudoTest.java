package br.simulado.etec.assinatura.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import br.simulado.etec.assinatura.model.Decisao;
import br.simulado.etec.assinatura.model.MotivoDaNegacao;
import br.simulado.etec.shared.security.ComPrincipal;
import br.simulado.etec.usuarios.model.Papel;

/**
 * A prova de que o chamador NÃO CONSEGUE esquecer a política (D140): a política é trocada por uma que NEGA TUDO e TODA rota
 * do registro {@link RotasPremium} tem de responder 403 {@code ENTITLEMENT_NEGADO}. Rota que esquece a consulta responde 200 e
 * o teste falha. A mesma rota, com a mesma classe, é liberada pela política real (ver {@link RotaPremiumComStubTest}): trocar a
 * política não muda nenhum chamador.
 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@Import({ ControllerPremiumDeTeste.class, RotasPremiumNegamTudoTest.NegaTudo.class })
@ComPrincipal(Papel.STUDENT)
class RotasPremiumNegamTudoTest {

	/** A política que nega tudo, trocada SÓ neste contexto de teste. */
	@TestConfiguration
	static class NegaTudo {

		@Bean
		@Primary
		PoliticaDeEntitlements politicaQueNegaTudo() {
			return (quem, recurso, programa) -> Decisao.negada(MotivoDaNegacao.RECURSO_FORA_DA_LISTA);
		}

	}

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private RequestMappingHandlerMapping mapeamentos;

	static List<RotasPremium.Rota> rotas() {
		return RotasPremium.TODAS;
	}

	@ParameterizedTest
	@MethodSource("rotas")
	void deve_dar_403_entitlement_negado_em_toda_rota_premium_registrada_com_a_politica_que_nega_tudo(RotasPremium.Rota rota)
			throws Exception {
		mockMvc.perform(request(HttpMethod.valueOf(rota.metodo()), rota.caminho()).contentType(MediaType.APPLICATION_JSON)
				.content(rota.corpo()))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.codigo").value("ENTITLEMENT_NEGADO"))
				.andExpect(jsonPath("$.mensagem").value("Este recurso não está disponível no seu plano."))
				.andExpect(jsonPath("$.detalhes.recurso").value("INICIAR_TENTATIVA"))
				.andExpect(jsonPath("$.detalhes.programa").value("ETEC"));
	}

	@Test
	void deve_ter_o_registro_nao_vazio_e_so_com_rotas_que_existem_de_verdade() {
		assertThat(RotasPremium.TODAS).isNotEmpty();
		List<String> mapeadas = mapeamentos.getHandlerMethods().keySet().stream().map(RequestMappingInfo::toString).toList();
		for (RotasPremium.Rota rota : RotasPremium.TODAS) {
			assertThat(mapeadas).as("a rota %s do registro precisa estar mapeada", rota)
					.anyMatch(m -> m.contains(rota.caminho()) && m.contains(rota.metodo()));
		}
	}

}
