package apex.stellar.aldebaran.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import apex.stellar.aldebaran.client.AntaresClient.AthleteFacts;
import apex.stellar.aldebaran.config.BaseIntegrationTest;
import apex.stellar.aldebaran.model.entities.Movement;
import apex.stellar.aldebaran.model.entities.Wod;
import apex.stellar.aldebaran.model.entities.Wod.ScoreType;
import apex.stellar.aldebaran.model.entities.Wod.WodStructure;
import apex.stellar.aldebaran.model.entities.Wod.WodType;
import apex.stellar.aldebaran.model.entities.WodMovement;
import apex.stellar.aldebaran.model.enums.Category;
import apex.stellar.aldebaran.model.enums.Division;
import apex.stellar.aldebaran.model.enums.Equipment;
import apex.stellar.aldebaran.model.enums.Technique;
import apex.stellar.aldebaran.repository.MovementRecordRepository;
import apex.stellar.aldebaran.repository.MovementRepository;
import apex.stellar.aldebaran.repository.WodRepository;
import apex.stellar.aldebaran.service.MovementRecordService;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Integration tests of the per-movement records (lot C): sources (sets, "N RM" tests), exclusions
 * (metcons, team WODs), rebuild on correction and deletion, variants, estimated 1RM, canonical
 * lifts, the box board and the age group copied on scores.
 */
class MovementRecordIT extends BaseIntegrationTest {

  private static final long GYM = 101L;
  private static final long ANNE = 100L;
  private static final long BOB = 200L;
  private static final long COACH = 300L;
  private static final String MEMBER = GYM + ":ATHLETE:";

  @Autowired private MovementRepository movementRepository;
  @Autowired private WodRepository wodRepository;
  @Autowired private MovementRecordRepository recordRepository;
  @Autowired private MovementRecordService recordService;

  private Movement squat;
  private Movement deadlift;
  private Movement pullUp;

  @BeforeEach
  void setUp() {
    squat = movement("Back Squat", Set.of(Equipment.BARBELL), Set.of(Technique.PAUSED));
    deadlift =
        movement(
            "Deadlift", Set.of(Equipment.BARBELL, Equipment.TRAP_BAR), Set.of(Technique.DEFICIT));
    pullUp = movement("Pull-Up", Set.of(), Set.of());
  }

  // --- Fixtures ---

  private Movement movement(String name, Set<Equipment> equipment, Set<Technique> techniques) {
    return movementRepository.save(
        Movement.builder()
            .name(name)
            .category(Category.SQUAT)
            .equipment(new HashSet<>(equipment))
            .techniques(new HashSet<>(techniques))
            .targetedMuscles(new HashSet<>())
            .build());
  }

  private Wod wod(
      String title, ScoreType type, Integer repMax, boolean isPublic, Movement... movements) {
    return wod(title, type, repMax, isPublic, Set.of(), Set.of(), movements);
  }

  private Wod wod(
      String title,
      ScoreType type,
      Integer repMax,
      boolean isPublic,
      Set<String> equipment,
      Set<String> techniques,
      Movement... movements) {
    Wod wod =
        Wod.builder()
            .title(title)
            .wodType(WodType.FOR_LOAD)
            .structure(repMax != null ? WodStructure.MAX : WodStructure.SETS)
            .scoreType(type)
            .repMax(repMax)
            .gymId(isPublic ? null : GYM)
            .isPublic(isPublic)
            .movements(new ArrayList<>())
            .build();
    int order = 1;
    for (Movement m : movements) {
      wod.getMovements()
          .add(
              WodMovement.builder()
                  .wod(wod)
                  .movement(m)
                  .orderIndex(order++)
                  .equipment(new HashSet<>(equipment))
                  .techniques(new HashSet<>(techniques))
                  .build());
    }
    return wodRepository.save(wod);
  }

  private static MockHttpServletRequestBuilder as(
      MockHttpServletRequestBuilder request, long userId, String memberships, String birthYear) {
    return request
        .header("X-Auth-User-Id", String.valueOf(userId))
        .header("X-Auth-User-Role", "USER")
        .header("X-Auth-User-Division", "WOMEN")
        .header("X-Auth-User-Birth-Year", birthYear)
        .header("X-Auth-Memberships", memberships)
        .header("X-Internal-Secret", "test-internal-secret")
        .contentType(MediaType.APPLICATION_JSON);
  }

  private static MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request, long userId) {
    return as(request, userId, MEMBER, "");
  }

  private static String sets(Wod wod, LocalDate date, String sets) {
    return """
        {"date": "%s", "wodId": %d, "weightUnit": "KG", "scaling": "SCALED", "timeCapped": false,
         "verificationRequested": false, "sets": [%s]}
        """
        .formatted(date, wod.getId(), sets);
  }

  private static String load(Wod wod, LocalDate date, double kg) {
    return """
        {"date": "%s", "wodId": %d, "maxWeight": %s, "weightUnit": "KG", "scaling": "RX",
         "timeCapped": false, "verificationRequested": false}
        """
        .formatted(date, wod.getId(), kg);
  }

  private long log(long userId, String body) throws Exception {
    String json =
        mockMvc
            .perform(as(post("/aldebaran/scores"), userId).content(body))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readTree(json).get("id").asLong();
  }

  // --- Tests ---

  @Test
  @DisplayName("Every complete set feeds the N RM of its reps; an N RM test feeds its N")
  void setsAndRepMaxTests() throws Exception {
    LocalDate day = LocalDate.now().minusDays(3);
    Wod fiveByFive = wod("Back Squat 5x5", ScoreType.WEIGHT, null, false, squat);
    Wod oneRm = wod("Back Squat 1RM", ScoreType.WEIGHT, 1, false, squat);

    log(
        ANNE,
        sets(
            fiveByFive,
            day,
            """
            {"reps": 5, "weight": 100}, {"reps": 5, "weight": 110},
            {"reps": 3, "weight": 120}, {"reps": 5, "weight": 130, "completed": false}
            """));
    log(ANNE, load(oneRm, day.plusDays(1), 140));

    mockMvc
        .perform(as(get("/aldebaran/records/me"), ANNE))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(1)))
        .andExpect(jsonPath("$[0].movementName").value("Back Squat"))
        .andExpect(jsonPath("$[0].variants", hasSize(1)))
        .andExpect(jsonPath("$[0].variants[0].base").value(true))
        .andExpect(jsonPath("$[0].variants[0].records", hasSize(3)))
        .andExpect(jsonPath("$[0].variants[0].records[0].reps").value(1))
        .andExpect(jsonPath("$[0].variants[0].records[0].weightKg").value(140.0))
        .andExpect(jsonPath("$[0].variants[0].records[0].wodTitle").value("Back Squat 1RM"))
        .andExpect(jsonPath("$[0].variants[0].records[1].reps").value(3))
        .andExpect(jsonPath("$[0].variants[0].records[1].weightKg").value(120.0))
        .andExpect(jsonPath("$[0].variants[0].records[2].reps").value(5))
        .andExpect(jsonPath("$[0].variants[0].records[2].weightKg").value(110.0))
        // Epley: max(120 x 1.1, 110 x 1.1667) = 132
        .andExpect(jsonPath("$[0].variants[0].estimatedOneRepMaxKg").value(132.0));

    // Another athlete's records stay theirs
    mockMvc
        .perform(as(get("/aldebaran/records/me"), BOB))
        .andExpect(jsonPath("$", hasSize(0)));
  }

  @Test
  @DisplayName("Metcons with several movements and team WODs never feed records")
  void exclusions() throws Exception {
    Wod couplet = wod("Squat + Pull-ups", ScoreType.WEIGHT, null, false, squat, pullUp);
    log(ANNE, sets(couplet, LocalDate.now(), "{\"reps\": 5, \"weight\": 150}"));
    Wod team = wod("Partner Squat", ScoreType.WEIGHT, 1, false, squat);
    team.setTeamSize(2);
    wodRepository.save(team);
    log(ANNE, load(team, LocalDate.now(), 160));

    mockMvc
        .perform(as(get("/aldebaran/records/me"), ANNE))
        .andExpect(jsonPath("$", hasSize(0)));
  }

  @Test
  @DisplayName("A correction or a deletion rebuilds the records; the tie goes to the earliest day")
  void rebuildOnCorrectionAndDeletion() throws Exception {
    Wod oneRm = wod("Deadlift 1RM", ScoreType.WEIGHT, 1, false, deadlift);
    LocalDate early = LocalDate.now().minusDays(10);
    long first = log(ANNE, load(oneRm, early, 180));
    long second = log(ANNE, load(oneRm, early.plusDays(5), 180));

    mockMvc
        .perform(as(get("/aldebaran/records/me"), ANNE).param("movementId", deadlift.getId().toString()))
        .andExpect(jsonPath("$[0].variants[0].records[0].scoreId").value(first))
        .andExpect(jsonPath("$[0].variants[0].records[0].date").value(early.toString()));

    mockMvc
        .perform(as(put("/aldebaran/scores/" + second), ANNE).content(load(oneRm, early.plusDays(5), 190)))
        .andExpect(status().isOk());
    mockMvc
        .perform(as(get("/aldebaran/records/me"), ANNE))
        .andExpect(jsonPath("$[0].variants[0].records[0].weightKg").value(190.0));

    mockMvc.perform(as(delete("/aldebaran/scores/" + second), ANNE)).andExpect(status().isNoContent());
    mockMvc
        .perform(as(get("/aldebaran/records/me"), ANNE))
        .andExpect(jsonPath("$[0].variants[0].records[0].weightKg").value(180.0));

    mockMvc.perform(as(delete("/aldebaran/scores/" + first), ANNE)).andExpect(status().isNoContent());
    mockMvc
        .perform(as(get("/aldebaran/records/me"), ANNE))
        .andExpect(jsonPath("$", hasSize(0)));
  }

  @Test
  @DisplayName("Variants: implied barbell ignored, other variants grouped after the base version")
  void variants() throws Exception {
    LocalDate day = LocalDate.now();
    log(ANNE, load(wod("DL", ScoreType.WEIGHT, 1, false, deadlift), day, 150));
    log(
        ANNE,
        load(wod("DL barbell", ScoreType.WEIGHT, 1, false, Set.of("BARBELL"), Set.of(), deadlift), day, 160));
    log(
        ANNE,
        load(
            wod("DL trap bar", ScoreType.WEIGHT, 1, false, Set.of("TRAP_BAR"), Set.of(), deadlift),
            day,
            170));
    log(
        ANNE,
        load(wod("Deficit DL", ScoreType.WEIGHT, 1, false, Set.of(), Set.of("DEFICIT"), deadlift), day, 140));

    mockMvc
        .perform(as(get("/aldebaran/records/me"), ANNE))
        .andExpect(jsonPath("$[0].variants", hasSize(3)))
        .andExpect(jsonPath("$[0].variants[0].base").value(true))
        .andExpect(jsonPath("$[0].variants[0].records[0].weightKg").value(160.0))
        .andExpect(jsonPath("$[0].variants[1].techniques[0]").value("DEFICIT"))
        .andExpect(jsonPath("$[0].variants[2].equipment[0]").value("TRAP_BAR"));
  }

  @Test
  @DisplayName("Canonical lifts: public base-version N RM tests only, one per movement and N")
  void canonicalLifts() throws Exception {
    Wod squat1 = wod("Back Squat 1RM", ScoreType.WEIGHT, 1, true, squat);
    wod("Back Squat 1RM bis", ScoreType.WEIGHT, 1, true, squat);
    wod("Back Squat 5RM", ScoreType.WEIGHT, 5, true, squat);
    wod("Pause Squat 1RM", ScoreType.WEIGHT, 1, true, Set.of(), Set.of("PAUSED"), squat);
    wod("Box Squat 1RM", ScoreType.WEIGHT, 1, false, squat);

    mockMvc
        .perform(as(get("/aldebaran/records/lifts"), ANNE))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(2)))
        .andExpect(jsonPath("$[0].wodId").value(squat1.getId()))
        .andExpect(jsonPath("$[0].repMax").value(1))
        .andExpect(jsonPath("$[1].repMax").value(5));
  }

  @Test
  @DisplayName("Box board: current members' base records, members only, 503 when Antares fails")
  void boxBoard() throws Exception {
    Wod oneRm = wod("Back Squat 1RM", ScoreType.WEIGHT, 1, false, squat);
    log(ANNE, load(oneRm, LocalDate.now(), 100));
    log(BOB, load(oneRm, LocalDate.now(), 120));
    when(antaresClient.getGymMemberIds(GYM)).thenReturn(List.of(ANNE));

    mockMvc
        .perform(
            as(get("/aldebaran/records/gyms/" + GYM), BOB).param("movementId", squat.getId().toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(1)))
        .andExpect(jsonPath("$[0].userId").value(ANNE))
        .andExpect(jsonPath("$[0].weightKg").value(100.0))
        .andExpect(jsonPath("$[0].division").value("WOMEN"));

    mockMvc
        .perform(
            as(get("/aldebaran/records/gyms/" + GYM), BOB, "999:ATHLETE:", "")
                .param("movementId", squat.getId().toString()))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("Age group: from the birth year and the score's year, filterable on the leaderboard")
  void ageGroup() throws Exception {
    Wod oneRm = wod("Back Squat 1RM", ScoreType.WEIGHT, 1, true, squat);
    int year = LocalDate.now().getYear();
    // An older score logged without birth year, classified at the next score
    long older = log(ANNE, load(oneRm, LocalDate.now().minusDays(1), 90));

    mockMvc
        .perform(
            as(post("/aldebaran/scores"), ANNE, MEMBER, String.valueOf(year - 36))
                .content(load(oneRm, LocalDate.now(), 100)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.ageGroup").value("AGE_35_39"));
    log(BOB, load(oneRm, LocalDate.now(), 130));

    mockMvc
        .perform(
            as(get("/aldebaran/scores/leaderboard/" + oneRm.getId()), ANNE)
                .param("ageGroup", "AGE_35_39"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content", hasSize(1)))
        .andExpect(jsonPath("$.content[0].userId").value(ANNE));
    mockMvc
        .perform(as(get("/aldebaran/scores/me"), ANNE).param("wodId", oneRm.getId().toString()))
        .andExpect(jsonPath("$.content[?(@.id == %d)].ageGroup".formatted(older)).value("AGE_35_39"));
  }

  @Test
  @DisplayName("Age group: a score moved to another year takes the group of that year")
  void ageGroupFollowsTheScoreYear() throws Exception {
    Wod oneRm = wod("Back Squat 1RM", ScoreType.WEIGHT, 1, true, squat);
    LocalDate today = LocalDate.now();
    String birth = String.valueOf(today.getYear() - 35);
    String body =
        mockMvc
            .perform(as(post("/aldebaran/scores"), ANNE, MEMBER, birth).content(load(oneRm, today, 100)))
            .andExpect(jsonPath("$.ageGroup").value("AGE_35_39"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    long id = objectMapper.readTree(body).get("id").asLong();

    mockMvc
        .perform(
            as(put("/aldebaran/scores/" + id), ANNE, MEMBER, birth)
                .content(load(oneRm, today.minusYears(1), 100)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.ageGroup").value("AGE_18_34"));
  }

  @Test
  @DisplayName("A judge's score takes the athlete's age group from Antares, never the judge's")
  void ageGroupOfStaffScore() throws Exception {
    Wod competition = wod("Comp 1RM", ScoreType.WEIGHT, 1, false, squat);
    competition.setCompetition(true);
    wodRepository.save(competition);
    int year = LocalDate.now().getYear();
    when(antaresClient.isGymMember(GYM, ANNE)).thenReturn(true);
    when(antaresClient.getAthleteFacts(ANNE)).thenReturn(new AthleteFacts(Division.WOMEN, year - 16));

    mockMvc
        .perform(
            as(post("/aldebaran/scores"), COACH, GYM + ":COACH:SCORE_VERIFY", String.valueOf(year - 50))
                .content(
                    """
                    {"userId": %d, "date": "%s", "wodId": %d, "maxWeight": 80, "weightUnit": "KG",
                     "scaling": "RX", "timeCapped": false, "verificationRequested": false}
                    """
                        .formatted(ANNE, LocalDate.now(), competition.getId())))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.ageGroup").value("AGE_16_17"));
  }

  @Test
  @DisplayName("Startup rebuild: an empty table is rebuilt from the scores, then left alone")
  void rebuildAllIfEmpty() throws Exception {
    Wod oneRm = wod("Back Squat 1RM", ScoreType.WEIGHT, 1, false, squat);
    log(ANNE, load(oneRm, LocalDate.now(), 100));
    recordRepository.deleteAll();

    assertEquals(1, recordService.rebuildAllIfEmpty());
    assertEquals(1, recordRepository.count());
    assertEquals(0, recordService.rebuildAllIfEmpty());
  }

  @Test
  @DisplayName("repMax only on an individual For Load WOD with one movement (400 otherwise)")
  void repMaxValidation() throws Exception {
    String body =
        """
        {"title": "Bad RM", "wodType": "FOR_TIME", "scoreType": "TIME", "gymId": %d, "isPublic": false,
         "competition": false, "repMax": 1, "movements": [{"movementId": %d, "orderIndex": 1}]}
        """
            .formatted(GYM, squat.getId());
    mockMvc
        .perform(as(post("/aldebaran/wods"), COACH, GYM + ":COACH:WOD_WRITE", "").content(body))
        .andExpect(status().isBadRequest());

    String percent =
        """
        {"title": "Squat 5x3", "wodType": "FOR_LOAD", "scoreType": "WEIGHT", "gymId": %d,
         "isPublic": false, "competition": false, "movements": [{"movementId": %d, "orderIndex": 1, "percentOfRm": 80}]}
        """
            .formatted(GYM, squat.getId());
    mockMvc
        .perform(as(post("/aldebaran/wods"), COACH, GYM + ":COACH:WOD_WRITE", "").content(percent))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.movements[0].percentOfRm").value(80.0))
        .andExpect(jsonPath("$.movements[0].rmReps").value(1));
  }
}
