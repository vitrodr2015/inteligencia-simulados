package br.simulado.etec.catalogo.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import br.simulado.etec.shared.security.JwtTokenProvider;
import br.simulado.etec.shared.security.JwtTokenProvider.TipoToken;

/**
 * Padrão (sem SWAGGER_ENABLED): a documentação não é aberta a ninguém. Com os filtros de segurança LIGADOS, o anônimo
 * cai no "negar por padrão" (401) e quem está autenticado recebe o 404 no formato padrão de erro: nenhum dos dois vê a
 * documentação.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SwaggerDesligadoTest {

	/** Usuário de exemplo do data.sql de teste (H2). */
	private static final UUID USUARIO_DE_EXEMPLO = UUID.fromString("11111111-1111-1111-1111-111111111111");

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JwtTokenProvider provedorDeTokens;

	private String autorizacao() {
		return "Bearer " + provedorDeTokens.emitir(TipoToken.ACCESS, USUARIO_DE_EXEMPLO, "STUDENT").valor();
	}

	@Test
	void deve_responder_401_ao_anonimo_quando_o_swagger_esta_desligado() throws Exception {
		mockMvc.perform(get("/v3/api-docs")).andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.codigo").value("NAO_AUTENTICADO"));
		mockMvc.perform(get("/v3/api-docs.yaml")).andExpect(status().isUnauthorized());
	}

	@Test
	void deve_responder_401_ao_anonimo_na_interface_quando_o_swagger_esta_desligado() throws Exception {
		mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/swagger-ui.html")).andExpect(status().isUnauthorized());
	}

	@Test
	void deve_responder_404_a_quem_esta_autenticado_quando_o_swagger_esta_desligado() throws Exception {
		mockMvc.perform(get("/v3/api-docs").header("Authorization", autorizacao())).andExpect(status().isNotFound())
				.andExpect(jsonPath("$.codigo").value("RECURSO_NAO_ENCONTRADO"));
		mockMvc.perform(get("/swagger-ui/index.html").header("Authorization", autorizacao()))
				.andExpect(status().isNotFound());
	}

}
