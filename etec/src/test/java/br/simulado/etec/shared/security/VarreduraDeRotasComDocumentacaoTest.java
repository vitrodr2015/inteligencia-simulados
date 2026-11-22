package br.simulado.etec.shared.security;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

/** Varredura com a documentação LIGADA (desenvolvimento e homologação): as rotas do springdoc existem e são públicas. */
@SpringBootTest(properties = { "springdoc.api-docs.enabled=true", "springdoc.swagger-ui.enabled=true" })
@AutoConfigureMockMvc
class VarreduraDeRotasComDocumentacaoTest extends AbstractVarreduraDeRotas {

	@Override
	boolean documentacaoLigada() {
		return true;
	}

}
