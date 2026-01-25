package apex.stellar.antares.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.Locale;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

/**
 * Test d'intégration complet pour l'internationalisation des DTOs.
 *
 * <p>Ce test charge les VRAIS fichiers {@code messages.properties} et {@code
 * messages_fr.properties}. Il parcourt TOUS les DTOs ayant des règles de validation pour s'assurer
 * que les clés sont correctes et que les traductions (EN/FR) sont bien chargées.
 */
class DtoLocalizationTest {

  private Validator validator;
  private Locale originalLocale;

  @BeforeEach
  void setUp() {
    originalLocale = Locale.getDefault();

    ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
    messageSource.setBasename("messages");
    messageSource.setDefaultEncoding("UTF-8");

    LocalValidatorFactoryBean factoryBean = new LocalValidatorFactoryBean();
    factoryBean.setValidationMessageSource(messageSource);
    factoryBean.afterPropertiesSet();

    this.validator = factoryBean;
  }

  @AfterEach
  void tearDown() {
    Locale.setDefault(originalLocale);
  }

  // ==========================================
  // 1. Authentication & Registration
  // ==========================================

  @Test
  @DisplayName("AuthenticationRequest: EN & FR validation")
  void testAuthenticationRequest() {
    AuthenticationRequest request = new AuthenticationRequest("", "");

    // English
    Locale.setDefault(Locale.ENGLISH);
    Set<ConstraintViolation<AuthenticationRequest>> violationsEn = validator.validate(request);
    assertViolation(violationsEn, "email", "Email is required");
    assertViolation(violationsEn, "password", "Password is required");

    // Français
    Locale.setDefault(Locale.FRENCH);
    Set<ConstraintViolation<AuthenticationRequest>> violationsFr = validator.validate(request);
    assertViolation(violationsFr, "email", "L'adresse email est requise.");
    assertViolation(violationsFr, "password", "Le mot de passe est requis.");
  }

  @Test
  @DisplayName("RegisterRequest: EN & FR validation")
  void testRegisterRequest() {
    RegisterRequest request = new RegisterRequest(null, null, "bad-email", "short");

    // English
    Locale.setDefault(Locale.ENGLISH);
    Set<ConstraintViolation<RegisterRequest>> violationsEn = validator.validate(request);
    assertViolation(violationsEn, "firstName", "The first name is required");
    assertViolation(violationsEn, "lastName", "Last name is required");
    assertViolation(violationsEn, "email", "Email must be a valid email address");
    assertViolation(violationsEn, "password", "Password must be at least 8 characters long");

    // Français
    Locale.setDefault(Locale.FRENCH);
    Set<ConstraintViolation<RegisterRequest>> violationsFr = validator.validate(request);
    assertViolation(violationsFr, "firstName", "Le prénom est requis.");
    assertViolation(violationsFr, "lastName", "Le nom de famille est requis.");
    assertViolation(violationsFr, "email", "L'adresse email doit être un format valide.");
    assertViolation(
        violationsFr, "password", "Le mot de passe doit contenir au moins 8 caractères.");
  }

  @Test
  @DisplayName("RegisterRequest: Size validation (EN & FR)")
  void testRegisterRequest_Size() {
    String longName = "a".repeat(51); // > 50
    String longEmail = "a".repeat(250) + "@test.com"; // > 255

    RegisterRequest request = new RegisterRequest(longName, longName, longEmail, "password");

    // English
    Locale.setDefault(Locale.ENGLISH);
    Set<ConstraintViolation<RegisterRequest>> violationsEn = validator.validate(request);
    assertViolation(violationsEn, "firstName", "The first name cannot exceed 50 characters");
    assertViolation(violationsEn, "lastName", "Last name cannot exceed 50 characters");
    assertViolation(violationsEn, "email", "Email cannot exceed 255 characters");

    // Français
    Locale.setDefault(Locale.FRENCH);
    Set<ConstraintViolation<RegisterRequest>> violationsFr = validator.validate(request);
    assertViolation(violationsFr, "firstName", "Le prénom ne peut pas dépasser 50 caractères.");
    assertViolation(
        violationsFr, "lastName", "Le nom de famille ne peut pas dépasser 50 caractères.");
    assertViolation(violationsFr, "email", "L'email ne peut pas dépasser 255 caractères.");
  }

  // ==========================================
  // 2. User Profile & Preferences
  // ==========================================

  @Test
  @DisplayName("ChangePasswordRequest: EN & FR validation")
  void testChangePasswordRequest() {
    ChangePasswordRequest request = new ChangePasswordRequest(null, null, null);

    // English
    Locale.setDefault(Locale.ENGLISH);
    Set<ConstraintViolation<ChangePasswordRequest>> violationsEn = validator.validate(request);
    assertViolation(violationsEn, "currentPassword", "Current password is required");
    assertViolation(violationsEn, "newPassword", "A new password is required");
    assertViolation(violationsEn, "confirmationPassword", "Confirmation password is required");

    // Français
    Locale.setDefault(Locale.FRENCH);
    Set<ConstraintViolation<ChangePasswordRequest>> violationsFr = validator.validate(request);
    assertViolation(violationsFr, "currentPassword", "Le mot de passe actuel est requis.");
    assertViolation(violationsFr, "newPassword", "Le nouveau mot de passe est requis.");
    assertViolation(
        violationsFr, "confirmationPassword", "Le mot de passe de confirmation est requis.");
  }

  @Test
  @DisplayName("ProfileUpdateRequest: EN & FR validation")
  void testProfileUpdateRequest() {
    ProfileUpdateRequest request = new ProfileUpdateRequest(null, null, "");

    // English
    Locale.setDefault(Locale.ENGLISH);
    Set<ConstraintViolation<ProfileUpdateRequest>> violationsEn = validator.validate(request);
    assertViolation(violationsEn, "firstName", "The first name is required");
    assertViolation(violationsEn, "lastName", "Last name is required");
    assertViolation(violationsEn, "email", "Email is required");

    // Français
    Locale.setDefault(Locale.FRENCH);
    Set<ConstraintViolation<ProfileUpdateRequest>> violationsFr = validator.validate(request);
    assertViolation(violationsFr, "firstName", "Le prénom est requis.");
    assertViolation(violationsFr, "lastName", "Le nom de famille est requis.");
    assertViolation(violationsFr, "email", "L'adresse email est requise.");
  }

  @Test
  @DisplayName("PreferencesUpdateRequest: EN & FR validation")
  void testPreferencesUpdateRequest() {
    PreferencesUpdateRequest request = new PreferencesUpdateRequest("bad", "blue");

    // English
    Locale.setDefault(Locale.ENGLISH);
    Set<ConstraintViolation<PreferencesUpdateRequest>> violationsEn = validator.validate(request);
    assertViolation(
        violationsEn, "locale", "Locale must be in a valid format (e.g., 'en' or 'en-US')");
    assertViolation(violationsEn, "theme", "Theme must be either 'light' or 'dark'");

    // Français
    Locale.setDefault(Locale.FRENCH);
    Set<ConstraintViolation<PreferencesUpdateRequest>> violationsFr = validator.validate(request);
    assertViolation(
        violationsFr,
        "locale",
        "La locale doit être dans un format valide (ex : 'fr' ou 'fr-FR').");
    assertViolation(violationsFr, "theme", "Le thème doit être 'light' ou 'dark'.");
  }

  @Test
  @DisplayName("PreferencesUpdateRequest: Required fields (EN & FR)")
  void testPreferencesUpdateRequest_Required() {
    PreferencesUpdateRequest request = new PreferencesUpdateRequest(null, "");

    // English
    Locale.setDefault(Locale.ENGLISH);
    Set<ConstraintViolation<PreferencesUpdateRequest>> violationsEn = validator.validate(request);
    assertViolation(violationsEn, "locale", "Locale is required");
    assertViolation(violationsEn, "theme", "Theme is required");

    // Français
    Locale.setDefault(Locale.FRENCH);
    Set<ConstraintViolation<PreferencesUpdateRequest>> violationsFr = validator.validate(request);
    assertViolation(violationsFr, "locale", "La locale est requise.");
    assertViolation(violationsFr, "theme", "Le thème est requis.");
  }

  // ==========================================
  // 3. Gym Management
  // ==========================================

  @Test
  @DisplayName("GymRequest: EN & FR validation")
  void testGymRequest() {
    GymRequest request = new GymRequest("", null, null, "");

    // English
    Locale.setDefault(Locale.ENGLISH);
    Set<ConstraintViolation<GymRequest>> violationsEn = validator.validate(request);
    assertViolation(violationsEn, "name", "Gym name is required");
    assertViolation(violationsEn, "isProgramming", "Programming flag is required");
    assertViolation(violationsEn, "creationToken", "Creation token is required");

    // Français
    Locale.setDefault(Locale.FRENCH);
    Set<ConstraintViolation<GymRequest>> violationsFr = validator.validate(request);
    assertViolation(violationsFr, "name", "Le nom de la salle est obligatoire.");
    assertViolation(
        violationsFr,
        "isProgramming",
        "L''indicateur de type (Programmation ou Physique) est obligatoire.");
    assertViolation(violationsFr, "creationToken", "Le jeton de création (token) est obligatoire.");
  }

  @Test
  @DisplayName("GymSettingsRequest: EN & FR validation")
  void testGymSettingsRequest() {
    GymSettingsRequest request = new GymSettingsRequest("", null);

    // English
    Locale.setDefault(Locale.ENGLISH);
    Set<ConstraintViolation<GymSettingsRequest>> violationsEn = validator.validate(request);
    assertViolation(violationsEn, "enrollmentCode", "Enrollment code is required");
    assertViolation(violationsEn, "isAutoSubscription", "Auto-subscription flag is required");

    // Français
    Locale.setDefault(Locale.FRENCH);
    Set<ConstraintViolation<GymSettingsRequest>> violationsFr = validator.validate(request);
    assertViolation(violationsFr, "enrollmentCode", "Le code d'inscription est obligatoire.");
    assertViolation(
        violationsFr,
        "isAutoSubscription",
        "Le paramètre d'inscription automatique est obligatoire.");
  }

  @Test
  @DisplayName("JoinGymRequest: EN & FR validation")
  void testJoinGymRequest() {
    JoinGymRequest request = new JoinGymRequest(null, "");

    // English
    Locale.setDefault(Locale.ENGLISH);
    Set<ConstraintViolation<JoinGymRequest>> violationsEn = validator.validate(request);
    assertViolation(violationsEn, "gymId", "Gym ID is required");
    assertViolation(violationsEn, "enrollmentCode", "Enrollment code is required");

    // Français
    Locale.setDefault(Locale.FRENCH);
    Set<ConstraintViolation<JoinGymRequest>> violationsFr = validator.validate(request);
    assertViolation(violationsFr, "gymId", "L'identifiant de la salle est obligatoire.");
    assertViolation(violationsFr, "enrollmentCode", "Le code d'inscription est obligatoire.");
  }

  // ==========================================
  // 4. Membership Management
  // ==========================================

  @Test
  @DisplayName("MembershipUpdateRequest: EN & FR validation")
  void testMembershipUpdateRequest() {
    MembershipUpdateRequest request = new MembershipUpdateRequest(null, null, null);

    // English
    Locale.setDefault(Locale.ENGLISH);
    Set<ConstraintViolation<MembershipUpdateRequest>> violationsEn = validator.validate(request);
    assertViolation(violationsEn, "status", "Membership status is required");
    assertViolation(violationsEn, "gymRole", "Role is required");
    assertViolation(violationsEn, "permissions", "Permissions set cannot be null");

    // Français
    Locale.setDefault(Locale.FRENCH);
    Set<ConstraintViolation<MembershipUpdateRequest>> violationsFr = validator.validate(request);
    assertViolation(violationsFr, "status", "Le statut de l'adhésion est obligatoire.");
    assertViolation(violationsFr, "gymRole", "Le rôle est obligatoire.");
    assertViolation(violationsFr, "permissions", "La liste des permissions est obligatoire.");
  }

  // ==========================================
  // Helper
  // ==========================================

  private <T> void assertViolation(
      Set<ConstraintViolation<T>> violations, String property, String expectedMessage) {
    boolean match =
        violations.stream()
            .anyMatch(
                v ->
                    v.getPropertyPath().toString().equals(property)
                        && v.getMessage().equals(expectedMessage));

    assertThat(match)
        .withFailMessage(
            "Erreur manquante sur le champ '%s'. \nAttendu : '%s'. \nTrouvé : %s",
            property,
            expectedMessage,
            violations.stream().map(ConstraintViolation::getMessage).toList())
        .isTrue();
  }
}
