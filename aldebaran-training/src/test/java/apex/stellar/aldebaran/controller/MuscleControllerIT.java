package apex.stellar.aldebaran.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import apex.stellar.aldebaran.config.BaseIntegrationTest;
import apex.stellar.aldebaran.dto.MuscleRequest;
import apex.stellar.aldebaran.model.entities.Muscle;
import apex.stellar.aldebaran.model.entities.Muscle.MuscleGroup;
import apex.stellar.aldebaran.repository.MuscleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

/**
 * Integration tests for {@link MuscleController}.
 *
 * <p>Verifies the API contract, database persistence, cache eviction, and security rules using real
 * infrastructure (Testcontainers).
 */
class MuscleControllerIT extends BaseIntegrationTest {

  @Autowired private MuscleRepository muscleRepository;

  @BeforeEach
  void setUp() {
    // Clear DB to ensure a clean state for each test
    muscleRepository.deleteAll();

    // Seed initial data
    Muscle chest =
        Muscle.builder()
            .medicalName("Pectoralis Major")
            .commonNameEn("Chest")
            .muscleGroup(MuscleGroup.CHEST)
            .build();
    muscleRepository.save(chest);
  }

  // -------------------------------------------------------------------------
  // GET Operations (Read)
  // -------------------------------------------------------------------------

  @Test
  @DisplayName("GET /muscles: should return list of muscles from DB")
  void testGetAllMuscles_Success() throws Exception {
    mockMvc
        .perform(
            get("/aldebaran/muscles")
                .header("X-Auth-User-Id", "1")
                .header("X-Auth-User-Role", "USER")
                .header("X-Internal-Secret", "test-internal-secret"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(1)))
        .andExpect(jsonPath("$[0].medicalName").value("Pectoralis Major"))
        .andExpect(jsonPath("$[0].muscleGroup").value("CHEST"));
  }

  @Test
  @DisplayName("GET /muscles/{name}: should return muscle details")
  void testGetMuscle_Success() throws Exception {
    mockMvc
        .perform(
            get("/aldebaran/muscles/Pectoralis Major")
                .header("X-Auth-User-Id", "1")
                .header("X-Auth-User-Role", "USER")
                .header("X-Internal-Secret", "test-internal-secret"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.medicalName").value("Pectoralis Major"));
  }

  // -------------------------------------------------------------------------
  // POST Operations (Create)
  // -------------------------------------------------------------------------

  @Test
  @DisplayName("POST /muscles: should create new muscle when Admin")
  void testCreateMuscle_AsAdmin_Success() throws Exception {
    // Given
    MuscleRequest request =
        new MuscleRequest(
            "Latissimus Dorsi",
            "Lats",
            "Dorsaux",
            "Back muscle",
            "Muscle du dos",
            MuscleGroup.BACK);

    // When/Then
    mockMvc
        .perform(
            post("/aldebaran/muscles")
                .with(csrf())
                .header("X-Auth-User-Id", "1")
                .header("X-Auth-User-Role", "ADMIN")
                .header("X-Internal-Secret", "test-internal-secret")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.medicalName").value("Latissimus Dorsi"));

    // Verify persistence
    boolean exists = muscleRepository.findByMedicalName("Latissimus Dorsi").isPresent();
    assert (exists);
  }

  @Test
  @DisplayName("POST /muscles: should return 403 Forbidden when simple User")
  void testCreateMuscle_AsUser_Forbidden() throws Exception {
    MuscleRequest request =
        new MuscleRequest("Biceps Brachii", "Biceps", "Biceps", null, null, MuscleGroup.ARMS);

    mockMvc
        .perform(
            post("/aldebaran/muscles")
                .with(csrf())
                .header("X-Auth-User-Id", "2")
                .header("X-Auth-User-Role", "USER")
                .header("X-Internal-Secret", "test-internal-secret")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("POST /muscles: should return 409 Conflict if name exists")
  void testCreateMuscle_Conflict() throws Exception {
    // Given: Request with existing name "Pectoralis Major" (seeded in setUp)
    MuscleRequest request =
        new MuscleRequest("Pectoralis Major", "Chest", "Pec", null, null, MuscleGroup.CHEST);

    // When/Then
    mockMvc
        .perform(
            post("/aldebaran/muscles")
                .with(csrf())
                .header("X-Auth-User-Id", "1")
                .header("X-Auth-User-Role", "ADMIN")
                .header("X-Internal-Secret", "test-internal-secret")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isConflict())
        // Verify that the GlobalExceptionHandler mapped DataConflictException correctly
        .andExpect(jsonPath("$.title").value("Data Conflict"));
  }

  // -------------------------------------------------------------------------
  // PUT Operations (Update)
  // -------------------------------------------------------------------------

  @Test
  @DisplayName("PUT /muscles/{id}: should update existing muscle")
  void testUpdateMuscle_Success() throws Exception {
    // Given: Retrieve the seeded muscle to get its generated ID
    Muscle existing = muscleRepository.findByMedicalName("Pectoralis Major").orElseThrow();

    // Request to update common name
    MuscleRequest updateRequest =
        new MuscleRequest(
            "Pectoralis Major",
            "Upper Chest", // Changed value
            "Pectoraux",
            null,
            null,
            MuscleGroup.CHEST);

    // When/Then
    mockMvc
        .perform(
            put("/aldebaran/muscles/" + existing.getId())
                .with(csrf())
                .header("X-Auth-User-Id", "1")
                .header("X-Auth-User-Role", "ADMIN")
                .header("X-Internal-Secret", "test-internal-secret")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateRequest)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.commonNameEn").value("Upper Chest"));
  }

  @Test
  @DisplayName("PUT /muscles/{id}: should return 404 if ID unknown")
  void testUpdateMuscle_NotFound() throws Exception {
    MuscleRequest request = new MuscleRequest("Unknown", "U", "U", null, null, MuscleGroup.LEGS);

    mockMvc
        .perform(
            put("/aldebaran/muscles/99999")
                .with(csrf())
                .header("X-Auth-User-Id", "1")
                .header("X-Auth-User-Role", "ADMIN")
                .header("X-Internal-Secret", "test-internal-secret")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.title").value("Resource Not Found"));
  }
}
