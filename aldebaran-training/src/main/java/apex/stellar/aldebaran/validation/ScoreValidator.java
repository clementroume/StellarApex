package apex.stellar.aldebaran.validation;

import apex.stellar.aldebaran.model.entities.Wod.ScoreType;
import apex.stellar.aldebaran.model.entities.WodScore;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.hibernate.validator.constraintvalidation.HibernateConstraintValidatorContext;
import org.springframework.stereotype.Component;

/** Validates that the WodScore fields correspond to the WOD's scoring type. */
@Component
public class ScoreValidator implements ConstraintValidator<ValidScore, WodScore> {

  @Override
  public boolean isValid(WodScore score, ConstraintValidatorContext context) {
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
      context.disableDefaultConstraintViolation();
      context
          .unwrap(HibernateConstraintValidatorContext.class)
          .addMessageParameter("type", scoreType.name())
          .buildConstraintViolationWithTemplate("{wod.score.invalid.type}")
          .addConstraintViolation();
    }

    return isValid;
  }
}
