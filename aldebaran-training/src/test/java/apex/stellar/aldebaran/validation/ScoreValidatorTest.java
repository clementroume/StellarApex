package apex.stellar.aldebaran.validation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import apex.stellar.aldebaran.model.entities.Wod;
import apex.stellar.aldebaran.model.entities.Wod.ScoreType;
import apex.stellar.aldebaran.model.entities.WodScore;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.ConstraintValidatorContext.ConstraintViolationBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;

@ExtendWith(MockitoExtension.class)
class ScoreValidatorTest {

  @Mock private MessageSource messageSource;
  @InjectMocks private ScoreValidator validator;

  private ConstraintValidatorContext context;
  private ConstraintViolationBuilder builder;

  @BeforeEach
  void setUp() {
    context = mock(ConstraintValidatorContext.class);
    builder = mock(ConstraintViolationBuilder.class);
  }

  private void mockContextForFailure() {
    when(context.buildConstraintViolationWithTemplate(anyString())).thenReturn(builder);
    when(builder.addConstraintViolation()).thenReturn(context);
    when(messageSource.getMessage(anyString(), any(), anyString(), any())).thenReturn("Error message");
  }

  @Test
  @DisplayName("isValid: should return true for valid TIME score")
  void testIsValid_Time_Success() {
    Wod wod = Wod.builder().scoreType(ScoreType.TIME).build();
    WodScore score = WodScore.builder().wod(wod).timeSeconds(100).build();

    assertTrue(validator.isValid(score, context));
  }

  @Test
  @DisplayName("isValid: should return false for missing TIME score")
  void testIsValid_Time_Failure() {
    Wod wod = Wod.builder().scoreType(ScoreType.TIME).build();
    WodScore score = WodScore.builder().wod(wod).timeSeconds(null).build();

    mockContextForFailure();

    assertFalse(validator.isValid(score, context));
  }

  @Test
  @DisplayName("isValid: should return true for ROUNDS_REPS if either is present")
  void testIsValid_RoundsReps_Success() {
    Wod wod = Wod.builder().scoreType(ScoreType.ROUNDS_REPS).build();
    
    // Case 1: Rounds only
    WodScore scoreRounds = WodScore.builder().wod(wod).rounds(5).build();
    assertTrue(validator.isValid(scoreRounds, context));

    // Case 2: Reps only
    WodScore scoreReps = WodScore.builder().wod(wod).reps(10).build();
    assertTrue(validator.isValid(scoreReps, context));
  }

  @Test
  @DisplayName("isValid: should return false for ROUNDS_REPS if both missing")
  void testIsValid_RoundsReps_Failure() {
    Wod wod = Wod.builder().scoreType(ScoreType.ROUNDS_REPS).build();
    WodScore score = WodScore.builder().wod(wod).rounds(null).reps(null).build();

    mockContextForFailure();

    assertFalse(validator.isValid(score, context));
  }

  @Test
  @DisplayName("isValid: should validate WEIGHT score (maxWeightKg required)")
  void testIsValid_Weight() {
    Wod wod = Wod.builder().scoreType(ScoreType.WEIGHT).build();
    
    // Success
    WodScore valid = WodScore.builder().wod(wod).maxWeightKg(100.0).build();
    assertTrue(validator.isValid(valid, context));

    // Failure
    WodScore invalid = WodScore.builder().wod(wod).maxWeightKg(null).build();
    mockContextForFailure();
    assertFalse(validator.isValid(invalid, context));
  }

  @Test
  @DisplayName("isValid: should validate DISTANCE score (totalDistanceMeters required)")
  void testIsValid_Distance() {
    Wod wod = Wod.builder().scoreType(ScoreType.DISTANCE).build();
    
    // Success
    WodScore valid = WodScore.builder().wod(wod).totalDistanceMeters(5000.0).build();
    assertTrue(validator.isValid(valid, context));

    // Failure
    WodScore invalid = WodScore.builder().wod(wod).totalDistanceMeters(null).build();
    mockContextForFailure();
    assertFalse(validator.isValid(invalid, context));
  }

  @Test
  @DisplayName("isValid: should handle null object gracefully")
  void testIsValid_Null() {
    assertTrue(validator.isValid(null, context));
  }
}