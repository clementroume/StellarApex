package apex.stellar.aldebaran.demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import apex.stellar.aldebaran.model.entities.Movement;
import apex.stellar.aldebaran.model.entities.Wod;
import apex.stellar.aldebaran.model.enums.Category;
import apex.stellar.aldebaran.repository.MovementRepository;
import apex.stellar.aldebaran.repository.WodRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

/** Unit tests for {@link DevGymWodSeeder}. */
@ExtendWith(MockitoExtension.class)
class DevGymWodSeederTest {

  private static final Map<String, Long> IDS =
      Map.of(
          DevGymWodSeeder.SPARTA_GYM, 10L,
          DevGymWodSeeder.ZEUS_GYM, 20L,
          DevGymWodSeeder.SPARTA_OWNER, 100L,
          DevGymWodSeeder.ZEUS_PROGRAMMER, 200L,
          DevGymWodSeeder.SPARTA_ATHLETE, 300L);
  private static final LocalDate TODAY = LocalDate.of(2026, 11, 19);
  private static final DemoPrograms.Program SPARTA =
      new DemoPrograms.Program("SPARTA", "Spartacus CrossFit", "owner", "Sparta · ", -6, null);
  private static final DemoPrograms.Program ZEUS =
      new DemoPrograms.Program("ZEUS", "Zeus Programming", "programmer", "Zeus · ", -6, null);

  @Mock private JdbcTemplate jdbcTemplate;
  @Mock private WodRepository wodRepository;
  @Mock private MovementRepository movementRepository;
  @Mock private DevScoreSeeder devScoreSeeder;
  @Mock private DemoPrograms demoPrograms;
  @Mock private DemoProgramScores demoProgramScores;
  @Mock private PlatformTransactionManager transactionManager;

  private DevGymWodSeeder seeder;

  @BeforeEach
  void setUp() {
    Clock clock = Clock.fixed(Instant.parse("2026-11-19T10:00:00Z"), ZoneOffset.UTC);
    seeder =
        new DevGymWodSeeder(
            jdbcTemplate,
            wodRepository,
            movementRepository,
            devScoreSeeder,
            demoPrograms,
            demoProgramScores,
            transactionManager,
            clock,
            3,
            Duration.ZERO);
    AtomicLong id = new AtomicLong();
    List<Movement> catalog =
        Stream.of("Running", "Air Squat")
            .map(n -> Movement.builder().id(id.incrementAndGet()).name(n).category(Category.OTHER_LIFTS).build())
            .toList();
    lenient().when(movementRepository.findAll()).thenReturn(catalog);
    lenient().when(movementRepository.count()).thenReturn((long) catalog.size());
    lenient().when(demoPrograms.programs()).thenReturn(List.of(SPARTA, ZEUS));
  }

  /** Antares rows exist: names and e-mails resolve; any other lookup finds nothing. */
  private void antaresReady() {
    when(jdbcTemplate.queryForList(anyString(), eq(Long.class), any(Object[].class)))
        .thenAnswer(
            inv -> {
              Object key = inv.getArgument(2);
              Long found = key instanceof String s ? IDS.get(s) : null;
              return found == null ? List.of() : List.of(found);
            });
  }

  @Test
  @DisplayName("Writes the personal WOD, both programs, then every score")
  void shouldSeedProgramsAndScores() {
    antaresReady();

    assertTrue(seeder.seedWhenReady());

    ArgumentCaptor<Wod> personal = ArgumentCaptor.forClass(Wod.class);
    verify(wodRepository).save(personal.capture());
    assertEquals(DevGymWodSeeder.PERSONAL_WOD, personal.getValue().getTitle());
    assertNull(personal.getValue().getGymId());
    assertEquals(300L, personal.getValue().getAuthorId());
    assertFalse(personal.getValue().isPublic());
    assertEquals(2, personal.getValue().getMovements().size());

    // Each program in its gym, written by its holder; scores after the sessions
    verify(demoPrograms).seed(SPARTA, 10L, 100L, TODAY);
    verify(demoPrograms).seed(ZEUS, 20L, 200L, TODAY);
    verify(devScoreSeeder).seed(10L, 20L);
    verify(demoProgramScores).seed(eq(SPARTA), eq(10L), any(), eq(TODAY));
    verify(demoProgramScores).seed(eq(ZEUS), eq(20L), any(), eq(TODAY));
  }

  @Test
  @DisplayName("Leaves a program already in its gym; scores keep their own check")
  void shouldSkipSeededPrograms() {
    antaresReady();
    when(demoPrograms.seeded(SPARTA, 10L)).thenReturn(true);
    when(demoPrograms.seeded(ZEUS, 20L)).thenReturn(true);

    assertTrue(seeder.seedWhenReady());

    verify(demoPrograms, never()).seed(any(), any(), any(), any());
    verify(devScoreSeeder).seed(10L, 20L);
    verify(demoProgramScores, times(2)).seed(any(), any(), any(), any());
  }

  @Test
  @DisplayName("Retries while Antares data is missing, then gives up")
  void shouldGiveUpWhenAntaresNotSeeded() {
    when(jdbcTemplate.queryForList(anyString(), eq(Long.class), any(Object[].class)))
        .thenReturn(List.of());

    assertFalse(seeder.seedWhenReady());

    verify(demoPrograms, never()).seed(any(), any(), any(), any());
    verify(jdbcTemplate, times(3 * 5))
        .queryForList(anyString(), eq(Long.class), any(Object[].class));
  }

  @Test
  @DisplayName("Retries after a database error (Antares schema not created yet)")
  void shouldRetryAfterDatabaseError() {
    when(jdbcTemplate.queryForList(anyString(), eq(Long.class), any(Object[].class)))
        .thenThrow(new DataAccessResourceFailureException("relation antares.gyms does not exist"))
        .thenAnswer(
            inv -> {
              Object key = inv.getArgument(2);
              Long found = key instanceof String s ? IDS.get(s) : null;
              return found == null ? List.of() : List.of(found);
            });

    assertTrue(seeder.seedWhenReady());

    verify(demoPrograms).seed(SPARTA, 10L, 100L, TODAY);
  }
}
