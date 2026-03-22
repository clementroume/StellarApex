package apex.stellar.aldebaran.validation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import apex.stellar.aldebaran.dto.ScoreRequest;
import apex.stellar.aldebaran.model.entities.Score.ScalingLevel;
import apex.stellar.aldebaran.model.enums.Unit;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

class ScoreRequestValidatorTest {

  private Validator validator;

  @BeforeEach
  void setUp() {
    // Initialisation du vrai moteur de validation avec les fichiers messages.properties
    ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
    messageSource.setBasename("messages");
    messageSource.setDefaultEncoding("UTF-8");

    LocalValidatorFactoryBean factoryBean = new LocalValidatorFactoryBean();
    factoryBean.setValidationMessageSource(messageSource);
    factoryBean.afterPropertiesSet();

    this.validator = factoryBean.getValidator();
  }

  @AfterEach
  void tearDown() {
    LocaleContextHolder.resetLocaleContext();
  }

  private ScoreRequest createRequest(
      Double maxWeight,
      Unit weightUnit,
      Double distance,
      Unit distanceUnit,
      Integer calories,
      Integer minutes,
      Integer seconds) {
    return new ScoreRequest(
        null,
        LocalDate.now(),
        1L,
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
        ScalingLevel.RX, // ScalingLevel in string
        false,
        null,
        null);
  }

  /** Exécute la validation et retourne la liste des messages d'erreur. */
  private List<String> validateAndGetMessages(ScoreRequest request) {
    Set<ConstraintViolation<ScoreRequest>> violations = validator.validate(request);
    return violations.stream().map(ConstraintViolation::getMessage).toList();
  }

  // =========================================================================
  // 1. UNIT REQUIRED
  // =========================================================================

  @Test
  @DisplayName("Unit Required (Weight) - EN")
  void testUnitRequired_Weight_EN() {
    LocaleContextHolder.setLocale(Locale.ENGLISH);

    ScoreRequest request = createRequest(100.0, null, null, null, null, null, null);
    List<String> messages = validateAndGetMessages(request);

    assertFalse(messages.isEmpty(), "Should have validation errors");
    assertTrue(messages.contains("Unit required when value is present."), "Actual: " + messages);
  }

  @Test
  @DisplayName("Unit Required (Weight) - FR")
  void testUnitRequired_Weight_FR() {
    LocaleContextHolder.setLocale(Locale.FRENCH);

    ScoreRequest request = createRequest(100.0, null, null, null, null, null, null);
    List<String> messages = validateAndGetMessages(request);

    assertFalse(messages.isEmpty(), "Should have validation errors");
    assertTrue(
        messages.contains("L'unité est requise lorsque la valeur est présente."),
        "Actual: " + messages);
  }

  @Test
  @DisplayName("Unit Required (Distance) - EN Check Only")
  void testUnitRequired_Distance_EN() {
    LocaleContextHolder.setLocale(Locale.ENGLISH);

    // Distance set, Unit null
    ScoreRequest request = createRequest(null, null, 500.0, null, null, null, null);
    List<String> messages = validateAndGetMessages(request);

    assertFalse(messages.isEmpty(), "Should have validation errors");
    assertTrue(messages.contains("Unit required when value is present."), "Actual: " + messages);
  }

  // =========================================================================
  // 2. WEIGHT MAX
  // =========================================================================

  @Test
  @DisplayName("Weight Max - EN")
  void testWeightMax_EN() {
    LocaleContextHolder.setLocale(Locale.ENGLISH);

    ScoreRequest request = createRequest(2000.0, Unit.KG, null, null, null, null, null);
    List<String> messages = validateAndGetMessages(request);

    assertFalse(messages.isEmpty(), "Should have validation errors");
    assertTrue(
        messages.contains("Weight exceeds realistic limit (Max 1500.0kg)."), "Actual: " + messages);
  }

  @Test
  @DisplayName("Weight Max - FR")
  void testWeightMax_FR() {
    LocaleContextHolder.setLocale(Locale.FRENCH);

    ScoreRequest request = createRequest(2000.0, Unit.KG, null, null, null, null, null);
    List<String> messages = validateAndGetMessages(request);

    assertFalse(messages.isEmpty(), "Should have validation errors");
    assertTrue(
        messages.contains("Le poids dépasse la limite réaliste (Max 1500.0kg)."),
        "Actual: " + messages);
  }

  // =========================================================================
  // 3. DISTANCE MAX
  // =========================================================================

  @Test
  @DisplayName("Distance Max - EN")
  void testDistanceMax_EN() {
    LocaleContextHolder.setLocale(Locale.ENGLISH);

    // 250,000 > 200,000
    ScoreRequest request = createRequest(null, null, 250_000.0, Unit.METERS, null, null, null);
    List<String> messages = validateAndGetMessages(request);

    assertFalse(messages.isEmpty(), "Should have validation errors");
    assertTrue(
        messages.contains("Distance exceeds realistic limit (Max 200000.0m)."),
        "Actual: " + messages);
  }

  @Test
  @DisplayName("Distance Max - FR")
  void testDistanceMax_FR() {
    LocaleContextHolder.setLocale(Locale.FRENCH);

    ScoreRequest request = createRequest(null, null, 250_000.0, Unit.METERS, null, null, null);
    List<String> messages = validateAndGetMessages(request);

    assertFalse(messages.isEmpty(), "Should have validation errors");
    assertTrue(
        messages.contains("La distance dépasse la limite réaliste (Max 200000.0m)."),
        "Actual: " + messages);
  }

  // =========================================================================
  // 4. CALORIES MAX
  // =========================================================================

  @Test
  @DisplayName("Calories Max - EN")
  void testCaloriesMax_EN() {
    LocaleContextHolder.setLocale(Locale.ENGLISH);

    // 15000 > 10000
    ScoreRequest request = createRequest(null, null, null, null, 15000, null, null);
    List<String> messages = validateAndGetMessages(request);

    assertFalse(messages.isEmpty(), "Should have validation errors");
    assertTrue(
        messages.contains("Calories exceed realistic limit (Max 10000)."), "Actual: " + messages);
  }

  @Test
  @DisplayName("Calories Max - FR")
  void testCaloriesMax_FR() {
    LocaleContextHolder.setLocale(Locale.FRENCH);

    ScoreRequest request = createRequest(null, null, null, null, 15000, null, null);
    List<String> messages = validateAndGetMessages(request);

    assertFalse(messages.isEmpty(), "Should have validation errors");
    assertTrue(
        messages.contains("Les calories dépassent la limite réaliste (Max 10000)."),
        "Actual: " + messages);
  }

  // =========================================================================
  // 5. TIME MAX
  // =========================================================================

  @Test
  @DisplayName("Time Max - EN")
  void testTimeMax_EN() {
    LocaleContextHolder.setLocale(Locale.ENGLISH);

    // 25h = 1500 minutes (> 1440 min / 24h)
    ScoreRequest request = createRequest(null, null, null, null, null, 1500, 0);
    List<String> messages = validateAndGetMessages(request);

    assertFalse(messages.isEmpty(), "Should have validation errors");
    assertTrue(messages.contains("Time exceeds realistic limit (Max 24h)."), "Actual: " + messages);
  }

  @Test
  @DisplayName("Time Max - FR")
  void testTimeMax_FR() {
    LocaleContextHolder.setLocale(Locale.FRENCH);

    ScoreRequest request = createRequest(null, null, null, null, null, 1500, 0);
    List<String> messages = validateAndGetMessages(request);

    assertFalse(messages.isEmpty(), "Should have validation errors");
    assertTrue(
        messages.contains("Le temps dépasse la limite réaliste (Max 24h)."), "Actual: " + messages);
  }
}
