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
 * Tamanho de texto contado em BYTES UTF-8. Serve para a senha: o limite de 72 do BCrypt é em bytes, não em
 * caracteres (uma senha de 40 letras acentuadas já passa de 72 bytes). {@code null} é válido: quem trata a
 * obrigatoriedade é o {@code @NotNull}. Os limites são inclusivos.
 */
@Documented
@Constraint(validatedBy = TamanhoEmBytesValidator.class)
@Target({ FIELD, PARAMETER, RECORD_COMPONENT, ANNOTATION_TYPE })
@Retention(RUNTIME)
public @interface TamanhoEmBytes {

	String message() default "O texto tem um tamanho inválido.";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};

	/** Mínimo de bytes (inclusivo). */
	int min() default 0;

	/** Máximo de bytes (inclusivo). */
	int max() default Integer.MAX_VALUE;

}
