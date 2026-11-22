package br.simulado.etec.shared.validacao;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.RECORD_COMPONENT;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * E-mail em formato válido e SÓ com caracteres ASCII (D119). A collation do banco ignora acento e caixa, então um
 * e-mail com acento colidiria com o equivalente sem acento e acabaria na conta de outra pessoa; por isso o cadastro
 * só aceita ASCII. Exige parte local e domínio com ponto; o tamanho máximo (255) é conferido à parte.
 * {@code null} é válido: quem trata a obrigatoriedade é o {@code @NotNull}.
 */
@Documented
@Constraint(validatedBy = EmailAsciiValidator.class)
@Target({ FIELD, PARAMETER, RECORD_COMPONENT, ANNOTATION_TYPE })
@Retention(RUNTIME)
public @interface EmailAscii {

	String message() default "Informe um e-mail válido, só com caracteres ASCII.";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};

}
