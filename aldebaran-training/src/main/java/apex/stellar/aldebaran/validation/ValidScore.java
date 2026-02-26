package apex.stellar.aldebaran.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Validates that a WodScore's fields match the WOD's scoreType.
 *
 * <p>Examples:
 *
 * <ul>
 *   <li>A "For Time" WOD must have timeSeconds filled
 *   <li>A "Rounds + Reps" WOD must have rounds or reps filled
 * </ul>
 */
@Documented
@Constraint(validatedBy = ScoreValidator.class)
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidScore {
  /**
   * Returns the default error message when the score validation fails.
   *
   * @return the error message string
   */
  String message() default "Invalid score configuration";

  /**
   * Allows the specification of validation groups, to which this constraint belongs.
   *
   * @return the array of groups
   */
  Class<?>[] groups() default {};

  /**
   * Can be used by clients of the Jakarta Bean Validation API to assign custom payload objects.
   *
   * @return the array of payload classes
   */
  Class<? extends Payload>[] payload() default {};
}
