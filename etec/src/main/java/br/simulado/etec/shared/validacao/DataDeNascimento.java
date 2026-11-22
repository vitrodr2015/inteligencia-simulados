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
 * Data de nascimento (D106): no passado, em UTC, e a partir de 1900-01-01. "Hoje" vem do {@code Clock} da aplicação
 * (o {@code @Past} do Bean Validation usa o fuso do sistema, o que erraria perto da meia-noite UTC).
 * {@code null} é válido: quem trata a obrigatoriedade é o {@code @NotNull}.
 */
@Documented
@Constraint(validatedBy = DataDeNascimentoValidator.class)
@Target({ FIELD, PARAMETER, RECORD_COMPONENT, ANNOTATION_TYPE })
@Retention(RUNTIME)
public @interface DataDeNascimento {

	String message() default "A data de nascimento deve estar no passado e a partir de 1900-01-01.";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};

}
