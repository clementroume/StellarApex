package apex.stellar.aldebaran.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Validation annotation for {@link apex.stellar.aldebaran.dto.WodScoreRequest}.
 *
 * <p>Validates unit consistency and value plausibility (Sanity Check).
 */
@Documented
@Constraint(validatedBy = ScoreRequestValidator.class)
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidScoreRequest {
  String message() default "Invalid score request data";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};
}
