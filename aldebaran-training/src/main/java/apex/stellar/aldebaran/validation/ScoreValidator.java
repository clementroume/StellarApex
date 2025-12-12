package apex.stellar.aldebaran.validation;

import apex.stellar.aldebaran.model.entities.Wod.ScoreType;
import apex.stellar.aldebaran.model.entities.WodScore;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

/**
 * Validates that the WodScore fields correspond to the WOD's scoring type.
 *
 * <p>This ensures data integrity at the application level before persistence.
 */
@Component
@RequiredArgsConstructor
public class ScoreValidator implements ConstraintValidator<ValidScore, WodScore> {

  private final MessageSource messageSource;

  @Override
  public boolean isValid(WodScore score, ConstraintValidatorContext context) {
    if (score == null || score.getWod() == null) {
      return true; // Let @NotNull handle nullity
    }

    ScoreType scoreType = score.getWod().getScoreType();

    boolean isValid =
        switch (scoreType) {
          case TIME -> score.getTimeSeconds() != null;
          case ROUNDS_REPS -> score.getRounds() != null || score.getReps() != null;
          case REPS -> score.getReps() != null;
          case WEIGHT -> score.getMaxWeight() != null;
          case LOAD -> score.getTotalLoad() != null;
          case CALORIES -> score.getTotalCalories() != null;
          case DISTANCE -> score.getTotalDistance() != null;
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
}
