package apex.stellar.aldebaran.validation;

import apex.stellar.aldebaran.model.entities.Wod.ScoreType;
import apex.stellar.aldebaran.model.entities.WodScore;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

/**
 * Validates that the WodScore fields correspond to the WOD's scoring type.
 *
 * <p>Checks are performed against the normalized fields (e.g., {@code maxWeightKg}) to ensure data
 * consistency regardless of the display unit.
 */
@Component
public class ScoreValidator implements ConstraintValidator<ValidScore, WodScore> {

  private MessageSource messageSource;

  @Override
  public boolean isValid(WodScore score, ConstraintValidatorContext context) {
    // If WOD or Score is null, let standard @NotNull annotations handle it
    if (score == null || score.getWod() == null) {
      return true;
    }

    ScoreType scoreType = score.getWod().getScoreType();

    boolean isValid =
        switch (scoreType) {
          case TIME -> score.getTimeSeconds() != null;
          case ROUNDS_REPS -> score.getRounds() != null || score.getReps() != null;
          case REPS -> score.getReps() != null;
          case WEIGHT -> score.getMaxWeightKg() != null;
          case LOAD -> score.getTotalLoadKg() != null;
          case CALORIES -> score.getTotalCalories() != null;
          case DISTANCE -> score.getTotalDistanceMeters() != null;
          case NONE -> true;
        };

    if (!isValid) {
      String errorMessage =
          messageSource.getMessage(
              "wod.score.invalid.type",
              new Object[] {scoreType.getDisplayName()},
              "Invalid Score",
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
