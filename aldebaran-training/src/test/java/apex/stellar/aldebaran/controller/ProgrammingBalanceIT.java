package apex.stellar.aldebaran.controller;

import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import apex.stellar.aldebaran.model.entities.Wod;
import apex.stellar.aldebaran.model.entities.Wod.ScoreType;
import apex.stellar.aldebaran.model.entities.Wod.WodStructure;
import apex.stellar.aldebaran.model.entities.Wod.WodType;
import apex.stellar.aldebaran.model.entities.WodMovement;
import apex.stellar.aldebaran.model.entities.WodScaledLine;
import apex.stellar.aldebaran.model.enums.Category;
import apex.stellar.aldebaran.model.enums.MovementPattern;
import apex.stellar.aldebaran.repository.MovementRepository;
import apex.stellar.aldebaran.repository.MuscleRepository;
import apex.stellar.aldebaran.repository.SessionRepository;
import apex.stellar.aldebaran.repository.WodRepository;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Integration tests of the programming balance (lot D'): staff only, CrossFit sessions of the
 * cycle, profiles, alerts, Scaled and optional switches.
 */
class ProgrammingBalanceIT extends BaseIntegrationTest {

  private static final long GYM = 301L;
  private static final long OTHER_GYM = 302L;
  private static final String URL = "/aldebaran/programming/gyms/" + GYM + "/balance";

  @Autowired private MuscleRepository muscleRepository;
  @Autowired private MovementRepository movementRepository;
  @Autowired private WodRepository wodRepository;
  @Autowired private SessionRepository sessionRepository;

  private LocalDate start;
  private Movement squat;
  private Movement press;

  @BeforeEach
  void setUp() {
    Muscle quads =
        muscleRepository.save(Muscle.builder().medicalName("Prog Quads").region(MuscleRegion.THIGH_LEG).build());
    Muscle delts =
        muscleRepository.save(Muscle.builder().medicalName("Prog Delts").region(MuscleRegion.SHOULDER).build());
    squat = movement("Prog Squat", quads, MovementPattern.SQUAT);
    press = movement("Prog Press", delts, MovementPattern.PUSH_V);
    start = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).plusWeeks(1);
    // 4 weeks: legs on Monday and Tuesday, shoulders on Wednesday and Thursday
    for (int w = 0; w < 4; w++) {
      LocalDate monday = start.plusWeeks(w);
      session(monday, Discipline.CROSSFIT, wod(squat, "Mon " + w), false);
      session(monday.plusDays(1), Discipline.CROSSFIT, wod(squat, "Tue " + w, press), false);
      session(monday.plusDays(2), Discipline.CROSSFIT, wod(press, "Wed " + w), false);
      session(monday.plusDays(3), Discipline.CROSSFIT, wod(press, "Thu " + w), false);
    }
    // A weightlifting class: a specialty, never in the profiles
    session(start.plusDays(4), Discipline.WEIGHTLIFTING, wod(press, "Oly"), false);
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

  private Wod wod(Movement movement, String title) {
    return wod(movement, title, null);
  }

  /** A WOD of one line; {@code scaledAs}: its official Scaled version replaces the movement. */
  private Wod wod(Movement movement, String title, Movement scaledAs) {
    Wod w =
        Wod.builder()
            .title(title)
            .wodType(WodType.FOR_TIME)
            .structure(WodStructure.SINGLE)
            .scoreType(ScoreType.TIME)
            .gymId(GYM)
            .standalone(false)
            .movements(new ArrayList<>())
            .build();
    WodMovement line = WodMovement.builder().wod(w).movement(movement).orderIndex(1).repsScheme("100").build();
    if (scaledAs != null) {
      line.setScaled(WodScaledLine.builder().wodMovement(line).movement(scaledAs).build());
    }
    w.getMovements().add(line);
    return wodRepository.save(w);
  }

  private Session session(LocalDate date, Discipline discipline, Wod wod, boolean optional) {
    Session s =
        Session.builder()
            .type(SessionType.WOD)
            .discipline(discipline)
            .date(date)
            .endDate(date)
            .gymId(GYM)
            .authorId(1L)
            .build();
    s.getBlocks().add(SessionBlock.builder().session(s).orderIndex(0).kind(Kind.WOD).wod(wod).optional(optional).build());
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

  @Test
  @DisplayName("A coach reads the full week, the profiles and the alerts; only a writer may reorder")
  void coachReads() throws Exception {
    mockMvc
        .perform(as(get(URL).param("start", start.plusDays(2).toString()), GYM + ":COACH:"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.start").value(start.toString()))
        .andExpect(jsonPath("$.end").value(start.plusWeeks(4).minusDays(1).toString()))
        .andExpect(jsonPath("$.trainingDays", hasSize(4)))
        .andExpect(jsonPath("$.sessions").value(16))
        .andExpect(jsonPath("$.windows").value(1))
        .andExpect(jsonPath("$.uncountedSessions").value(1))
        .andExpect(jsonPath("$.programmedWeeks").value(4))
        // The Tuesday WODs have an official Scaled version (set up)
        .andExpect(jsonPath("$.scaledLines").value(4))
        .andExpect(jsonPath("$.fullWeek.inAlert").value(true))
        // This test catalog has none of the 7 main groups: their minimum, 3 alerts at most
        .andExpect(jsonPath("$.fullWeek.alerts", hasSize(3)))
        .andExpect(jsonPath("$.fullWeek.alerts[0].kind").value("GROUP_MINIMUM"))
        .andExpect(jsonPath("$.fullWeek.alerts[0].limit").value(4.0))
        .andExpect(jsonPath("$.fullWeek.regions[?(@.region == 'SHOULDER')].share").value(hasItem(closeTo(50.0, 1e-9))))
        // The radar: squats on Monday and Tuesday, presses on Wednesday and Thursday
        .andExpect(jsonPath("$.fullWeek.axes", hasSize(7)))
        .andExpect(jsonPath("$.fullWeek.axes[?(@.axis == 'SQUAT')].share").value(hasItem(closeTo(50.0, 1e-9))))
        .andExpect(jsonPath("$.fullWeek.axes[?(@.axis == 'PUSH')].judged").value(hasItem(false)))
        .andExpect(jsonPath("$.fullWeek.details[?(@.pattern == 'PUSH_V')].share").value(hasItem(closeTo(50.0, 1e-9))))
        .andExpect(jsonPath("$.concentratedAxes", hasSize(0)))
        .andExpect(jsonPath("$.unusualSessions", hasSize(0)))
        // 4 training days: 4 profiles of 3 days; 4 and 5 days are the full week (no profile)
        .andExpect(jsonPath("$.summaries", hasSize(3)))
        .andExpect(jsonPath("$.summaries[0].size").value(3))
        .andExpect(jsonPath("$.summaries[0].total").value(4))
        .andExpect(jsonPath("$.summaries[1].total").value(0))
        .andExpect(jsonPath("$.summaries[2].total").value(0))
        .andExpect(jsonPath("$.profile").isEmpty())
        .andExpect(jsonPath("$.capabilities.canReorder").value(false));

    mockMvc
        .perform(as(get(URL).param("start", start.toString()), GYM + ":COACH:WOD_WRITE"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.capabilities.canReorder").value(true));
  }

  @Test
  @DisplayName("A profile is detailed on request")
  void profile() throws Exception {
    mockMvc
        .perform(
            as(get(URL).param("start", start.toString()).param("days", "MONDAY", "WEDNESDAY", "THURSDAY"), GYM + ":OWNER:"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.profile.days", hasSize(3)))
        .andExpect(jsonPath("$.profile.regions[?(@.region == 'SHOULDER')].share").value(hasItem(closeTo(66.67, 0.01))))
        .andExpect(jsonPath("$.profile.regions[?(@.region == 'SHOULDER')].trend").value(hasItem("AHEAD")))
        .andExpect(jsonPath("$.profile.regions[?(@.region == 'THIGH_LEG')].trend").value(hasItem("BEHIND")))
        // 3 days out of 4 always hold a squat day: both axes are regular, hence judged; no gap
        .andExpect(jsonPath("$.profile.axes[?(@.axis == 'SQUAT')].share").value(hasItem(closeTo(33.33, 0.01))))
        .andExpect(jsonPath("$.profile.axes[?(@.axis == 'SQUAT')].fullShare").value(hasItem(closeTo(50.0, 1e-9))))
        .andExpect(jsonPath("$.profile.axes[?(@.axis == 'SQUAT')].judged").value(hasItem(true)))
        .andExpect(jsonPath("$.profile.alerts[?(@.kind == 'PATTERN_GAP')]", hasSize(0)))
        .andExpect(jsonPath("$.profile.windowStart").value(start.toString()));

    // A day without session is left out: the profile stays the 3-day one
    mockMvc
        .perform(
            as(get(URL).param("start", start.toString()).param("days", "MONDAY", "WEDNESDAY", "THURSDAY", "SUNDAY"),
                GYM + ":OWNER:"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.profile.days", hasSize(3)))
        .andExpect(jsonPath("$.profile.axes[?(@.axis == 'SQUAT')].judged").value(hasItem(true)));
    mockMvc
        .perform(as(get(URL).param("start", start.toString()).param("days", "SUNDAY"), GYM + ":OWNER:"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.profile").isEmpty());
  }

  @Test
  @DisplayName("Optional blocks count only when asked; Scaled reads the official Scaled version")
  void switches() throws Exception {
    // An optional press block each Friday (a day with nothing else); the Tuesday WODs have a
    // Scaled version made of presses (set up)
    for (int w = 0; w < 4; w++) {
      session(start.plusWeeks(w).plusDays(4), Discipline.CROSSFIT, wod(press, "Fri opt " + w), true);
    }
    mockMvc
        .perform(as(get(URL).param("start", start.toString()), GYM + ":COACH:"))
        .andExpect(jsonPath("$.trainingDays", hasSize(4)))
        .andExpect(jsonPath("$.fullWeek.regions[?(@.region == 'SHOULDER')].share").value(hasItem(closeTo(50.0, 1e-9))));
    mockMvc
        .perform(as(get(URL).param("start", start.toString()).param("optional", "true"), GYM + ":COACH:"))
        .andExpect(jsonPath("$.trainingDays", hasSize(5)))
        .andExpect(jsonPath("$.fullWeek.regions[?(@.region == 'SHOULDER')].share").value(hasItem(closeTo(60.0, 1e-9))));
    mockMvc
        .perform(as(get(URL).param("start", start.toString()).param("scaled", "true"), GYM + ":COACH:"))
        .andExpect(jsonPath("$.scaled").value(true))
        .andExpect(jsonPath("$.fullWeek.regions[?(@.region == 'SHOULDER')].share").value(hasItem(closeTo(75.0, 1e-9))));
  }

  @Test
  @DisplayName("Athletes, other gyms' staff and a cycle out of bounds are refused")
  void refused() throws Exception {
    mockMvc
        .perform(as(get(URL).param("start", start.toString()), GYM + ":ATHLETE:"))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(as(get(URL).param("start", start.toString()), OTHER_GYM + ":OWNER:"))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(as(get(URL).param("start", start.toString()).param("weeks", "3"), GYM + ":COACH:"))
        .andExpect(status().isBadRequest());
    mockMvc
        .perform(as(get(URL).param("start", start.toString()).param("weeks", "9"), GYM + ":COACH:"))
        .andExpect(status().isBadRequest());
  }
}
