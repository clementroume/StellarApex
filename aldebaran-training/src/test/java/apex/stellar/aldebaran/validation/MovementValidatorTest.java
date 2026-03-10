package apex.stellar.aldebaran.validation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import apex.stellar.aldebaran.model.entities.Movement;
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

class MovementValidatorTest {

  private Validator validator;

  @BeforeEach
  void setUp() {
    // Initialisation du vrai moteur de validation de Spring/Hibernate
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

  /** Vérifie que l'erreur métier spécifique à la cohérence du poids de corps n'est PAS présente. */
  private void assertValidConsistency(Movement movement) {
    Set<ConstraintViolation<Movement>> violations = validator.validate(movement);
    boolean hasConsistencyError =
        violations.stream()
            .anyMatch(
                v -> "{validation.movement.bodyweight.consistency}".equals(v.getMessageTemplate()));
    assertFalse(
        hasConsistencyError,
        "Le mouvement devrait être valide concernant la contrainte de poids de corps");
  }

  // =========================================================================
  // LOGIC TESTS (Consistency Check)
  // =========================================================================

  @Test
  @DisplayName("isValid: should return true when bodyweight involved and factor > 0")
  void testIsValid_BodyweightTrue_FactorPositive() {
    Movement movement = Movement.builder().involvesBodyweight(true).bodyweightFactor(1.0).build();
    assertValidConsistency(movement);
  }

  @Test
  @DisplayName("isValid: should return true when bodyweight NOT involved and factor is 0")
  void testIsValid_BodyweightFalse_FactorZero() {
    Movement movement = Movement.builder().involvesBodyweight(false).bodyweightFactor(0.0).build();
    assertValidConsistency(movement);
  }

  @Test
  @DisplayName("isValid: should return true for null object (handled by @NotNull)")
  void testIsValid_NullObject() {
    MovementValidator customValidator = new MovementValidator();
    // Appel direct de la méthode pour tester le null-check interne (le validateur réel rejette null
    // à la racine)
    assertTrue(customValidator.isValid(null, null));
  }

  // =========================================================================
  // I18N AND INVALIDITY TESTS
  // =========================================================================

  @Test
  @DisplayName(
      "Validation i18n (EN): should fail with English message when bodyweight true, factor 0")
  void testIsValid_Inconsistent_English_BodyweightTrue_FactorZero() {
    LocaleContextHolder.setLocale(Locale.ENGLISH);

    Movement movement =
        Movement.builder()
            .involvesBodyweight(true)
            .bodyweightFactor(0.0) // Invalide !
            .build();

    Set<ConstraintViolation<Movement>> violations = validator.validate(movement);
    assertFalse(violations.isEmpty(), "There should be validation errors");

    List<String> messages = violations.stream().map(ConstraintViolation::getMessage).toList();

    String expectedMessage =
        "Bodyweight factor configuration is invalid relative to 'involvesBodyweight'.";
    assertTrue(
        messages.contains(expectedMessage),
        "Expected message not found. Actual messages: " + messages);
  }

  @Test
  @DisplayName(
      "Validation i18n (FR): should fail with French message when bodyweight false, factor positive")
  void testIsValid_Inconsistent_French_BodyweightFalse_FactorPositive() {
    LocaleContextHolder.setLocale(Locale.FRENCH);

    Movement movement =
        Movement.builder()
            .involvesBodyweight(false)
            .bodyweightFactor(0.5) // Invalide !
            .build();

    Set<ConstraintViolation<Movement>> violations = validator.validate(movement);
    assertFalse(violations.isEmpty(), "There should be validation errors");

    List<String> messages = violations.stream().map(ConstraintViolation::getMessage).toList();

    String expectedMessage =
        "La configuration du facteur de poids de corps est invalide par rapport à 'involvesBodyweight'.";
    assertTrue(
        messages.contains(expectedMessage),
        "Expected message not found. Actual messages: " + messages);
  }
}
