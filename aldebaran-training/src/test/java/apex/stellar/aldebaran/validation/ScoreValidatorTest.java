package apex.stellar.aldebaran.validation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import apex.stellar.aldebaran.model.entities.Wod;
import apex.stellar.aldebaran.model.entities.Wod.ScoreType;
import apex.stellar.aldebaran.model.entities.WodScore;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
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

class ScoreValidatorTest {

  private Validator validator;

  @BeforeEach
  void setUp() {
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

  private WodScore createScore(ScoreType type) {
    return WodScore.builder().wod(Wod.builder().scoreType(type).build()).build();
  }

  /** Vérifie que l'erreur de type custom n'est PAS présente */
  private void assertValidScoreType(WodScore score) {
    Set<ConstraintViolation<WodScore>> violations = validator.validate(score);
    boolean hasTypeError =
        violations.stream()
            .anyMatch(v -> "{wod.score.invalid.type}".equals(v.getMessageTemplate()));
    assertFalse(
        hasTypeError, "Le score devrait être valide pour ce type (aucune erreur custom attendue)");
  }

  /** Vérifie que l'erreur de type custom EST bien présente */
  private void assertInvalidScoreType(WodScore score) {
    Set<ConstraintViolation<WodScore>> violations = validator.validate(score);
    boolean hasTypeError =
        violations.stream()
            .anyMatch(v -> "{wod.score.invalid.type}".equals(v.getMessageTemplate()));
    assertTrue(
        hasTypeError, "Le score devrait être invalide pour ce type (erreur custom attendue)");
  }

  // =========================================================================
  // 1. TIME
  // =========================================================================

  @Test
  @DisplayName("TIME: Valid if timeSeconds is present")
  void testTime_Valid() {
    WodScore score = createScore(ScoreType.TIME);
    score.setTimeSeconds(120);
    assertValidScoreType(score);
  }

  @Test
  @DisplayName("TIME: Invalid if timeSeconds is null")
  void testTime_Invalid() {
    WodScore score = createScore(ScoreType.TIME);
    score.setTimeSeconds(null);
    assertInvalidScoreType(score);
  }

  // =========================================================================
  // 2. ROUNDS_REPS (AMRAP Mixte)
  // =========================================================================

  @Test
  @DisplayName("ROUNDS_REPS: Valid if Rounds is present")
  void testRoundsReps_ValidRounds() {
    WodScore score = createScore(ScoreType.ROUNDS_REPS);
    score.setRounds(5);
    assertValidScoreType(score);
  }

  @Test
  @DisplayName("ROUNDS_REPS: Valid if Reps is present")
  void testRoundsReps_ValidReps() {
    WodScore score = createScore(ScoreType.ROUNDS_REPS);
    score.setReps(50);
    assertValidScoreType(score);
  }

  @Test
  @DisplayName("ROUNDS_REPS: Invalid if both are null")
  void testRoundsReps_Invalid() {
    WodScore score = createScore(ScoreType.ROUNDS_REPS);
    assertInvalidScoreType(score);
  }

  // =========================================================================
  // 3. REPS (AMRAP simple)
  // =========================================================================

  @Test
  @DisplayName("REPS: Valid if Reps is present")
  void testReps_Valid() {
    WodScore score = createScore(ScoreType.REPS);
    score.setReps(100);
    assertValidScoreType(score);
  }

  @Test
  @DisplayName("REPS: Invalid if Reps is null")
  void testReps_Invalid() {
    WodScore score = createScore(ScoreType.REPS);
    assertInvalidScoreType(score);
  }

  // =========================================================================
  // 4. WEIGHT (1RM etc.)
  // =========================================================================

  @Test
  @DisplayName("WEIGHT: Valid if MaxWeightKg is present")
  void testWeight_Valid() {
    WodScore score = createScore(ScoreType.WEIGHT);
    score.setMaxWeightKg(100.0);
    assertValidScoreType(score);
  }

  @Test
  @DisplayName("WEIGHT: Invalid if MaxWeightKg is null")
  void testWeight_Invalid() {
    WodScore score = createScore(ScoreType.WEIGHT);
    assertInvalidScoreType(score);
  }

  // =========================================================================
  // 5. LOAD (Total Volume)
  // =========================================================================

  @Test
  @DisplayName("LOAD: Valid if TotalLoadKg is present")
  void testLoad_Valid() {
    WodScore score = createScore(ScoreType.LOAD);
    score.setTotalLoadKg(5000.0);
    assertValidScoreType(score);
  }

  @Test
  @DisplayName("LOAD: Invalid if TotalLoadKg is null")
  void testLoad_Invalid() {
    WodScore score = createScore(ScoreType.LOAD);
    assertInvalidScoreType(score);
  }

  // =========================================================================
  // 6. CALORIES
  // =========================================================================

  @Test
  @DisplayName("CALORIES: Valid if TotalCalories is present")
  void testCalories_Valid() {
    WodScore score = createScore(ScoreType.CALORIES);
    score.setTotalCalories(50);
    assertValidScoreType(score);
  }

  @Test
  @DisplayName("CALORIES: Invalid if TotalCalories is null")
  void testCalories_Invalid() {
    WodScore score = createScore(ScoreType.CALORIES);
    assertInvalidScoreType(score);
  }

  // =========================================================================
  // 7. DISTANCE
  // =========================================================================

  @Test
  @DisplayName("DISTANCE: Valid if TotalDistanceMeters is present")
  void testDistance_Valid() {
    WodScore score = createScore(ScoreType.DISTANCE);
    score.setTotalDistanceMeters(2000.0);
    assertValidScoreType(score);
  }

  @Test
  @DisplayName("DISTANCE: Invalid if TotalDistanceMeters is null")
  void testDistance_Invalid() {
    WodScore score = createScore(ScoreType.DISTANCE);
    assertInvalidScoreType(score);
  }

  // =========================================================================
  // 8. NONE & NULL SAFETY
  // =========================================================================

  @Test
  @DisplayName("NONE: Always valid")
  void testNone_Valid() {
    WodScore score = createScore(ScoreType.NONE);
    assertValidScoreType(score);
  }

  @Test
  @DisplayName("Null Handling: Valid if Wod is null (ignored)")
  void testNullSafety() {
    WodScore score = new WodScore(); // Wod est null
    assertValidScoreType(score);
  }

  // =========================================================================
  // I18N MESSAGE CHECK
  // =========================================================================

  @Test
  @DisplayName("I18N EN: Should return correct English message with dynamic parameter")
  void testMessage_EN() {
    LocaleContextHolder.setLocale(Locale.ENGLISH);

    WodScore score = createScore(ScoreType.TIME);
    String expectedType = ScoreType.TIME.name();

    Set<ConstraintViolation<WodScore>> violations = validator.validate(score);

    List<String> messages = violations.stream().map(ConstraintViolation::getMessage).toList();

    String expectedMessage =
        "Score does not match WOD type " + expectedType + ". Missing required fields.";
    assertTrue(messages.contains(expectedMessage), "Actual messages: " + messages);
  }

  @Test
  @DisplayName("I18N FR: Should return correct French message with dynamic parameter")
  void testMessage_FR() {
    LocaleContextHolder.setLocale(Locale.FRENCH);

    WodScore score = createScore(ScoreType.TIME);
    String expectedType = ScoreType.TIME.name();

    Set<ConstraintViolation<WodScore>> violations = validator.validate(score);

    List<String> messages = violations.stream().map(ConstraintViolation::getMessage).toList();

    String expectedMessage =
        "Le score ne correspond pas au type " + expectedType + ". Champs requis manquants.";
    assertTrue(messages.contains(expectedMessage), "Actual messages: " + messages);
  }
}
