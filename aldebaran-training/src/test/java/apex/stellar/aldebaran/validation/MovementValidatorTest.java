package apex.stellar.aldebaran.validation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import apex.stellar.aldebaran.model.entities.Movement;
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
class MovementValidatorTest {

  @Mock private MessageSource messageSource;
  @InjectMocks private MovementValidator validator;

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
  @DisplayName("isValid: should return true when bodyweight involved and factor > 0")
  void testIsValid_BodyweightTrue_FactorPositive() {
    Movement movement = Movement.builder()
        .involvesBodyweight(true)
        .bodyweightFactor(1.0)
        .build();

    assertTrue(validator.isValid(movement, context));
  }

  @Test
  @DisplayName("isValid: should return true when bodyweight NOT involved and factor is 0")
  void testIsValid_BodyweightFalse_FactorZero() {
    Movement movement = Movement.builder()
        .involvesBodyweight(false)
        .bodyweightFactor(0.0)
        .build();

    assertTrue(validator.isValid(movement, context));
  }

  @Test
  @DisplayName("isValid: should return false when bodyweight involved but factor is 0")
  void testIsValid_Inconsistent_TrueZero() {
    Movement movement = Movement.builder()
        .involvesBodyweight(true)
        .bodyweightFactor(0.0)
        .build();

    mockContextForFailure();

    assertFalse(validator.isValid(movement, context));
  }

  @Test
  @DisplayName("isValid: should return false when bodyweight NOT involved but factor > 0")
  void testIsValid_Inconsistent_FalsePositive() {
    Movement movement = Movement.builder()
        .involvesBodyweight(false)
        .bodyweightFactor(0.5)
        .build();

    mockContextForFailure();

    assertFalse(validator.isValid(movement, context));
  }

  @Test
  @DisplayName("isValid: should return true for null object (handled by @NotNull)")
  void testIsValid_NullObject() {
    assertTrue(validator.isValid(null, context));
  }
}