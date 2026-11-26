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
 * O host da URL precisa estar em {@code app.imagens.hosts-permitidos} (D36, D46). Só olha o host: se o texto não é uma URL com host,
 * quem recusa é {@link UrlHttps}. {@code null} é válido.
 */
@Documented
@Constraint(validatedBy = HostDeImagemPermitidoValidator.class)
@Target({ FIELD, PARAMETER, RECORD_COMPONENT, ANNOTATION_TYPE })
@Retention(RUNTIME)
public @interface HostDeImagemPermitido {

	String message() default "O endereço da imagem não está em um servidor permitido.";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};

}
