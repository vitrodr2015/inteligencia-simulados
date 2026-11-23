package br.simulado.etec.shared.security.limite;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Ponta a ponta, com a cadeia de segurança LIGADA: se o contador não consegue gravar (deadlock que esgotou as repetições), a
 * requisição de /auth/* recebe 429 LIMITE_EXCEDIDO com Retry-After curto e fixo, e NÃO 500 nem passa sem contar.
 */
@SpringBootTest
@AutoConfigureMockMvc
class LimiteFalhaFechadaWebTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private ContadorDeTentativas contador;

	@Test
	void deve_responder_429_e_nao_500_nem_deixar_passar_quando_o_contador_esgota_as_repeticoes() throws Exception {
		when(contador.contar(anyString(), anyInt(), any(), any())).thenThrow(new CannotAcquireLockException("deadlock"));

		mockMvc.perform(post("/api/v1/auth/signin").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"ana@exemplo.invalid\",\"senha\":\"qualquer-senha\"}"))
				.andExpect(status().isTooManyRequests())
				.andExpect(header().string("Retry-After", String.valueOf(LimiteDeTentativas.RETRY_AFTER_POR_CONTENCAO_SEGUNDOS)))
				.andExpect(header().string("Cache-Control", "no-store"))
				.andExpect(jsonPath("$.codigo").value("LIMITE_EXCEDIDO"));
	}

}
