package apex.stellar.aldebaran.validation;

import apex.stellar.aldebaran.model.enums.UnitField;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** A unit field holds a unit of its kind (D150): a height in KG is refused. Null passes. */
@Documented
@Constraint(validatedBy = UnitOfValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
public @interface UnitOf {

  /**
   * The kind of field.
   *
   * @return The kind, which lists the units accepted.
   */
  UnitField value();

  /**
   * The error message.
   *
   * @return The message key.
   */
  String message() default "{validation.unit.kind}";

  /**
   * Validation groups.
   *
   * @return The groups.
   */
  Class<?>[] groups() default {};

  /**
   * Payload.
   *
   * @return The payload classes.
   */
  Class<? extends Payload>[] payload() default {};
}
