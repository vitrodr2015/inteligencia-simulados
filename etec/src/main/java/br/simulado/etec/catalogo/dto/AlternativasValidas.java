package br.simulado.etec.catalogo.dto;

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

/** As alternativas da questão: EXATAMENTE 5, com as letras A a E, cada uma uma vez e em qualquer ordem (D2, D90). */
@Documented
@Constraint(validatedBy = AlternativasValidasValidator.class)
@Target({ FIELD, PARAMETER, RECORD_COMPONENT, ANNOTATION_TYPE })
@Retention(RUNTIME)
public @interface AlternativasValidas {

	String message() default "A questão precisa de exatamente 5 alternativas, com as letras A, B, C, D e E, cada uma uma vez.";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};

}
