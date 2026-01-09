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
import apex.stellar.aldebaran.model.enums.Category;
import apex.stellar.aldebaran.repository.MovementRepository;
import apex.stellar.aldebaran.repository.WodRepository;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

/** Integration tests for {@link WodController}. */
class WodControllerIT extends BaseIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private JsonMapper objectMapper;
  @Autowired private WodRepository wodRepository;
  @Autowired private MovementRepository movementRepository;
  @Autowired private StringRedisTemplate redisTemplate;

  private Movement pullUp;

  @BeforeEach
  void setUp() {
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

  @AfterEach
  void cleanUpCache() {
    redisTemplate.execute(
        (RedisConnection connection) -> {
          connection.serverCommands().flushAll();
          return null;
        });
  }

  @Test
  @DisplayName("GET /wods: should return list of summaries")
  void testGetAllWods_Success() throws Exception {
    // Public access allows reading
    mockMvc
        .perform(
            get("/aldebaran/wods")
                .header("X-Auth-User-Id", "1")
                .header("X-Auth-User-Role", "ROLE_USER"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(1)))
        .andExpect(jsonPath("$[0].title").value("Fran"))
        .andExpect(jsonPath("$[0].wodType").value("FOR_TIME"));
  }

  @Test
  @DisplayName("GET /wods/{id}: should return detailed WOD")
  void testGetWod_Success() throws Exception {
    Wod fran = wodRepository.findAll().get(0);

    mockMvc
        .perform(
            get("/aldebaran/wods/" + fran.getId())
                .header("X-Auth-User-Id", "1")
                .header("X-Auth-User-Role", "ROLE_USER"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.title").value("Fran"));
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
                .header("X-Auth-User-Role", "ROLE_COACH")
                .header("X-Auth-User-Permissions", "WOD_WRITE") // Permission requise
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.title").value("Murph"))
        .andExpect(jsonPath("$.movements", hasSize(1)));
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
                .header("X-Auth-User-Role", "ROLE_COACH")
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
                .header("X-Auth-User-Role", "ROLE_USER") // Simple User
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isForbidden());
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
                .header("X-Auth-User-Role", "ROLE_ADMIN")) // Global Admin
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
                .header("X-Auth-User-Role", "ROLE_USER"))
        .andExpect(status().isForbidden());

    assert (wodRepository.findById(existing.getId()).isPresent());
  }
}
