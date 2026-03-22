package apex.stellar.aldebaran.dto;

import static org.assertj.core.api.Assertions.assertThat;

import apex.stellar.aldebaran.model.entities.MovementMuscle.MuscleRole;
import apex.stellar.aldebaran.model.entities.Muscle.MuscleGroup;
import apex.stellar.aldebaran.model.entities.Score.ScalingLevel;
import apex.stellar.aldebaran.model.entities.Wod.ScoreType;
import apex.stellar.aldebaran.model.entities.Wod.WodType;
import apex.stellar.aldebaran.model.enums.Category;
import apex.stellar.aldebaran.model.enums.Equipment;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.time.LocalDate;
import java.util.Collections;
import java.util.Locale;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

class DtoLocalizationTest {

  private Validator validator;

  @BeforeEach
  void setUp() {
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
    LocaleContextHolder.resetLocaleContext();
  }

  // ==================================================================================
  // UTILITIES
  // ==================================================================================

  private <T> void assertViolationEnAndFr(T request, String property, String msgEn, String msgFr) {
    LocaleContextHolder.setLocale(Locale.ENGLISH);
    Set<ConstraintViolation<T>> violationsEn = validator.validate(request);
    assertViolation(violationsEn, property, msgEn);

    LocaleContextHolder.setLocale(Locale.FRENCH);
    Set<ConstraintViolation<T>> violationsFr = validator.validate(request);
    assertViolation(violationsFr, property, msgFr);
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

  // ==================================================================================
  // MUSCLE REQUEST TESTS
  // ==================================================================================

  @Nested
  @DisplayName("MuscleRequest Tests")
  class MuscleRequestTests {

    @Test
    @DisplayName("Required fields")
    void requiredFields() {
      MuscleRequest request = new MuscleRequest("", null, null, null, null, null, null);

      assertViolationEnAndFr(
          request, "medicalName", "Medical name is required.", "Le nom médical est requis.");
      assertViolationEnAndFr(
          request, "muscleGroup", "Muscle group is required.", "Le groupe musculaire est requis.");
    }

    @Test
    @DisplayName("Size constraints")
    void sizeConstraints() {
      String long101 = "A".repeat(101);
      String long2001 = "A".repeat(2001);

      MuscleRequest request =
          new MuscleRequest(long101, MuscleGroup.CHEST, long101, null, long2001, null, null);

      assertViolationEnAndFr(
          request,
          "medicalName",
          "Medical name cannot exceed 100 characters.",
          "Le nom médical ne peut pas dépasser 100 caractères.");
      assertViolationEnAndFr(
          request,
          "commonNameEn",
          "Common name cannot exceed 100 characters.",
          "Le nom commun ne peut pas dépasser 100 caractères.");
      assertViolationEnAndFr(
          request,
          "descriptionEn",
          "The description must not exceed 2000 characters.",
          "La description ne doit pas dépasser 2000 caractères.");
    }
  }

  // ==================================================================================
  // MOVEMENT MUSCLE REQUEST TESTS
  // ==================================================================================

  @Nested
  @DisplayName("MovementMuscleRequest Tests")
  class MovementMuscleRequestTests {

    @Test
    @DisplayName("Required fields")
    void requiredFields() {
      MovementMuscleRequest request = new MovementMuscleRequest(null, null, null);

      assertViolationEnAndFr(request, "muscleId", "ID is required.", "L'ID est requis.");
      assertViolationEnAndFr(
          request, "role", "Muscle role is required.", "Le rôle du muscle est requis.");
    }

    @Test
    @DisplayName("Impact Factor Range")
    void rangeConstraints() {
      MovementMuscleRequest reqMax = new MovementMuscleRequest(1L, MuscleRole.AGONIST, 1.1);
      MovementMuscleRequest reqMin = new MovementMuscleRequest(1L, MuscleRole.AGONIST, -0.1);

      assertViolationEnAndFr(
          reqMax,
          "impactFactor",
          "Impact factor must be at most 1.0.",
          "Le facteur d'impact doit être au plus de 1.0.");
      assertViolationEnAndFr(
          reqMin,
          "impactFactor",
          "Impact factor must be at least 0.0.",
          "Le facteur d'impact doit être au moins de 0.0.");
    }
  }

  // ==================================================================================
  // MOVEMENT REQUEST TESTS
  // ==================================================================================

  @Nested
  @DisplayName("MovementRequest Tests")
  class MovementRequestTests {

    @Test
    @DisplayName("Required fields")
    void requiredFields() {
      MovementRequest request =
          new MovementRequest("", null, null, null, null, null, null, null, null, null, null, null);

      assertViolationEnAndFr(
          request, "name", "Movement name is required.", "Le nom du mouvement est requis.");
      assertViolationEnAndFr(
          request, "category", "Category is required.", "La catégorie est requise.");
    }

    @Test
    @DisplayName("Size constraints")
    void sizeConstraints() {
      String name51 = "A".repeat(51);
      String abbrev21 = "A".repeat(21);
      String desc4001 = "A".repeat(4001);
      String url513 = "https://" + "a".repeat(505);

      MovementRequest request =
          new MovementRequest(
              name51,
              abbrev21,
              Category.SQUAT,
              Collections.singleton(Equipment.BARBELL),
              Collections.emptySet(),
              Collections.emptySet(),
              desc4001,
              null,
              null,
              null,
              url513,
              null);

      assertViolationEnAndFr(
          request,
          "name",
          "Movement name cannot exceed 50 characters.",
          "Le nom du mouvement ne peut pas dépasser 50 caractères.");
      assertViolationEnAndFr(
          request,
          "nameAbbreviation",
          "Abbreviation cannot exceed 20 characters.",
          "L'abréviation ne peut pas dépasser 20 caractères.");
      assertViolationEnAndFr(
          request,
          "descriptionEn",
          "Text content must not exceed 4000 characters.",
          "Le contenu du texte ne doit pas dépasser 4000 caractères.");
      assertViolationEnAndFr(
          request,
          "videoUrl",
          "URL cannot exceed 512 characters.",
          "L'URL ne peut pas dépasser 512 caractères.");
    }
  }

  // ==================================================================================
  // WOD REQUEST TESTS
  // ==================================================================================

  @Nested
  @DisplayName("WodRequest Tests")
  class WodRequestTests {

    @Test
    @DisplayName("Required fields")
    void requiredFields() {
      WodRequest request =
          new WodRequest(
              "",
              null,
              null,
              null,
              null,
              false,
              null,
              null,
              null,
              null,
              null,
              null,
              Collections.emptyList());

      assertViolationEnAndFr(
          request, "title", "WOD title is required.", "Le titre du WOD est requis.");
      assertViolationEnAndFr(
          request, "wodType", "WOD type is required.", "Le type de WOD est requis.");
      assertViolationEnAndFr(
          request, "scoreType", "Score type is required.", "Le type de score est requis.");
      assertViolationEnAndFr(
          request,
          "movements",
          "A WOD must contain at least one movement.",
          "Un WOD doit contenir au moins un mouvement.");
    }

    @Test
    @DisplayName("Size & Min constraints")
    void sizeAndMinConstraints() {
      //noinspection DataFlowIssue
      WodRequest request =
          new WodRequest(
              "A".repeat(101),
              WodType.FOR_TIME,
              ScoreType.TIME,
              null,
              null,
              false,
              "A".repeat(4001),
              "A".repeat(4001),
              -1,
              -1,
              null,
              "A".repeat(101),
              Collections.singletonList(
                  new WodMovementRequest(
                      1L, 1, null, null, null, null, null, null, null, null, null, null, null,
                      null)));

      assertViolationEnAndFr(
          request,
          "title",
          "WOD title cannot exceed 100 characters.",
          "Le titre du WOD ne peut pas dépasser 100 caractères.");
      assertViolationEnAndFr(
          request,
          "description",
          "Description cannot exceed 4000 characters.",
          "La description ne peut pas dépasser 4000 caractères.");
      assertViolationEnAndFr(
          request,
          "notes",
          "Notes cannot exceed 4000 characters.",
          "Les notes ne peuvent pas dépasser 4000 caractères.");
      assertViolationEnAndFr(
          request,
          "repScheme",
          "Rep scheme summary cannot exceed 100 characters.",
          "Le schéma de répétition (résumé) ne peut pas dépasser 100 caractères.");
      assertViolationEnAndFr(
          request,
          "timeCapSeconds",
          "Time cap must be 0 or positive.",
          "Le time cap doit être positif ou nul.");
      assertViolationEnAndFr(
          request,
          "emomInterval",
          "EMOM settings must be 0 or positive.",
          "Les paramètres EMOM doivent être positifs ou nuls.");
    }
  }

  // ==================================================================================
  // WOD MOVEMENT REQUEST TESTS
  // ==================================================================================

  @Nested
  @DisplayName("WodMovementRequest Tests")
  class WodMovementRequestTests {

    @Test
    @DisplayName("Required fields")
    void requiredFields() {
      WodMovementRequest request =
          new WodMovementRequest(
              null, null, null, null, null, null, null, null, null, null, null, null, null, null);

      assertViolationEnAndFr(
          request,
          "movementId",
          "Movement ID is required.",
          "L'identifiant du mouvement est requis.");
      assertViolationEnAndFr(
          request, "orderIndex", "Order index is required.", "L'index d'ordre est requis.");
    }

    @Test
    @DisplayName("Size & Value constraints")
    void constraints() {
      //noinspection DataFlowIssue
      WodMovementRequest request =
          new WodMovementRequest(
              1L,
              0,
              "A".repeat(51),
              -1.0,
              null,
              -1,
              null,
              -1.0,
              null,
              -1,
              null,
              null,
              null,
              "A".repeat(4001));

      assertViolationEnAndFr(
          request,
          "orderIndex",
          "Order index must be positive.",
          "L'index d'ordre doit être positif.");
      assertViolationEnAndFr(
          request,
          "repsScheme",
          "Rep scheme cannot exceed 50 characters.",
          "Le schéma de répétition (détail) ne peut pas dépasser 50 caractères.");
      assertViolationEnAndFr(
          request,
          "scalingOptions",
          "Scaling options cannot exceed 4000 characters.",
          "Les options de scaling ne peuvent pas dépasser 4000 caractères.");
      assertViolationEnAndFr(
          request,
          "weight",
          "Value must be 0 or positive.",
          "La valeur doit être positive ou nulle.");
      assertViolationEnAndFr(
          request,
          "durationSeconds",
          "Value must be 0 or positive.",
          "La valeur doit être positive ou nulle.");
      assertViolationEnAndFr(
          request,
          "distance",
          "Value must be 0 or positive.",
          "La valeur doit être positive ou nulle.");
      assertViolationEnAndFr(
          request,
          "calories",
          "Value must be 0 or positive.",
          "La valeur doit être positive ou nulle.");
    }
  }

  // ==================================================================================
  // SCORE REQUEST TESTS
  // ==================================================================================

  @Nested
  @DisplayName("ScoreRequest Tests")
  class ScoreRequestTests {

    @Test
    @DisplayName("Required fields")
    void requiredFields() {
      ScoreRequest request =
          new ScoreRequest(
              null, null, null, null, null, null, null, null, null, null, null, null, null, null,
              false, null, null);

      assertViolationEnAndFr(request, "wodId", "WOD ID is required.", "L'ID du WOD est requis.");
      assertViolationEnAndFr(request, "date", "Date is required.", "La date est requise.");
      assertViolationEnAndFr(
          request, "scaling", "Scaling level is required.", "Le niveau de scaling est requis.");
    }

    @Test
    @DisplayName("Date constraints (Future)")
    void futureDateConstraint() {
      ScoreRequest request =
          new ScoreRequest(
              1L,
              LocalDate.now().plusDays(1),
              1L,
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
              ScalingLevel.RX,
              false,
              null,
              null);

      assertViolationEnAndFr(
          request,
          "date",
          "Date cannot be in the future.",
          "La date ne peut pas être dans le futur.");
    }

    @Test
    @DisplayName("Value & Size constraints")
    void valueAndSizeConstraints() {
      //noinspection DataFlowIssue
      ScoreRequest request =
          new ScoreRequest(
              1L,
              LocalDate.now(),
              1L,
              -1,
              -1,
              -1,
              -1,
              -10.0,
              -10.0,
              null,
              -10.0,
              null,
              -1,
              ScalingLevel.RX,
              false,
              "A".repeat(4001),
              "A".repeat(4001));

      // Metrics (Negative values)
      assertViolationEnAndFr(
          request,
          "timeMinutes",
          "Value must be 0 or positive.",
          "La valeur doit être positive ou nulle.");
      assertViolationEnAndFr(
          request,
          "timeSeconds",
          "Value must be 0 or positive.",
          "La valeur doit être positive ou nulle.");
      assertViolationEnAndFr(
          request,
          "rounds",
          "Value must be 0 or positive.",
          "La valeur doit être positive ou nulle.");
      assertViolationEnAndFr(
          request,
          "reps",
          "Value must be 0 or positive.",
          "La valeur doit être positive ou nulle.");
      assertViolationEnAndFr(
          request,
          "maxWeight",
          "Value must be 0 or positive.",
          "La valeur doit être positive ou nulle.");
      assertViolationEnAndFr(
          request,
          "totalLoad",
          "Value must be 0 or positive.",
          "La valeur doit être positive ou nulle.");
      assertViolationEnAndFr(
          request,
          "totalDistance",
          "Value must be 0 or positive.",
          "La valeur doit être positive ou nulle.");
      assertViolationEnAndFr(
          request,
          "totalCalories",
          "Value must be 0 or positive.",
          "La valeur doit être positive ou nulle.");

      // Text Limits
      assertViolationEnAndFr(
          request,
          "userComment",
          "User comment cannot exceed 4000 characters.",
          "Le commentaire ne peut pas dépasser 4000 caractères.");
      assertViolationEnAndFr(
          request,
          "scalingNotes",
          "Scaling notes cannot exceed 4000 characters.",
          "Les notes de scaling ne peuvent pas dépasser 4000 caractères.");
    }
  }
}
