package br.simulado.etec.catalogo.controller;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/** Com o Swagger ligado (como SWAGGER_ENABLED=true): a documentação responde e a Matéria aparece. */
@SpringBootTest(properties = { "springdoc.api-docs.enabled=true", "springdoc.swagger-ui.enabled=true" })
@AutoConfigureMockMvc
class SwaggerLigadoTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void deve_responder_200_com_a_materia_quando_o_swagger_esta_ligado() throws Exception {
		mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
				.andExpect(jsonPath("$.paths['/api/v1/materias']").exists())
				.andExpect(jsonPath("$.paths['/api/v1/materias/{id}']").exists())
				.andExpect(jsonPath("$.paths['/api/v1/materias/{id}/disable']").exists())
				.andExpect(jsonPath("$.paths['/api/v1/materias/{id}/enable']").exists())
				.andExpect(jsonPath("$.components.schemas.MateriaResponse").exists());
	}

	@Test
	void deve_documentar_so_os_status_que_o_codigo_devolve_hoje() throws Exception {
		mockMvc.perform(get("/v3/api-docs"))
				.andExpect(jsonPath("$.paths['/api/v1/materias'].post.responses.201").exists())
				.andExpect(jsonPath("$.paths['/api/v1/materias'].post.responses.400").exists())
				.andExpect(jsonPath("$.paths['/api/v1/materias'].post.responses.409").exists())
				.andExpect(jsonPath("$.paths['/api/v1/materias/{id}'].delete.responses.204").exists())
				.andExpect(jsonPath("$.paths['/api/v1/materias/{id}'].get.responses.401").doesNotExist())
				.andExpect(jsonPath("$.paths['/api/v1/materias/{id}'].get.responses.403").doesNotExist());
	}

	@Test
	void deve_documentar_a_listagem_com_200_e_400_e_parametros_sem_example() throws Exception {
		String listagem = "$.paths['/api/v1/materias'].get";
		mockMvc.perform(get("/v3/api-docs"))
				.andExpect(jsonPath(listagem + ".responses.200").exists())
				.andExpect(jsonPath(listagem + ".responses.400").exists())
				.andExpect(jsonPath(listagem + ".responses.401").doesNotExist())
				.andExpect(jsonPath(listagem + ".responses.403").doesNotExist())
				.andExpect(jsonPath(listagem + ".parameters[?(@.name=='nome')].description").isNotEmpty())
				.andExpect(jsonPath(listagem + ".parameters[?(@.name=='ativo')].description").isNotEmpty())
				.andExpect(jsonPath(listagem + ".parameters[?(@.name=='page')].description").isNotEmpty())
				.andExpect(jsonPath(listagem + ".parameters[?(@.name=='size')].description").isNotEmpty())
				.andExpect(jsonPath(listagem + ".parameters[?(@.name=='sort')].description").isNotEmpty())
				// "Try it out" envia o valor de um example como filtro: nenhum parâmetro da listagem pode ter example
				.andExpect(jsonPath(listagem + ".parameters[?(@.example)]").isEmpty())
				.andExpect(jsonPath(listagem + ".parameters[?(@.schema.example)]").isEmpty())
				.andExpect(jsonPath("$.components.schemas.PaginaResponseMateriaResponse").exists());
	}

	@Test
	void deve_servir_a_interface_do_swagger_mesmo_com_add_mappings_false() throws Exception {
		mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk())
				.andExpect(content().string(containsString("swagger")));
	}

}
