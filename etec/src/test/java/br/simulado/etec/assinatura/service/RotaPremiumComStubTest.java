package br.simulado.etec.assinatura.service;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import br.simulado.etec.shared.security.ComPrincipal;
import br.simulado.etec.usuarios.model.Papel;

/** A mesma rota premium de teste, agora com a política REAL do contexto (o stub): STUDENT e ADMIN passam, GUARDIAN não (D140, D122). */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@Import(ControllerPremiumDeTeste.class)
class RotaPremiumComStubTest {

	private static final String ROTA = "/api/v1/teste/premium/consultando";

	@Autowired
	private MockMvc mockMvc;

	@Test
	@ComPrincipal(Papel.STUDENT)
	void deve_liberar_o_student_no_programa_etec() throws Exception {
		mockMvc.perform(post(ROTA).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isOk())
				.andExpect(jsonPath("$.resultado").value("liberado"));
	}

	@Test
	@ComPrincipal(Papel.ADMIN)
	void deve_liberar_o_admin() throws Exception {
		mockMvc.perform(post(ROTA).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isOk());
	}

	@Test
	@ComPrincipal(Papel.GUARDIAN)
	void deve_negar_o_guardian_com_403_e_a_mesma_mensagem() throws Exception {
		mockMvc.perform(post(ROTA).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden())
				.andExpect(jsonPath("$.codigo").value("ENTITLEMENT_NEGADO"))
				.andExpect(jsonPath("$.mensagem").value("Este recurso não está disponível no seu plano."))
				.andExpect(jsonPath("$.detalhes.recurso").value("INICIAR_TENTATIVA"));
	}

}
