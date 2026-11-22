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
 * Tamanho de texto contado em PONTOS DE CÓDIGO (D98), e não em unidades UTF-16 como faz o
 * {@code @Size} do Bean Validation. Um emoji (por exemplo, U+1F600) é UM ponto de código, mas ocupa
 * duas unidades UTF-16; com {@code @Size(max = 100)} só caberiam 50 emojis, e o contrato manda caber 100.
 *
 * <p>{@code null} é válido: quem trata a obrigatoriedade é o {@code @NotNull}/{@code @NotBlank}. Os limites
 * são inclusivos. A coluna do banco é {@code VARCHAR(n)} em utf8mb4, e o MySQL conta {@code n} em
 * caracteres (pontos de código), então o mesmo limite vale nas duas pontas.
 */
@Documented
@Constraint(validatedBy = TamanhoEmPontosDeCodigoValidator.class)
@Target({ FIELD, PARAMETER, RECORD_COMPONENT, ANNOTATION_TYPE })
@Retention(RUNTIME)
public @interface TamanhoEmPontosDeCodigo {

	String message() default "O texto tem um tamanho inválido.";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};

	/** Mínimo de pontos de código (inclusivo). */
	int min() default 0;

	/** Máximo de pontos de código (inclusivo). */
	int max() default Integer.MAX_VALUE;

}
