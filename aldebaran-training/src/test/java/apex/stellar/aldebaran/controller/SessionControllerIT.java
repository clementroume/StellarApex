package apex.stellar.aldebaran.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import apex.stellar.aldebaran.config.BaseIntegrationTest;
import apex.stellar.aldebaran.dto.GymSessionSettingsDto;
import apex.stellar.aldebaran.dto.ScoreRequest;
import apex.stellar.aldebaran.dto.ScoreSetRequest;
import apex.stellar.aldebaran.dto.SessionBlockRequest;
import apex.stellar.aldebaran.dto.SessionRequest;
import apex.stellar.aldebaran.dto.SessionVisibilityRequest;
import apex.stellar.aldebaran.dto.WodMovementRequest;
import apex.stellar.aldebaran.dto.WodRequest;
import apex.stellar.aldebaran.model.entities.Movement;
import apex.stellar.aldebaran.model.entities.Score.ScalingLevel;
import apex.stellar.aldebaran.model.entities.Session.Discipline;
import apex.stellar.aldebaran.model.entities.Session.SessionType;
import apex.stellar.aldebaran.model.entities.Session.VisibilityPolicy;
import apex.stellar.aldebaran.model.entities.SessionBlock.Kind;
import apex.stellar.aldebaran.model.entities.Wod;
import apex.stellar.aldebaran.model.entities.Wod.ScoreType;
import apex.stellar.aldebaran.model.entities.Wod.WodType;
import apex.stellar.aldebaran.model.enums.Category;
import apex.stellar.aldebaran.model.enums.Unit;
import apex.stellar.aldebaran.repository.GymSessionSettingsRepository;
import apex.stellar.aldebaran.repository.MovementRepository;
import apex.stellar.aldebaran.repository.ScoreRepository;
import apex.stellar.aldebaran.repository.SessionLogRepository;
import apex.stellar.aldebaran.repository.SessionRepository;
import apex.stellar.aldebaran.repository.WodRepository;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;

/**
 * Integration tests of daily sessions (lot B): composition, visibility per role and policy,
 * journal ("Séance faite"), scores with sets, lock, competition blocks and gym settings.
 */
class SessionControllerIT extends BaseIntegrationTest {

  private static final long BOX = 101L;
  private static final long PROG = 102L;
  private static final long OWNER = 1L;
  private static final long COACH = 2L;
  private static final long ATHLETE = 3L;
  private static final long OUTSIDER = 4L;
  private static final long OTHER_ATHLETE = 5L;
  private static final String OWNER_MEMBERSHIPS =
      BOX + ":OWNER:WOD_WRITE+SCORE_VERIFY+MANAGE_MEMBERSHIPS+MANAGE_SETTINGS,"
          + PROG + ":PROGRAMMER:WOD_WRITE+SCORE_VERIFY+MANAGE_MEMBERSHIPS+MANAGE_SETTINGS";
  private static final String COACH_MEMBERSHIPS = BOX + ":COACH:SCORE_VERIFY";
  private static final String ATHLETE_MEMBERSHIPS = BOX + ":ATHLETE:";
  private static final ZoneId PARIS = ZoneId.of("Europe/Paris");

  @Autowired private WodRepository wodRepository;
  @Autowired private MovementRepository movementRepository;
  @Autowired private ScoreRepository scoreRepository;
  @Autowired private SessionRepository sessionRepository;
  @Autowired private SessionLogRepository sessionLogRepository;
  @Autowired private GymSessionSettingsRepository settingsRepository;

  private Movement backSquat;
  private Wod fran;
  private Wod open;
  private LocalDate today;

  @BeforeEach
  void setUp() {
    cleanUp();
    today = LocalDate.now(PARIS);
    when(antaresClient.isProgrammingGym(anyLong())).thenReturn(false);
    when(antaresClient.isProgrammingGym(PROG)).thenReturn(true);
    when(antaresClient.isGymMember(anyLong(), anyLong())).thenReturn(true);
    backSquat =
        movementRepository.save(
            Movement.builder().name("Back Squat").category(Category.SQUAT).build());
    fran =
        wodRepository.save(
            Wod.builder()
                .title("Fran")
                .wodType(WodType.FOR_TIME)
                .scoreType(ScoreType.TIME)
                .isPublic(true)
                .build());
    open =
        wodRepository.save(
            Wod.builder()
                .title("26.1")
                .wodType(WodType.AMRAP)
                .scoreType(ScoreType.REPS)
                .isPublic(true)
                .build());
  }

  @AfterEach
  void cleanUp() {
    scoreRepository.deleteAll();
    sessionLogRepository.deleteAll();
    sessionRepository.deleteAll();
    settingsRepository.deleteAll();
    wodRepository.deleteAll();
    movementRepository.deleteAll();
  }

  // -------------------------------------------------------------------------
  // HELPERS
  // -------------------------------------------------------------------------

  private static MockHttpServletRequestBuilder as(
      MockHttpServletRequestBuilder request, long userId, String memberships) {
    return request
        .header("X-Auth-User-Id", String.valueOf(userId))
        .header("X-Auth-User-Role", "USER")
        .header("X-Auth-Memberships", memberships)
        .header("X-Internal-Secret", "test-internal-secret")
        .contentType(MediaType.APPLICATION_JSON);
  }

  private WodRequest squatWod() {
    return new WodRequest(
        "Back Squat 5x5", WodType.STRENGTH, null, ScoreType.WEIGHT, null, null, false, false,
        null, null, null, null, null, null, null, null, null, null, "5x5",
        List.of(
            new WodMovementRequest(
                backSquat.getId(), 1, "5", null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, 5)));
  }

  private static SessionBlockRequest warmUp() {
    return new SessionBlockRequest(
        null, Kind.WARM_UP, null, null, "Mobilité hanches", false, null, 10,
        "Hip mobility", "Mobilité hanches", false, null, null);
  }

  private SessionBlockRequest skill() {
    return new SessionBlockRequest(
        null, Kind.SKILL, "A", "Heavy squat", "Squat lourd", false, null, 20, null, null, false,
        null, squatWod());
  }

  private SessionBlockRequest borrowed(Long wodId, boolean competition) {
    return new SessionBlockRequest(
        null, Kind.WOD, "B", "Benchmark", "Benchmark", false, null, 15, null, null, competition,
        wodId, null);
  }

  private SessionRequest wodSession(
      Long gymId, VisibilityPolicy policy, LocalDateTime at, List<SessionBlockRequest> blocks) {
    return new SessionRequest(
        SessionType.WOD, Discipline.CROSSFIT, today, "Tuesday", "Coach notes", "Notes du coach",
        gymId, policy, at, blocks);
  }

  private JsonNode create(SessionRequest request, long userId, String memberships)
      throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                as(post("/aldebaran/sessions"), userId, memberships)
                    .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andReturn();
    return objectMapper.readTree(result.getResponse().getContentAsString());
  }

  private JsonNode createBoxSession(VisibilityPolicy policy) throws Exception {
    return create(
        wodSession(BOX, policy, null, List.of(warmUp(), skill(), borrowed(fran.getId(), false))),
        OWNER,
        OWNER_MEMBERSHIPS);
  }

  private String json(Object value) throws Exception {
    return objectMapper.writeValueAsString(value);
  }

  // -------------------------------------------------------------------------
  // TESTS
  // -------------------------------------------------------------------------

  @Test
  @DisplayName("Create: blocks in order, inline WOD hidden from lists, borrowed WOD, stimulus")
  void createAndRead() throws Exception {
    JsonNode session = createBoxSession(VisibilityPolicy.AFTER_PARTICIPANTS);
    long id = session.get("id").asLong();
    long squatId = session.at("/blocks/1/wod/id").asLong();

    assertEquals(3, session.get("blocks").size());
    assertEquals("WARM_UP", session.at("/blocks/0/kind").asString());
    assertTrue(session.at("/blocks/0/wod").isNull());
    assertEquals("STRENGTH", session.at("/blocks/1/stimulus").asString());
    assertFalse(session.at("/blocks/1/borrowed").asBoolean());
    assertFalse(session.at("/blocks/1/wod/standalone").asBoolean());
    assertEquals(5, session.at("/blocks/1/wod/movements/0/sets").asInt());
    assertTrue(session.at("/blocks/2/borrowed").asBoolean());
    assertEquals("MIXED", session.at("/blocks/2/stimulus").asString());
    assertEquals(
        today.plusDays(1).atStartOfDay(PARIS).toInstant().toString(),
        session.get("contentVisibleFrom").asString());
    assertTrue(session.at("/capabilities/canModify").asBoolean());

    // The session WOD never shows in the WOD list
    mockMvc
        .perform(as(get("/aldebaran/wods"), OWNER, OWNER_MEMBERSHIPS))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content", hasSize(2)));

    // Athlete: frame (themes) in the list, content hidden until participation or the next day
    mockMvc
        .perform(
            as(get("/aldebaran/sessions"), ATHLETE, ATHLETE_MEMBERSHIPS)
                .param("from", today.toString())
                .param("to", today.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content", hasSize(1)))
        .andExpect(jsonPath("$.content[0].contentVisible").value(false))
        .andExpect(jsonPath("$.content[0].blocks[1].themeFr").value("Squat lourd"));
    mockMvc
        .perform(as(get("/aldebaran/sessions/" + id), ATHLETE, ATHLETE_MEMBERSHIPS))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.blocks[1].wod").value(nullValue()))
        .andExpect(jsonPath("$.blocks[1].label").value(nullValue()))
        .andExpect(jsonPath("$.descriptionFr").value(nullValue()))
        .andExpect(jsonPath("$.capabilities.contentVisible").value(false))
        .andExpect(jsonPath("$.capabilities.canLog").value(true))
        .andExpect(jsonPath("$.capabilities.canModify").value(false));
    mockMvc
        .perform(as(get("/aldebaran/wods/" + squatId), ATHLETE, ATHLETE_MEMBERSHIPS))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(as(get("/aldebaran/scores/leaderboard/" + squatId), ATHLETE, ATHLETE_MEMBERSHIPS))
        .andExpect(status().isForbidden());

    // A coach without WOD_WRITE reads everything but cannot edit
    mockMvc
        .perform(as(get("/aldebaran/sessions/" + id), COACH, COACH_MEMBERSHIPS))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.blocks[1].wod.title").value("Back Squat 5x5"))
        .andExpect(jsonPath("$.capabilities.contentVisible").value(true))
        .andExpect(jsonPath("$.capabilities.canModify").value(false));
    mockMvc
        .perform(
            as(put("/aldebaran/sessions/" + id), COACH, COACH_MEMBERSHIPS)
                .content(json(wodSession(BOX, null, null, List.of(skill(), borrowed(fran.getId(), false))))))
        .andExpect(status().isForbidden());

    // Outsiders see nothing; a session WOD cannot be edited through /wods
    mockMvc
        .perform(as(get("/aldebaran/sessions/" + id), OUTSIDER, ""))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(
            as(put("/aldebaran/wods/" + squatId), OWNER, OWNER_MEMBERSHIPS)
                .content(json(squatWod())))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("Composition: a WOD session needs Skill + WOD; text blocks carry no WOD")
  void composition() throws Exception {
    mockMvc
        .perform(
            as(post("/aldebaran/sessions"), OWNER, OWNER_MEMBERSHIPS)
                .content(json(wodSession(BOX, null, null, List.of(warmUp(), borrowed(fran.getId(), false))))))
        .andExpect(status().isBadRequest());
    SessionBlockRequest textWithWod =
        new SessionBlockRequest(
            null, Kind.COOL_DOWN, null, null, null, false, null, null, "Stretch", null, false,
            fran.getId(), null);
    mockMvc
        .perform(
            as(post("/aldebaran/sessions"), OWNER, OWNER_MEMBERSHIPS)
                .content(
                    json(
                        wodSession(
                            BOX, null, null,
                            List.of(skill(), borrowed(fran.getId(), false), textWithWod)))))
        .andExpect(status().isBadRequest());
    // An athlete cannot create a box session
    mockMvc
        .perform(
            as(post("/aldebaran/sessions"), ATHLETE, ATHLETE_MEMBERSHIPS)
                .content(json(wodSession(BOX, null, null, List.of(skill(), borrowed(fran.getId(), false))))))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("Journal: Séance faite reveals the content, scores with sets join it, lock")
  void journalScoresAndLock() throws Exception {
    JsonNode session = createBoxSession(VisibilityPolicy.AFTER_PARTICIPANTS);
    long id = session.get("id").asLong();
    long squatId = session.at("/blocks/1/wod/id").asLong();
    long warmUpId = session.at("/blocks/0/id").asLong();

    MvcResult logged =
        mockMvc
            .perform(as(post("/aldebaran/sessions/" + id + "/logs"), ATHLETE, ATHLETE_MEMBERSHIPS))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.blocks", hasSize(3)))
            .andExpect(jsonPath("$.blocks[0].done").value(true))
            .andExpect(jsonPath("$.gymId").value(BOX))
            .andReturn();
    long logId = objectMapper.readTree(logged.getResponse().getContentAsString()).get("id").asLong();

    mockMvc
        .perform(as(get("/aldebaran/sessions/" + id), ATHLETE, ATHLETE_MEMBERSHIPS))
        .andExpect(jsonPath("$.capabilities.contentVisible").value(true))
        .andExpect(jsonPath("$.capabilities.canLog").value(false))
        .andExpect(jsonPath("$.blocks[1].wod.title").value("Back Squat 5x5"))
        .andExpect(jsonPath("$.myLog.id").value(logId));

    // Strength score set by set: the best complete set is the performance
    ScoreRequest squatScore =
        new ScoreRequest(
            null, today, squatId, null, null, null, null, null, null, Unit.KG, null, null, null,
            ScalingLevel.RX, false, null, null, null, null, null,
            List.of(
                new ScoreSetRequest(5, 100.0, null),
                new ScoreSetRequest(5, 110.0, true),
                new ScoreSetRequest(3, 120.0, false)),
            false);
    mockMvc
        .perform(as(post("/aldebaran/scores"), ATHLETE, ATHLETE_MEMBERSHIPS).content(json(squatScore)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.maxWeight").value(110.0))
        .andExpect(jsonPath("$.sets", hasSize(3)))
        .andExpect(jsonPath("$.sets[2].completed").value(false))
        .andExpect(jsonPath("$.sessionLogId").value(logId))
        .andExpect(jsonPath("$.personalRecord").value(true));

    // Borrowed Fran: linked to the journal, performed in the box
    ScoreRequest franScore =
        new ScoreRequest(
            null, today, fran.getId(), 4, 30, null, null, null, null, null, null, null, null,
            ScalingLevel.RX, false, null, null, null, null, logId, null, false);
    mockMvc
        .perform(as(post("/aldebaran/scores"), ATHLETE, ATHLETE_MEMBERSHIPS).content(json(franScore)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.gymId").value(BOX))
        .andExpect(jsonPath("$.sessionLogId").value(logId));

    // Another athlete cannot use this journal
    mockMvc
        .perform(
            as(post("/aldebaran/scores"), OTHER_ATHLETE, ATHLETE_MEMBERSHIPS).content(json(franScore)))
        .andExpect(status().isBadRequest());

    // Journal: blocks unchecked, deletion refused while scores are linked
    mockMvc
        .perform(
            as(put("/aldebaran/session-logs/" + logId + "/blocks/" + warmUpId), ATHLETE, ATHLETE_MEMBERSHIPS)
                .content("{\"done\":false}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.blocks[0].done").value(false))
        .andExpect(jsonPath("$.scores", hasSize(2)));
    mockMvc
        .perform(
            as(put("/aldebaran/session-logs/" + logId + "/blocks/" + warmUpId), OTHER_ATHLETE, ATHLETE_MEMBERSHIPS)
                .content("{\"done\":true}"))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(as(delete("/aldebaran/session-logs/" + logId), ATHLETE, ATHLETE_MEMBERSHIPS))
        .andExpect(status().isConflict());
    mockMvc
        .perform(as(get("/aldebaran/session-logs/me"), ATHLETE, ATHLETE_MEMBERSHIPS))
        .andExpect(jsonPath("$.content", hasSize(1)))
        .andExpect(jsonPath("$.content[0].scores", hasSize(2)));

    // Locked: content frozen, visibility still editable
    mockMvc
        .perform(
            as(put("/aldebaran/sessions/" + id), OWNER, OWNER_MEMBERSHIPS)
                .content(json(wodSession(BOX, null, null, List.of(skill(), borrowed(fran.getId(), false))))))
        .andExpect(status().isConflict());
    mockMvc
        .perform(as(delete("/aldebaran/sessions/" + id), OWNER, OWNER_MEMBERSHIPS))
        .andExpect(status().isConflict());
    mockMvc
        .perform(
            as(put("/aldebaran/sessions/" + id + "/visibility"), OWNER, OWNER_MEMBERSHIPS)
                .content(json(new SessionVisibilityRequest(VisibilityPolicy.PARTICIPANTS_ONLY, null))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.visibilityPolicy").value("PARTICIPANTS_ONLY"))
        .andExpect(jsonPath("$.contentVisibleFrom").value(nullValue()))
        .andExpect(jsonPath("$.capabilities.locked").value(true));

    // Borrowed Fran cannot be deleted while a block uses it (even by an admin)
    mockMvc
        .perform(
            delete("/aldebaran/wods/" + fran.getId())
                .header("X-Auth-User-Id", "9")
                .header("X-Auth-User-Role", "ADMIN")
                .header("X-Internal-Secret", "test-internal-secret"))
        .andExpect(status().isConflict());
  }

  @Test
  @DisplayName("Visibility: policies give the opening instant; a program uses ADVANCE / DAY_OF")
  void visibilityPolicies() throws Exception {
    JsonNode dayOf =
        create(
            wodSession(
                BOX, VisibilityPolicy.DAY_OF, today.atTime(18, 0),
                List.of(skill(), borrowed(fran.getId(), false))),
            OWNER,
            OWNER_MEMBERSHIPS);
    assertEquals(
        today.atTime(18, 0).atZone(PARIS).toInstant().toString(),
        dayOf.get("contentVisibleFrom").asString());

    JsonNode advance =
        create(
            wodSession(
                BOX, VisibilityPolicy.ADVANCE, today.minusDays(2).atTime(20, 0),
                List.of(skill(), borrowed(fran.getId(), false))),
            OWNER,
            OWNER_MEMBERSHIPS);
    // Already open: the athlete reads the content without logging
    mockMvc
        .perform(
            as(get("/aldebaran/sessions/" + advance.get("id").asLong()), ATHLETE, ATHLETE_MEMBERSHIPS))
        .andExpect(jsonPath("$.capabilities.contentVisible").value(true))
        .andExpect(jsonPath("$.blocks[0].wod.title").value("Back Squat 5x5"));

    // ADVANCE after the session's start, DAY_OF on another day: refused
    mockMvc
        .perform(
            as(post("/aldebaran/sessions"), OWNER, OWNER_MEMBERSHIPS)
                .content(
                    json(
                        wodSession(
                            BOX, VisibilityPolicy.ADVANCE, today.atTime(6, 0),
                            List.of(skill(), borrowed(fran.getId(), false))))))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(
            as(post("/aldebaran/sessions"), OWNER, OWNER_MEMBERSHIPS)
                .content(
                    json(
                        wodSession(
                            BOX, VisibilityPolicy.DAY_OF, today.plusDays(1).atTime(6, 0),
                            List.of(skill(), borrowed(fran.getId(), false))))))
        .andExpect(status().isBadRequest());

    // Participants only: a logged athlete reads it, never the whole box
    JsonNode participants = createBoxSession(VisibilityPolicy.PARTICIPANTS_ONLY);
    assertTrue(participants.get("contentVisibleFrom").isNull());

    // Program: AFTER refused, default ADVANCE
    mockMvc
        .perform(
            as(post("/aldebaran/sessions"), OWNER, OWNER_MEMBERSHIPS)
                .content(
                    json(
                        wodSession(
                            PROG, VisibilityPolicy.AFTER, null,
                            List.of(skill(), borrowed(fran.getId(), false))))))
        .andExpect(status().isBadRequest());
    JsonNode prog =
        create(
            wodSession(PROG, null, null, List.of(skill(), borrowed(fran.getId(), false))),
            OWNER,
            OWNER_MEMBERSHIPS);
    assertEquals("ADVANCE", prog.get("visibilityPolicy").asString());
  }

  @Test
  @DisplayName("Competition block: Open copied for the box, athletes submit, judges verify")
  void competitionBlock() throws Exception {
    JsonNode session =
        create(
            wodSession(
                BOX, VisibilityPolicy.ADVANCE, today.minusDays(1).atTime(12, 0),
                List.of(skill(), borrowed(open.getId(), true))),
            OWNER,
            OWNER_MEMBERSHIPS);
    long openCopyId = session.at("/blocks/1/wod/id").asLong();
    assertTrue(session.at("/blocks/1/competition").asBoolean());
    assertFalse(session.at("/blocks/1/borrowed").asBoolean());
    assertTrue(openCopyId != open.getId());

    ScoreRequest submitted =
        new ScoreRequest(
            null, today, openCopyId, null, null, null, 180, null, null, null, null, null, null,
            ScalingLevel.RX, false, null, null, null, null, null, null, true);
    ScoreRequest notSubmitted =
        new ScoreRequest(
            null, today, openCopyId, null, null, null, 150, null, null, null, null, null, null,
            ScalingLevel.RX, false, null, null, null, null, null, null, false);
    MvcResult s1 =
        mockMvc
            .perform(as(post("/aldebaran/scores"), ATHLETE, ATHLETE_MEMBERSHIPS).content(json(submitted)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.verificationRequested").value(true))
            .andReturn();
    MvcResult s2 =
        mockMvc
            .perform(
                as(post("/aldebaran/scores"), OTHER_ATHLETE, ATHLETE_MEMBERSHIPS)
                    .content(json(notSubmitted)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.verificationRequested").value(false))
            .andReturn();
    long submittedId = objectMapper.readTree(s1.getResponse().getContentAsString()).get("id").asLong();
    long plainId = objectMapper.readTree(s2.getResponse().getContentAsString()).get("id").asLong();

    mockMvc
        .perform(as(get("/aldebaran/scores/verification-queue/" + openCopyId), COACH, COACH_MEMBERSHIPS))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(1)))
        .andExpect(jsonPath("$[0].id").value(submittedId))
        .andExpect(jsonPath("$[0].capabilities.canVerify").value(true));
    mockMvc
        .perform(
            as(get("/aldebaran/scores/verification-queue/" + openCopyId), ATHLETE, ATHLETE_MEMBERSHIPS))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(as(post("/aldebaran/scores/" + submittedId + "/verify"), COACH, COACH_MEMBERSHIPS))
        .andExpect(status().isOk());
    mockMvc
        .perform(as(post("/aldebaran/scores/" + plainId + "/verify"), COACH, COACH_MEMBERSHIPS))
        .andExpect(status().isForbidden());

    // A competition WOD cannot be borrowed as is; a program has no competition block
    mockMvc
        .perform(
            as(post("/aldebaran/sessions"), OWNER, OWNER_MEMBERSHIPS)
                .content(json(wodSession(BOX, null, null, List.of(skill(), borrowed(openCopyId, false))))))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(
            as(post("/aldebaran/sessions"), OWNER, OWNER_MEMBERSHIPS)
                .content(json(wodSession(PROG, null, null, List.of(skill(), borrowed(open.getId(), true))))))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("Update merges blocks; delete removes the session WODs, borrowed WODs stay")
  void updateAndDelete() throws Exception {
    JsonNode session = createBoxSession(VisibilityPolicy.AFTER);
    long id = session.get("id").asLong();
    long squatId = session.at("/blocks/1/wod/id").asLong();
    long skillBlockId = session.at("/blocks/1/id").asLong();

    // Reorder: skill first (same block and WOD, updated in place), warm-up dropped
    List<SessionBlockRequest> blocks = new ArrayList<>();
    blocks.add(
        new SessionBlockRequest(
            skillBlockId, Kind.SKILL, "A", null, null, false, null, null, null, null, false, null,
            squatWod()));
    blocks.add(borrowed(fran.getId(), false));
    mockMvc
        .perform(
            as(put("/aldebaran/sessions/" + id), OWNER, OWNER_MEMBERSHIPS)
                .content(json(wodSession(BOX, VisibilityPolicy.AFTER, null, blocks))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.blocks", hasSize(2)))
        .andExpect(jsonPath("$.blocks[0].id").value(skillBlockId))
        .andExpect(jsonPath("$.blocks[0].wod.id").value(squatId));

    mockMvc
        .perform(as(delete("/aldebaran/sessions/" + id), OWNER, OWNER_MEMBERSHIPS))
        .andExpect(status().isNoContent());
    assertTrue(wodRepository.findById(squatId).isEmpty());
    assertTrue(wodRepository.findById(fran.getId()).isPresent());
  }

  @Test
  @DisplayName("Personal session: author only, week of a specialty from its Monday")
  void personalSession() throws Exception {
    SessionRequest request =
        new SessionRequest(
            SessionType.SPECIALTY, Discipline.STRENGTH, today, "My squat day", null, null, null,
            VisibilityPolicy.AFTER, null, List.of(skill()));
    JsonNode session = create(request, ATHLETE, ATHLETE_MEMBERSHIPS);
    LocalDate monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));

    assertEquals(monday.toString(), session.get("date").asString());
    assertEquals(monday.plusDays(6).toString(), session.get("endDate").asString());
    assertTrue(session.get("visibilityPolicy").isNull());
    assertTrue(session.at("/capabilities/canModify").asBoolean());

    mockMvc
        .perform(
            as(get("/aldebaran/sessions/" + session.get("id").asLong()), OTHER_ATHLETE, ATHLETE_MEMBERSHIPS))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(as(get("/aldebaran/sessions").param("scope", "MINE"), ATHLETE, ATHLETE_MEMBERSHIPS))
        .andExpect(jsonPath("$.content", hasSize(1)));
  }

  @Test
  @DisplayName("Settings: members read, MANAGE_SETTINGS writes, a program keeps its policies")
  void settings() throws Exception {
    mockMvc
        .perform(as(get("/aldebaran/gyms/" + BOX + "/session-settings"), ATHLETE, ATHLETE_MEMBERSHIPS))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.defaultVisibility").value("AFTER_PARTICIPANTS"))
        .andExpect(jsonPath("$.timeZone").value("Europe/Paris"));
    mockMvc
        .perform(
            as(put("/aldebaran/gyms/" + BOX + "/session-settings"), ATHLETE, ATHLETE_MEMBERSHIPS)
                .content(json(new GymSessionSettingsDto(VisibilityPolicy.ADVANCE, "Europe/Paris"))))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(
            as(put("/aldebaran/gyms/" + BOX + "/session-settings"), OWNER, OWNER_MEMBERSHIPS)
                .content(
                    json(new GymSessionSettingsDto(VisibilityPolicy.PARTICIPANTS_ONLY, "America/Montreal"))))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            as(put("/aldebaran/gyms/" + PROG + "/session-settings"), OWNER, OWNER_MEMBERSHIPS)
                .content(json(new GymSessionSettingsDto(VisibilityPolicy.AFTER, "Europe/Paris"))))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(
            as(put("/aldebaran/gyms/" + BOX + "/session-settings"), OWNER, OWNER_MEMBERSHIPS)
                .content(json(new GymSessionSettingsDto(VisibilityPolicy.AFTER, "Mars/Olympus"))))
        .andExpect(status().isBadRequest());

    // New sessions take the default
    JsonNode session =
        create(
            wodSession(BOX, null, null, List.of(skill(), borrowed(fran.getId(), false))),
            OWNER,
            OWNER_MEMBERSHIPS);
    assertEquals("PARTICIPANTS_ONLY", session.get("visibilityPolicy").asString());
  }
}
