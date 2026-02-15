package apex.stellar.aldebaran.dto;

import static org.assertj.core.api.Assertions.assertThat;

import apex.stellar.aldebaran.model.entities.MovementMuscle.MuscleRole;
import apex.stellar.aldebaran.model.entities.Muscle.MuscleGroup;
import apex.stellar.aldebaran.model.entities.Wod.ScoreType;
import apex.stellar.aldebaran.model.entities.Wod.WodType;
import apex.stellar.aldebaran.model.entities.WodScore.ScalingLevel;
import apex.stellar.aldebaran.model.enums.Category;
import apex.stellar.aldebaran.model.enums.Equipment;
import apex.stellar.aldebaran.validation.ScoreRequestValidator;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorFactory;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.time.LocalDate;
import java.util.Collections;
import java.util.Locale;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

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

    factoryBean.setConstraintValidatorFactory(
        new ConstraintValidatorFactory() {
          @Override
          public <T extends ConstraintValidator<?, ?>> T getInstance(Class<T> key) {
            if (key == ScoreRequestValidator.class) {
              var instance = new ScoreRequestValidator();
              instance.setMessageSource(messageSource);
              return key.cast(instance);
            }

            try {
              return key.getDeclaredConstructor().newInstance();
            } catch (Exception e) {
              throw new RuntimeException("Impossible d'instancier " + key, e);
            }
          }

          @Override
          public void releaseInstance(ConstraintValidator<?, ?> instance) {
            // Rien à faire ici
          }
        });

    factoryBean.afterPropertiesSet();
    this.validator = factoryBean;
  }

  @AfterEach
  void tearDown() {
    Locale.setDefault(originalLocale);
  }

  @Test
  @DisplayName("WodRequest: Required fields (EN & FR)")
  void testWodRequest_Required() {
    WodRequest request =
        new WodRequest(
            "",
            null,
            null,
            null,
            null,
            null,
            null,
            false,
            null,
            null,
            null,
            null,
            Collections.emptyList());

    Locale.setDefault(Locale.ENGLISH);
    Set<ConstraintViolation<WodRequest>> violationsEn = validator.validate(request);
    assertViolation(violationsEn, "title", "WOD title is required.");
    assertViolation(violationsEn, "wodType", "WOD type is required.");
    assertViolation(violationsEn, "scoreType", "Score type is required.");
    assertViolation(violationsEn, "movements", "A WOD must contain at least one movement.");

    Locale.setDefault(Locale.FRENCH);
    Set<ConstraintViolation<WodRequest>> violationsFr = validator.validate(request);
    assertViolation(violationsFr, "title", "Le titre du WOD est requis.");
    assertViolation(violationsFr, "wodType", "Le type de WOD est requis.");
    assertViolation(violationsFr, "scoreType", "Le type de score est requis.");
    assertViolation(violationsFr, "movements", "Un WOD doit contenir au moins un mouvement.");
  }

  @Test
  @DisplayName("WodRequest: Size & Min constraints (EN & FR)")
  void testWodRequest_Constraints() {
    //noinspection DataFlowIssue
    WodRequest request =
        new WodRequest(
            "A".repeat(101),
            WodType.FOR_TIME,
            ScoreType.TIME,
            "A".repeat(4001),
            "A".repeat(4001),
            null,
            null,
            false,
            -1,
            -1,
            null,
            null,
            Collections.singletonList(
                new WodMovementRequest(
                    "1", 1, null, null, null, null, null, null, null, null, null, null)));

    Locale.setDefault(Locale.ENGLISH);
    Set<ConstraintViolation<WodRequest>> violationsEn = validator.validate(request);
    assertViolation(violationsEn, "title", "WOD title cannot exceed 100 characters.");
    assertViolation(violationsEn, "description", "Description cannot exceed 4000 characters.");
    assertViolation(violationsEn, "notes", "Notes cannot exceed 4000 characters.");
    assertViolation(violationsEn, "timeCapSeconds", "Time cap must be 0 or positive.");
    assertViolation(violationsEn, "emomInterval", "EMOM settings must be 0 or positive.");

    Locale.setDefault(Locale.FRENCH);
    Set<ConstraintViolation<WodRequest>> violationsFr = validator.validate(request);
    assertViolation(violationsFr, "title", "Le titre du WOD ne peut pas dépasser 100 caractères.");
    assertViolation(
        violationsFr, "description", "La description ne peut pas dépasser 4000 caractères.");
    assertViolation(violationsFr, "timeCapSeconds", "Le time cap doit être positif ou nul.");
  }

  @Test
  @DisplayName("WodMovementRequest: Required fields (EN & FR)")
  void testWodMovementRequest_Required() {
    WodMovementRequest request =
        new WodMovementRequest(
            "", null, null, null, null, null, null, null, null, null, null, null);

    Locale.setDefault(Locale.ENGLISH);
    Set<ConstraintViolation<WodMovementRequest>> violationsEn = validator.validate(request);
    assertViolation(violationsEn, "movementId", "Movement ID is required.");
    assertViolation(violationsEn, "orderIndex", "Order index is required.");

    Locale.setDefault(Locale.FRENCH);
    Set<ConstraintViolation<WodMovementRequest>> violationsFr = validator.validate(request);
    assertViolation(violationsFr, "movementId", "L'identifiant du mouvement est requis.");
    assertViolation(violationsFr, "orderIndex", "L'index d'ordre est requis.");
  }

  @Test
  @DisplayName("WodMovementRequest: Value constraints (EN & FR)")
  void testWodMovementRequest_Constraints() {
    //noinspection DataFlowIssue
    WodMovementRequest request =
        new WodMovementRequest(
            "ID", 0, "A".repeat(51), -1.0, null, -1, null, -1.0, null, -1, null, null);

    Locale.setDefault(Locale.ENGLISH);
    Set<ConstraintViolation<WodMovementRequest>> violationsEn = validator.validate(request);

    assertViolation(violationsEn, "orderIndex", "Order index must be positive.");
    assertViolation(violationsEn, "repsScheme", "Rep scheme cannot exceed 50 characters.");
    assertViolation(violationsEn, "weight", "Value must be 0 or positive.");
    assertViolation(violationsEn, "durationSeconds", "Value must be 0 or positive.");
  }

  @Test
  @DisplayName("MuscleRequest: Required fields (EN & FR)")
  void testMuscleRequest_Required() {
    MuscleRequest request = new MuscleRequest("", null, null, null, null, null);

    Locale.setDefault(Locale.ENGLISH);
    Set<ConstraintViolation<MuscleRequest>> violationsEn = validator.validate(request);
    assertViolation(violationsEn, "medicalName", "Medical name is required.");
    assertViolation(violationsEn, "muscleGroup", "Muscle group is required.");

    Locale.setDefault(Locale.FRENCH);
    Set<ConstraintViolation<MuscleRequest>> violationsFr = validator.validate(request);
    assertViolation(violationsFr, "medicalName", "Le nom médical est requis.");
    assertViolation(violationsFr, "muscleGroup", "Le groupe musculaire est requis.");
  }

  @Test
  @DisplayName("MuscleRequest: Size constraints (EN & FR)")
  void testMuscleRequest_Size() {
    String longName = "A".repeat(101);
    MuscleRequest request =
        new MuscleRequest(longName, longName, null, null, null, MuscleGroup.CHEST);

    Locale.setDefault(Locale.ENGLISH);
    Set<ConstraintViolation<MuscleRequest>> violationsEn = validator.validate(request);
    assertViolation(violationsEn, "medicalName", "Medical name cannot exceed 100 characters.");
    assertViolation(violationsEn, "commonNameEn", "Common name cannot exceed 100 characters.");

    Locale.setDefault(Locale.FRENCH);
    Set<ConstraintViolation<MuscleRequest>> violationsFr = validator.validate(request);
    assertViolation(
        violationsFr, "medicalName", "Le nom médical ne peut pas dépasser 100 caractères.");
  }

  @Test
  @DisplayName("MovementRequest: Required fields (EN & FR)")
  void testMovementRequest_Required() {
    MovementRequest request =
        new MovementRequest(
            "", null, null, null, null, null, false, null, null, null, null, null, null, null);

    Locale.setDefault(Locale.ENGLISH);
    Set<ConstraintViolation<MovementRequest>> violationsEn = validator.validate(request);
    assertViolation(violationsEn, "name", "Movement name is required.");
    assertViolation(violationsEn, "category", "Category is required.");

    Locale.setDefault(Locale.FRENCH);
    Set<ConstraintViolation<MovementRequest>> violationsFr = validator.validate(request);
    assertViolation(violationsFr, "name", "Le nom du mouvement est requis.");
    assertViolation(violationsFr, "category", "La catégorie est requise.");
  }

  @Test
  @DisplayName("MovementRequest: Logic & Size constraints (EN & FR)")
  void testMovementRequest_Constraints() {
    String longName = "A".repeat(51);
    String longUrl = "https://" + "a".repeat(510);

    MovementRequest request =
        new MovementRequest(
            longName,
            "ABBR",
            Category.SQUAT,
            Collections.singleton(Equipment.BARBELL),
            Collections.emptySet(),
            Collections.emptyList(),
            true,
            1.5,
            null,
            null,
            null,
            null,
            longUrl,
            null);

    Locale.setDefault(Locale.ENGLISH);
    Set<ConstraintViolation<MovementRequest>> violationsEn = validator.validate(request);

    assertViolation(violationsEn, "name", "Movement name cannot exceed 50 characters.");
    assertViolation(violationsEn, "bodyweightFactor", "Bodyweight factor must be at most 1.0.");
    assertViolation(violationsEn, "videoUrl", "URL cannot exceed 512 characters.");

    MovementRequest negativeFactor =
        new MovementRequest(
            "Name",
            null,
            Category.SQUAT,
            null,
            null,
            null,
            true,
            -0.1,
            null,
            null,
            null,
            null,
            null,
            null);
    assertViolation(
        validator.validate(negativeFactor),
        "bodyweightFactor",
        "Bodyweight factor must be at least 0.0.");
  }

  @Test
  @DisplayName("MovementMuscleRequest: Required fields (EN & FR)")
  void testMovementMuscleRequest_Required() {
    MovementMuscleRequest request = new MovementMuscleRequest("", null, null);

    Locale.setDefault(Locale.ENGLISH);
    Set<ConstraintViolation<MovementMuscleRequest>> violationsEn = validator.validate(request);
    assertViolation(violationsEn, "medicalName", "Medical name is required.");
    assertViolation(violationsEn, "role", "Muscle role is required.");

    Locale.setDefault(Locale.FRENCH);
    Set<ConstraintViolation<MovementMuscleRequest>> violationsFr = validator.validate(request);
    assertViolation(violationsFr, "medicalName", "Le nom médical est requis.");
    assertViolation(violationsFr, "role", "Le rôle du muscle est requis.");
  }

  @Test
  @DisplayName("MovementMuscleRequest: Impact Factor Range (EN)")
  void testMovementMuscleRequest_Range() {
    MovementMuscleRequest reqMax = new MovementMuscleRequest("Name", MuscleRole.AGONIST, 1.1);
    MovementMuscleRequest reqMin = new MovementMuscleRequest("Name", MuscleRole.AGONIST, -0.1);

    Locale.setDefault(Locale.ENGLISH);

    Set<ConstraintViolation<MovementMuscleRequest>> vMax = validator.validate(reqMax);
    assertViolation(vMax, "impactFactor", "Impact factor must be at most 1.0.");

    Set<ConstraintViolation<MovementMuscleRequest>> vMin = validator.validate(reqMin);
    assertViolation(vMin, "impactFactor", "Impact factor must be at least 0.0.");
  }

  @Test
  @DisplayName("WodScoreRequest: Required fields & Future Date (EN & FR)")
  void testWodScoreRequest_Validation() {
    WodScoreRequest request =
        new WodScoreRequest(
            null,
            null,
            LocalDate.now().plusDays(1),
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            false,
            null,
            null);

    Locale.setDefault(Locale.ENGLISH);
    Set<ConstraintViolation<WodScoreRequest>> violationsEn = validator.validate(request);
    assertViolation(violationsEn, "wodId", "WOD ID is required.");
    assertViolation(violationsEn, "date", "Date cannot be in the future.");
    assertViolation(violationsEn, "scaling", "Scaling level is required.");

    Locale.setDefault(Locale.FRENCH);
    Set<ConstraintViolation<WodScoreRequest>> violationsFr = validator.validate(request);
    assertViolation(violationsFr, "wodId", "L'ID du WOD est requis.");
    assertViolation(violationsFr, "date", "La date ne peut pas être dans le futur.");
    assertViolation(violationsFr, "scaling", "Le niveau de scaling est requis.");
  }

  @Test
  @DisplayName("WodScoreRequest: Value constraints (EN & FR)")
  void testWodScoreRequest_Constraints() {
    //noinspection DataFlowIssue
    WodScoreRequest request =
        new WodScoreRequest(
            null,
            1L,
            LocalDate.now(),
            -1,
            0,
            null,
            null,
            -10.0,
            null,
            null,
            null,
            null,
            null,
            ScalingLevel.RX,
            false,
            "A".repeat(4001),
            null);

    Locale.setDefault(Locale.ENGLISH);
    Set<ConstraintViolation<WodScoreRequest>> violations = validator.validate(request);

    assertViolation(violations, "timeMinutes", "Value must be 0 or positive.");
    assertViolation(violations, "maxWeight", "Value must be 0 or positive.");
    assertViolation(violations, "scalingNotes", "Scaling notes cannot exceed 4000 characters.");
  }

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
