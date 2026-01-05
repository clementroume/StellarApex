package apex.stellar.aldebaran.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import apex.stellar.aldebaran.config.BaseIntegrationTest;
import apex.stellar.aldebaran.dto.MovementMuscleRequest;
import apex.stellar.aldebaran.dto.MovementRequest;
import apex.stellar.aldebaran.model.entities.Movement;
import apex.stellar.aldebaran.model.entities.MovementMuscle.MuscleRole;
import apex.stellar.aldebaran.model.entities.Muscle;
import apex.stellar.aldebaran.model.entities.Muscle.MuscleGroup;
import apex.stellar.aldebaran.model.enums.Category;
import apex.stellar.aldebaran.repository.MovementRepository;
import apex.stellar.aldebaran.repository.MuscleRepository;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Integration tests for {@link MovementController}.
 *
 * <p>Verifies the API contract, database persistence, ID generation strategies, and security rules.
 */
@Transactional
class MovementControllerIT extends BaseIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private JsonMapper objectMapper;
  @Autowired private MovementRepository movementRepository;
  @Autowired private MuscleRepository muscleRepository;
  @Autowired private StringRedisTemplate redisTemplate;

  @BeforeEach
  void setUp() {
    movementRepository.deleteAll();
    muscleRepository.deleteAll();

    // Seed Reference Data (Muscle) required for linking
    Muscle quadriceps =
        Muscle.builder()
            .medicalName("Quadriceps Femoris")
            .commonNameEn("Quads")
            .muscleGroup(MuscleGroup.LEGS)
            .build();
    muscleRepository.save(quadriceps);

    // Seed Initial Movement
    Movement backSquat =
        Movement.builder()
            .id("WL-SQ-001")
            .name("Back Squat")
            .category(Category.SQUAT)
            .targetedMuscles(new java.util.HashSet<>())
            .build();
    movementRepository.save(backSquat);
  }

  @AfterEach
  void cleanUpCache() {
    redisTemplate.execute(
        (RedisConnection connection) -> {
          connection.serverCommands().flushAll();
          return null;
        });
  }

  // -------------------------------------------------------------------------
  // GET Operations
  // -------------------------------------------------------------------------

  @Test
  @WithMockUser(username = "athlete")
  @DisplayName("GET /movements: should return summaries matching query")
  void testSearchMovements_Success() throws Exception {
    mockMvc
        .perform(get("/aldebaran/movements").param("query", "Squat"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(1)))
        .andExpect(jsonPath("$[0].id").value("WL-SQ-001"))
        .andExpect(jsonPath("$[0].name").value("Back Squat"))
        .andExpect(jsonPath("$[0].category").value("SQUAT"));
  }

  @Test
  @WithMockUser(username = "athlete")
  @DisplayName("GET /movements/{id}: should return detailed response")
  void testGetMovement_Success() throws Exception {
    mockMvc
        .perform(get("/aldebaran/movements/WL-SQ-001"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value("WL-SQ-001"))
        .andExpect(jsonPath("$.name").value("Back Squat"));
  }

  @Test
  @WithMockUser(username = "athlete")
  @DisplayName("GET /movements/{id}: should return 404 for unknown ID")
  void testGetMovement_NotFound() throws Exception {
    mockMvc
        .perform(get("/aldebaran/movements/UNKNOWN-ID"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.title").value("Resource Not Found"));
  }

  // -------------------------------------------------------------------------
  // POST Operations
  // -------------------------------------------------------------------------

  @Test
  @WithMockUser(
      username = "admin",
      roles = {"ADMIN"})
  @DisplayName("POST /movements: should create new movement and generate ID")
  void testCreateMovement_Success() throws Exception {
    // Given
    MovementRequest request =
        new MovementRequest(
            "Front Squat",
            "FS",
            Category.SQUAT,
            Collections.emptySet(),
            Collections.emptySet(),
            List.of(new MovementMuscleRequest("Quadriceps Femoris", MuscleRole.AGONIST, 1.0)),
            true,
            1.0,
            "English Desc",
            "French Desc",
            null,
            null,
            null,
            null);

    // When/Then
    mockMvc
        .perform(
            post("/aldebaran/movements")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.name").value("Front Squat"))
        // Verify Semantic ID generation (Category SQUAT -> WL-SQ prefix)
        .andExpect(jsonPath("$.id", startsWith("WL-SQ-")));
  }

  @Test
  @WithMockUser(
      username = "user",
      roles = {"USER"})
  @DisplayName("POST /movements: should return 403 Forbidden for non-admin")
  void testCreateMovement_Forbidden() throws Exception {
    MovementRequest request =
        new MovementRequest(
            "Forbidden Move",
            "FM",
            Category.SQUAT,
            null,
            null,
            null,
            true,
            1.0,
            null,
            null,
            null,
            null,
            null,
            null);

    mockMvc
        .perform(
            post("/aldebaran/movements")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isForbidden());
  }

  // -------------------------------------------------------------------------
  // PUT Operations
  // -------------------------------------------------------------------------

  @Test
  @WithMockUser(
      username = "admin",
      roles = {"ADMIN"})
  @DisplayName("PUT /movements/{id}: should update existing movement")
  void testUpdateMovement_Success() throws Exception {
    MovementRequest updateRequest =
        new MovementRequest(
            "Back Squat (High Bar)", // Changed Name
            "HBBS",
            Category.SQUAT,
            Collections.emptySet(),
            Collections.emptySet(),
            List.of(new MovementMuscleRequest("Quadriceps Femoris", MuscleRole.AGONIST, 1.0)),
            true,
            1.0,
            null,
            null,
            null,
            null,
            null,
            null);

    mockMvc
        .perform(
            put("/aldebaran/movements/WL-SQ-001")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateRequest)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Back Squat (High Bar)"));
  }
}
