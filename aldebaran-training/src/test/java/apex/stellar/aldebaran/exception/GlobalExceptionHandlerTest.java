package apex.stellar.aldebaran.exception;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.Locale;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

class GlobalExceptionHandlerTest {

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    // Load REAL messages.properties from the classpath
    ResourceBundleMessageSource realMessageSource = new ResourceBundleMessageSource();
    realMessageSource.setBasename("messages");
    realMessageSource.setDefaultEncoding("UTF-8");
    realMessageSource.setUseCodeAsDefaultMessage(true);

    GlobalExceptionHandler handler = new GlobalExceptionHandler(realMessageSource);
    mockMvc =
        MockMvcBuilders.standaloneSetup(new TestController()).setControllerAdvice(handler).build();
  }

  // ===============================================================================================
  // ENGLISH TESTS (Based on strict content provided)
  // ===============================================================================================

  @Test
  @DisplayName("EN - Access Denied")
  void testAccessDenied_EN() throws Exception {
    mockMvc
        .perform(get("/error/access-denied").locale(Locale.ENGLISH))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.title").value("Access Denied"))
        .andExpect(
            jsonPath("$.detail").value("You do not have permission to access this resource."));
  }

  @Test
  @DisplayName("EN - Internal Server Error")
  void testInternalServerError_EN() throws Exception {
    mockMvc
        .perform(get("/error/generic").locale(Locale.ENGLISH))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.title").value("Internal Server Error"))
        .andExpect(
            jsonPath("$.detail")
                .value("An unexpected internal error occurred. Please try again later."));
  }

  @Test
  @DisplayName("EN - Validation Error")
  void testValidation_EN() throws Exception {
    mockMvc
        .perform(
            post("/error/validation")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
                .locale(Locale.ENGLISH))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.title").value("Validation Error"));
  }

  @Test
  @DisplayName("EN - Muscle: Name Exists")
  void testMuscleNameExists_EN() throws Exception {
    // Note: Property contains '{0}' (quoted). MessageFormat treats it as literal {0}.
    mockMvc
        .perform(get("/error/muscle/name-exists").locale(Locale.ENGLISH))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.title").value("Data Conflict"))
        .andExpect(
            jsonPath("$.detail")
                .value("A muscle with the medical name Pectoralis already exists."));
  }

  @Test
  @DisplayName("EN - Muscle: Not Found")
  void testMuscleNotFound_EN() throws Exception {
    mockMvc
        .perform(get("/error/muscle/not-found").locale(Locale.ENGLISH))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.title").value("Resource Not Found"))
        .andExpect(jsonPath("$.detail").value("Muscle not found with ID: 99"));
  }

  @Test
  @DisplayName("EN - Movement: Not Found")
  void testMovementNotFound_EN() throws Exception {
    mockMvc
        .perform(get("/error/movement/not-found").locale(Locale.ENGLISH))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.title").value("Resource Not Found"))
        .andExpect(jsonPath("$.detail").value("Movement not found with ID: SQ-01"));
  }

  @Test
  @DisplayName("EN - Movement: Muscle Reference Not Found")
  void testMuscleRefNotFound_EN() throws Exception {
    mockMvc
        .perform(get("/error/movement/muscle-ref-missing").locale(Locale.ENGLISH))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.title").value("Resource Not Found"))
        .andExpect(
            jsonPath("$.detail").value("Muscle reference not found with medical name: Biceps"));
  }

  @Test
  @DisplayName("EN - Movement: Duplicate")
  void testMovementDuplicate_EN() throws Exception {
    mockMvc
        .perform(get("/error/movement/duplicate").locale(Locale.ENGLISH))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.title").value("Data Conflict"))
        .andExpect(jsonPath("$.detail").value("A movement with the same name already exists."));
  }

  @Test
  @DisplayName("EN - WOD: Not Found")
  void testWodNotFound_EN() throws Exception {
    mockMvc
        .perform(get("/error/wod/not-found").locale(Locale.ENGLISH))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.title").value("Resource Not Found"))
        .andExpect(jsonPath("$.detail").value("WOD not found with ID: 1"));
  }

  @Test
  @DisplayName("EN - WOD: Locked")
  void testWodLocked_EN() throws Exception {
    mockMvc
        .perform(get("/error/wod/locked").locale(Locale.ENGLISH))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.title").value("Resource Locked"))
        .andExpect(
            jsonPath("$.detail")
                .value("WOD 1 is locked and cannot be modified because scores are registered."));
  }

  @Test
  @DisplayName("EN - Score: Not Found")
  void testScoreNotFound_EN() throws Exception {
    mockMvc
        .perform(get("/error/score/not-found").locale(Locale.ENGLISH))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.title").value("Resource Not Found"))
        .andExpect(jsonPath("$.detail").value("Score not found with ID: 500"));
  }

  // ===============================================================================================
  // FRENCH TESTS (Exhaustive based on strict content provided)
  // ===============================================================================================

  @Test
  @DisplayName("FR - Access Denied")
  void testAccessDenied_FR() throws Exception {
    mockMvc
        .perform(get("/error/access-denied").locale(Locale.FRENCH))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.title").value("Access Denied"))
        .andExpect(
            jsonPath("$.detail")
                .value("Vous n'avez pas la permission d'accéder à cette ressource."));
  }

  @Test
  @DisplayName("FR - Internal Server Error")
  void testInternalServerError_FR() throws Exception {
    mockMvc
        .perform(get("/error/generic").locale(Locale.FRENCH))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.title").value("Internal Server Error"))
        .andExpect(
            jsonPath("$.detail")
                .value(
                    "Une erreur interne inattendue est survenue. Veuillez réessayer plus tard."));
  }

  @Test
  @DisplayName("FR - Validation Error")
  void testValidation_FR() throws Exception {
    mockMvc
        .perform(
            post("/error/validation")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
                .locale(Locale.FRENCH))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.title").value("Erreur de validation"));
  }

  @Test
  @DisplayName("FR - Muscle: Name Exists")
  void testMuscleNameExists_FR() throws Exception {
    mockMvc
        .perform(get("/error/muscle/name-exists").locale(Locale.FRENCH))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.title").value("Data Conflict"))
        .andExpect(
            jsonPath("$.detail").value("Un muscle avec le nom médical Pectoralis existe déjà."));
  }

  @Test
  @DisplayName("FR - Muscle: Not Found")
  void testMuscleNotFound_FR() throws Exception {
    mockMvc
        .perform(get("/error/muscle/not-found").locale(Locale.FRENCH))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.title").value("Resource Not Found"))
        .andExpect(jsonPath("$.detail").value("Muscle introuvable avec l'ID : 99"));
  }

  @Test
  @DisplayName("FR - Movement: Not Found")
  void testMovementNotFound_FR() throws Exception {
    mockMvc
        .perform(get("/error/movement/not-found").locale(Locale.FRENCH))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.title").value("Resource Not Found"))
        .andExpect(jsonPath("$.detail").value("Mouvement introuvable avec l'ID : SQ-01"));
  }

  @Test
  @DisplayName("FR - Movement: Muscle Reference Not Found")
  void testMuscleRefNotFound_FR() throws Exception {
    mockMvc
        .perform(get("/error/movement/muscle-ref-missing").locale(Locale.FRENCH))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.title").value("Resource Not Found"))
        .andExpect(
            jsonPath("$.detail")
                .value("Référence musculaire introuvable avec le nom médical : Biceps"));
  }

  @Test
  @DisplayName("FR - Movement: Duplicate")
  void testMovementDuplicate_FR() throws Exception {
    mockMvc
        .perform(get("/error/movement/duplicate").locale(Locale.FRENCH))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.title").value("Data Conflict"))
        .andExpect(jsonPath("$.detail").value("Un mouvement avec le même nom existe déjà."));
  }

  @Test
  @DisplayName("FR - WOD: Not Found")
  void testWodNotFound_FR() throws Exception {
    mockMvc
        .perform(get("/error/wod/not-found").locale(Locale.FRENCH))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.title").value("Resource Not Found"))
        .andExpect(jsonPath("$.detail").value("WOD introuvable avec l'ID : 1"));
  }

  @Test
  @DisplayName("FR - WOD: Locked")
  void testWodLocked_FR() throws Exception {
    mockMvc
        .perform(get("/error/wod/locked").locale(Locale.FRENCH))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.title").value("Resource Locked"))
        .andExpect(
            jsonPath("$.detail")
                .value(
                    "Le WOD 1 est verrouillé et ne peut pas être modifié car des scores sont enregistrés."));
  }

  @Test
  @DisplayName("FR - Score: Not Found")
  void testScoreNotFound_FR() throws Exception {
    mockMvc
        .perform(get("/error/score/not-found").locale(Locale.FRENCH))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.title").value("Resource Not Found"))
        .andExpect(jsonPath("$.detail").value("Score introuvable avec l'ID : 500"));
  }

  // ===============================================================================================
  // TEST CONTROLLER STUB
  // ===============================================================================================

  @RestController
  static class TestController {

    // MUSCLE
    @GetMapping("/error/muscle/name-exists")
    void muscleNameExists() {
      throw new DataConflictException("error.muscle.name.exists", "Pectoralis");
    }

    @GetMapping("/error/muscle/not-found")
    void muscleNotFound() {
      throw new ResourceNotFoundException("error.muscle.not.found", 99L);
    }

    // MOVEMENT
    @GetMapping("/error/movement/not-found")
    void movementNotFound() {
      throw new ResourceNotFoundException("error.movement.not.found", "SQ-01");
    }

    @GetMapping("/error/movement/muscle-ref-missing")
    void muscleRefMissing() {
      throw new ResourceNotFoundException("error.muscle.name.not.found", "Biceps");
    }

    @GetMapping("/error/movement/duplicate")
    void movementDuplicate() {
      throw new DataConflictException("error.movement.duplicate");
    }

    // WOD
    @GetMapping("/error/wod/not-found")
    void wodNotFound() {
      throw new ResourceNotFoundException("error.wod.not.found", 1L);
    }

    @GetMapping("/error/wod/locked")
    void wodLocked() {
      throw new WodLockedException("error.wod.locked", 1L);
    }

    // SCORE
    @GetMapping("/error/score/not-found")
    void scoreNotFound() {
      throw new ResourceNotFoundException("error.score.not.found", 500L);
    }

    // GENERIC
    @GetMapping("/error/access-denied")
    void accessDenied() {
      throw new AccessDeniedException("Forbidden");
    }

    @GetMapping("/error/generic")
    void generic() {
      throw new RuntimeException("Boom");
    }

    @SuppressWarnings("unused")
    @PostMapping("/error/validation")
    void validation(@RequestBody @Valid DummyDto dto) {
      /* Empty DTO */
    }
  }

  @SuppressWarnings({"LombokGetterMayBeUsed", "LombokSetterMayBeUsed", "unused"})
  static class DummyDto {
    @NotNull String field;

    public String getField() {
      return field;
    }

    public void setField(String field) {
      this.field = field;
    }
  }
}
