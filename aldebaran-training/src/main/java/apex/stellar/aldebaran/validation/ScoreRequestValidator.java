package apex.stellar.aldebaran.validation;

import apex.stellar.aldebaran.dto.WodScoreRequest;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.hibernate.validator.constraintvalidation.HibernateConstraintValidatorContext;
import org.springframework.stereotype.Component;

/**
 * Sanity check validator for score requests. Checks consistency between values and units, and
 * enforces realistic physical bounds.
 */
@Component
public class ScoreRequestValidator
    implements ConstraintValidator<ValidScoreRequest, WodScoreRequest> {

  private static final String MSG_UNIT_REQUIRED = "{validation.score.sanity.unit.required}";
  private static final String MSG_WEIGHT_MAX = "{validation.score.sanity.weight.max}";
  private static final String MSG_DISTANCE_MAX = "{validation.score.sanity.distance.max}";
  private static final String MSG_CALORIES_MAX = "{validation.score.sanity.calories.max}";
  private static final String MSG_TIME_MAX = "{validation.score.sanity.time.max}";

  // Sanity Bounds
  private static final double MAX_REALISTIC_WEIGHT_KG = 1500.0;
  private static final double MAX_REALISTIC_DISTANCE_METERS = 200_000.0;
  private static final int MAX_REALISTIC_CALORIES = 10_000;
  private static final int MAX_REALISTIC_TIME_SECONDS = 86_400; // 24h

  @Override
  public boolean isValid(WodScoreRequest request, ConstraintValidatorContext context) {
    if (request == null) {
      return true;
    }

    boolean isValid = validateWeight(request, context);
    isValid &= validateTotalLoad(request, context);
    isValid &= validateDistance(request, context);
    isValid &= validateCalories(request, context);
    isValid &= validateTime(request, context);

    return isValid;
  }

  private boolean validateWeight(WodScoreRequest request, ConstraintValidatorContext context) {
    if (request.maxWeight() != null) {
      if (request.weightUnit() == null) {
        addViolation(context, "weightUnit", MSG_UNIT_REQUIRED, null, null);
        return false;
      }
      double weightInKg = request.weightUnit().toBase(request.maxWeight());
      if (weightInKg < 0 || weightInKg > MAX_REALISTIC_WEIGHT_KG) {
        addViolation(context, "maxWeight", MSG_WEIGHT_MAX, "max", MAX_REALISTIC_WEIGHT_KG);
        return false;
      }
    }
    return true;
  }

  private boolean validateTotalLoad(WodScoreRequest request, ConstraintValidatorContext context) {
    if (request.totalLoad() != null && request.weightUnit() == null) {
      addViolation(context, "weightUnit", MSG_UNIT_REQUIRED, null, null);
      return false;
    }
    return true;
  }

  private boolean validateDistance(WodScoreRequest request, ConstraintValidatorContext context) {
    if (request.totalDistance() != null) {
      if (request.distanceUnit() == null) {
        addViolation(context, "distanceUnit", MSG_UNIT_REQUIRED, null, null);
        return false;
      }
      double distInMeters = request.distanceUnit().toBase(request.totalDistance());
      if (distInMeters < 0 || distInMeters > MAX_REALISTIC_DISTANCE_METERS) {
        addViolation(
            context, "totalDistance", MSG_DISTANCE_MAX, "max", MAX_REALISTIC_DISTANCE_METERS);
        return false;
      }
    }
    return true;
  }

  private boolean validateCalories(WodScoreRequest request, ConstraintValidatorContext context) {
    if (request.totalCalories() != null
        && (request.totalCalories() < 0 || request.totalCalories() > MAX_REALISTIC_CALORIES)) {
      addViolation(context, "totalCalories", MSG_CALORIES_MAX, "max", MAX_REALISTIC_CALORIES);
      return false;
    }
    return true;
  }

  private boolean validateTime(WodScoreRequest request, ConstraintValidatorContext context) {
    long totalSeconds = 0;
    if (request.timeMinutes() != null) {
      totalSeconds += request.timeMinutes() * 60L;
    }
    if (request.timeSeconds() != null) {
      totalSeconds += request.timeSeconds();
    }

    if (totalSeconds > MAX_REALISTIC_TIME_SECONDS) {
      addViolation(context, "timeMinutes", MSG_TIME_MAX, "max", MAX_REALISTIC_TIME_SECONDS);
      return false;
    }
    return true;
  }

  /** Builds the constraint violation using Hibernate interpolation. */
  private void addViolation(
      ConstraintValidatorContext context,
      String fieldName,
      String messageTemplate,
      String paramName,
      Object paramValue) {

    context.disableDefaultConstraintViolation();

    if (paramName != null && paramValue != null) {
      context
          .unwrap(HibernateConstraintValidatorContext.class)
          .addMessageParameter(paramName, paramValue)
          .buildConstraintViolationWithTemplate(messageTemplate)
          .addPropertyNode(fieldName)
          .addConstraintViolation();
    } else {
      context
          .buildConstraintViolationWithTemplate(messageTemplate)
          .addPropertyNode(fieldName)
          .addConstraintViolation();
    }
  }
}
