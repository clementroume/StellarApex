package apex.stellar.aldebaran.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import apex.stellar.aldebaran.config.BaseIntegrationTest;
import apex.stellar.aldebaran.dto.ScoreRequest;
import apex.stellar.aldebaran.dto.WodCopyRequest;
import apex.stellar.aldebaran.exception.AntaresUnavailableException;
import apex.stellar.aldebaran.model.entities.Score;
import apex.stellar.aldebaran.model.entities.Score.ScalingLevel;
import apex.stellar.aldebaran.model.entities.Wod;
import apex.stellar.aldebaran.model.entities.Wod.ScoreType;
import apex.stellar.aldebaran.model.entities.Wod.WodType;
import apex.stellar.aldebaran.repository.ScoreRepository;
import apex.stellar.aldebaran.repository.WodRepository;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/**
 * Integration tests of competition WODs: a public WOD copied by a gym, scores logged and verified
 * by the gym's judging staff (SCORE_VERIFY), athletes' verified scores locked.
 */
@Transactional
class CompetitionScoreIT extends BaseIntegrationTest {

  private static final long GYM = 101L;
  private static final long COACH = 2L;
  private static final long ATHLETE = 99L;
  private static final String COACH_MEMBERSHIPS = GYM + ":COACH:SCORE_VERIFY+WOD_WRITE";
  private static final String ATHLETE_MEMBERSHIPS = GYM + ":ATHLETE:";

  @Autowired private ScoreRepository scoreRepository;
  @Autowired private WodRepository wodRepository;

  private Wod open;
  private Wod competition;

  @BeforeEach
  void setUp() {
    scoreRepository.deleteAll();
    wodRepository.deleteAll();
    open =
        wodRepository.save(
            Wod.builder()
                .title("26.1")
                .wodType(WodType.FOR_TIME)
                .scoreType(ScoreType.TIME)
                .isPublic(true)
                .build());
    competition =
        wodRepository.save(
            Wod.builder()
                .title("26.1")
                .wodType(WodType.FOR_TIME)
                .scoreType(ScoreType.TIME)
                .gymId(GYM)
                .competition(true)
                .sourceWodId(open.getId())
                .build());
  }

  private static MockHttpServletRequestBuilder as(
      MockHttpServletRequestBuilder request, long userId, String memberships) {
    return request
        .header("X-Auth-User-Id", String.valueOf(userId))
        .header("X-Auth-User-Role", "USER")
        .header("X-Auth-Memberships", memberships)
        .header("X-Internal-Secret", "test-internal-secret")
        .contentType(MediaType.APPLICATION_JSON);
  }

  private String scoreFor(Long userId, Long wodId, int minutes) throws Exception {
    return objectMapper.writeValueAsString(
        new ScoreRequest(
            userId, LocalDate.now(), wodId, minutes, 0, null, null, null, null, null, null, null,
            null, ScalingLevel.RX, false, null, null, null, null));
  }

  private Score saveScore(long userId, boolean verified) {
    return scoreRepository.save(
        Score.builder()
            .userId(userId)
            .wod(competition)
            .gymId(GYM)
            .date(LocalDate.now())
            .timeSeconds(600)
            .scaling(ScalingLevel.RX)
            .personalRecord(true)
            .verifiedBy(verified ? COACH : null)
            .verifiedAt(verified ? java.time.LocalDateTime.now() : null)
            .build());
  }

  @Test
  @DisplayName("Copy: a coach copies a public WOD as a private competition WOD of the gym")
  void copyPublicWodAsCompetition() throws Exception {
    mockMvc
        .perform(
            as(post("/aldebaran/wods/" + open.getId() + "/copy"), COACH, COACH_MEMBERSHIPS)
                .content(objectMapper.writeValueAsString(new WodCopyRequest(GYM, true))))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.title").value("26.1"))
        .andExpect(jsonPath("$.gymId").value(GYM))
        .andExpect(jsonPath("$.competition").value(true))
        .andExpect(jsonPath("$.isPublic").value(false))
        .andExpect(jsonPath("$.sourceWodId").value(open.getId()))
        .andExpect(jsonPath("$.capabilities.locked").value(false))
        .andExpect(jsonPath("$.capabilities.canJudge").value(true));

    // An athlete cannot copy into the gym (no WOD_WRITE)
    mockMvc
        .perform(
            as(post("/aldebaran/wods/" + open.getId() + "/copy"), ATHLETE, ATHLETE_MEMBERSHIPS)
                .content(objectMapper.writeValueAsString(new WodCopyRequest(GYM, true))))
        .andExpect(status().isForbidden());

    // A personal competition copy is rejected
    mockMvc
        .perform(
            as(post("/aldebaran/wods/" + open.getId() + "/copy"), ATHLETE, ATHLETE_MEMBERSHIPS)
                .content(objectMapper.writeValueAsString(new WodCopyRequest(null, true))))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("Log for an athlete: staff on a competition WOD, member confirmed -> verified")
  void staffLogsForMember() throws Exception {
    when(antaresClient.isGymMember(GYM, ATHLETE)).thenReturn(true);

    mockMvc
        .perform(
            as(post("/aldebaran/scores"), COACH, COACH_MEMBERSHIPS)
                .content(scoreFor(ATHLETE, competition.getId(), 9)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.userId").value(ATHLETE))
        .andExpect(jsonPath("$.loggedBy").value(COACH))
        .andExpect(jsonPath("$.verifiedBy").value(COACH))
        .andExpect(jsonPath("$.capabilities.canModify").value(true))
        .andExpect(jsonPath("$.capabilities.canVerify").value(true));
  }

  @Test
  @DisplayName("Log for an athlete: refused for a non-member, 503 when Antares is down")
  void staffLogsForNonMemberOrAntaresDown() throws Exception {
    when(antaresClient.isGymMember(GYM, ATHLETE)).thenReturn(false);
    mockMvc
        .perform(
            as(post("/aldebaran/scores"), COACH, COACH_MEMBERSHIPS)
                .content(scoreFor(ATHLETE, competition.getId(), 9)))
        .andExpect(status().isForbidden());

    when(antaresClient.isGymMember(GYM, ATHLETE))
        .thenThrow(new AntaresUnavailableException("down", null));
    mockMvc
        .perform(
            as(post("/aldebaran/scores"), COACH, COACH_MEMBERSHIPS)
                .content(scoreFor(ATHLETE, competition.getId(), 9)))
        .andExpect(status().isServiceUnavailable());

    // Not a competition WOD: never, whatever Antares says
    mockMvc
        .perform(
            as(post("/aldebaran/scores"), COACH, COACH_MEMBERSHIPS)
                .content(scoreFor(ATHLETE, open.getId(), 9)))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("Verify: staff verifies, the athlete's score is then locked until withdrawn")
  void verifyLocksForAthlete() throws Exception {
    Score score = saveScore(ATHLETE, false);

    // The athlete cannot verify their own score
    mockMvc
        .perform(as(post("/aldebaran/scores/" + score.getId() + "/verify"), ATHLETE, ATHLETE_MEMBERSHIPS))
        .andExpect(status().isForbidden());

    mockMvc
        .perform(as(post("/aldebaran/scores/" + score.getId() + "/verify"), COACH, COACH_MEMBERSHIPS))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.verifiedBy").value(COACH));

    mockMvc
        .perform(
            as(put("/aldebaran/scores/" + score.getId()), ATHLETE, ATHLETE_MEMBERSHIPS)
                .content(scoreFor(null, competition.getId(), 5)))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(as(delete("/aldebaran/scores/" + score.getId()), ATHLETE, ATHLETE_MEMBERSHIPS))
        .andExpect(status().isForbidden());

    mockMvc
        .perform(as(delete("/aldebaran/scores/" + score.getId() + "/verify"), COACH, COACH_MEMBERSHIPS))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.verifiedBy").doesNotExist());

    mockMvc
        .perform(
            as(put("/aldebaran/scores/" + score.getId()), ATHLETE, ATHLETE_MEMBERSHIPS)
                .content(scoreFor(null, competition.getId(), 5)))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("Leaderboard: verifiedOnly keeps the verified personal records only")
  void leaderboardVerifiedOnly() throws Exception {
    saveScore(ATHLETE, true);
    saveScore(ATHLETE + 1, false);

    mockMvc
        .perform(as(get("/aldebaran/scores/leaderboard/" + competition.getId()), ATHLETE, ATHLETE_MEMBERSHIPS))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(2));

    mockMvc
        .perform(
            as(get("/aldebaran/scores/leaderboard/" + competition.getId()), ATHLETE, ATHLETE_MEMBERSHIPS)
                .param("verifiedOnly", "true"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.content[0].userId").value(ATHLETE))
        .andExpect(jsonPath("$.content[0].capabilities.canModify").value(false))
        .andExpect(jsonPath("$.content[0].capabilities.canVerify").value(false));
  }

  @Test
  @DisplayName("Leaderboard: myGyms keeps the scores performed in the caller's gyms (public WOD)")
  void leaderboardMyGyms() throws Exception {
    for (long[] row : new long[][] {{ATHLETE, GYM}, {ATHLETE + 1, 555L}}) {
      scoreRepository.save(
          Score.builder()
              .userId(row[0])
              .wod(open)
              .gymId(row[1])
              .date(LocalDate.now())
              .timeSeconds(600)
              .scaling(ScalingLevel.RX)
              .personalRecord(true)
              .build());
    }

    mockMvc
        .perform(as(get("/aldebaran/scores/leaderboard/" + open.getId()), ATHLETE, ATHLETE_MEMBERSHIPS))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(2));
    mockMvc
        .perform(
            as(get("/aldebaran/scores/leaderboard/" + open.getId()), ATHLETE, ATHLETE_MEMBERSHIPS)
                .param("myGyms", "true"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.content[0].gymId").value(GYM));
    // No gym at all: nothing to show
    mockMvc
        .perform(
            as(get("/aldebaran/scores/leaderboard/" + open.getId()), ATHLETE, "")
                .param("myGyms", "true"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(0));
  }

  @Test
  @DisplayName("Virtual programming: no copy into it, no competition WOD (400)")
  void programmingGymRefusesCopiesAndCompetitions() throws Exception {
    when(antaresClient.isProgrammingGym(GYM)).thenReturn(true);

    mockMvc
        .perform(
            as(post("/aldebaran/wods/" + open.getId() + "/copy"), COACH, COACH_MEMBERSHIPS)
                .content(objectMapper.writeValueAsString(new WodCopyRequest(GYM, false))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail").value("WODs cannot be copied into a virtual programming."));
  }
}
