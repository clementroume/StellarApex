package apex.stellar.aldebaran.demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import apex.stellar.aldebaran.dto.ScoreRequest;
import apex.stellar.aldebaran.model.entities.Score.ScalingLevel;
import apex.stellar.aldebaran.model.entities.Wod;
import apex.stellar.aldebaran.model.entities.Wod.ScoreType;
import apex.stellar.aldebaran.repository.ScoreRepository;
import apex.stellar.aldebaran.repository.WodRepository;
import apex.stellar.aldebaran.service.ScoreService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

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
  @Mock private ScoreService scoreService;
  @Mock private DemoActor actor;
  @Mock private PlatformTransactionManager transactionManager;

  private DevScoreSeeder seeder;
  private Long currentUser;
  /** Each request with the account it was logged as. */
  private final List<Map.Entry<Long, ScoreRequest>> logged = new ArrayList<>();

  @BeforeEach
  void setUp() {
    seeder =
        new DevScoreSeeder(
            jdbcTemplate,
            wodRepository,
            scoreRepository,
            scoreService,
            actor,
            transactionManager,
            Clock.fixed(TODAY.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC));
    lenient()
        .when(jdbcTemplate.queryForList(anyString(), eq(Long.class), any(Object[].class)))
        .thenAnswer(
            inv -> {
              Long id = USERS.get((String) inv.getArgument(2));
              return id == null ? List.of() : List.of(id);
            });
    // The account runs the call: the service sees it as the current user
    lenient()
        .doAnswer(
            inv -> {
              currentUser = inv.getArgument(0);
              ((Runnable) inv.getArgument(1)).run();
              return null;
            })
        .when(actor)
        .run(anyLong(), any(Runnable.class));
    lenient()
        .when(scoreService.logScore(any()))
        .thenAnswer(
            call -> {
              logged.add(Map.entry(currentUser, call.getArgument(0)));
              return null;
            });
  }

  private static Wod wod(AtomicLong ids, String title, ScoreType type, boolean isPublic) {
    return Wod.builder().id(ids.incrementAndGet()).title(title).scoreType(type).isPublic(isPublic).build();
  }

  private void wodsPresent() {
    AtomicLong ids = new AtomicLong();
    when(wodRepository.findAll())
        .thenReturn(
            List.of(
                wod(ids, DevGymWodSeeder.PERSONAL_WOD, ScoreType.REPS, false),
                wod(ids, "Fran", ScoreType.TIME, true),
                // A box copy shares the benchmark's title: never scored here
                Wod.builder().id(99L).title("Fran").scoreType(ScoreType.TIME).gymId(SPARTA).build(),
                wod(ids, "Chelsea", ScoreType.ROUNDS, true),
                wod(ids, "Cindy", ScoreType.REPS, true),
                wod(ids, "Grace", ScoreType.TIME, true),
                wod(ids, "Murph", ScoreType.TIME, true),
                wod(ids, "Open 25.1", ScoreType.REPS, true),
                wod(ids, "Open 25.2", ScoreType.TIME, true),
                wod(ids, "CrossFit Total", ScoreType.LOAD, true),
                wod(ids, "Back Squat 1RM", ScoreType.WEIGHT, true),
                wod(ids, "Deadlift 1RM", ScoreType.WEIGHT, true)));
  }

  @Test
  @DisplayName("Logs each score through the service as its athlete, on public and personal WODs")
  void shouldLogScoresAsTheirAthletes() {
    wodsPresent();

    seeder.seed(SPARTA, ZEUS);

    assertTrue(logged.size() > 40, "a rich data set is expected");
    assertTrue(logged.stream().allMatch(e -> e.getValue().userId() == null), "always the athlete itself");
    assertTrue(logged.stream().noneMatch(e -> e.getValue().date().isAfter(TODAY)));
    assertTrue(logged.stream().noneMatch(e -> e.getValue().wodId() == 99L), "never the box copy");
    // A public WOD records the athlete's gym (or none); the personal WOD none
    assertTrue(
        logged.stream()
            .anyMatch(e -> e.getKey() == 104L && e.getValue().wodId() == 2L && Objects.equals(e.getValue().gymId(), ZEUS)));
    assertTrue(logged.stream().filter(e -> e.getValue().wodId() == 1L).allMatch(e -> e.getValue().gymId() == null));
    // Times are split in minutes and seconds; a capped score gives its reps and tiebreak
    ScoreRequest fran =
        logged.stream()
            .filter(e -> e.getKey().equals(OWNER_ID) && e.getValue().wodId() == 2L)
            .findFirst()
            .orElseThrow()
            .getValue();
    assertEquals(3, fran.timeMinutes());
    assertTrue(
        logged.stream()
            .map(Map.Entry::getValue)
            .filter(ScoreRequest::timeCapped)
            .allMatch(r -> r.reps() != null && r.timeMinutes() == null));
    assertTrue(logged.stream().anyMatch(e -> e.getValue().scaling() == ScalingLevel.SCALED));
  }

  @Test
  @DisplayName("Logs the lift tests so records exist")
  void shouldLogLiftTests() {
    wodsPresent();

    seeder.seed(SPARTA, ZEUS);

    assertTrue(
        logged.stream()
            .anyMatch(e -> e.getKey().equals(OWNER_ID) && e.getValue().wodId() == 10L && e.getValue().maxWeight() != null));
    assertTrue(logged.stream().anyMatch(e -> e.getValue().wodId() == 11L));
  }

  @Test
  @DisplayName("Skips missing WODs and accounts instead of failing")
  void shouldSkipMissingWods() {
    AtomicLong ids = new AtomicLong();
    when(wodRepository.findAll()).thenReturn(List.of(wod(ids, "Fran", ScoreType.TIME, true)));

    seeder.seed(SPARTA, ZEUS);

    assertFalse(logged.isEmpty());
    assertTrue(logged.stream().allMatch(e -> e.getValue().wodId() == 1L));
    assertNull(logged.stream().filter(e -> e.getKey() == 400L).findFirst().orElseThrow().getValue().gymId());
  }

  @Test
  @DisplayName("Skips what is already logged: benchmarks, lift tests and the personal WOD have their own marker")
  void shouldSkipWhenAlreadySeeded() {
    wodsPresent();
    when(scoreRepository.existsByUserIdAndWodId(anyLong(), anyLong())).thenReturn(true);

    seeder.seed(SPARTA, ZEUS);

    verify(scoreService, never()).logScore(any());
  }

  @Test
  @DisplayName("Logs the personal WOD's scores even without a public catalog")
  void shouldLogPersonalScoresWithoutCatalog() {
    AtomicLong ids = new AtomicLong();
    when(wodRepository.findAll())
        .thenReturn(List.of(wod(ids, DevGymWodSeeder.PERSONAL_WOD, ScoreType.REPS, false)));

    seeder.seed(SPARTA, ZEUS);

    assertEquals(2, logged.size());
    assertTrue(logged.stream().allMatch(e -> e.getKey().equals(ATHLETE_ID) && e.getValue().gymId() == null));
  }

  @Test
  @DisplayName("A score the service refuses is skipped, the others are logged")
  void shouldGoOnAfterARefusedScore() {
    AtomicLong ids = new AtomicLong();
    when(wodRepository.findAll()).thenReturn(List.of(wod(ids, "Fran", ScoreType.TIME, true)));
    doAnswer(
            inv -> {
              currentUser = inv.getArgument(0);
              if (currentUser.equals(OWNER_ID)) {
                throw new IllegalStateException("refused");
              }
              ((Runnable) inv.getArgument(1)).run();
              return null;
            })
        .when(actor)
        .run(anyLong(), any(Runnable.class));

    seeder.seed(SPARTA, ZEUS);

    assertTrue(logged.size() > 3);
  }
}
