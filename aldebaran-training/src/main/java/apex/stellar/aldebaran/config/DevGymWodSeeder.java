package apex.stellar.aldebaran.config;

import apex.stellar.aldebaran.model.entities.Movement;
import apex.stellar.aldebaran.model.entities.Wod;
import apex.stellar.aldebaran.model.entities.Wod.ScoreType;
import apex.stellar.aldebaran.model.entities.Wod.WodType;
import apex.stellar.aldebaran.model.entities.WodMovement;
import apex.stellar.aldebaran.model.enums.Unit;
import apex.stellar.aldebaran.repository.MovementRepository;
import apex.stellar.aldebaran.repository.WodRepository;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Temporary development tool (profile {@code dev} only): creates gym WODs for the two fake test
 * gyms seeded by Antares, so the UI can be checked with real rights.
 *
 * <ul>
 *   <li>"Spartacus CrossFit" (physical gym), authored by {@code owner@sparta.com};
 *   <li>"Zeus Programming" (virtual gym), authored by {@code programmer@zeus.com};
 *   <li>one personal private WOD for {@code athlete@sparta.com}.
 * </ul>
 *
 * <p>Gym and user IDs are read with {@link JdbcTemplate} from the Antares schema of the same
 * PostgreSQL instance. Antares may seed after Aldebaran starts, so the lookup is retried in the
 * background (about 60 s by default) before giving up. Idempotent: nothing is done when the Sparta
 * gym already has WODs.
 */
@Component
@Profile("dev")
@Slf4j
public class DevGymWodSeeder implements ApplicationRunner {

  static final String SPARTA_GYM = "Spartacus CrossFit";
  static final String ZEUS_GYM = "Zeus Programming";
  static final String SPARTA_OWNER = "owner@sparta.com";
  static final String ZEUS_PROGRAMMER = "programmer@zeus.com";
  static final String SPARTA_ATHLETE = "athlete@sparta.com";

  private final JdbcTemplate jdbcTemplate;
  private final WodRepository wodRepository;
  private final MovementRepository movementRepository;
  private final TransactionTemplate transactionTemplate;
  private final int maxAttempts;
  private final Duration retryDelay;

  /**
   * Creates the seeder.
   *
   * @param jdbcTemplate Access to the Antares tables.
   * @param wodRepository WOD persistence.
   * @param movementRepository Movement catalog.
   * @param transactionManager Transaction manager used for the background seeding.
   * @param maxAttempts Number of lookups before giving up.
   * @param retryDelay Delay between two lookups.
   */
  public DevGymWodSeeder(
      JdbcTemplate jdbcTemplate,
      WodRepository wodRepository,
      MovementRepository movementRepository,
      PlatformTransactionManager transactionManager,
      @Value("${aldebaran.dev-seeder.max-attempts:12}") int maxAttempts,
      @Value("${aldebaran.dev-seeder.retry-delay:5s}") Duration retryDelay) {
    this.jdbcTemplate = jdbcTemplate;
    this.wodRepository = wodRepository;
    this.movementRepository = movementRepository;
    this.transactionTemplate = new TransactionTemplate(transactionManager);
    this.maxAttempts = maxAttempts;
    this.retryDelay = retryDelay;
  }

  @Override
  public void run(ApplicationArguments args) {
    Thread.ofVirtual().name("dev-gym-wod-seeder").start(this::seedWhenReady);
  }

  /**
   * Retries the lookup until the test gyms, users and movements exist, then seeds once.
   *
   * @return true if the seeding ran or was already done, false if it gave up.
   */
  boolean seedWhenReady() {
    for (int attempt = 1; attempt <= maxAttempts; attempt++) {
      try {
        Ids ids = lookupIds();
        if (ids != null && movementRepository.count() > 0) {
          transactionTemplate.executeWithoutResult(status -> seed(ids));
          return true;
        }
      } catch (DataAccessException e) {
        log.debug("Dev gym WOD seeding: Antares data not readable yet ({})", e.getMessage());
      }
      if (attempt < maxAttempts && !sleep()) {
        return false;
      }
    }
    log.warn(
        "Dev gym WOD seeding skipped: '{}', '{}' or their users not found after {} attempts",
        SPARTA_GYM,
        ZEUS_GYM,
        maxAttempts);
    return false;
  }

  private boolean sleep() {
    try {
      Thread.sleep(retryDelay);
      return true;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return false;
    }
  }

  private Ids lookupIds() {
    Long sparta = findId("SELECT id FROM antares.gyms WHERE name = ?", SPARTA_GYM);
    Long zeus = findId("SELECT id FROM antares.gyms WHERE name = ?", ZEUS_GYM);
    Long owner = findId("SELECT id FROM antares.users WHERE email = ?", SPARTA_OWNER);
    Long programmer = findId("SELECT id FROM antares.users WHERE email = ?", ZEUS_PROGRAMMER);
    Long athlete = findId("SELECT id FROM antares.users WHERE email = ?", SPARTA_ATHLETE);
    if (sparta == null || zeus == null || owner == null || programmer == null || athlete == null) {
      return null;
    }
    return new Ids(sparta, zeus, owner, programmer, athlete);
  }

  private Long findId(String sql, String key) {
    List<Long> ids = jdbcTemplate.queryForList(sql, Long.class, key);
    return ids.isEmpty() ? null : ids.getFirst();
  }

  private void seed(Ids ids) {
    if (wodRepository.existsByGymId(ids.sparta())) {
      log.info("Dev gym WOD seeding skipped: '{}' already has WODs", SPARTA_GYM);
      return;
    }
    Map<String, Movement> catalog = WodCsvCodec.byName(movementRepository.findAll());
    Long sparta = ids.sparta();
    Long zeus = ids.zeus();
    List<Wod> wods = new ArrayList<>();

    // --- Spartacus CrossFit (physical gym) ---
    wods.add(
        wod(
            catalog,
            base("Sparta Burner", WodType.AMRAP, ScoreType.REPS, sparta, ids.owner())
                .repScheme("AMRAP 12 min")
                .repsPerRound(30)
                .timeCapSeconds(720)
                .descriptionEn("As many rounds as possible in 12 minutes.")
                .descriptionFr("Le plus de tours possible en 12 minutes."),
            mv("Wall-ball Shot", "10").weight(9.0).weightFemale(6.0).weightUnit(Unit.KG),
            mv("Box Jump", "10").height(60.0).heightFemale(50.0).heightUnit(Unit.CENTIMETERS),
            mv("American Kettlebell Swing", "10")
                .weight(24.0)
                .weightFemale(16.0)
                .weightUnit(Unit.KG)));
    wods.add(
        wod(
            catalog,
            base("Leonidas EMOM", WodType.EMOM, ScoreType.NONE, sparta, ids.owner())
                .repScheme("EMOM 10 min")
                .emomInterval(60)
                .emomRounds(10)
                .descriptionEn("Every minute on the minute for 10 minutes.")
                .descriptionFr("Chaque minute, pendant 10 minutes."),
            mv("Power Clean", "3").weight(70.0).weightFemale(45.0).weightUnit(Unit.KG),
            mv("Burpee", "6")));
    wods.add(
        wod(
            catalog,
            base("Hoplite Chipper", WodType.FOR_TIME, ScoreType.TIME, sparta, ids.owner())
                .repScheme("50-40-30-20-10")
                .timeCapSeconds(900)
                .descriptionEn("For time, one movement after the other.")
                .descriptionFr("Pour le temps, un mouvement après l'autre."),
            mv("Double-under", "50"),
            mv("Air Squat", "40"),
            mv("Sit-up", "30"),
            mv("Push-up", "20"),
            mv("Pull-up", "10")));
    wods.add(
        wod(
            catalog,
            base("Sparta Back Squat 5x5", WodType.STRENGTH, ScoreType.WEIGHT, sparta, ids.owner())
                .repScheme("5x5")
                .descriptionEn("5 sets of 5 reps, rest 2 minutes between sets. Score the heaviest.")
                .descriptionFr(
                    "5 séries de 5 répétitions, 2 minutes de repos entre les séries. Score : la"
                        + " série la plus lourde."),
            mv("Back Squat", "5x5")
                .notesEn("Build up to a heavy 5")
                .notesFr("Monte jusqu'à 5 répétitions lourdes")));

    // --- Zeus Programming (virtual gym) ---
    wods.add(
        wod(
            catalog,
            base("Olympus Deadlift", WodType.STRENGTH, ScoreType.WEIGHT, zeus, ids.programmer())
                .repScheme("5-3-1")
                .descriptionEn("Work up to a heavy single.")
                .descriptionFr("Monte jusqu'à une répétition lourde."),
            mv("Deadlift", "5-3-1")));
    wods.add(
        wod(
            catalog,
            base("Thunderbolt Intervals", WodType.EMOM, ScoreType.NONE, zeus, ids.programmer())
                .repScheme("EMOM 16 min")
                .emomInterval(60)
                .emomRounds(16)
                .descriptionEn("Odd minutes: row. Even minutes: toes-to-bar.")
                .descriptionFr("Minutes impaires : rameur. Minutes paires : toes-to-bar."),
            mv("Rowing", null)
                .calories(15)
                .caloriesFemale(12)
                .notesEn("Odd minutes")
                .notesFr("Minutes impaires"),
            mv("Toes-to-Bar", "12").notesEn("Even minutes").notesFr("Minutes paires")));
    wods.add(
        wod(
            catalog,
            base("Titan AMRAP", WodType.AMRAP, ScoreType.REPS, zeus, ids.programmer())
                .repScheme("AMRAP 15 min")
                .repsPerRound(32)
                .timeCapSeconds(900)
                .descriptionEn("As many rounds as possible in 15 minutes.")
                .descriptionFr("Le plus de tours possible en 15 minutes."),
            mv("Thruster", "10").weight(43.0).weightFemale(30.0).weightUnit(Unit.KG),
            mv("Chest-to-Bar Pull-up", "10"),
            mv("Rowing", null).calories(12)));
    wods.add(
        wod(
            catalog,
            base("Pantheon 21-15-9", WodType.FOR_TIME, ScoreType.TIME, zeus, ids.programmer())
                .repScheme("21-15-9")
                .timeCapSeconds(720)
                .descriptionEn("21-15-9 reps for time.")
                .descriptionFr("21-15-9 répétitions pour le temps."),
            mv("Front Squat", "21-15-9").weight(60.0).weightFemale(40.0).weightUnit(Unit.KG),
            mv("Handstand Push-up", "21-15-9")));

    // --- Personal private WOD (no gym) ---
    wods.add(
        wod(
            catalog,
            base("Athlete Engine Builder", WodType.AMRAP, ScoreType.REPS, null, ids.athlete())
                .repScheme("AMRAP 20 min")
                .timeCapSeconds(1200)
                .descriptionEn("Personal session: as many rounds as possible in 20 minutes.")
                .descriptionFr("Séance perso : le plus de tours possible en 20 minutes."),
            mv("Running", null).distance(400.0).distanceUnit(Unit.METERS),
            mv("Air Squat", "20")));

    wodRepository.saveAll(wods);
    log.info("Dev gym WOD seeding: created {} WODs", wods.size());
  }

  // --- Builders ---

  private static Wod.WodBuilder base(
      String title, WodType type, ScoreType scoreType, Long gymId, Long authorId) {
    return Wod.builder()
        .title(title)
        .wodType(type)
        .scoreType(scoreType)
        .gymId(gymId)
        .authorId(authorId)
        .isPublic(false)
        .modalities(new HashSet<>())
        .movements(new ArrayList<>());
  }

  /** Movement spec: the movement is a name placeholder, resolved against the catalog. */
  private static WodMovement.WodMovementBuilder mv(String name, String reps) {
    return WodMovement.builder().movement(Movement.builder().name(name).build()).repsScheme(reps);
  }

  /**
   * Builds the WOD and links its movements, resolved by exact name in the catalog (unknown names
   * are logged and skipped). Order indexes follow the argument order.
   */
  private static Wod wod(
      Map<String, Movement> catalog,
      Wod.WodBuilder builder,
      WodMovement.WodMovementBuilder... specs) {
    Wod wod = builder.build();
    int order = 1;
    for (WodMovement.WodMovementBuilder spec : specs) {
      WodMovement wm = spec.build();
      String name = wm.getMovement().getName();
      Movement movement = catalog.get(name);
      if (movement == null) {
        log.warn("Dev gym WOD '{}': unknown movement '{}', skipped", wod.getTitle(), name);
        continue;
      }
      wm.checkVariantsAllowed(
          movement.getName(), movement.getEquipment(), movement.getTechniques());
      wm.setMovement(movement);
      wm.setWod(wod);
      wm.setOrderIndex(order++);
      wod.getMovements().add(wm);
      if (movement.getCategory() != null && movement.getCategory().getModality() != null) {
        wod.getModalities().add(movement.getCategory().getModality());
      }
    }
    return wod;
  }

  private record Ids(Long sparta, Long zeus, Long owner, Long programmer, Long athlete) {}
}
