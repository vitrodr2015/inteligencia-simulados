package br.simulado.etec.catalogo.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import br.simulado.etec.catalogo.model.Materia;
import br.simulado.etec.catalogo.model.Subtopico;
import br.simulado.etec.catalogo.model.ProgramaDeProva;
import br.simulado.etec.catalogo.repository.MateriaRepository;
import br.simulado.etec.catalogo.repository.ProgramaRepository;
import br.simulado.etec.catalogo.repository.SubtopicoRepository;
import br.simulado.etec.shared.security.ComPrincipal;
import br.simulado.etec.usuarios.model.Papel;

// SEM filtros de segurança (addFilters = false): este teste cobre a VISIBILIDADE por papel (o escopo de leitura chega
// do principal posto por @ComPrincipal) e NÃO testa a cadeia de segurança, que é provada nos testes de integração.
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
class VisibilidadePorPapelControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private MateriaRepository materias;

	@Autowired
	private ProgramaRepository programas;

	@Autowired
	private SubtopicoRepository subtopicos;

	private Materia ativa;

	private Materia inativa;

	private Subtopico subAtivoEmMateriaAtiva;

	private Subtopico subInativoEmMateriaAtiva;

	private Subtopico subAtivoEmMateriaInativa;

	@BeforeEach
	void montar() {
		limpar();
		ProgramaDeProva etec = programas.findByCodigo("ETEC").orElseThrow();
		ativa = materias.saveAndFlush(new Materia(etec, "Ativa", null));
		inativa = materias.saveAndFlush(new Materia(etec, "Inativa", null));
		subAtivoEmMateriaAtiva = subtopicos.saveAndFlush(new Subtopico(ativa, "Sub ativo"));
		subInativoEmMateriaAtiva = new Subtopico(ativa, "Sub inativo");
		subInativoEmMateriaAtiva.desativar();
		subInativoEmMateriaAtiva = subtopicos.saveAndFlush(subInativoEmMateriaAtiva);
		subAtivoEmMateriaInativa = subtopicos.saveAndFlush(new Subtopico(inativa, "Sub de materia inativa"));
		inativa.desativar();
		inativa = materias.saveAndFlush(inativa);
	}

	@AfterEach
	void limpar() {
		subtopicos.deleteAll();
		materias.deleteAll();
	}

	// ----- matérias -----

	@Test
	@ComPrincipal(Papel.STUDENT)
	void deve_listar_so_as_materias_ativas_para_o_student() throws Exception {
		mockMvc.perform(get("/api/v1/materias")).andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElementos").value(1)).andExpect(jsonPath("$.conteudo[0].nome").value("Ativa"));
	}

	@Test
	@ComPrincipal(Papel.ADMIN)
	void deve_listar_todas_as_materias_para_o_admin() throws Exception {
		mockMvc.perform(get("/api/v1/materias")).andExpect(status().isOk()).andExpect(jsonPath("$.totalElementos").value(2));
		mockMvc.perform(get("/api/v1/materias").param("ativo", "false")).andExpect(jsonPath("$.totalElementos").value(1))
				.andExpect(jsonPath("$.conteudo[0].nome").value("Inativa"));
	}

	@Test
	@ComPrincipal(Papel.STUDENT)
	void deve_dar_400_parametro_invalido_quando_o_student_envia_ativo_false_e_aceitar_ativo_true() throws Exception {
		mockMvc.perform(get("/api/v1/materias").param("ativo", "false")).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("PARAMETRO_INVALIDO"));
		mockMvc.perform(get("/api/v1/subtopicos").param("ativo", "FALSE")).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("PARAMETRO_INVALIDO"));
		mockMvc.perform(get("/api/v1/materias").param("ativo", "true")).andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElementos").value(1));
	}

	@Test
	@ComPrincipal(Papel.STUDENT)
	void deve_dar_o_mesmo_404_para_materia_inativa_e_inexistente_quando_o_chamador_e_student() throws Exception {
		String inativaCorpo = mockMvc.perform(get("/api/v1/materias/" + inativa.getPublicId()))
				.andExpect(status().isNotFound()).andExpect(jsonPath("$.codigo").value("MATERIA_NAO_ENCONTRADA"))
				.andReturn().getResponse().getContentAsString();
		String inexistenteCorpo = mockMvc.perform(get("/api/v1/materias/" + UUID.randomUUID()))
				.andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();

		assertThat(semDataHora(inativaCorpo)).isEqualTo(semDataHora(inexistenteCorpo));
		mockMvc.perform(get("/api/v1/materias/" + ativa.getPublicId())).andExpect(status().isOk());
	}

	@Test
	@ComPrincipal(Papel.ADMIN)
	void deve_devolver_a_materia_inativa_ao_admin() throws Exception {
		mockMvc.perform(get("/api/v1/materias/" + inativa.getPublicId())).andExpect(status().isOk())
				.andExpect(jsonPath("$.ativo").value(false));
	}

	// ----- subtópicos -----

	@Test
	@ComPrincipal(Papel.STUDENT)
	void deve_listar_so_subtopicos_ativos_de_materia_ativa_para_o_student() throws Exception {
		mockMvc.perform(get("/api/v1/subtopicos")).andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElementos").value(1)).andExpect(jsonPath("$.conteudo[0].nome").value("Sub ativo"));
	}

	@Test
	@ComPrincipal(Papel.ADMIN)
	void deve_listar_todos_os_subtopicos_para_o_admin() throws Exception {
		mockMvc.perform(get("/api/v1/subtopicos")).andExpect(jsonPath("$.totalElementos").value(3));
	}

	@Test
	@ComPrincipal(Papel.STUDENT)
	void deve_devolver_lista_vazia_quando_o_student_filtra_por_materia_inativa() throws Exception {
		mockMvc.perform(get("/api/v1/subtopicos").param("materiaId", inativa.getPublicId().toString()))
				.andExpect(status().isOk()).andExpect(jsonPath("$.totalElementos").value(0))
				.andExpect(jsonPath("$.conteudo.length()").value(0));
	}

	@Test
	@ComPrincipal(Papel.ADMIN)
	void deve_devolver_os_subtopicos_da_materia_inativa_ao_admin() throws Exception {
		mockMvc.perform(get("/api/v1/subtopicos").param("materiaId", inativa.getPublicId().toString()))
				.andExpect(jsonPath("$.totalElementos").value(1));
	}

	@Test
	@ComPrincipal(Papel.STUDENT)
	void deve_dar_o_mesmo_404_para_subtopico_escondido_e_inexistente_quando_o_chamador_e_student() throws Exception {
		String inexistente = mockMvc.perform(get("/api/v1/subtopicos/" + UUID.randomUUID()))
				.andExpect(status().isNotFound()).andExpect(jsonPath("$.codigo").value("SUBTOPICO_NAO_ENCONTRADO"))
				.andReturn().getResponse().getContentAsString();

		for (Subtopico escondido : new Subtopico[] { subInativoEmMateriaAtiva, subAtivoEmMateriaInativa }) {
			String corpo = mockMvc.perform(get("/api/v1/subtopicos/" + escondido.getPublicId()))
					.andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
			assertThat(semDataHora(corpo)).isEqualTo(semDataHora(inexistente));
		}
		mockMvc.perform(get("/api/v1/subtopicos/" + subAtivoEmMateriaAtiva.getPublicId())).andExpect(status().isOk());
	}

	@Test
	@ComPrincipal(Papel.ADMIN)
	void deve_devolver_qualquer_subtopico_ao_admin() throws Exception {
		for (Subtopico s : new Subtopico[] { subAtivoEmMateriaAtiva, subInativoEmMateriaAtiva, subAtivoEmMateriaInativa }) {
			mockMvc.perform(get("/api/v1/subtopicos/" + s.getPublicId())).andExpect(status().isOk());
		}
	}

	// ----- falha fechada -----

	@Test
	@ComPrincipal(Papel.GUARDIAN)
	void deve_dar_403_acesso_negado_quando_o_principal_e_guardian() throws Exception {
		mockMvc.perform(get("/api/v1/materias")).andExpect(status().isForbidden())
				.andExpect(jsonPath("$.codigo").value("ACESSO_NEGADO"));
		mockMvc.perform(get("/api/v1/subtopicos/" + subAtivoEmMateriaAtiva.getPublicId()))
				.andExpect(status().isForbidden());
	}

	@Test
	void deve_dar_403_acesso_negado_quando_nao_ha_principal_nunca_assumindo_ver_tudo() throws Exception {
		mockMvc.perform(get("/api/v1/materias")).andExpect(status().isForbidden())
				.andExpect(jsonPath("$.codigo").value("ACESSO_NEGADO"));
		mockMvc.perform(get("/api/v1/materias/" + inativa.getPublicId())).andExpect(status().isForbidden());
	}

	private static String semDataHora(String json) {
		return json.replaceAll("\"dataHora\":\"[^\"]+\",?", "");
	}

}
