package apex.stellar.aldebaran.validation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import apex.stellar.aldebaran.dto.WodScoreRequest;
import apex.stellar.aldebaran.model.entities.WodScore.ScalingLevel;
import apex.stellar.aldebaran.model.enums.Unit;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.ConstraintValidatorContext.ConstraintViolationBuilder;
import jakarta.validation.ConstraintValidatorContext.ConstraintViolationBuilder.NodeBuilderCustomizableContext;
import java.time.LocalDate;
import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.ResourceBundleMessageSource;

@ExtendWith(MockitoExtension.class)
class ScoreRequestValidatorTest {

  @InjectMocks private ScoreRequestValidator validator;

  private ConstraintValidatorContext context;
  private ConstraintViolationBuilder builder;
  private NodeBuilderCustomizableContext nodeBuilder;

  @BeforeEach
  void setUp() {
    ResourceBundleMessageSource realMessageSource = new ResourceBundleMessageSource();
    realMessageSource.setBasename("messages");
    realMessageSource.setDefaultEncoding("UTF-8");
    realMessageSource.setUseCodeAsDefaultMessage(true);

    validator.setMessageSource(realMessageSource);

    context = mock(ConstraintValidatorContext.class);
    builder = mock(ConstraintViolationBuilder.class);
    nodeBuilder = mock(NodeBuilderCustomizableContext.class);
  }

  @AfterEach
  void tearDown() {
    LocaleContextHolder.resetLocaleContext();
  }

  private void prepareContextForFailure() {
    when(context.buildConstraintViolationWithTemplate(anyString())).thenReturn(builder);
    when(builder.addPropertyNode(anyString())).thenReturn(nodeBuilder);
    when(nodeBuilder.addConstraintViolation()).thenReturn(context);
  }

  private WodScoreRequest createRequest(
      Double maxWeight,
      Unit weightUnit,
      Double distance,
      Unit distanceUnit,
      Integer calories,
      Integer minutes,
      Integer seconds) {
    return new WodScoreRequest(
        null,
        1L,
        LocalDate.now(),
        minutes,
        seconds,
        null,
        null,
        maxWeight,
        null,
        weightUnit,
        distance,
        distanceUnit,
        calories,
        ScalingLevel.RX,
        false,
        null,
        null);
  }

  // =========================================================================
  // 1. UNIT REQUIRED
  // =========================================================================

  @Test
  @DisplayName("Unit Required (Weight) - EN")
  void testUnitRequired_Weight_EN() {
    LocaleContextHolder.setLocale(Locale.ENGLISH);
    prepareContextForFailure();

    WodScoreRequest request = createRequest(100.0, null, null, null, null, null, null);

    assertFalse(validator.isValid(request, context));
    verify(context).buildConstraintViolationWithTemplate("Unit required when value is present.");
    verify(builder).addPropertyNode("weightUnit");
  }

  @Test
  @DisplayName("Unit Required (Weight) - FR")
  void testUnitRequired_Weight_FR() {
    LocaleContextHolder.setLocale(Locale.FRENCH);
    prepareContextForFailure();

    WodScoreRequest request = createRequest(100.0, null, null, null, null, null, null);

    assertFalse(validator.isValid(request, context));
    verify(context)
        .buildConstraintViolationWithTemplate(
            "L'unité est requise lorsque la valeur est présente.");
    verify(builder).addPropertyNode("weightUnit");
  }

  @Test
  @DisplayName("Unit Required (Distance) - EN Check Only")
  void testUnitRequired_Distance_EN() {
    LocaleContextHolder.setLocale(Locale.ENGLISH);
    prepareContextForFailure();
    // Distance set, Unit null
    WodScoreRequest request = createRequest(null, null, 500.0, null, null, null, null);

    assertFalse(validator.isValid(request, context));
    verify(context).buildConstraintViolationWithTemplate("Unit required when value is present.");
    verify(builder).addPropertyNode("distanceUnit");
  }

  // =========================================================================
  // 2. WEIGHT MAX
  // =========================================================================

  @Test
  @DisplayName("Weight Max - EN")
  void testWeightMax_EN() {
    LocaleContextHolder.setLocale(Locale.ENGLISH);
    prepareContextForFailure();

    WodScoreRequest request = createRequest(2000.0, Unit.KG, null, null, null, null, null);

    assertFalse(validator.isValid(request, context));
    verify(context)
        .buildConstraintViolationWithTemplate("Weight exceeds realistic limit (Max 1,500kg).");
    verify(builder).addPropertyNode("maxWeight");
  }

  @Test
  @DisplayName("Weight Max - FR")
  void testWeightMax_FR() {
    LocaleContextHolder.setLocale(Locale.FRENCH);
    prepareContextForFailure();

    WodScoreRequest request = createRequest(2000.0, Unit.KG, null, null, null, null, null);

    assertFalse(validator.isValid(request, context));
    verify(context)
        .buildConstraintViolationWithTemplate("Le poids dépasse la limite réaliste (Max 1 500kg).");
    verify(builder).addPropertyNode("maxWeight");
  }

  // =========================================================================
  // 3. DISTANCE MAX (validation.score.sanity.distance.max)
  // =========================================================================

  @Test
  @DisplayName("Distance Max - EN")
  void testDistanceMax_EN() {
    LocaleContextHolder.setLocale(Locale.ENGLISH);
    prepareContextForFailure();

    // 250,000 > 200,000
    WodScoreRequest request = createRequest(null, null, 250_000.0, Unit.METERS, null, null, null);

    assertFalse(validator.isValid(request, context));
    verify(context)
        .buildConstraintViolationWithTemplate("Distance exceeds realistic limit (Max 200,000m).");
    verify(builder).addPropertyNode("totalDistance");
  }

  @Test
  @DisplayName("Distance Max - FR")
  void testDistanceMax_FR() {
    LocaleContextHolder.setLocale(Locale.FRENCH);
    prepareContextForFailure();

    WodScoreRequest request = createRequest(null, null, 250_000.0, Unit.METERS, null, null, null);

    assertFalse(validator.isValid(request, context));
    // "La distance dépasse la limite réaliste (Max {0}m)."
    verify(context)
        .buildConstraintViolationWithTemplate(
            "La distance dépasse la limite réaliste (Max 200 000m).");
    verify(builder).addPropertyNode("totalDistance");
  }

  // =========================================================================
  // 4. CALORIES MAX (validation.score.sanity.calories.max)
  // =========================================================================

  @Test
  @DisplayName("Calories Max - EN")
  void testCaloriesMax_EN() {
    LocaleContextHolder.setLocale(Locale.ENGLISH);
    prepareContextForFailure();

    // 15000 > 10000
    WodScoreRequest request = createRequest(null, null, null, null, 15000, null, null);

    assertFalse(validator.isValid(request, context));
    // "Calories exceed realistic limit (Max {0})." -> 10000
    verify(context)
        .buildConstraintViolationWithTemplate("Calories exceed realistic limit (Max 10,000).");
    verify(builder).addPropertyNode("totalCalories");
  }

  @Test
  @DisplayName("Calories Max - FR")
  void testCaloriesMax_FR() {
    LocaleContextHolder.setLocale(Locale.FRENCH);
    prepareContextForFailure();

    WodScoreRequest request = createRequest(null, null, null, null, 15000, null, null);

    assertFalse(validator.isValid(request, context));
    // "Les calories dépassent la limite réaliste (Max {0})."
    verify(context)
        .buildConstraintViolationWithTemplate(
            "Les calories dépassent la limite réaliste (Max 10 000).");
    verify(builder).addPropertyNode("totalCalories");
  }

  // =========================================================================
  // 5. TIME MAX (validation.score.sanity.time.max)
  // =========================================================================

  @Test
  @DisplayName("Time Max - EN")
  void testTimeMax_EN() {
    LocaleContextHolder.setLocale(Locale.ENGLISH);
    prepareContextForFailure();

    // 25h = 1500 minutes (> 1440 min / 24h)
    WodScoreRequest request = createRequest(null, null, null, null, null, 1500, 0);

    assertFalse(validator.isValid(request, context));
    // "Time exceeds realistic limit (Max 24h)."
    verify(context).buildConstraintViolationWithTemplate("Time exceeds realistic limit (Max 24h).");
    verify(builder).addPropertyNode("timeMinutes");
  }

  @Test
  @DisplayName("Time Max - FR")
  void testTimeMax_FR() {
    LocaleContextHolder.setLocale(Locale.FRENCH);
    prepareContextForFailure();

    WodScoreRequest request = createRequest(null, null, null, null, null, 1500, 0);

    assertFalse(validator.isValid(request, context));
    // "Le temps dépasse la limite réaliste (Max 24h)."
    verify(context)
        .buildConstraintViolationWithTemplate("Le temps dépasse la limite réaliste (Max 24h).");
    verify(builder).addPropertyNode("timeMinutes");
  }
}
