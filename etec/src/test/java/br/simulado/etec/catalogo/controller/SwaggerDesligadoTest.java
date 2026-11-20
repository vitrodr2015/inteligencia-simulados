package br.simulado.etec.catalogo.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/** Padrão (sem SWAGGER_ENABLED): a documentação não existe e o 404 sai no formato padrão de erro. */
@SpringBootTest
@AutoConfigureMockMvc
class SwaggerDesligadoTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void deve_responder_404_quando_o_swagger_esta_desligado() throws Exception {
		mockMvc.perform(get("/v3/api-docs")).andExpect(status().isNotFound())
				.andExpect(jsonPath("$.codigo").value("RECURSO_NAO_ENCONTRADO"));
	}

	@Test
	void deve_responder_404_na_interface_quando_o_swagger_esta_desligado() throws Exception {
		mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isNotFound());
	}

}
