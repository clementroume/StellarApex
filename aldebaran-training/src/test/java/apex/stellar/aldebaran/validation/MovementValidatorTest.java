package apex.stellar.aldebaran.validation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import apex.stellar.aldebaran.model.entities.Movement;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.ConstraintValidatorContext.ConstraintViolationBuilder;
import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
// Not used for MessageSource anymore, but kept for context if needed
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.ResourceBundleMessageSource;

@ExtendWith(MockitoExtension.class)
class MovementValidatorTest {

  @InjectMocks private MovementValidator validator;

  private ConstraintValidatorContext context;
  private ConstraintViolationBuilder builder;

  @BeforeEach
  void setUp() {
    // 1. Setup Real MessageSource to verify properties files existence
    ResourceBundleMessageSource realMessageSource = new ResourceBundleMessageSource();
    realMessageSource.setBasename("messages"); // Checks messages.properties and messages_fr.properties
    realMessageSource.setDefaultEncoding("UTF-8");
    realMessageSource.setUseCodeAsDefaultMessage(true);

    // Inject real source into validator
    validator.setMessageSource(realMessageSource);

    // 2. Mock Context chain
    context = mock(ConstraintValidatorContext.class);
    builder = mock(ConstraintViolationBuilder.class);
  }

  @AfterEach
  void tearDown() {
    // Reset Locale to avoid polluting other tests
    LocaleContextHolder.resetLocaleContext();
  }

  private void prepareContextMock() {
    when(context.buildConstraintViolationWithTemplate(anyString())).thenReturn(builder);
    when(builder.addConstraintViolation()).thenReturn(context);
  }

  // =========================================================================
  // LOGIC TESTS (Consistency Check)
  // =========================================================================

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
  @DisplayName("isValid: should return true for null object (handled by @NotNull)")
  void testIsValid_NullObject() {
    assertTrue(validator.isValid(null, context));
  }

  // =========================================================================
  // I18N MESSAGING TESTS (Checking Properties content)
  // =========================================================================

  @Test
  @DisplayName("isValid: should fail with ENGLISH message when inconsistent")
  void testIsValid_Inconsistent_English() {
    // Set Locale to English
    LocaleContextHolder.setLocale(Locale.ENGLISH);
    prepareContextMock();

    Movement movement = Movement.builder()
        .involvesBodyweight(true)
        .bodyweightFactor(0.0) // Invalid
        .build();

    // Execute
    boolean isValid = validator.isValid(movement, context);

    // Verify
    assertFalse(isValid);

    // Check that the context was called with the REAL message from messages.properties
    verify(context).buildConstraintViolationWithTemplate("Bodyweight factor configuration is invalid relative to 'involvesBodyweight'.");
  }

  @Test
  @DisplayName("isValid: should fail with FRENCH message when inconsistent")
  void testIsValid_Inconsistent_French() {
    // Set Locale to French
    LocaleContextHolder.setLocale(Locale.FRENCH);
    prepareContextMock();

    Movement movement = Movement.builder()
        .involvesBodyweight(false)
        .bodyweightFactor(0.5) // Invalid
        .build();

    // Execute
    boolean isValid = validator.isValid(movement, context);

    // Verify
    assertFalse(isValid);

    // Check that the context was called with the REAL message from messages_fr.properties
    verify(context).buildConstraintViolationWithTemplate("La configuration du facteur de poids de corps est invalide par rapport à 'involvesBodyweight'.");
  }
}