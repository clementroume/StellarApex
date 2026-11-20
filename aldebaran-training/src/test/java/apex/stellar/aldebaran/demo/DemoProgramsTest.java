package apex.stellar.aldebaran.demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import apex.stellar.aldebaran.dto.WodRequest;
import apex.stellar.aldebaran.repository.MovementRepository;
import apex.stellar.aldebaran.repository.SessionRepository;
import apex.stellar.aldebaran.repository.WodRepository;
import apex.stellar.aldebaran.service.SessionService;
import apex.stellar.aldebaran.service.WodService;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Unit tests for {@link DemoPrograms} and its resource. */
class DemoProgramsTest {

  private static final LocalDate TODAY = LocalDate.of(2026, 11, 19); // a Thursday

  private DemoPrograms demoPrograms;
  private List<DemoPrograms.Program> programs;

  @BeforeEach
  void setUp() {
    demoPrograms =
        new DemoPrograms(
            JsonMapper.builder().build(),
            mock(SessionRepository.class),
            mock(MovementRepository.class),
            mock(WodRepository.class),
            mock(WodService.class),
            mock(SessionService.class),
            mock(DemoActor.class));
    programs = demoPrograms.programs();
  }

  @Test
  @DisplayName("Holds the two programs: 12 weeks each, 6 days a week")
  void shouldHoldBothPrograms() {
    assertEquals(List.of("SPARTA", "ZEUS"), programs.stream().map(DemoPrograms.Program::key).toList());
    assertEquals(72, programs.get(0).node().get("sessions").size());
    assertEquals(72, programs.get(1).node().get("sessions").size());
    // Spartacus copies its benchmarks with its Scaled version; a program never copies
    assertTrue(programs.get(0).node().get("copies").size() > 0);
    assertEquals(0, programs.get(1).node().get("copies").size());
  }

  @Test
  @DisplayName("Centers the cycle on the seed week: 6 weeks before, 5 after")
  void shouldCenterOnTheSeedWeek() {
    DemoPrograms.Program sparta = programs.getFirst();
    assertEquals(LocalDate.of(2026, 10, 5), DemoPrograms.firstMonday(sparta, TODAY));
    List<LocalDate> dates = new ArrayList<>();
    for (JsonNode s : sparta.node().get("sessions")) {
      dates.add(DemoPrograms.dateOf(sparta, s, TODAY));
    }
    assertEquals(LocalDate.of(2026, 10, 5), dates.getFirst());
    assertEquals(LocalDate.of(2026, 12, 26), dates.getLast()); // Saturday of week 12
    assertTrue(dates.stream().noneMatch(d -> d.getDayOfWeek() == DayOfWeek.SUNDAY));
  }

  @Test
  @DisplayName("Every session has a Skill and a WOD; Spartacus has an official Scaled version on every WOD")
  void shouldComposeSessions() {
    for (DemoPrograms.Program p : programs) {
      for (JsonNode s : p.node().get("sessions")) {
        Set<String> kinds = new HashSet<>();
        s.get("blocks").forEach(b -> kinds.add(b.get("kind").asString()));
        assertTrue(kinds.containsAll(Set.of("SKILL", "WOD")), s.get("title").asString());
        assertTrue(s.get("title").asString().startsWith(p.titlePrefix()));
      }
    }
    for (JsonNode s : programs.getFirst().node().get("sessions")) {
      for (JsonNode b : s.get("blocks")) {
        if ("WOD".equals(b.get("kind").asString()) && b.has("wod")) {
          boolean scaled = false;
          for (JsonNode line : b.get("wod").get("movements")) {
            scaled |= line.has("scaled");
          }
          assertTrue(scaled, b.get("wod").get("title").asString());
        }
      }
    }
  }

  @Test
  @DisplayName("Every WOD of the resource reads as a request once its names are resolved")
  void shouldMapEveryWod() {
    Map<String, Long> ids = new HashMap<>();
    List<JsonNode> specs = new ArrayList<>();
    for (DemoPrograms.Program p : programs) {
      p.node().get("copies").forEach(c -> specs.add(c.get("wod")));
      for (JsonNode s : p.node().get("sessions")) {
        for (JsonNode b : s.get("blocks")) {
          if (b.has("wod")) {
            specs.add(b.get("wod"));
          }
          if (b.has("fallback")) {
            specs.add(b.get("fallback"));
          }
        }
      }
    }
    for (JsonNode spec : specs) {
      for (JsonNode line : spec.get("movements")) {
        ids.putIfAbsent(line.get("movement").asString(), (long) ids.size() + 1);
        if (line.has("scaled")) {
          ids.putIfAbsent(line.get("scaled").get("movement").asString(), (long) ids.size() + 1);
        }
      }
    }
    for (JsonNode spec : specs) {
      WodRequest request = demoPrograms.wodRequest(spec, 10L, false, ids);
      assertNotNull(request.wodType(), request.title());
      assertNotNull(request.scoreType(), request.title());
      assertFalse(request.movements().isEmpty(), request.title());
      assertEquals(10L, request.gymId());
      request.movements().forEach(m -> assertNotNull(m.movementId(), request.title()));
    }
    assertTrue(specs.size() > 250);
  }
}
