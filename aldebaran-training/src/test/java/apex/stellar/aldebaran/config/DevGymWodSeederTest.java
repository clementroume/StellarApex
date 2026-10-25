package apex.stellar.aldebaran.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import apex.stellar.aldebaran.model.entities.WodMovement;
import apex.stellar.aldebaran.model.enums.Category;
import apex.stellar.aldebaran.repository.MovementRepository;
import apex.stellar.aldebaran.repository.WodRepository;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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

  @Mock private JdbcTemplate jdbcTemplate;
  @Mock private WodRepository wodRepository;
  @Mock private MovementRepository movementRepository;
  @Mock private PlatformTransactionManager transactionManager;

  private DevGymWodSeeder seeder;

  @BeforeEach
  void setUp() {
    seeder =
        new DevGymWodSeeder(
            jdbcTemplate,
            wodRepository,
            movementRepository,
            transactionManager,
            3,
            Duration.ZERO);
    AtomicLong id = new AtomicLong();
    List<Movement> catalog =
        Stream.of(
                "Wall-ball Shot", "Box Jump", "American Kettlebell Swing", "Power Clean",
                "Burpee", "Double Under", "Air Squat", "Sit-up", "Push-up", "Pull-up",
                "Back Squat", "Deadlift", "Rowing", "Toes-to-Bar", "Thruster", "Chest To Bar",
                "Front Squat", "Handstand Push-up", "Running")
            .map(
                n ->
                    Movement.builder()
                        .id(id.incrementAndGet())
                        .name(n)
                        .category(Category.OTHER_LIFTS)
                        .build())
            .toList();
    lenient().when(movementRepository.findAll()).thenReturn(catalog);
    lenient().when(movementRepository.count()).thenReturn((long) catalog.size());
  }

  private void antaresReady() {
    when(jdbcTemplate.queryForList(anyString(), eq(Long.class), any(Object[].class)))
        .thenAnswer(
            inv -> {
              Long found = IDS.get((String) inv.getArgument(2));
              return found == null ? List.of() : List.of(found);
            });
  }

  @Test
  @DisplayName("Seeds varied private WODs for both gyms and one personal WOD")
  @SuppressWarnings("unchecked")
  void shouldSeedGymWods() {
    antaresReady();

    assertTrue(seeder.seedWhenReady());

    ArgumentCaptor<List<Wod>> captor = ArgumentCaptor.forClass(List.class);
    verify(wodRepository).saveAll(captor.capture());
    List<Wod> wods = captor.getValue();

    List<Wod> sparta = wods.stream().filter(w -> Objects.equals(w.getGymId(), 10L)).toList();
    List<Wod> zeus = wods.stream().filter(w -> Objects.equals(w.getGymId(), 20L)).toList();
    List<Wod> personal = wods.stream().filter(w -> w.getGymId() == null).toList();
    assertEquals(4, sparta.size());
    assertEquals(4, zeus.size());
    assertEquals(1, personal.size());
    assertTrue(sparta.stream().allMatch(w -> w.getAuthorId() == 100L));
    assertTrue(zeus.stream().allMatch(w -> w.getAuthorId() == 200L));
    assertEquals(300L, personal.getFirst().getAuthorId());
    assertTrue(wods.stream().noneMatch(Wod::isPublic));

    // Varied formats so the UI can be checked
    assertEquals(
        4,
        wods.stream().map(Wod::getWodType).distinct().count(),
        "AMRAP, EMOM, FOR_TIME and STRENGTH expected");
    // Every movement resolved against the catalog, with a 1-based order
    for (Wod wod : wods) {
      assertFalse(wod.getMovements().isEmpty(), wod.getTitle());
      for (int i = 0; i < wod.getMovements().size(); i++) {
        WodMovement wm = wod.getMovements().get(i);
        assertEquals(i + 1, wm.getOrderIndex());
        assertTrue(wm.getMovement().getId() != null, wm.getMovement().getName());
      }
    }
  }

  @Test
  @DisplayName("Skips when the Sparta gym already has WODs")
  void shouldSkipWhenAlreadySeeded() {
    antaresReady();
    when(wodRepository.existsByGymId(10L)).thenReturn(true);

    assertTrue(seeder.seedWhenReady());

    verify(wodRepository, never()).saveAll(any());
  }

  @Test
  @DisplayName("Retries while Antares data is missing, then gives up")
  void shouldGiveUpWhenAntaresNotSeeded() {
    when(jdbcTemplate.queryForList(anyString(), eq(Long.class), any(Object[].class)))
        .thenReturn(List.of());

    assertFalse(seeder.seedWhenReady());

    verify(wodRepository, never()).saveAll(any());
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
              Long found = IDS.get((String) inv.getArgument(2));
              return found == null ? List.of() : List.of(found);
            });

    assertTrue(seeder.seedWhenReady());

    verify(wodRepository).saveAll(any());
  }
}
