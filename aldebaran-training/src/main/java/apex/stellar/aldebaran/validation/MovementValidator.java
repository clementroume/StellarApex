package apex.stellar.aldebaran.validation;

import apex.stellar.aldebaran.model.entities.Movement;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

@Component
public class MovementValidator implements ConstraintValidator<ValidMovement, Movement> {

  private MessageSource messageSource;

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

  @Autowired
  public void setMessageSource(MessageSource messageSource) {
    this.messageSource = messageSource;
  }
}
