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

  /**
   * Returns the default error message when validation fails.
   *
   * @return the error message string
   */
  String message() default "Invalid score request data";

  /**
   * Allows the specification of validation groups, to which this constraint belongs.
   *
   * @return the array of groups
   */
  Class<?>[] groups() default {};

  /**
   * Can be used by clients of the Jakarta Bean Validation API to assign custom payload objects to a
   * constraint.
   *
   * @return the array of payload classes
   */
  Class<? extends Payload>[] payload() default {};
}
