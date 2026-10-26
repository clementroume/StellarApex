package apex.stellar.aldebaran.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
import java.util.Locale;
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

  private Long testMuscleId;

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
    testMuscleId = chest.getId();
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
  @DisplayName("GET /muscles/{id}: should return muscle details")
  void testGetMuscle_Success() throws Exception {
    mockMvc
        .perform(
            get("/aldebaran/muscles/" + testMuscleId)
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
  @DisplayName("POST /muscles: should create a member of a functional group, exposed on both sides")
  void testCreateMuscle_FunctionalGroup() throws Exception {
    Muscle quads =
        muscleRepository.save(
            Muscle.builder().medicalName("Quadriceps Femoris").muscleGroup(MuscleGroup.LEGS).build());
    MuscleRequest member =
        new MuscleRequest(
            "Rectus Femoris", MuscleGroup.LEGS, quads.getId(), null, null, null, null, null);

    String body =
        mockMvc
            .perform(
                post("/aldebaran/muscles")
                    .with(csrf())
                    .header("X-Auth-User-Id", "1")
                    .header("X-Auth-User-Role", "ADMIN")
                    .header("X-Internal-Secret", "test-internal-secret")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(member)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.parent.medicalName").value("Quadriceps Femoris"))
            .andExpect(jsonPath("$.members", hasSize(0)))
            .andReturn()
            .getResponse()
            .getContentAsString();
    Long memberId = objectMapper.readTree(body).get("id").asLong();

    mockMvc
        .perform(
            get("/aldebaran/muscles/" + quads.getId())
                .header("X-Auth-User-Id", "1")
                .header("X-Auth-User-Role", "USER")
                .header("X-Internal-Secret", "test-internal-secret"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.parent").isEmpty())
        .andExpect(jsonPath("$.members[0].medicalName").value("Rectus Femoris"));

    // Groups do not nest: a member cannot become a group
    MuscleRequest nested =
        new MuscleRequest(
            "Vastus Lateralis", MuscleGroup.LEGS, memberId, null, null, null, null, null);
    mockMvc
        .perform(
            post("/aldebaran/muscles")
                .with(csrf())
                .header("X-Auth-User-Id", "1")
                .header("X-Auth-User-Role", "ADMIN")
                .header("X-Internal-Secret", "test-internal-secret")
                .locale(Locale.ENGLISH)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(nested)))
        .andExpect(status().isConflict())
        .andExpect(
            jsonPath("$.detail")
                .value(
                    "The muscle Rectus Femoris belongs to a functional group and cannot be a group itself."));
  }

  @Test
  @DisplayName("POST /muscles: should create new muscle when Admin")
  void testCreateMuscle_AsAdmin_Success() throws Exception {
    // Given
    MuscleRequest request =
        new MuscleRequest(
            "Latissimus Dorsi",
            MuscleGroup.BACK,
            null,
            "Lats",
            "Dorsaux",
            "Back muscle",
            "Muscle du dos",
            null);

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
    boolean exists =
        muscleRepository.findAll().stream()
            .anyMatch(m -> "Latissimus Dorsi".equals(m.getMedicalName()));
    assert (exists);
  }

  @Test
  @DisplayName("POST /muscles: should return 403 Forbidden when simple User")
  void testCreateMuscle_AsUser_Forbidden() throws Exception {
    MuscleRequest request =
        new MuscleRequest("Biceps Brachii", MuscleGroup.ARMS, null, "Biceps", "Biceps", null, null, null);

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
    // Given: Request with the existing name "Pectoralis Major" (seeded in setUp)
    MuscleRequest request =
        new MuscleRequest("Pectoralis Major", MuscleGroup.CHEST, null, "Chest", "Pec", null, null, null);

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
        .andExpect(jsonPath("$.title").value("Data Conflict"));
  }

  // -------------------------------------------------------------------------
  // PUT Operations (Update)
  // -------------------------------------------------------------------------

  @Test
  @DisplayName("PUT /muscles/{id}: should update existing muscle")
  void testUpdateMuscle_Success() throws Exception {
    MuscleRequest updateRequest =
        new MuscleRequest(
            "Pectoralis Major",
            MuscleGroup.CHEST,
            null,
            "Upper Chest", // Changed value
            "Pectoraux",
            null,
            null,
            null);

    // When/Then
    mockMvc
        .perform(
            put("/aldebaran/muscles/" + testMuscleId)
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
    MuscleRequest request =
        new MuscleRequest("Unknown", MuscleGroup.LEGS, null, "U", "U", null, null, null);

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

  // -------------------------------------------------------------------------
  // DELETE Operations (Delete)
  // -------------------------------------------------------------------------

  @Test
  @DisplayName("DELETE /muscles/{id}: should delete muscle when Admin")
  void testDeleteMuscle_AsAdmin_Success() throws Exception {
    mockMvc
        .perform(
            delete("/aldebaran/muscles/" + testMuscleId)
                .with(csrf())
                .header("X-Auth-User-Id", "1")
                .header("X-Auth-User-Role", "ADMIN")
                .header("X-Internal-Secret", "test-internal-secret"))
        .andExpect(status().isNoContent());

    // Verify persistence
    boolean exists = muscleRepository.existsById(testMuscleId);
    assertFalse(exists, "Muscle should have been deleted from database");
  }

  @Test
  @DisplayName("DELETE /muscles/{id}: should return 403 Forbidden when simple User")
  void testDeleteMuscle_AsUser_Forbidden() throws Exception {
    mockMvc
        .perform(
            delete("/aldebaran/muscles/" + testMuscleId)
                .with(csrf())
                .header("X-Auth-User-Id", "2")
                .header("X-Auth-User-Role", "USER")
                .header("X-Internal-Secret", "test-internal-secret"))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("DELETE /muscles/{id}: should return 404 if ID unknown")
  void testDeleteMuscle_NotFound() throws Exception {
    mockMvc
        .perform(
            delete("/aldebaran/muscles/99999")
                .with(csrf())
                .header("X-Auth-User-Id", "1")
                .header("X-Auth-User-Role", "ADMIN")
                .header("X-Internal-Secret", "test-internal-secret"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.title").value("Resource Not Found"));
  }
}
