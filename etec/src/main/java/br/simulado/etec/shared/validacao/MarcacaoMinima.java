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
 * Marcação mínima dos textos (D12, D32): só {@code <b>}, {@code <u>}, {@code <i>}, {@code <sup>}, {@code <sub>} e {@code <br>},
 * em minúsculas e SEM atributos; as tags de abrir e fechar precisam casar e estar bem aninhadas ({@code <br>} não fecha); e os
 * caracteres {@code <}, {@code >} e {@code &} do texto se escrevem {@code &lt;}, {@code &gt;} e {@code &amp;}. Qualquer outra
 * coisa ({@code <script>}, {@code <b class="x">}, {@code <br/>}, um {@code <} solto) é inválida. {@code null} é válido.
 */
@Documented
@Constraint(validatedBy = MarcacaoMinimaValidator.class)
@Target({ FIELD, PARAMETER, RECORD_COMPONENT, ANNOTATION_TYPE })
@Retention(RUNTIME)
public @interface MarcacaoMinima {

	String message() default "O texto só pode usar as marcações b, u, i, sup, sub e br.";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};

}
