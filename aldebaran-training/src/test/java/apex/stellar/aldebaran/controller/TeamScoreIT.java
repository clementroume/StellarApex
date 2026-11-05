package apex.stellar.aldebaran.controller;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import apex.stellar.aldebaran.config.BaseIntegrationTest;
import apex.stellar.aldebaran.model.entities.Wod;
import apex.stellar.aldebaran.model.entities.Wod.ScoreType;
import apex.stellar.aldebaran.model.entities.Wod.TeamMode;
import apex.stellar.aldebaran.model.entities.Wod.WodType;
import apex.stellar.aldebaran.model.enums.Division;
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
 * Integration tests of team WODs: one performance shared by the members of a team (one score row
 * each), corrections applied to every member, a member leaving the team, one leaderboard row per
 * team, and no team WOD on a program.
 */
@Transactional
class TeamScoreIT extends BaseIntegrationTest {

  private static final long GYM = 101L;
  private static final long ANNE = 100L;
  private static final long BOB = 200L;
  private static final long OUTSIDER = 300L;
  private static final String MEMBERSHIPS = GYM + ":ATHLETE:";

  @Autowired private ScoreRepository scoreRepository;
  @Autowired private WodRepository wodRepository;

  private Wod partnerWod;

  @BeforeEach
  void setUp() {
    scoreRepository.deleteAll();
    wodRepository.deleteAll();
    partnerWod =
        wodRepository.save(
            Wod.builder()
                .title("Partner Chipper")
                .wodType(WodType.FOR_TIME)
                .scoreType(ScoreType.TIME)
                .gymId(GYM)
                .teamSize(2)
                .teamMode(TeamMode.YOU_GO_I_GO)
                .build());
    when(antaresClient.isGymMember(GYM, BOB)).thenReturn(true);
    when(antaresClient.isGymMember(GYM, OUTSIDER)).thenReturn(false);
    when(antaresClient.getUserDivision(BOB)).thenReturn(Division.WOMEN);
  }

  private static MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request, long userId) {
    return request
        .header("X-Auth-User-Id", String.valueOf(userId))
        .header("X-Auth-User-Role", "USER")
        .header("X-Auth-User-Division", userId == ANNE ? "MEN" : "WOMEN")
        .header("X-Auth-Memberships", MEMBERSHIPS)
        .header("X-Internal-Secret", "test-internal-secret")
        .contentType(MediaType.APPLICATION_JSON);
  }

  private String score(int minutes, Long... teammates) {
    StringBuilder ids = new StringBuilder();
    for (Long id : teammates) {
      ids.append(ids.isEmpty() ? "" : ",").append(id);
    }
    return """
        {"date": "%s", "wodId": %d, "timeMinutes": %d, "timeSeconds": 0, "scaling": "RX",
         "timeCapped": false, "verificationRequested": false, "teammateIds": [%s]}
        """
        .formatted(LocalDate.now(), partnerWod.getId(), minutes, ids);
  }

  private long logTeamScore() throws Exception {
    String body =
        mockMvc
            .perform(as(post("/aldebaran/scores"), ANNE).content(score(10, BOB)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.teamMemberIds", containsInAnyOrder(100, 200)))
            .andExpect(jsonPath("$.division").value("MIXED"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readTree(body).get("id").asLong();
  }

  @Test
  @DisplayName("A team score reaches every member and counts once on the leaderboard")
  void teamScoreSharedByMembers() throws Exception {
    long anneScore = logTeamScore();

    mockMvc
        .perform(
            as(get("/aldebaran/scores/me"), BOB).param("wodId", partnerWod.getId().toString()))
        .andExpect(jsonPath("$.content", hasSize(1)))
        .andExpect(jsonPath("$.content[0].timeSeconds").value(600))
        .andExpect(jsonPath("$.content[0].personalRecord").value(true));
    mockMvc
        .perform(as(get("/aldebaran/scores/leaderboard/" + partnerWod.getId()), ANNE))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content", hasSize(1)))
        .andExpect(jsonPath("$.content[0].teamMemberIds", hasSize(2)));
    // The teammate's copy is not a rival
    mockMvc
        .perform(as(get("/aldebaran/scores/" + anneScore + "/compare"), ANNE))
        .andExpect(jsonPath("$.rank").value(1))
        .andExpect(jsonPath("$.totalScores").value(1));
  }

  @Test
  @DisplayName("Teammates must belong to the box, in the team size, on a team WOD")
  void teammatesChecked() throws Exception {
    mockMvc
        .perform(as(post("/aldebaran/scores"), ANNE).content(score(10, OUTSIDER)))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(as(post("/aldebaran/scores"), ANNE).content(score(10, BOB, OUTSIDER, 400L)))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("A team score is never logged on a program")
  void noTeamScoreOnProgram() throws Exception {
    when(antaresClient.isProgrammingGym(GYM)).thenReturn(true);
    mockMvc
        .perform(as(post("/aldebaran/scores"), ANNE).content(score(10, BOB)))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("A correction applies to the team; a teammate leaves, the logger deletes for all")
  void correctionLeaveAndDelete() throws Exception {
    long anneScore = logTeamScore();
    long bobScore = scoreRepository.findByTeamId(
            scoreRepository.findById(anneScore).orElseThrow().getTeam().getId())
        .stream()
        .filter(s -> s.getUserId() == BOB)
        .findFirst()
        .orElseThrow()
        .getId();

    // Bob corrects the time: Anne's row follows
    mockMvc
        .perform(as(put("/aldebaran/scores/" + bobScore), BOB).content(score(9)))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            as(get("/aldebaran/scores/me"), ANNE).param("wodId", partnerWod.getId().toString()))
        .andExpect(jsonPath("$.content[0].timeSeconds").value(540));

    // Bob leaves the team: Anne keeps her score
    mockMvc.perform(as(delete("/aldebaran/scores/" + bobScore), BOB)).andExpect(status().isNoContent());
    mockMvc
        .perform(
            as(get("/aldebaran/scores/me"), ANNE).param("wodId", partnerWod.getId().toString()))
        .andExpect(jsonPath("$.content", hasSize(1)))
        .andExpect(jsonPath("$.content[0].teamMemberIds", hasSize(1)));

    // A new team score deleted by its logger disappears for both
    long second = logTeamScore();
    mockMvc.perform(as(delete("/aldebaran/scores/" + second), ANNE)).andExpect(status().isNoContent());
    mockMvc
        .perform(
            as(get("/aldebaran/scores/me"), BOB).param("wodId", partnerWod.getId().toString()))
        .andExpect(jsonPath("$.content", hasSize(0)));
  }

  @Test
  @DisplayName("A program has no team WOD")
  void noTeamWodOnProgram() throws Exception {
    when(antaresClient.isProgrammingGym(GYM)).thenReturn(true);
    String wod =
        """
        {"title": "Partner WOD", "wodType": "FOR_TIME", "scoreType": "TIME", "gymId": %d,
         "isPublic": false, "competition": false, "teamSize": 2, "teamMode": "SPLIT",
         "movements": [{"movementId": 1, "orderIndex": 1, "repsScheme": "10"}]}
        """
            .formatted(GYM);
    mockMvc
        .perform(
            post("/aldebaran/wods")
                .header("X-Auth-User-Id", "1")
                .header("X-Auth-User-Role", "USER")
                .header("X-Auth-Memberships", GYM + ":PROGRAMMER:WOD_WRITE")
                .header("X-Internal-Secret", "test-internal-secret")
                .contentType(MediaType.APPLICATION_JSON)
                .content(wod))
        .andExpect(status().isBadRequest());
  }
}
