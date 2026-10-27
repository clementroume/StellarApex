package apex.stellar.aldebaran.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import apex.stellar.aldebaran.model.entities.Score;
import apex.stellar.aldebaran.model.entities.Score.ScalingLevel;
import apex.stellar.aldebaran.model.entities.Wod;
import apex.stellar.aldebaran.model.entities.Wod.ScoreType;
import apex.stellar.aldebaran.repository.ScoreRepository;
import apex.stellar.aldebaran.repository.WodRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

/** Unit tests for {@link DevScoreSeeder}. */
@ExtendWith(MockitoExtension.class)
class DevScoreSeederTest {

  private static final Long SPARTA = 10L;
  private static final Long ZEUS = 20L;
  private static final Long OWNER_ID = 100L;
  private static final Long ATHLETE_ID = 300L;
  private static final Map<String, Long> USERS =
      Map.of(
          DevScoreSeeder.OWNER, OWNER_ID,
          DevScoreSeeder.COACH, 101L,
          DevScoreSeeder.SCORER, 102L,
          DevScoreSeeder.ATHLETE, ATHLETE_ID,
          DevScoreSeeder.HYBRID, 103L,
          DevScoreSeeder.MULTI, 104L,
          DevScoreSeeder.PROGRAMMER, 200L,
          DevScoreSeeder.ACTIVE, 201L,
          DevScoreSeeder.FREE, 400L);
  private static final LocalDate TODAY = LocalDate.of(2026, 10, 27);

  @Mock private JdbcTemplate jdbcTemplate;
  @Mock private WodRepository wodRepository;
  @Mock private ScoreRepository scoreRepository;

  private DevScoreSeeder seeder;

  @BeforeEach
  void setUp() {
    Clock clock = Clock.fixed(Instant.parse("2026-10-27T10:00:00Z"), ZoneOffset.UTC);
    seeder = new DevScoreSeeder(jdbcTemplate, wodRepository, scoreRepository, clock);
    when(jdbcTemplate.queryForList(
            eq("SELECT id FROM antares.users WHERE email = ?"), eq(Long.class), anyString()))
        .thenAnswer(
            inv -> {
              Long id = USERS.get((String) inv.getArgument(2));
              return id == null ? List.of() : List.of(id);
            });
  }

  private static Wod wod(AtomicLong ids, String title, ScoreType type, Long gymId) {
    return Wod.builder().id(ids.incrementAndGet()).title(title).scoreType(type).gymId(gymId).build();
  }

  private void wodsPresent() {
    AtomicLong ids = new AtomicLong();
    when(wodRepository.findAll())
        .thenReturn(
            List.of(
                wod(ids, "Sparta Burner", ScoreType.REPS, SPARTA),
                wod(ids, "Hoplite Chipper", ScoreType.TIME, SPARTA),
                wod(ids, "Sparta Back Squat 5x5", ScoreType.WEIGHT, SPARTA),
                wod(ids, "Olympus Deadlift", ScoreType.WEIGHT, ZEUS),
                wod(ids, "Titan AMRAP", ScoreType.REPS, ZEUS),
                wod(ids, "Pantheon 21-15-9", ScoreType.TIME, ZEUS),
                wod(ids, "Athlete Engine Builder", ScoreType.REPS, null),
                wod(ids, "Fran", ScoreType.TIME, null),
                wod(ids, "Chelsea", ScoreType.ROUNDS, null),
                wod(ids, "Cindy", ScoreType.REPS, null),
                wod(ids, "Grace", ScoreType.TIME, null),
                wod(ids, "Murph", ScoreType.TIME, null),
                wod(ids, "Open 25.1", ScoreType.REPS, null),
                wod(ids, "Open 25.2", ScoreType.TIME, null),
                wod(ids, "CrossFit Total", ScoreType.LOAD, null)));
  }

  @SuppressWarnings("unchecked")
  private List<Score> savedScores() {
    ArgumentCaptor<List<Score>> captor = ArgumentCaptor.forClass(List.class);
    verify(scoreRepository).saveAll(captor.capture());
    return captor.getValue();
  }

  @Test
  @DisplayName("Logs scores on gym and public WODs with one personal record per athlete and WOD")
  void shouldSeedScores() {
    wodsPresent();
    when(jdbcTemplate.queryForList(anyString(), eq(Long.class), eq(OWNER_ID))).thenReturn(List.of());

    seeder.seed(SPARTA, ZEUS);

    List<Score> scores = savedScores();
    assertTrue(scores.size() > 50, "a rich data set is expected");
    scores.stream()
        .collect(Collectors.groupingBy(s -> List.of(s.getWod().getId(), s.getUserId())))
        .forEach(
            (key, list) ->
                assertEquals(1, list.stream().filter(Score::isPersonalRecord).count(), key.toString()));
    assertTrue(scores.stream().noneMatch(s -> s.getDate().isAfter(TODAY)));

    // Gym WOD scores belong to the WOD's gym; public ones to the athlete's gym, or none
    for (Score s : scores) {
      if (s.getWod().getGymId() != null) {
        assertEquals(s.getWod().getGymId(), s.getGymId(), s.getWod().getTitle());
      }
    }
    assertTrue(
        scores.stream()
            .anyMatch(s -> s.getWod().getTitle().equals("Fran") && Objects.equals(s.getGymId(), ZEUS)));
    assertTrue(
        scores.stream()
            .anyMatch(
                s -> s.getWod().getTitle().equals("Fran") && s.getUserId() == 400L && s.getGymId() == null));

    // The athlete's later Fran in RX beats an earlier, faster scaled one
    Score athleteFranPr =
        scores.stream()
            .filter(
                s ->
                    s.getWod().getTitle().equals("Fran")
                        && s.getUserId().equals(ATHLETE_ID)
                        && s.isPersonalRecord())
            .findFirst()
            .orElseThrow();
    assertEquals(ScalingLevel.RX, athleteFranPr.getScaling());

    // Capped scores are stored as reps at the cap
    assertTrue(
        scores.stream()
            .filter(s -> Boolean.TRUE.equals(s.getTimeCapped()))
            .allMatch(s -> s.getTimeSeconds() == null && s.getReps() != null));
  }

  @Test
  @DisplayName("Skips missing WODs instead of failing")
  void shouldSkipMissingWods() {
    AtomicLong ids = new AtomicLong();
    when(wodRepository.findAll()).thenReturn(List.of(wod(ids, "Fran", ScoreType.TIME, null)));
    when(jdbcTemplate.queryForList(anyString(), eq(Long.class), eq(OWNER_ID))).thenReturn(List.of());

    seeder.seed(SPARTA, ZEUS);

    List<Score> scores = savedScores();
    assertFalse(scores.isEmpty());
    assertTrue(scores.stream().allMatch(s -> s.getWod().getTitle().equals("Fran")));
    assertNull(
        scores.stream()
            .filter(s -> s.getUserId() == 400L)
            .findFirst()
            .orElseThrow()
            .getGymId());
  }

  @Test
  @DisplayName("Skips when the owner already has scores")
  void shouldSkipWhenAlreadySeeded() {
    when(jdbcTemplate.queryForList(anyString(), eq(Long.class), eq(OWNER_ID))).thenReturn(List.of(1L));

    seeder.seed(SPARTA, ZEUS);

    verify(scoreRepository, never()).saveAll(any());
  }
}
