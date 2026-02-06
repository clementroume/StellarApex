package apex.stellar.aldebaran.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import apex.stellar.aldebaran.config.BaseIntegrationTest;
import apex.stellar.aldebaran.dto.WodMovementRequest;
import apex.stellar.aldebaran.dto.WodRequest;
import apex.stellar.aldebaran.model.entities.Movement;
import apex.stellar.aldebaran.model.entities.Wod;
import apex.stellar.aldebaran.model.entities.Wod.ScoreType;
import apex.stellar.aldebaran.model.entities.Wod.WodType;
import apex.stellar.aldebaran.model.entities.WodScore;
import apex.stellar.aldebaran.model.entities.WodScore.ScalingLevel;
import apex.stellar.aldebaran.model.enums.Category;
import apex.stellar.aldebaran.repository.MovementRepository;
import apex.stellar.aldebaran.repository.WodRepository;
import apex.stellar.aldebaran.repository.WodScoreRepository;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

/** Integration tests for {@link WodController}. */
class WodControllerIT extends BaseIntegrationTest {

  @Autowired private WodRepository wodRepository;
  @Autowired private MovementRepository movementRepository;
  @Autowired private WodScoreRepository wodScoreRepository;

  private Movement pullUp;

  @BeforeEach
  void setUp() {
    wodScoreRepository.deleteAll();
    wodRepository.deleteAll();
    movementRepository.deleteAll();

    // Seed Movement
    pullUp = Movement.builder().id("GY-PU-001").name("Pull-up").category(Category.PULLING).build();
    movementRepository.save(pullUp);

    // Seed Initial WOD (Created by Gym 101)
    Wod fran =
        Wod.builder()
            .title("Fran")
            .wodType(WodType.FOR_TIME)
            .scoreType(ScoreType.TIME)
            .description("21-15-9 Thrusters and Pull-ups")
            .isPublic(true)
            .gymId(101L) // Important for security checks
            .authorId(50L)
            .build();
    wodRepository.save(fran);
  }

  @Test
  @DisplayName("GET /wods: should return list of summaries")
  void testGetAllWods_Success() throws Exception {
    // Public access allows reading
    mockMvc
        .perform(
            get("/aldebaran/wods")
                .header("X-Auth-User-Id", "1")
                .header("X-Auth-User-Role", "USER"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(1)))
        .andExpect(jsonPath("$[0].title").value("Fran"))
        .andExpect(jsonPath("$[0].wodType").value("FOR_TIME"));
  }

  @Test
  @DisplayName("GET /wods: should filter by title")
  void testGetWods_FilterByTitle() throws Exception {
    // Given: Another WOD "Grace"
    wodRepository.save(
        Wod.builder()
            .title("Grace")
            .wodType(WodType.FOR_TIME)
            .scoreType(ScoreType.TIME)
            .isPublic(true)
            .build());

    mockMvc
        .perform(
            get("/aldebaran/wods")
                .param("search", "Grace")
                .header("X-Auth-User-Id", "1")
                .header("X-Auth-User-Role", "USER"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(1)))
        .andExpect(jsonPath("$[0].title").value("Grace"));
  }

  @Test
  @DisplayName("GET /wods: should filter by type")
  void testGetWods_FilterByType() throws Exception {
    mockMvc
        .perform(
            get("/aldebaran/wods")
                .param("type", "FOR_TIME")
                .header("X-Auth-User-Id", "1")
                .header("X-Auth-User-Role", "USER"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(1)))
        .andExpect(jsonPath("$[0].title").value("Fran"));
  }

  @Test
  @DisplayName("GET /wods/{id}: should return detailed WOD")
  void testGetWod_Success() throws Exception {
    Wod fran = wodRepository.findAll().get(0);

    mockMvc
        .perform(
            get("/aldebaran/wods/" + fran.getId())
                .header("X-Auth-User-Id", "1")
                .header("X-Auth-User-Role", "USER"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.title").value("Fran"));
  }

  @Test
  @DisplayName("GET /wods/{id}: should return 404 if not found")
  void testGetWod_NotFound() throws Exception {
    mockMvc
        .perform(
            get("/aldebaran/wods/99999")
                .header("X-Auth-User-Id", "1")
                .header("X-Auth-User-Role", "USER"))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("GET /wods/{id}: Private WOD should be accessible only by Author")
  void testGetWod_Private_AccessControl() throws Exception {
    // Given: Private WOD by User 100
    Wod privateWod =
        Wod.builder()
            .title("Secret WOD")
            .wodType(WodType.FOR_TIME)
            .scoreType(ScoreType.TIME)
            .isPublic(false)
            .authorId(100L)
            .build();
    wodRepository.save(privateWod);

    // When: Author requests -> OK
    mockMvc
        .perform(
            get("/aldebaran/wods/" + privateWod.getId())
                .header("X-Auth-User-Id", "100")
                .header("X-Auth-User-Role", "USER"))
        .andExpect(status().isOk());

    // When: Other user requests -> Forbidden
    mockMvc
        .perform(
            get("/aldebaran/wods/" + privateWod.getId())
                .header("X-Auth-User-Id", "200")
                .header("X-Auth-User-Role", "USER"))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("GET /wods/{id}: Gym WOD should be accessible only by Gym Members")
  void testGetWod_Gym_AccessControl() throws Exception {
    // Given: Gym WOD for Gym 101 (seeded in setUp as 'fran' but let's make a private one)
    Wod gymWod =
        Wod.builder()
            .title("Gym Exclusive")
            .wodType(WodType.AMRAP)
            .scoreType(ScoreType.ROUNDS_REPS)
            .isPublic(false)
            .gymId(101L)
            .build();
    wodRepository.save(gymWod);

    // When: Member of Gym 101 requests -> OK
    mockMvc
        .perform(
            get("/aldebaran/wods/" + gymWod.getId())
                .header("X-Auth-User-Id", "50")
                .header("X-Auth-Gym-Id", "101")
                .header("X-Auth-User-Role", "ATHLETE"))
        .andExpect(status().isOk());

    // When: Member of Gym 102 requests -> Forbidden
    mockMvc
        .perform(
            get("/aldebaran/wods/" + gymWod.getId())
                .header("X-Auth-User-Id", "60")
                .header("X-Auth-Gym-Id", "102")
                .header("X-Auth-User-Role", "ATHLETE"))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("POST /wods: should create WOD when Coach with WOD_WRITE permission")
  void testCreateWod_Success() throws Exception {
    WodRequest request =
        new WodRequest(
            "Murph",
            WodType.FOR_TIME,
            ScoreType.TIME,
            "Hero Wod",
            "Wear a vest",
            null, // authorId (will be set by service)
            101L, // gymId
            true, // isPublic
            3600,
            0,
            0,
            "Chipper",
            List.of(
                new WodMovementRequest(
                    pullUp.getId(), 1, "100", 0.0, null, 0, null, 0.0, null, 0, null, null)));

    mockMvc
        .perform(
            post("/aldebaran/wods")
                .with(csrf())
                // Simulation des headers Traefik pour un Coach autorisé
                .header("X-Auth-User-Id", "20")
                .header("X-Auth-Gym-Id", "101")
                .header("X-Auth-User-Role", "COACH")
                .header("X-Auth-User-Permissions", "WOD_WRITE") // Permission requise
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.title").value("Murph"))
        .andExpect(jsonPath("$.movements", hasSize(1)));
  }

  @Test
  @DisplayName("POST /wods: Personal WOD creation should be allowed for any User")
  void testCreateWod_Personal_Success() throws Exception {
    WodRequest request =
        new WodRequest(
            "My Custom WOD",
            WodType.EMOM,
            ScoreType.NONE,
            null, null,
            100L, // authorId matches User
            null, // No Gym
            false, // Private
            null, 60, 10, null,
            List.of(new WodMovementRequest(pullUp.getId(), 1, "10", 0.0, null, 0, null, 0.0, null, 0, null, null)));

    mockMvc
        .perform(
            post("/aldebaran/wods")
                .with(csrf())
                .header("X-Auth-User-Id", "100")
                .header("X-Auth-User-Role", "USER")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated());
  }

  @Test
  @DisplayName("POST /wods: Gym Owner should be able to create Gym WOD")
  void testCreateWod_GymOwner_Success() throws Exception {
    WodRequest request =
        new WodRequest(
            "Owner WOD",
            WodType.FOR_TIME,
            ScoreType.TIME,
            null, null,
            null,
            101L, // Gym 101
            false,
            null, null, null, null,
            List.of(new WodMovementRequest(pullUp.getId(), 1, "50", 0.0, null, 0, null, 0.0, null, 0, null, null)));

    mockMvc
        .perform(
            post("/aldebaran/wods")
                .with(csrf())
                .header("X-Auth-User-Id", "10")
                .header("X-Auth-Gym-Id", "101")
                .header("X-Auth-User-Role", "OWNER") // Owner
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated());
  }

  @Test
  @DisplayName("POST /wods: Coach WITHOUT permission should be Forbidden")
  void testCreateWod_CoachNoPerm_Forbidden() throws Exception {
    WodRequest request =
        new WodRequest(
            "Unauthorized WOD",
            WodType.FOR_TIME,
            ScoreType.TIME,
            null, null,
            null,
            101L, // Gym 101
            false,
            null, null, null, null,
            List.of(new WodMovementRequest(pullUp.getId(), 1, "50", 0.0, null, 0, null, 0.0, null, 0, null, null)));

    mockMvc
        .perform(
            post("/aldebaran/wods")
                .with(csrf())
                .header("X-Auth-User-Id", "20")
                .header("X-Auth-Gym-Id", "101")
                .header("X-Auth-User-Role", "COACH")
                // No Permissions header
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("POST /wods: Cross-Gym creation should be Forbidden")
  void testCreateWod_CrossGym_Forbidden() throws Exception {
    WodRequest request =
        new WodRequest(
            "Cross Gym WOD",
            WodType.FOR_TIME,
            ScoreType.TIME,
            null, null,
            null,
            101L, // Target Gym 101
            false,
            null, null, null, null,
            List.of(new WodMovementRequest(pullUp.getId(), 1, "50", 0.0, null, 0, null, 0.0, null, 0, null, null)));

    mockMvc
        .perform(
            post("/aldebaran/wods")
                .with(csrf())
                .header("X-Auth-User-Id", "10")
                .header("X-Auth-Gym-Id", "102") // User is in Gym 102
                .header("X-Auth-User-Role", "OWNER")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("POST /wods: should return 400 Bad Request for invalid input")
  void testCreateWod_ValidationFailure() throws Exception {
    WodRequest invalidRequest =
        new WodRequest(
            "", // Empty title
            null, // Null Type
            ScoreType.TIME,
            null,
            null,
            null,
            null,
            true,
            -10, // Negative time cap
            0,
            0,
            null,
            List.of()); // Empty movements

    mockMvc
        .perform(
            post("/aldebaran/wods")
                .with(csrf())
                .header("X-Auth-User-Id", "1")
                .header("X-Auth-User-Role", "ADMIN")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidRequest)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.title").value("Validation Error"));
  }

  @Test
  @DisplayName("PUT /wods/{id}: should update WOD when Coach of same Gym")
  void testUpdateWod_Success() throws Exception {
    Wod existing = wodRepository.findAll().get(0); // Gym ID 101

    WodRequest updateRequest =
        new WodRequest(
            "Fran Modified",
            WodType.FOR_TIME,
            ScoreType.TIME,
            "Harder version",
            "Scale if needed",
            null,
            null, // GymId ignored in update
            true,
            1200,
            0,
            0,
            "21-15-9",
            List.of(
                new WodMovementRequest(
                    pullUp.getId(), 1, "21-15-9", 40.0, null, 0, null, 0.0, null, 0, null, null)));

    mockMvc
        .perform(
            put("/aldebaran/wods/" + existing.getId())
                .with(csrf())
                // Coach du gym 101
                .header("X-Auth-User-Id", "20")
                .header("X-Auth-Gym-Id", "101")
                .header("X-Auth-User-Role", "COACH")
                .header("X-Auth-User-Permissions", "WOD_WRITE")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateRequest)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.title").value("Fran Modified"));

    Wod updated = wodRepository.findByIdWithMovements(existing.getId()).orElseThrow();
    assert (updated.getTitle().equals("Fran Modified"));
  }

  @Test
  @DisplayName("PUT /wods/{id}: should return 403 Forbidden for simple User")
  void testUpdateWod_Forbidden() throws Exception {
    Wod existing = wodRepository.findAll().get(0);

    WodRequest request =
        new WodRequest(
            "Hacked Wod",
            WodType.AMRAP,
            ScoreType.ROUNDS_REPS,
            null,
            null,
            null,
            null,
            false,
            0,
            0,
            0,
            null,
            List.of(
                new WodMovementRequest(
                    pullUp.getId(), 1, "10", 0.0, null, 0, null, 0.0, null, 0, null, null)));

    mockMvc
        .perform(
            put("/aldebaran/wods/" + existing.getId())
                .with(csrf())
                .header("X-Auth-User-Id", "99")
                .header("X-Auth-User-Role", "USER") // Simple User
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("PUT /wods/{id}: Author can update Personal WOD")
  void testUpdateWod_Personal_Success() throws Exception {
    Wod personalWod =
        Wod.builder()
            .title("My WOD")
            .wodType(WodType.FOR_TIME)
            .scoreType(ScoreType.TIME)
            .authorId(100L)
            .build();
    wodRepository.save(personalWod);

    WodRequest updateRequest =
        new WodRequest(
            "My WOD Updated",
            WodType.FOR_TIME,
            ScoreType.TIME,
            null, null, null, null, false, null, null, null, null,
            List.of(new WodMovementRequest(pullUp.getId(), 1, "10", 0.0, null, 0, null, 0.0, null, 0, null, null)));

    mockMvc
        .perform(
            put("/aldebaran/wods/" + personalWod.getId())
                .with(csrf())
                .header("X-Auth-User-Id", "100") // Author
                .header("X-Auth-User-Role", "USER")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateRequest)))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("PUT /wods/{id}: Non-Author cannot update Personal WOD")
  void testUpdateWod_Personal_Forbidden() throws Exception {
    Wod personalWod =
        Wod.builder()
            .title("My WOD")
            .wodType(WodType.FOR_TIME)
            .scoreType(ScoreType.TIME)
            .authorId(100L)
            .build();
    wodRepository.save(personalWod);

    WodRequest updateRequest =
        new WodRequest(
            "Hacked",
            WodType.FOR_TIME,
            ScoreType.TIME,
            null, null, null, null, false, null, null, null, null,
            List.of(new WodMovementRequest(pullUp.getId(), 1, "10", 0.0, null, 0, null, 0.0, null, 0, null, null)));

    mockMvc
        .perform(
            put("/aldebaran/wods/" + personalWod.getId())
                .with(csrf())
                .header("X-Auth-User-Id", "200") // Not Author
                .header("X-Auth-User-Role", "USER")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateRequest)))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("PUT /wods/{id}: should return 404 if WOD not found")
  void testUpdateWod_NotFound() throws Exception {
    WodRequest request =
        new WodRequest(
            "Ghost Wod",
            WodType.FOR_TIME,
            ScoreType.TIME,
            null,
            null,
            null,
            null,
            true,
            0,
            0,
            0,
            null,
            List.of(
                new WodMovementRequest(
                    pullUp.getId(), 1, "10", 0.0, null, 0, null, 0.0, null, 0, null, null)));

    mockMvc
        .perform(
            put("/aldebaran/wods/99999")
                .with(csrf())
                .header("X-Auth-User-Id", "1")
                .header("X-Auth-User-Role", "ADMIN")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("PUT /wods/{id}: should return 409 Conflict if WOD is locked (has scores)")
  void testUpdateWod_Locked() throws Exception {
    Wod existing = wodRepository.findAll().get(0);

    // Create a score to lock the WOD
    WodScore score =
        WodScore.builder()
            .wod(existing)
            .userId(100L)
            .date(LocalDate.now())
            .scaling(ScalingLevel.RX)
            .timeSeconds(300)
            .build();
    wodScoreRepository.save(score);

    WodRequest updateRequest =
        new WodRequest(
            "Fran Modified",
            WodType.FOR_TIME,
            ScoreType.TIME,
            null,
            null,
            null,
            null,
            true,
            0,
            0,
            0,
            null,
            List.of(
                new WodMovementRequest(
                    pullUp.getId(), 1, "21-15-9", 0.0, null, 0, null, 0.0, null, 0, null, null)));

    mockMvc
        .perform(
            put("/aldebaran/wods/" + existing.getId())
                .with(csrf())
                .header("X-Auth-User-Id", "1")
                .header("X-Auth-User-Role", "ADMIN")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateRequest)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.title").value("Resource Locked"));
  }

  // -------------------------------------------------------------------------
  // DELETE Operations
  // -------------------------------------------------------------------------

  @Test
  @DisplayName("DELETE /wods/{id}: should delete WOD when Admin")
  void testDeleteWod_Success() throws Exception {
    Wod existing = wodRepository.findAll().get(0);

    mockMvc
        .perform(
            delete("/aldebaran/wods/" + existing.getId())
                .with(csrf())
                .header("X-Auth-User-Id", "1")
                .header("X-Auth-User-Role", "ADMIN")) // Global Admin
        .andExpect(status().isNoContent());

    assert (wodRepository.findById(existing.getId()).isEmpty());
  }

  @Test
  @DisplayName("DELETE /wods/{id}: should return 403 Forbidden for simple User")
  void testDeleteWod_Forbidden() throws Exception {
    Wod existing = wodRepository.findAll().get(0);

    mockMvc
        .perform(
            delete("/aldebaran/wods/" + existing.getId())
                .with(csrf())
                .header("X-Auth-User-Id", "99")
                .header("X-Auth-User-Role", "USER"))
        .andExpect(status().isForbidden());

    assert (wodRepository.findById(existing.getId()).isPresent());
  }
}
