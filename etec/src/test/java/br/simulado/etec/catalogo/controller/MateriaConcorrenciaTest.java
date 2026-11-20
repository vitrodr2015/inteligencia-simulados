package br.simulado.etec.catalogo.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import br.simulado.etec.catalogo.model.Materia;
import br.simulado.etec.catalogo.repository.MateriaRepository;

/**
 * Vários DELETE simultâneos do mesmo recurso: exatamente um 204 e todos os demais 404 (nunca 500).
 * Sem @Transactional no teste: os dados precisam estar confirmados no banco para as threads
 * enxergarem. Com H2; a prova contra MySQL real é do Módulo 7.
 */
@SpringBootTest
@AutoConfigureMockMvc
class MateriaConcorrenciaTest {

	private static final int PEDIDOS_SIMULTANEOS = 12;

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private MateriaRepository repositorio;

	@AfterEach
	void limpar() {
		repositorio.deleteAll();
	}

	@RepeatedTest(5)
	void deve_ter_exatamente_um_204_e_os_demais_404_quando_varios_delete_chegam_ao_mesmo_tempo() throws Exception {
		UUID id = repositorio.saveAndFlush(new Materia("Matéria concorrente", null)).getPublicId();
		ExecutorService pool = Executors.newFixedThreadPool(PEDIDOS_SIMULTANEOS);
		CountDownLatch todosProntos = new CountDownLatch(PEDIDOS_SIMULTANEOS);
		CountDownLatch largada = new CountDownLatch(1);
		try {
			List<Future<Integer>> resultados = new ArrayList<>();
			for (int i = 0; i < PEDIDOS_SIMULTANEOS; i++) {
				resultados.add(pool.submit(() -> {
					todosProntos.countDown();
					largada.await();
					return mockMvc.perform(delete("/api/v1/materias/" + id)).andReturn().getResponse().getStatus();
				}));
			}
			todosProntos.await(10, TimeUnit.SECONDS);
			largada.countDown();

			List<Integer> status = new ArrayList<>();
			for (Future<Integer> resultado : resultados) {
				status.add(resultado.get(30, TimeUnit.SECONDS));
			}

			assertThat(status).as("status devolvidos pelos %d pedidos simultâneos", PEDIDOS_SIMULTANEOS)
					.filteredOn(codigo -> codigo == 204).hasSize(1);
			assertThat(status).filteredOn(codigo -> codigo == 404).hasSize(PEDIDOS_SIMULTANEOS - 1);
			assertThat(status).doesNotContain(500);
		}
		finally {
			pool.shutdownNow();
		}
	}

}
