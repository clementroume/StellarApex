package apex.stellar.aldebaran.validation;

import apex.stellar.aldebaran.model.entities.Movement;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

/**
 * MovementValidator is responsible for validating a {@code Movement} entity annotated with
 * {@code @ValidMovement}, ensuring consistency between its bodyweight-related fields.
 *
 * <p>This validator enforces the following rules:
 *
 * <ul>
 *   <li>If {@code involvesBodyweight} is {@code true}, {@code bodyweightFactor} must be greater
 *       than 0.
 *   <li>If {@code involvesBodyweight} is {@code false}, {@code bodyweightFactor} must be exactly 0.
 * </ul>
 *
 * <p>If validation fails, a localized error message is provided through the {@code MessageSource}
 * bean.
 */
@Component
public class MovementValidator implements ConstraintValidator<ValidMovement, Movement> {

  private MessageSource messageSource;

  /**
   * Validates the consistency between the bodyweight flag and the bodyweight factor.
   *
   * @param movement The movement entity to validate.
   * @param context The validator context.
   * @return true if the configuration is consistent, false otherwise.
   */
  @Override
  public boolean isValid(Movement movement, ConstraintValidatorContext context) {
    if (movement == null) {
      return true;
    }

    boolean isValid;
    if (Boolean.TRUE.equals(movement.getInvolvesBodyweight())) {
      isValid = movement.getBodyweightFactor() != null && movement.getBodyweightFactor() > 0.0;
    } else {
      isValid = movement.getBodyweightFactor() != null && movement.getBodyweightFactor() == 0.0;
    }

    if (!isValid) {
      String errorMessage =
          messageSource.getMessage(
              "validation.movement.bodyweight.consistency",
              null,
              "Invalid movement configuration",
              LocaleContextHolder.getLocale());

      context.disableDefaultConstraintViolation();
      context.buildConstraintViolationWithTemplate(errorMessage).addConstraintViolation();
    }

    return isValid;
  }

  /**
   * Sets the message source for internationalized error messages.
   *
   * @param messageSource The Spring MessageSource.
   */
  @Autowired
  public void setMessageSource(MessageSource messageSource) {
    this.messageSource = messageSource;
  }
}
