package apex.stellar.aldebaran.validation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import apex.stellar.aldebaran.model.entities.Wod;
import apex.stellar.aldebaran.model.entities.Wod.ScoreType;
import apex.stellar.aldebaran.model.entities.WodScore;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.ConstraintValidatorContext.ConstraintViolationBuilder;
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
class ScoreValidatorTest {

  @InjectMocks private ScoreValidator validator;

  private ConstraintValidatorContext context;
  private ConstraintViolationBuilder builder;

  @BeforeEach
  void setUp() {
    ResourceBundleMessageSource realMessageSource = new ResourceBundleMessageSource();
    realMessageSource.setBasename("messages");
    realMessageSource.setDefaultEncoding("UTF-8");
    realMessageSource.setUseCodeAsDefaultMessage(true);

    validator.setMessageSource(realMessageSource);

    context = mock(ConstraintValidatorContext.class);
    builder = mock(ConstraintViolationBuilder.class);
  }

  @AfterEach
  void tearDown() {
    LocaleContextHolder.resetLocaleContext();
  }

  private void prepareContextForFailure() {
    when(context.buildConstraintViolationWithTemplate(anyString())).thenReturn(builder);
    when(builder.addConstraintViolation()).thenReturn(context);
  }

  private WodScore createScore(ScoreType type) {
    return WodScore.builder().wod(Wod.builder().scoreType(type).build()).build();
  }

  // =========================================================================
  // 1. TIME
  // =========================================================================

  @Test
  @DisplayName("TIME: Valid if timeSeconds is present")
  void testTime_Valid() {
    WodScore score = createScore(ScoreType.TIME);
    score.setTimeSeconds(120);
    assertTrue(validator.isValid(score, context));
  }

  @Test
  @DisplayName("TIME: Invalid if timeSeconds is null")
  void testTime_Invalid() {
    WodScore score = createScore(ScoreType.TIME);
    score.setTimeSeconds(null); // Explicit

    prepareContextForFailure();
    assertFalse(validator.isValid(score, context));
  }

  // =========================================================================
  // 2. ROUNDS_REPS (AMRAP Mixte)
  // =========================================================================

  @Test
  @DisplayName("ROUNDS_REPS: Valid if Rounds is present")
  void testRoundsReps_ValidRounds() {
    WodScore score = createScore(ScoreType.ROUNDS_REPS);
    score.setRounds(5);
    assertTrue(validator.isValid(score, context));
  }

  @Test
  @DisplayName("ROUNDS_REPS: Valid if Reps is present")
  void testRoundsReps_ValidReps() {
    WodScore score = createScore(ScoreType.ROUNDS_REPS);
    score.setReps(50);
    assertTrue(validator.isValid(score, context));
  }

  @Test
  @DisplayName("ROUNDS_REPS: Invalid if both are null")
  void testRoundsReps_Invalid() {
    WodScore score = createScore(ScoreType.ROUNDS_REPS);
    // Rounds & Reps null

    prepareContextForFailure();
    assertFalse(validator.isValid(score, context));
  }

  // =========================================================================
  // 3. REPS (AMRAP simple)
  // =========================================================================

  @Test
  @DisplayName("REPS: Valid if Reps is present")
  void testReps_Valid() {
    WodScore score = createScore(ScoreType.REPS);
    score.setReps(100);
    assertTrue(validator.isValid(score, context));
  }

  @Test
  @DisplayName("REPS: Invalid if Reps is null")
  void testReps_Invalid() {
    WodScore score = createScore(ScoreType.REPS);
    // Reps null

    prepareContextForFailure();
    assertFalse(validator.isValid(score, context));
  }

  // =========================================================================
  // 4. WEIGHT (1RM etc.)
  // =========================================================================

  @Test
  @DisplayName("WEIGHT: Valid if MaxWeightKg is present")
  void testWeight_Valid() {
    WodScore score = createScore(ScoreType.WEIGHT);
    score.setMaxWeightKg(100.0);
    assertTrue(validator.isValid(score, context));
  }

  @Test
  @DisplayName("WEIGHT: Invalid if MaxWeightKg is null")
  void testWeight_Invalid() {
    WodScore score = createScore(ScoreType.WEIGHT);

    prepareContextForFailure();
    assertFalse(validator.isValid(score, context));
  }

  // =========================================================================
  // 5. LOAD (Total Volume)
  // =========================================================================

  @Test
  @DisplayName("LOAD: Valid if TotalLoadKg is present")
  void testLoad_Valid() {
    WodScore score = createScore(ScoreType.LOAD);
    score.setTotalLoadKg(5000.0);
    assertTrue(validator.isValid(score, context));
  }

  @Test
  @DisplayName("LOAD: Invalid if TotalLoadKg is null")
  void testLoad_Invalid() {
    WodScore score = createScore(ScoreType.LOAD);

    prepareContextForFailure();
    assertFalse(validator.isValid(score, context));
  }

  // =========================================================================
  // 6. CALORIES
  // =========================================================================

  @Test
  @DisplayName("CALORIES: Valid if TotalCalories is present")
  void testCalories_Valid() {
    WodScore score = createScore(ScoreType.CALORIES);
    score.setTotalCalories(50);
    assertTrue(validator.isValid(score, context));
  }

  @Test
  @DisplayName("CALORIES: Invalid if TotalCalories is null")
  void testCalories_Invalid() {
    WodScore score = createScore(ScoreType.CALORIES);

    prepareContextForFailure();
    assertFalse(validator.isValid(score, context));
  }

  // =========================================================================
  // 7. DISTANCE
  // =========================================================================

  @Test
  @DisplayName("DISTANCE: Valid if TotalDistanceMeters is present")
  void testDistance_Valid() {
    WodScore score = createScore(ScoreType.DISTANCE);
    score.setTotalDistanceMeters(2000.0);
    assertTrue(validator.isValid(score, context));
  }

  @Test
  @DisplayName("DISTANCE: Invalid if TotalDistanceMeters is null")
  void testDistance_Invalid() {
    WodScore score = createScore(ScoreType.DISTANCE);

    prepareContextForFailure();
    assertFalse(validator.isValid(score, context));
  }

  // =========================================================================
  // 8. NONE & NULL SAFETY
  // =========================================================================

  @Test
  @DisplayName("NONE: Always valid")
  void testNone_Valid() {
    WodScore score = createScore(ScoreType.NONE);
    assertTrue(validator.isValid(score, context));
  }

  @Test
  @DisplayName("Null Handling: Valid if object or Wod is null (ignored)")
  void testNullSafety() {
    assertTrue(validator.isValid(null, context));
    assertTrue(validator.isValid(new WodScore(), context)); // Wod is null
  }

  // =========================================================================
  // I18N MESSAGE CHECK
  // =========================================================================

  @Test
  @DisplayName("I18N EN: Should return correct English message")
  void testMessage_EN() {
    // 1. Setup Context
    LocaleContextHolder.setLocale(Locale.ENGLISH);
    prepareContextForFailure();

    // 2. Create Invalid Score
    WodScore score = createScore(ScoreType.TIME);
    // Assuming ScoreType.TIME.getDisplayName() returns "Time" or "TIME"
    // Adjust expectations based on your Enum implementation
    String expectedType = ScoreType.TIME.getDisplayName();

    // 3. Execute
    assertFalse(validator.isValid(score, context));

    // 4. Verify (Property: "Score does not match WOD type {0}. Missing required fields.")
    verify(context)
        .buildConstraintViolationWithTemplate(
            "Score does not match WOD type " + expectedType + ". Missing required fields.");
  }

  @Test
  @DisplayName("I18N FR: Should return correct French message with quotes")
  void testMessage_FR() {
    // 1. Setup Context
    LocaleContextHolder.setLocale(Locale.FRENCH);
    prepareContextForFailure();

    // 2. Create Invalid Score
    WodScore score = createScore(ScoreType.TIME);
    String expectedType = ScoreType.TIME.getDisplayName();

    // 3. Execute
    assertFalse(validator.isValid(score, context));

    // 4. Verify
    verify(context)
        .buildConstraintViolationWithTemplate(
            "Le score ne correspond pas au type '" + expectedType + "'. Champs requis manquants.");
  }
}
