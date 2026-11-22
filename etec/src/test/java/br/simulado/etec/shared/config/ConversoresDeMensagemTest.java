package br.simulado.etec.shared.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;

/**
 * Só JSON (contrato, seção 1): nenhum conversor de XML, YAML, HAL ou outro formato de objeto pode
 * estar registrado no Spring MVC. Se alguém adicionar uma dependência que traga um (por exemplo,
 * jackson-dataformat-xml ou HATEOAS), este teste avisa na hora.
 */
@SpringBootTest
class ConversoresDeMensagemTest {

	@Autowired
	private RequestMappingHandlerAdapter adaptador;

	private List<String> nomes() {
		return adaptador.getMessageConverters().stream().map(conversor -> conversor.getClass().getName()).toList();
	}

	@Test
	void deve_ter_um_conversor_de_json_para_objetos() {
		assertThat(nomes()).anyMatch(nome -> nome.toLowerCase().contains("json"));
	}

	@Test
	void deve_nao_ter_conversor_de_xml_yaml_hal_smile_cbor_ou_atom() {
		List<String> proibidos = List.of("xml", "yaml", "hal", "hateoas", "smile", "cbor", "atom", "rss", "jaxb", "protobuf");

		for (HttpMessageConverter<?> conversor : adaptador.getMessageConverters()) {
			String nome = conversor.getClass().getName().toLowerCase();
			assertThat(proibidos).as("conversor %s", conversor.getClass().getName())
					.noneMatch(proibido -> nome.contains(proibido));
		}
	}

	@Test
	void deve_nao_aceitar_xml_nem_yaml_em_nenhum_conversor_que_converte_objetos() {
		for (HttpMessageConverter<?> conversor : adaptador.getMessageConverters()) {
			String tipos = conversor.getSupportedMediaTypes().toString().toLowerCase();
			assertThat(tipos).as("tipos de %s", conversor.getClass().getSimpleName()).doesNotContain("xml")
					.doesNotContain("yaml");
		}
	}

}
