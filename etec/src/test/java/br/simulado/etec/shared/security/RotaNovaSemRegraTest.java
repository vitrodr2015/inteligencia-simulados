package br.simulado.etec.shared.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.simulado.etec.shared.security.JwtTokenProvider.TipoToken;
import br.simulado.etec.usuarios.model.Papel;
import br.simulado.etec.usuarios.model.Usuario;
import br.simulado.etec.usuarios.repository.UsuarioRepository;

/**
 * "Rota nova nasce negada" de ponta a ponta: um controller de teste, com rotas que NINGUÉM mapeou na
 * {@code SecurityConfig}, dá 401 ao anônimo e 403 a qualquer papel autenticado, inclusive o ADMIN. Quem escrever uma rota
 * nova sem regra descobre aqui (e na varredura de rotas) antes de chegar ao ar. H2, HTTP de verdade.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(RotaNovaSemRegraTest.ControllerNovoSemRegra.class)
class RotaNovaSemRegraTest {

	@RestController
	@RequestMapping("/api/v1/rota-nova-sem-regra")
	static class ControllerNovoSemRegra {

		@GetMapping
		String ler() {
			return "\"segredo-da-rota-nova\"";
		}

		@PostMapping
		String escrever() {
			return "\"segredo-da-rota-nova\"";
		}

		@DeleteMapping("/{id}")
		void apagar() {
		}

	}

	@LocalServerPort
	private int porta;

	@Autowired
	private JwtTokenProvider provedorDeTokens;

	@Autowired
	private UsuarioRepository usuarios;

	private String tokenDe(Papel papel) {
		Usuario usuario = usuarios.saveAndFlush(new Usuario("Teste " + papel, papel.name().toLowerCase() + "."
				+ System.nanoTime() + "@exemplo.invalid", "hash-falso-nao-e-real", LocalDate.of(2000, 1, 1), "v1", papel));
		return provedorDeTokens.emitir(TipoToken.ACCESS, usuario.getPublicId(), papel.name()).valor();
	}

	private HttpResponse<String> enviar(String metodo, String caminho, String token) throws Exception {
		HttpRequest.Builder requisicao = HttpRequest.newBuilder(URI.create("http://localhost:" + porta + caminho))
				.header("Accept", "application/json").method(metodo, HttpRequest.BodyPublishers.noBody());
		if (token != null) {
			requisicao.header("Authorization", "Bearer " + token);
		}
		return HttpClient.newHttpClient().send(requisicao.build(), HttpResponse.BodyHandlers.ofString());
	}

	@Test
	void deve_dar_401_ao_anonimo_e_403_a_todo_papel_autenticado_em_rota_nova_sem_regra() throws Exception {
		String[][] rotas = { { "GET", "/api/v1/rota-nova-sem-regra" }, { "HEAD", "/api/v1/rota-nova-sem-regra" },
				{ "POST", "/api/v1/rota-nova-sem-regra" }, { "DELETE", "/api/v1/rota-nova-sem-regra/abc" } };
		for (String[] rota : rotas) {
			HttpResponse<String> anonimo = enviar(rota[0], rota[1], null);
			assertThat(anonimo.statusCode()).as("anônimo " + rota[0]).isEqualTo(401);
			assertThat(anonimo.body()).doesNotContain("segredo-da-rota-nova");
			for (Papel papel : Papel.values()) {
				HttpResponse<String> resposta = enviar(rota[0], rota[1], tokenDe(papel));
				assertThat(resposta.statusCode()).as(papel + " " + rota[0]).isEqualTo(403);
				assertThat(resposta.body()).doesNotContain("segredo-da-rota-nova");
			}
		}
	}

}
