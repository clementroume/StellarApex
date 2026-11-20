package apex.stellar.aldebaran.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import apex.stellar.aldebaran.config.BaseIntegrationTest;
import apex.stellar.aldebaran.model.entities.Movement;
import apex.stellar.aldebaran.model.entities.MovementMuscle;
import apex.stellar.aldebaran.model.entities.MovementMuscle.MuscleRole;
import apex.stellar.aldebaran.model.entities.Muscle;
import apex.stellar.aldebaran.model.entities.Muscle.MuscleRegion;
import apex.stellar.aldebaran.model.entities.PerSet;
import apex.stellar.aldebaran.model.entities.Session;
import apex.stellar.aldebaran.model.entities.Session.Discipline;
import apex.stellar.aldebaran.model.entities.Session.SessionType;
import apex.stellar.aldebaran.model.entities.SessionBlock;
import apex.stellar.aldebaran.model.entities.SessionBlock.Kind;
import apex.stellar.aldebaran.model.entities.SessionLog;
import apex.stellar.aldebaran.model.entities.Wod;
import apex.stellar.aldebaran.model.entities.Wod.ScoreType;
import apex.stellar.aldebaran.model.entities.Wod.WodStructure;
import apex.stellar.aldebaran.model.entities.Wod.WodType;
import apex.stellar.aldebaran.model.entities.WodMovement;
import apex.stellar.aldebaran.model.enums.Category;
import apex.stellar.aldebaran.model.enums.MovementPattern;
import apex.stellar.aldebaran.repository.MovementRepository;
import apex.stellar.aldebaran.repository.MuscleRepository;
import apex.stellar.aldebaran.repository.SessionLogRepository;
import apex.stellar.aldebaran.repository.SessionRepository;
import apex.stellar.aldebaran.repository.WodRepository;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Integration tests of the session-order proposal (lot D', D168-D171): writers only, a proposal
 * that writes nothing, an application that moves parts or whole sessions within a week and refuses
 * a locked session or a foreign block.
 */
class SessionOrderIT extends BaseIntegrationTest {

  private static final long GYM = 311L;
  private static final String BASE = "/aldebaran/programming/gyms/" + GYM;
  private static final String WRITER = GYM + ":COACH:WOD_WRITE";

  @Autowired private MuscleRepository muscleRepository;
  @Autowired private MovementRepository movementRepository;
  @Autowired private WodRepository wodRepository;
  @Autowired private SessionRepository sessionRepository;
  @Autowired private SessionLogRepository sessionLogRepository;

  private LocalDate start;
  private Movement squat;
  private Movement press;
  /** Sessions of the first week, Monday to Friday. */
  private final Map<DayOfWeek, Session> firstWeek = new LinkedHashMap<>();

  @BeforeEach
  void setUp() {
    Muscle quads = muscleRepository.save(Muscle.builder().medicalName("Order Quads").region(MuscleRegion.THIGH_LEG).build());
    Muscle delts = muscleRepository.save(Muscle.builder().medicalName("Order Delts").region(MuscleRegion.SHOULDER).build());
    squat = movement("Order Squat", quads, MovementPattern.SQUAT);
    press = movement("Order Press", delts, MovementPattern.PUSH_V);
    start = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).plusWeeks(2);
    firstWeek.clear();
    // 4 weeks, Monday to Friday: legs early in the week, shoulders late
    for (int w = 0; w < 4; w++) {
      for (int d = 0; d < 5; d++) {
        LocalDate date = start.plusWeeks(w).plusDays(d);
        Session s = session(date, d < 2 ? squat : press, "W" + w + "D" + d);
        if (w == 0) {
          firstWeek.put(date.getDayOfWeek(), s);
        }
      }
    }
  }

  private Movement movement(String name, Muscle muscle, MovementPattern pattern) {
    Movement m =
        Movement.builder()
            .name(name)
            .category(Category.LUNGES)
            .perSet(new PerSet(10, null, 60, null))
            .equipment(new HashSet<>())
            .techniques(new HashSet<>())
            .patterns(EnumSet.of(pattern))
            .build();
    m.getTargetedMuscles().add(MovementMuscle.builder().movement(m).muscle(muscle).role(MuscleRole.AGONIST).build());
    return movementRepository.save(m);
  }

  private Wod wod(Movement movement, String title, WodType type, ScoreType score) {
    Wod w =
        Wod.builder()
            .title(title)
            .wodType(type)
            .structure(type == WodType.FOR_LOAD ? WodStructure.SETS : WodStructure.SINGLE)
            .scoreType(score)
            .gymId(GYM)
            .standalone(false)
            .movements(new ArrayList<>())
            .build();
    w.getMovements().add(WodMovement.builder().wod(w).movement(movement).orderIndex(1).repsScheme("50").build());
    return wodRepository.save(w);
  }

  /** A session with a strength Skill (always squats) and a WOD of the given movement. */
  private Session session(LocalDate date, Movement wodMovement, String title) {
    Session s =
        Session.builder()
            .type(SessionType.WOD)
            .discipline(Discipline.CROSSFIT)
            .date(date)
            .endDate(date)
            .gymId(GYM)
            .authorId(1L)
            .title(title)
            .build();
    s.getBlocks().add(
        SessionBlock.builder().session(s).orderIndex(0).kind(Kind.SKILL)
            .wod(wod(squat, title + " skill", WodType.FOR_LOAD, ScoreType.WEIGHT)).build());
    s.getBlocks().add(
        SessionBlock.builder().session(s).orderIndex(1).kind(Kind.WOD)
            .wod(wod(wodMovement, title + " wod", WodType.FOR_TIME, ScoreType.TIME)).build());
    return sessionRepository.save(s);
  }

  private static MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request, String memberships) {
    return request
        .header("X-Auth-User-Id", "7")
        .header("X-Auth-User-Role", "USER")
        .header("X-Auth-Memberships", memberships)
        .header("X-Internal-Secret", "test-internal-secret")
        .contentType(MediaType.APPLICATION_JSON);
  }

  private Long block(Session s, Kind kind) {
    return s.getBlocks().stream().filter(b -> b.getKind() == kind).findFirst().orElseThrow().getId();
  }

  /** The first week as it is, with the WOD parts of two days swapped. */
  private String swapWods(DayOfWeek a, DayOfWeek b, String mode) {
    List<Map<String, Object>> days = new ArrayList<>();
    firstWeek.forEach(
        (day, s) -> {
          DayOfWeek wodFrom = day == a ? b : day == b ? a : day;
          days.add(
              Map.of(
                  "sessionId", s.getId(),
                  "skillBlockIds", List.of(block(s, Kind.SKILL)),
                  "wodBlockIds", List.of(block(firstWeek.get(wodFrom), Kind.WOD))));
        });
    return objectMapper.writeValueAsString(Map.of("mode", mode, "weeks", List.of(Map.of("monday", start, "days", days))));
  }

  private String proposal(String mode) {
    return objectMapper.writeValueAsString(
        Map.of("start", start, "weeks", 4, "scaled", false, "optional", false, "mode", mode, "heavy", "STRICT"));
  }

  @Test
  @DisplayName("A writer gets a proposal of the whole cycle; a coach without WOD_WRITE does not")
  void proposal() throws Exception {
    mockMvc
        .perform(as(post(BASE + "/order-proposals"), GYM + ":COACH:").content(proposal("WOD_ONLY")))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(as(post(BASE + "/order-proposals"), WRITER).content(proposal("WOD_ONLY")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.weeks", hasSize(4)))
        .andExpect(jsonPath("$.weeks[0].days", hasSize(5)))
        .andExpect(jsonPath("$.weeks[0].days[0].skill.strength").value(true))
        .andExpect(jsonPath("$.weeks[0].days[0].wod.title").value("W0D0 wod"))
        .andExpect(jsonPath("$.before.moves").value(0));
    // Nothing written by a proposal
    assertThat(sessionRepository.findProgramming(GYM, start, start.plusDays(6)))
        .allSatisfy(s -> assertThat(s.getBlocks()).hasSize(2));
  }

  @Test
  @DisplayName("WOD only: the WOD blocks change session, the Skills stay")
  void applyWods() throws Exception {
    Long mondayWod = block(firstWeek.get(DayOfWeek.MONDAY), Kind.WOD);
    Long wednesdayWod = block(firstWeek.get(DayOfWeek.WEDNESDAY), Kind.WOD);

    mockMvc
        .perform(as(post(BASE + "/order"), WRITER).content(swapWods(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, "WOD_ONLY")))
        .andExpect(status().isNoContent());

    Map<LocalDate, Session> after = new LinkedHashMap<>();
    sessionRepository.findProgramming(GYM, start, start.plusDays(6)).forEach(s -> after.put(s.getDate(), s));
    Session monday = after.get(start);
    Session wednesday = after.get(start.plusDays(2));
    assertThat(block(monday, Kind.WOD)).isEqualTo(wednesdayWod);
    assertThat(block(wednesday, Kind.WOD)).isEqualTo(mondayWod);
    assertThat(block(monday, Kind.SKILL)).isEqualTo(block(firstWeek.get(DayOfWeek.MONDAY), Kind.SKILL));
    assertThat(monday.getBlocks()).extracting(SessionBlock::getOrderIndex).containsExactly(0, 1);
  }

  @Test
  @DisplayName("Whole sessions: two sessions swap their days")
  void applySessions() throws Exception {
    Session monday = firstWeek.get(DayOfWeek.MONDAY);
    Session wednesday = firstWeek.get(DayOfWeek.WEDNESDAY);
    // Whole sessions: Monday lists Wednesday's Skill and WOD, and the reverse
    List<Map<String, Object>> days = new ArrayList<>();
    firstWeek.forEach(
        (day, s) -> {
          Session from = day == DayOfWeek.MONDAY ? wednesday : day == DayOfWeek.WEDNESDAY ? monday : s;
          days.add(
              Map.of(
                  "sessionId", s.getId(),
                  "skillBlockIds", List.of(block(from, Kind.SKILL)),
                  "wodBlockIds", List.of(block(from, Kind.WOD))));
        });
    String body =
        objectMapper.writeValueAsString(Map.of("mode", "WHOLE_SESSIONS", "weeks", List.of(Map.of("monday", start, "days", days))));

    mockMvc.perform(as(post(BASE + "/order"), WRITER).content(body)).andExpect(status().isNoContent());

    assertThat(sessionRepository.findById(monday.getId()).orElseThrow().getDate()).isEqualTo(start.plusDays(2));
    assertThat(sessionRepository.findById(wednesday.getId()).orElseThrow().getDate()).isEqualTo(start);
  }

  @Test
  @DisplayName("A session logged since the proposal is locked (409); a foreign block is refused (400)")
  void refusals() throws Exception {
    Session tuesday = firstWeek.get(DayOfWeek.TUESDAY);
    sessionLogRepository.save(
        SessionLog.builder().userId(8L).session(tuesday).gymId(GYM).date(tuesday.getDate()).loggedBy(8L).build());

    mockMvc
        .perform(as(post(BASE + "/order"), WRITER).content(swapWods(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, "WOD_ONLY")))
        .andExpect(status().isConflict());

    sessionLogRepository.deleteAll();
    String foreign =
        swapWods(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, "WOD_ONLY")
            .replace(
                String.valueOf(block(firstWeek.get(DayOfWeek.WEDNESDAY), Kind.WOD)),
                String.valueOf(block(sessionRepository.findProgramming(GYM, start.plusWeeks(1), start.plusWeeks(1)).getFirst(), Kind.WOD)));
    mockMvc
        .perform(as(post(BASE + "/order"), WRITER).content(foreign))
        .andExpect(status().isBadRequest());

    // WOD only never moves a Skill
    Long mondaySkill = block(firstWeek.get(DayOfWeek.MONDAY), Kind.SKILL);
    Long wednesdaySkill = block(firstWeek.get(DayOfWeek.WEDNESDAY), Kind.SKILL);
    String skills =
        swapWods(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, "WOD_ONLY")
            .replace("[" + mondaySkill + "]", "[X]")
            .replace("[" + wednesdaySkill + "]", "[" + mondaySkill + "]")
            .replace("[X]", "[" + wednesdaySkill + "]");
    mockMvc
        .perform(as(post(BASE + "/order"), WRITER).content(skills))
        .andExpect(status().isBadRequest());
  }
}
