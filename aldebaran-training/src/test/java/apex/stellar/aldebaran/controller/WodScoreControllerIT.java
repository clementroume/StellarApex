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
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Integration tests for {@link WodScoreController}.
 *
 * <p>Verifies the full lifecycle of a score: Input (User Units) -> Storage (System Units) -> Output
 * (User Units).
 */
@Transactional
class WodScoreControllerIT extends BaseIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private JsonMapper objectMapper;
  @Autowired private WodScoreRepository scoreRepository;
  @Autowired private WodRepository wodRepository;
  @Autowired private StringRedisTemplate redisTemplate;

  private Wod fran;
  private Wod gymWod;

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

    // WOD privé appartenant au Gym 101
    gymWod =
        Wod.builder()
            .title("Gym 101 Exclusive")
            .wodType(WodType.AMRAP)
            .scoreType(ScoreType.ROUNDS_REPS)
            .gymId(101L)
            .isPublic(false)
            .build();
    wodRepository.save(gymWod);
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
  // Normalization Tests (POST)
  // -------------------------------------------------------------------------

  @Test
  @DisplayName("POST /scores: should normalize Time Input (1 min 30 -> 90s)")
  void testLogScore_TimeNormalization() throws Exception {
    WodScoreRequest request =
        new WodScoreRequest(
            null, // userId (defaults to current)
            fran.getId(),
            LocalDate.now(),
            1,
            30, // 1 min 30s
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
                .header("X-Auth-User-Id", "100")
                .header("X-Auth-User-Role", "USER")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated())
        // Response should reflect user input preference
        .andExpect(jsonPath("$.timeMinutesPart").value(1))
        .andExpect(jsonPath("$.timeSecondsPart").value(30))
        .andExpect(jsonPath("$.timeDisplayUnit").value("MINUTES"));

    // DB Verification: Canonical storage
    List<WodScore> scores = scoreRepository.findAll();
    assert (scores.getFirst().getTimeSeconds() == 90);
  }

  @Test
  @DisplayName("POST /scores: should normalize Weight Input (LBS -> KG)")
  void testLogScore_WeightNormalization() throws Exception {
    // 225 LBS ~ 102.058 KG
    WodScoreRequest request =
        new WodScoreRequest(
            null,
            fran.getId(),
            LocalDate.now(),
            null,
            600,
            null,
            null,
            225.0,
            null,
            Unit.LBS,
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
                .header("X-Auth-User-Id", "100")
                .header("X-Auth-User-Role", "USER")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated())
        // Response should match input
        .andExpect(jsonPath("$.maxWeight").value(225.0))
        .andExpect(jsonPath("$.weightUnit").value("LBS"));

    // DB Verification: Canonical storage
    List<WodScore> scores = scoreRepository.findAll();
    double storedKg = scores.getFirst().getMaxWeightKg();
    assert (storedKg > 102.0 && storedKg < 103.0);
  }

  @Test
  @DisplayName("POST /scores: should normalize Distance Input (MILES -> METERS)")
  void testLogScore_DistanceNormalization() throws Exception {
    // 10 MILES ~ 16093.4 METERS
    WodScoreRequest request =
        new WodScoreRequest(
            null,
            fran.getId(),
            LocalDate.now(),
            null,
            600,
            null,
            null,
            null,
            null,
            null,
            10.0,
            Unit.MILES,
            null,
            ScalingLevel.RX,
            false,
            null,
            null);

    mockMvc
        .perform(
            post("/aldebaran/scores")
                .header("X-Auth-User-Id", "100")
                .header("X-Auth-User-Role", "USER")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated())
        // Response should match input
        .andExpect(jsonPath("$.totalDistance").value(10.0))
        .andExpect(jsonPath("$.distanceUnit").value("MILES"));

    // DB Verification: Canonical storage
    List<WodScore> scores = scoreRepository.findAll();
    double storedMeters = scores.getFirst().getTotalDistanceMeters();
    assert (storedMeters > 16093.0 && storedMeters < 16094.0);
  }

  // -------------------------------------------------------------------------
  // Round-Trip Tests (GET retrieves what was POSTed)
  // -------------------------------------------------------------------------

  @Test
  @DisplayName("Round-Trip: POST in LBS/MILES -> DB (KG/M) -> GET in LBS/MILES")
  void testRoundTrip_Conversion() throws Exception {
    // 1. POST a complex score (Imperial units + Time split)
    WodScoreRequest request =
        new WodScoreRequest(
            null,
            fran.getId(),
            LocalDate.now(),
            2,
            15, // 2min 15s (135s)
            null,
            null,
            135.0, // 135 LBS
            null,
            Unit.LBS,
            5.0, // 5 MILES
            Unit.MILES,
            null,
            ScalingLevel.RX,
            false,
            null,
            null);

    mockMvc
        .perform(
            post("/aldebaran/scores")
                .header("X-Auth-User-Id", "100")
                .header("X-Auth-User-Role", "USER")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated());

    // 2. GET the history
    mockMvc
        .perform(
            get("/aldebaran/scores/me?wodId=" + fran.getId())
                .header("X-Auth-User-Id", "100")
                .header("X-Auth-User-Role", "USER"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content", hasSize(1)))
        // Verify Time reconstruction
        .andExpect(jsonPath("$.content[0].timeSeconds").value(135))
        .andExpect(jsonPath("$.content[0].timeMinutesPart").value(2))
        .andExpect(jsonPath("$.content[0].timeSecondsPart").value(15))
        // Verify Weight reconstruction (should be exactly 135.0 LBS)
        .andExpect(jsonPath("$.content[0].maxWeight").value(135.0))
        .andExpect(jsonPath("$.content[0].weightUnit").value("LBS"))
        // Verify Distance reconstruction (should be exactly 5.0 MILES)
        .andExpect(jsonPath("$.content[0].totalDistance").value(5.0))
        .andExpect(jsonPath("$.content[0].distanceUnit").value("MILES"));
  }

  // -------------------------------------------------------------------------
  // Standard Operations
  // -------------------------------------------------------------------------

  @Test
  @DisplayName("PUT /{id}: User updates own score -> 200 OK")
  void testUpdateScore_Owner_Success() throws Exception {
    WodScore score = createScore(100L, 100);

    WodScoreRequest updateRequest =
        new WodScoreRequest(
            100L,
            fran.getId(),
            LocalDate.now(),
            null,
            120,
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
            put("/aldebaran/scores/" + score.getId())
                .header("X-Auth-User-Id", "100")
                .header("X-Auth-User-Role", "ROLE_USER")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateRequest)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.timeSeconds").value(120));
  }

  @Test
  @DisplayName("PUT /{id}: User tries to update other's score -> 403 Forbidden")
  void testUpdateScore_OtherUser_Forbidden() throws Exception {
    WodScore score = createScore(100L, 100); // Owner is 100

    WodScoreRequest updateRequest =
        new WodScoreRequest(
            100L,
            fran.getId(),
            LocalDate.now(),
            null,
            120,
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
            put("/aldebaran/scores/" + score.getId())
                .header("X-Auth-User-Id", "200") // User 200 tries to update
                .header("X-Auth-User-Role", "ROLE_USER")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateRequest)))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("PUT /{id}: Coach updates member score (Same Gym) -> 200 OK")
  void testUpdateScore_CoachSameGym_Success() throws Exception {
    // Score sur un WOD du Gym 101
    WodScore score =
        WodScore.builder()
            .wod(gymWod) // Gym 101
            .userId(100L)
            .date(LocalDate.now())
            .scaling(ScalingLevel.RX)
            .rounds(10)
            .loggedAt(java.time.LocalDateTime.now())
            .build();
    score = scoreRepository.save(score);

    WodScoreRequest updateRequest =
        new WodScoreRequest(
            100L,
            gymWod.getId(),
            LocalDate.now(),
            null,
            null,
            12,
            0,
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
            put("/aldebaran/scores/" + score.getId())
                .header("X-Auth-User-Id", "50") // Coach
                .header("X-Auth-Gym-Id", "101") // Same Gym
                .header("X-Auth-User-Role", "ROLE_COACH")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateRequest)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.rounds").value(12));
  }

  @Test
  @DisplayName("POST /scores: Coach logs score for athlete (Same Gym) -> 201 Created")
  void testLogScore_CoachForAthlete_Success() throws Exception {
    WodScoreRequest request =
        new WodScoreRequest(
            100L, // Target Athlete
            gymWod.getId(), // Gym 101 WOD
            LocalDate.now(),
            null,
            null,
            10,
            0,
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
                .header("X-Auth-User-Id", "50") // Coach
                .header("X-Auth-Gym-Id", "101") // Same Gym
                .header("X-Auth-User-Role", "ROLE_COACH")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.userId").value(100));
  }

  @Test
  @DisplayName("DELETE /{id}: Admin deletes any score -> 204 No Content")
  void testDeleteScore_Admin_Success() throws Exception {
    WodScore score = createScore(100L, 100);

    mockMvc
        .perform(
            delete("/aldebaran/scores/" + score.getId())
                .header("X-Auth-User-Id", "1")
                .header("X-Auth-User-Role", "ROLE_ADMIN")
                .with(csrf()))
        .andExpect(status().isNoContent());

    assert (scoreRepository.findById(score.getId()).isEmpty());
  }

  @Test
  @DisplayName("DELETE /{id}: should delete own score")
  void testDeleteScore_Success() throws Exception {
    WodScore score =
        WodScore.builder()
            .wod(fran)
            .userId(100L)
            .date(LocalDate.now())
            .scaling(ScalingLevel.RX)
            .timeSeconds(100)
            .loggedAt(java.time.LocalDateTime.now())
            .build();
    score = scoreRepository.save(score);

    mockMvc
        .perform(
            delete("/aldebaran/scores/" + score.getId())
                .header("X-Auth-User-Id", "100")
                .header("X-Auth-User-Role", "USER")
                .with(csrf()))
        .andExpect(status().isNoContent());
  }

  @Test
  @DisplayName("DELETE /{id}: should return 403 Forbidden when deleting others' score")
  void testDeleteScore_Others_Forbidden() throws Exception {
    WodScore score =
        WodScore.builder()
            .wod(fran)
            .userId(200L)
            .date(LocalDate.now())
            .scaling(ScalingLevel.RX)
            .timeSeconds(100)
            .loggedAt(java.time.LocalDateTime.now())
            .build();
    score = scoreRepository.save(score);

    mockMvc
        .perform(
            delete("/aldebaran/scores/" + score.getId())
                .header("X-Auth-User-Id", "999")
                .header("X-Auth-User-Role", "USER")
                .with(csrf()))
        .andExpect(status().isForbidden());
  }

  // -------------------------------------------------------------------------
  // Comparison / Leaderboard Tests
  // -------------------------------------------------------------------------

  @Test
  @DisplayName("GET /{id}/compare: should return rank 1 for best score")
  void testCompareScore_Best() throws Exception {
    // 1. Create 3 scores for Fran (Time: Lower is better)
    // Score A: 100s (Best)
    // Score B: 200s
    // Score C: 300s

    createScore(101L, 100);
    WodScore scoreB = createScore(102L, 200);
    createScore(103L, 300);

    // 2. Compare Score B (Should be Rank 2)
    mockMvc
        .perform(
            get("/aldebaran/scores/" + scoreB.getId() + "/compare")
                .header("X-Auth-User-Id", "102")
                .header("X-Auth-User-Role", "USER"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.rank").value(2))
        .andExpect(jsonPath("$.totalScores").value(3))
        .andExpect(jsonPath("$.percentile").value(50.0)); // (3-2)/2 * 100 = 50%
  }

  private WodScore createScore(Long userId, int seconds) {
    WodScore s =
        WodScore.builder()
            .wod(fran)
            .userId(userId)
            .date(LocalDate.now())
            .scaling(ScalingLevel.RX)
            .timeSeconds(seconds)
            .loggedAt(java.time.LocalDateTime.now())
            .build();
    return scoreRepository.save(s);
  }
}
