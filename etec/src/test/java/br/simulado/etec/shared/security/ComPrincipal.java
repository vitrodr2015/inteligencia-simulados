package br.simulado.etec.shared.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.junit.jupiter.api.extension.ExtendWith;

import br.simulado.etec.usuarios.model.Papel;

/**
 * Para os testes de CONTRATO com os filtros de segurança DESLIGADOS ({@code addFilters = false}): coloca um
 * {@link UsuarioAutenticado} do papel dado no {@code SecurityContextHolder} antes de cada teste e o LIMPA depois. No
 * método vale o do método; na classe, o da classe. Sem a anotação não há principal, e o resolvedor do escopo de leitura
 * falha fechado (403). Estes testes NÃO testam segurança: a cadeia é provada nos testes de integração.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.TYPE, ElementType.METHOD })
@ExtendWith(PrincipalDeTesteExtension.class)
public @interface ComPrincipal {

	Papel value();

}
