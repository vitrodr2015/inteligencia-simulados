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

	/** O SomenteJsonConfig remove o conversor de YAML do Spring MVC; a rota .yaml do springdoc tem de continuar funcionando. */
	@Test
	void deve_responder_200_em_yaml_quando_pede_api_docs_yaml_com_o_somente_json_ativo() throws Exception {
		mockMvc.perform(get("/v3/api-docs.yaml")).andExpect(status().isOk())
				.andExpect(content().string(containsString("/api/v1/materias")))
				.andExpect(content().string(containsString("openapi:")));
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


	@Test
	void deve_documentar_as_rotas_do_subtopico_e_o_schema_de_saida() throws Exception {
		mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
				.andExpect(jsonPath("$.paths['/api/v1/subtopicos']").exists())
				.andExpect(jsonPath("$.paths['/api/v1/subtopicos/{id}']").exists())
				.andExpect(jsonPath("$.paths['/api/v1/subtopicos/{id}/disable']").exists())
				.andExpect(jsonPath("$.paths['/api/v1/subtopicos/{id}/enable']").exists())
				.andExpect(jsonPath("$.components.schemas.SubtopicoResponse").exists())
				.andExpect(jsonPath("$.components.schemas.SubtopicoResponse.properties.materiaId").exists())
				.andExpect(jsonPath("$.components.schemas.PaginaResponseSubtopicoResponse").exists());
	}

	@Test
	void deve_documentar_so_os_status_que_o_codigo_devolve_hoje_no_subtopico() throws Exception {
		mockMvc.perform(get("/v3/api-docs"))
				.andExpect(jsonPath("$.paths['/api/v1/subtopicos'].post.responses.201").exists())
				.andExpect(jsonPath("$.paths['/api/v1/subtopicos'].post.responses.400").exists())
				.andExpect(jsonPath("$.paths['/api/v1/subtopicos'].post.responses.409").exists())
				.andExpect(jsonPath("$.paths['/api/v1/subtopicos'].get.responses.200").exists())
				.andExpect(jsonPath("$.paths['/api/v1/subtopicos'].get.responses.400").exists())
				.andExpect(jsonPath("$.paths['/api/v1/subtopicos/{id}'].put.responses.409").exists())
				.andExpect(jsonPath("$.paths['/api/v1/subtopicos/{id}'].delete.responses.204").exists())
				.andExpect(jsonPath("$.paths['/api/v1/subtopicos/{id}/enable'].patch.responses.409").exists())
				.andExpect(jsonPath("$.paths['/api/v1/subtopicos/{id}'].get.responses.401").doesNotExist())
				.andExpect(jsonPath("$.paths['/api/v1/subtopicos/{id}'].get.responses.403").doesNotExist());
	}

	@Test
	void deve_documentar_os_filtros_e_o_sort_da_listagem_do_subtopico_sem_example() throws Exception {
		String listagem = "$.paths['/api/v1/subtopicos'].get";
		mockMvc.perform(get("/v3/api-docs"))
				.andExpect(jsonPath(listagem + ".parameters[?(@.name=='materiaId')].description").isNotEmpty())
				.andExpect(jsonPath(listagem + ".parameters[?(@.name=='nome')].description").isNotEmpty())
				.andExpect(jsonPath(listagem + ".parameters[?(@.name=='ativo')].description").isNotEmpty())
				.andExpect(jsonPath(listagem + ".parameters[?(@.name=='page')].description").isNotEmpty())
				.andExpect(jsonPath(listagem + ".parameters[?(@.name=='size')].description").isNotEmpty())
				.andExpect(jsonPath(listagem + ".parameters[?(@.name=='sort')].description").isNotEmpty())
				// a requisição servlet é só para ler o sort cru: não aparece como parâmetro
				.andExpect(jsonPath(listagem + ".parameters[?(@.name=='requisicao')]").isEmpty())
				.andExpect(jsonPath(listagem + ".parameters[?(@.example)]").isEmpty());
	}

	@Test
	void deve_documentar_o_409_de_materia_com_subtopicos_no_delete_da_materia() throws Exception {
		mockMvc.perform(get("/v3/api-docs"))
				.andExpect(jsonPath("$.paths['/api/v1/materias/{id}'].delete.responses.409").exists());
	}

	@Test
	void deve_manter_o_sort_da_listagem_de_materias_documentado_sem_expor_a_requisicao() throws Exception {
		String listagem = "$.paths['/api/v1/materias'].get";
		mockMvc.perform(get("/v3/api-docs"))
				.andExpect(jsonPath(listagem + ".parameters[?(@.name=='sort')].description").isNotEmpty())
				.andExpect(jsonPath(listagem + ".parameters[?(@.name=='requisicao')]").isEmpty());
	}

	@Test
	void deve_documentar_as_tres_rotas_da_prova_e_o_schema_de_saida_sem_o_programa() throws Exception {
		mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
				.andExpect(jsonPath("$.paths['/api/v1/admin/provas'].post.responses.201").exists())
				.andExpect(jsonPath("$.paths['/api/v1/admin/provas'].post.responses.400").exists())
				.andExpect(jsonPath("$.paths['/api/v1/admin/provas'].post.responses.409").exists())
				.andExpect(jsonPath("$.paths['/api/v1/provas'].get.responses.200").exists())
				.andExpect(jsonPath("$.paths['/api/v1/provas'].get.responses.400").exists())
				.andExpect(jsonPath("$.paths['/api/v1/provas/{id}'].get.responses.200").exists())
				.andExpect(jsonPath("$.paths['/api/v1/provas/{id}'].get.responses.400").exists())
				.andExpect(jsonPath("$.paths['/api/v1/provas/{id}'].get.responses.404").exists())
				.andExpect(jsonPath("$.paths['/api/v1/provas/{id}'].get.responses.401").doesNotExist())
				.andExpect(jsonPath("$.paths['/api/v1/provas/{id}'].get.responses.403").doesNotExist())
				.andExpect(jsonPath("$.components.schemas.ProvaResponse").exists())
				.andExpect(jsonPath("$.components.schemas.ProvaResponse.properties.programa").doesNotExist())
				.andExpect(jsonPath("$.components.schemas.ProvaResponse.properties.quantidadeQuestoes").exists())
				.andExpect(jsonPath("$.components.schemas.ProvaRequest").exists())
				.andExpect(jsonPath("$.components.schemas.PaginaResponseProvaResponse").exists());
	}

	@Test
	void deve_documentar_a_edicao_e_o_gabarito_em_lote_com_o_aviso_de_que_o_put_apaga_o_gabarito() throws Exception {
		String prova = "$.paths['/api/v1/admin/provas/{id}'].put";
		String questao = "$.paths['/api/v1/admin/questoes/{id}'].put";
		String gabarito = "$.paths['/api/v1/admin/provas/{id}/gabarito'].put";
		var documento = mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
		for (String operacao : new String[] { prova, questao, gabarito }) {
			for (String codigo : new String[] { "200", "400", "404", "409" }) {
				documento.andExpect(jsonPath(operacao + ".responses." + codigo).exists());
			}
			documento.andExpect(jsonPath(operacao + ".responses.401").doesNotExist())
					.andExpect(jsonPath(operacao + ".responses.403").doesNotExist())
					.andExpect(jsonPath(operacao + ".responses.201").doesNotExist());
		}
		documento
				.andExpect(jsonPath(questao + ".description")
						.value(org.hamcrest.Matchers.containsString("omite o gabarito APAGA o gabarito")))
				.andExpect(jsonPath("$.components.schemas.QuestaoAtualizacaoRequest.properties.gabarito.description")
						.value(org.hamcrest.Matchers.containsString("APAGA")))
				.andExpect(jsonPath("$.components.schemas.GabaritoRequest").exists())
				.andExpect(jsonPath("$.components.schemas.ItemDoGabaritoRequest").exists())
				.andExpect(jsonPath("$.components.schemas.GabaritoResponse.properties.atualizadas").exists());
	}

	@Test
	void deve_documentar_o_publicar_da_prova_sem_corpo_com_os_motivos_de_pendencia() throws Exception {
		String publicar = "$.paths['/api/v1/provas/{id}/publish'].patch";
		var documento = mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
		for (String codigo : new String[] { "200", "400", "404", "409" }) {
			documento.andExpect(jsonPath(publicar + ".responses." + codigo).exists());
		}
		documento.andExpect(jsonPath(publicar + ".responses.401").doesNotExist())
				.andExpect(jsonPath(publicar + ".requestBody").doesNotExist());
		for (br.simulado.etec.catalogo.service.QuestaoService.MotivoDePendencia motivo : br.simulado.etec.catalogo.service.QuestaoService.MotivoDePendencia
				.values()) {
			documento.andExpect(jsonPath(publicar + ".description")
					.value(org.hamcrest.Matchers.containsString(motivo.name())));
		}
	}

	@Test
	void deve_documentar_os_filtros_e_o_sort_da_listagem_de_provas_sem_example() throws Exception {
		String listagem = "$.paths['/api/v1/provas'].get";
		mockMvc.perform(get("/v3/api-docs"))
				.andExpect(jsonPath(listagem + ".parameters[?(@.name=='ano')].description").isNotEmpty())
				.andExpect(jsonPath(listagem + ".parameters[?(@.name=='semestre')].description").isNotEmpty())
				.andExpect(jsonPath(listagem + ".parameters[?(@.name=='status')].description").isNotEmpty())
				.andExpect(jsonPath(listagem + ".parameters[?(@.name=='page')].description").isNotEmpty())
				.andExpect(jsonPath(listagem + ".parameters[?(@.name=='size')].description").isNotEmpty())
				.andExpect(jsonPath(listagem + ".parameters[?(@.name=='sort')].description").isNotEmpty())
				.andExpect(jsonPath(listagem + ".parameters[?(@.name=='requisicao')]").isEmpty())
				.andExpect(jsonPath(listagem + ".parameters[?(@.example)]").isEmpty());
	}

	@Test
	void deve_documentar_as_duas_rotas_da_questao_e_o_schema_de_saida_sem_programa_nem_tipo() throws Exception {
		mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
				.andExpect(jsonPath("$.paths['/api/v1/admin/provas/{id}/questoes'].post.responses.201").exists())
				.andExpect(jsonPath("$.paths['/api/v1/admin/provas/{id}/questoes'].post.responses.200").exists())
				.andExpect(jsonPath("$.paths['/api/v1/admin/provas/{id}/questoes'].post.responses.400").exists())
				.andExpect(jsonPath("$.paths['/api/v1/admin/provas/{id}/questoes'].post.responses.404").exists())
				.andExpect(jsonPath("$.paths['/api/v1/admin/provas/{id}/questoes'].post.responses.409").exists())
				.andExpect(jsonPath("$.paths['/api/v1/admin/questoes/{id}'].get.responses.200").exists())
				.andExpect(jsonPath("$.paths['/api/v1/admin/questoes/{id}'].get.responses.400").exists())
				.andExpect(jsonPath("$.paths['/api/v1/admin/questoes/{id}'].get.responses.404").exists())
				.andExpect(jsonPath("$.paths['/api/v1/admin/questoes/{id}'].get.parameters[?(@.name=='versao')].description").isNotEmpty())
				.andExpect(jsonPath("$.paths['/api/v1/admin/questoes/{id}'].get.responses.401").doesNotExist())
				.andExpect(jsonPath("$.paths['/api/v1/admin/questoes/{id}'].get.responses.403").doesNotExist())
				.andExpect(jsonPath("$.components.schemas.QuestaoResponse").exists())
				.andExpect(jsonPath("$.components.schemas.QuestaoResponse.properties.programa").doesNotExist())
				.andExpect(jsonPath("$.components.schemas.QuestaoResponse.properties.tipo").doesNotExist())
				.andExpect(jsonPath("$.components.schemas.QuestaoResponse.properties.alternativas").exists())
				.andExpect(jsonPath("$.components.schemas.QuestaoRequest.properties.programa").doesNotExist())
				.andExpect(jsonPath("$.components.schemas.QuestaoRequest.properties.tipo").doesNotExist())
				.andExpect(jsonPath("$.components.schemas.AlternativaRequest").exists());
	}

	@Test
	void deve_documentar_a_listagem_de_questoes_com_filtros_sort_e_sem_example() throws Exception {
		String listagem = "$.paths['/api/v1/admin/questoes'].get";
		mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
				.andExpect(jsonPath(listagem + ".responses.200").exists())
				.andExpect(jsonPath(listagem + ".responses.400").exists())
				.andExpect(jsonPath(listagem + ".responses.404").doesNotExist())
				.andExpect(jsonPath(listagem + ".responses.401").doesNotExist())
				.andExpect(jsonPath(listagem + ".responses.403").doesNotExist())
				.andExpect(jsonPath(listagem + ".parameters[?(@.name=='provaId')].description").isNotEmpty())
				.andExpect(jsonPath(listagem + ".parameters[?(@.name=='subtopicoId')].description").isNotEmpty())
				.andExpect(jsonPath(listagem + ".parameters[?(@.name=='status')].description").isNotEmpty())
				.andExpect(jsonPath(listagem + ".parameters[?(@.name=='page')].description").isNotEmpty())
				.andExpect(jsonPath(listagem + ".parameters[?(@.name=='size')].description").isNotEmpty())
				.andExpect(jsonPath(listagem + ".parameters[?(@.name=='sort')].description").isNotEmpty())
				.andExpect(jsonPath(listagem + ".parameters[?(@.name=='requisicao')]").isEmpty())
				.andExpect(jsonPath(listagem + ".parameters[?(@.example)]").isEmpty())
				.andExpect(jsonPath("$.components.schemas.PaginaResponseQuestaoResponse").exists())
				.andExpect(jsonPath("$.components.schemas.ProvaResponse.properties.questoesCadastradas.description").isNotEmpty());
	}

	@Test
	void deve_documentar_a_importacao_com_200_400_409_413_e_os_schemas() throws Exception {
		String importacao = "$.paths['/api/v1/admin/importacoes'].post";
		mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
				.andExpect(jsonPath(importacao + ".responses.200").exists())
				.andExpect(jsonPath(importacao + ".responses.400").exists())
				.andExpect(jsonPath(importacao + ".responses.409").exists())
				.andExpect(jsonPath(importacao + ".responses.413").exists())
				.andExpect(jsonPath(importacao + ".responses.201").doesNotExist())
				.andExpect(jsonPath(importacao + ".responses.401").doesNotExist())
				.andExpect(jsonPath(importacao + ".responses.403").doesNotExist())
				.andExpect(jsonPath("$.components.schemas.ImportacaoRequest.properties.provaId").exists())
				.andExpect(jsonPath("$.components.schemas.ImportacaoRequest.properties.questoes.maxItems").value(100))
				.andExpect(jsonPath("$.components.schemas.ImportacaoResponse.properties.questoesIgnoradas").exists());
	}

	@Test
	void deve_documentar_o_409_de_subtopico_com_questoes_no_delete_do_subtopico() throws Exception {
		mockMvc.perform(get("/v3/api-docs"))
				.andExpect(jsonPath("$.paths['/api/v1/subtopicos/{id}'].delete.responses.409").exists());
	}

	@Test
	void deve_documentar_a_nova_versao_e_a_publicacao_avulsa_sem_corpo_com_os_codigos_de_recusa() throws Exception {
		String versoes = "$.paths['/api/v1/admin/questoes/{id}/versoes'].post";
		String publicar = "$.paths['/api/v1/admin/questoes/{id}/publish'].patch";
		var documento = mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
		for (String codigo : new String[] { "201", "400", "404", "409" }) {
			documento.andExpect(jsonPath(versoes + ".responses." + codigo).exists());
		}
		for (String codigo : new String[] { "200", "400", "404", "409" }) {
			documento.andExpect(jsonPath(publicar + ".responses." + codigo).exists());
		}
		for (String rota : new String[] { versoes, publicar }) {
			documento.andExpect(jsonPath(rota + ".responses.401").doesNotExist())
					.andExpect(jsonPath(rota + ".requestBody").doesNotExist());
		}
		documento.andExpect(jsonPath(versoes + ".responses.200").doesNotExist())
				.andExpect(jsonPath(versoes + ".responses.409.description")
						.value(org.hamcrest.Matchers.containsString("QUESTAO_COM_RASCUNHO")));
		for (String codigo : new String[] { "PROVA_NAO_PUBLICADA", "QUESTAO_SEM_RASCUNHO", "QUESTAO_COM_PENDENCIAS" }) {
			documento.andExpect(jsonPath(publicar + ".responses.409.description")
					.value(org.hamcrest.Matchers.containsString(codigo)));
		}
	}

	@Test
	void deve_documentar_os_dois_deletes_com_204_400_404_409_e_os_codigos_de_recusa() throws Exception {
		String questao = "$.paths['/api/v1/admin/questoes/{id}'].delete";
		String prova = "$.paths['/api/v1/admin/provas/{id}'].delete";
		var documento = mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
		for (String rota : new String[] { questao, prova }) {
			for (String codigo : new String[] { "204", "400", "404", "409" }) {
				documento.andExpect(jsonPath(rota + ".responses." + codigo).exists());
			}
			documento.andExpect(jsonPath(rota + ".responses.200").doesNotExist())
					.andExpect(jsonPath(rota + ".responses.401").doesNotExist())
					.andExpect(jsonPath(rota + ".requestBody").doesNotExist());
		}
		for (String codigo : new String[] { "QUESTAO_SEM_RASCUNHO", "PROVA_NAO_EDITAVEL" }) {
			documento.andExpect(jsonPath(questao + ".responses.409.description")
					.value(org.hamcrest.Matchers.containsString(codigo)));
		}
		for (String codigo : new String[] { "PROVA_NAO_EDITAVEL", "PROVA_COM_QUESTOES_PUBLICADAS" }) {
			documento.andExpect(jsonPath(prova + ".responses.409.description")
					.value(org.hamcrest.Matchers.containsString(codigo)));
		}
	}

}
