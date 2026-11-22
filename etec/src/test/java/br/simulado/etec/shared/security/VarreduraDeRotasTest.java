package br.simulado.etec.shared.security;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

/** Varredura com a documentação DESLIGADA (o padrão): as rotas do springdoc nem são mapeadas. Filtros LIGADOS. */
@SpringBootTest
@AutoConfigureMockMvc
class VarreduraDeRotasTest extends AbstractVarreduraDeRotas {

	@Override
	boolean documentacaoLigada() {
		return false;
	}

}
