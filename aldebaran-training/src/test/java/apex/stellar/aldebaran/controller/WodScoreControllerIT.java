package apex.stellar.aldebaran.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import apex.stellar.aldebaran.config.BaseIntegrationTest;
import apex.stellar.aldebaran.dto.WodScoreRequest;
import apex.stellar.aldebaran.model.entities.Wod;
import apex.stellar.aldebaran.model.entities.Wod.ScoreType;
import apex.stellar.aldebaran.model.entities.Wod.WodType;
import apex.stellar.aldebaran.model.entities.WodScore;
import apex.stellar.aldebaran.model.entities.WodScore.ScalingLevel;
import apex.stellar.aldebaran.model.enums.Unit;
import apex.stellar.aldebaran.repository.WodRepository;
import apex.stellar.aldebaran.repository.WodScoreRepository;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Integration tests for {@link WodScoreController}. * Simulated Context: Auth is handled upstream
 * (Traefik). We use @WithMockUser to simulate the authenticated Principal arriving at the
 * controller.
 */
@Transactional
class WodScoreControllerIT extends BaseIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private JsonMapper objectMapper;
  @Autowired private WodScoreRepository scoreRepository;
  @Autowired private WodRepository wodRepository;

  @Autowired private StringRedisTemplate stringRedisTemplate;

  private Wod fran;

  @BeforeEach
  void setUp() {
    scoreRepository.deleteAll();
    wodRepository.deleteAll();

    fran =
        Wod.builder()
            .title("Fran")
            .wodType(WodType.FOR_TIME)
            .scoreType(ScoreType.TIME)
            .isPublic(true)
            .build();
    wodRepository.save(fran);
  }

  @AfterEach
  void cleanUpCache() {
    if (stringRedisTemplate.getConnectionFactory() != null) {
      stringRedisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
    }
  }

  // --- GET ---

  @Test
  @WithMockUser(username = "athlete") // "athlete" devient le SecurityUtils.getCurrentUserId()
  @DisplayName("GET /scores/me: should return only authenticated user scores")
  void testGetMyScores_Success() throws Exception {
    // Given
    seedScore("athlete", 300);
    seedScore("other", 240);

    // When/Then
    mockMvc
        .perform(get("/aldebaran/scores/me"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(1)))
        .andExpect(jsonPath("$[0].timeSeconds").value(300));
  }

  // --- POST ---

  @Test
  @WithMockUser(username = "athlete")
  @DisplayName("POST /scores: should log score and return 201 Created")
  void testLogScore_Success() throws Exception {
    WodScoreRequest request =
        new WodScoreRequest(
            fran.getId(),
            LocalDate.now(),
            420,
            Unit.SECONDS,
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
            "Tough!",
            "No Scaling");

    mockMvc
        .perform(
            post("/aldebaran/scores")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.timeSeconds").value(420))
        .andExpect(jsonPath("$.personalRecord").value(true));
  }

  @Test
  @WithMockUser(username = "athlete")
  @DisplayName("POST /scores: should return 404 if WOD does not exist")
  void testLogScore_WodNotFound() throws Exception {
    WodScoreRequest request =
        new WodScoreRequest(
            9999L,
            LocalDate.now(),
            420,
            Unit.SECONDS,
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

    mockMvc
        .perform(
            post("/aldebaran/scores")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.title").value("Resource Not Found"));
  }

  // --- DELETE ---

  @Test
  @WithMockUser(username = "athlete")
  @DisplayName("DELETE /{id}: should delete own score")
  void testDeleteScore_Own_Success() throws Exception {
    WodScore score = seedScore("athlete", 300);

    mockMvc
        .perform(delete("/aldebaran/scores/" + score.getId()).with(csrf()))
        .andExpect(status().isNoContent());

    assert (scoreRepository.findById(score.getId()).isEmpty());
  }

  @Test
  @WithMockUser(username = "hacker") // "hacker" is the current user
  @DisplayName("DELETE /{id}: should return 403 Forbidden when deleting others' score")
  void testDeleteScore_Others_Forbidden() throws Exception {
    WodScore score = seedScore("victim", 300); // Score belongs to "victim"

    mockMvc
        .perform(delete("/aldebaran/scores/" + score.getId()).with(csrf()))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.title").value("Access Denied"));

    assert (scoreRepository.findById(score.getId()).isPresent());
  }

  @Test
  @WithMockUser(username = "athlete")
  @DisplayName("DELETE /{id}: should return 404 if score unknown")
  void testDeleteScore_NotFound() throws Exception {
    mockMvc
        .perform(delete("/aldebaran/scores/99999").with(csrf()))
        .andExpect(status().isNotFound());
  }

  // --- Helper ---

  private WodScore seedScore(String userId, Integer timeSeconds) {
    WodScore score =
        WodScore.builder()
            .wod(fran)
            .userId(userId)
            .date(LocalDate.now())
            .timeSeconds(timeSeconds)
            .scaling(ScalingLevel.RX)
            .loggedAt(java.time.LocalDateTime.now())
            .build();
    return scoreRepository.save(score);
  }
}
