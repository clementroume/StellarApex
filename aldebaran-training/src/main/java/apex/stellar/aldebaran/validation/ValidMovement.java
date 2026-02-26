package apex.stellar.aldebaran.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to validate movement configurations. This constraint is applied at the class level to
 * ensure complex movement rules are met.
 */
@Documented
@Constraint(validatedBy = MovementValidator.class)
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidMovement {
  /**
   * Returns the default error message when the movement validation fails.
   *
   * @return the error message string
   */
  String message() default "Invalid movement configuration";

  /**
   * Allows the specification of validation groups to which this constraint belongs.
   *
   * @return the array of group classes
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
