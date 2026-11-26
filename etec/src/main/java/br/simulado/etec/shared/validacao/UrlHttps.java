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
 * URL absoluta com esquema {@code https}, host e sem credenciais embutidas ({@code usuario:senha@}). {@code null} é válido: quem
 * trata a obrigatoriedade é o {@code @NotNull}. O tamanho é de {@link TamanhoEmPontosDeCodigo}.
 */
@Documented
@Constraint(validatedBy = UrlHttpsValidator.class)
@Target({ FIELD, PARAMETER, RECORD_COMPONENT, ANNOTATION_TYPE })
@Retention(RUNTIME)
public @interface UrlHttps {

	String message() default "A URL deve começar com https://.";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};

}
