package apex.stellar.aldebaran.demo;

import apex.stellar.aldebaran.model.entities.Movement;
import apex.stellar.aldebaran.model.entities.MovementUsualName;
import apex.stellar.aldebaran.model.entities.Wod;
import apex.stellar.aldebaran.model.entities.Wod.ScoreType;
import apex.stellar.aldebaran.model.entities.Wod.WodType;
import apex.stellar.aldebaran.model.entities.WodMovement;
import apex.stellar.aldebaran.service.CatalogRules;
import apex.stellar.aldebaran.model.enums.Unit;
import apex.stellar.aldebaran.repository.MovementRepository;
import apex.stellar.aldebaran.repository.WodRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
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
 * Demo data of the training side (profile {@code demo}): the two demo programs and one personal WOD.
 *
 * <ul>
 *   <li>"Spartacus CrossFit" (physical box): a balanced 12-week programming, written by {@code
 *       owner.sparta@demo.test}; "Zeus Programming" (virtual program): an unbalanced one, 6 days a
 *       week, written by {@code programmer.zeus@demo.test} ({@link DemoPrograms});
 *   <li>one personal private WOD for {@code athlete.sparta@demo.test}.
 * </ul>
 *
 * <p>Then {@link DevScoreSeeder} logs the public test and benchmark scores, and {@link
 * DemoProgramScores} the journals and scores of the programs' past sessions (each with its own
 * idempotency check, so an existing database gets what it lacks).
 *
 * <p>Gym and user IDs are read with {@link JdbcTemplate} from the Antares schema of the same
 * PostgreSQL instance. Antares may seed after Aldebaran starts, so the lookup is retried in the
 * background (about 60 s by default) before giving up.
 */
@Component
@Profile("demo")
@Slf4j
public class DevGymWodSeeder implements ApplicationRunner {

  static final String SPARTA_GYM = "Spartacus CrossFit";
  static final String ZEUS_GYM = "Zeus Programming";
  static final String SPARTA_OWNER = "owner.sparta@demo.test";
  static final String ZEUS_PROGRAMMER = "programmer.zeus@demo.test";
  static final String SPARTA_ATHLETE = "athlete.sparta@demo.test";
  static final String PERSONAL_WOD = "Athlete Engine Builder";

  private final JdbcTemplate jdbcTemplate;
  private final WodRepository wodRepository;
  private final MovementRepository movementRepository;
  private final DevScoreSeeder devScoreSeeder;
  private final DemoPrograms demoPrograms;
  private final DemoProgramScores demoProgramScores;
  private final TransactionTemplate transactionTemplate;
  private final Clock clock;
  private final int maxAttempts;
  private final Duration retryDelay;

  /**
   * Creates the seeder.
   *
   * @param jdbcTemplate Access to the Antares tables.
   * @param wodRepository WOD persistence.
   * @param movementRepository Movement catalog.
   * @param devScoreSeeder Public test and benchmark scores.
   * @param demoPrograms The demo programs' sessions.
   * @param demoProgramScores The demo programs' journals and scores.
   * @param transactionManager Transaction manager used for the background seeding.
   * @param clock The application clock (the seed day centres the programs).
   * @param maxAttempts Number of lookups before giving up.
   * @param retryDelay Delay between two lookups.
   */
  @SuppressWarnings("java:S107") // Spring wiring of the demo seeders
  public DevGymWodSeeder(
      JdbcTemplate jdbcTemplate,
      WodRepository wodRepository,
      MovementRepository movementRepository,
      DevScoreSeeder devScoreSeeder,
      DemoPrograms demoPrograms,
      DemoProgramScores demoProgramScores,
      PlatformTransactionManager transactionManager,
      Clock clock,
      @Value("${aldebaran.dev-seeder.max-attempts:12}") int maxAttempts,
      @Value("${aldebaran.dev-seeder.retry-delay:5s}") Duration retryDelay) {
    this.jdbcTemplate = jdbcTemplate;
    this.wodRepository = wodRepository;
    this.movementRepository = movementRepository;
    this.devScoreSeeder = devScoreSeeder;
    this.demoPrograms = demoPrograms;
    this.demoProgramScores = demoProgramScores;
    this.transactionTemplate = new TransactionTemplate(transactionManager);
    this.clock = clock;
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
          seed(ids);
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

  private void seed(Ids ids) {
    transactionTemplate.executeWithoutResult(status -> seedPersonalWod(ids));
    LocalDate today = LocalDate.now(clock);
    List<DemoPrograms.Program> programs = demoPrograms.programs();
    for (DemoPrograms.Program program : programs) {
      Long gymId = gymOf(program, ids);
      if (demoPrograms.seeded(program, gymId)) {
        log.info("Demo program {} already in '{}'", program.key(), program.gym());
      } else {
        demoPrograms.seed(program, gymId, "ZEUS".equals(program.key()) ? ids.programmer() : ids.owner(), today);
      }
    }
    devScoreSeeder.seed(ids.sparta(), ids.zeus());
    Map<String, Long> users = users();
    for (DemoPrograms.Program program : programs) {
      demoProgramScores.seed(program, gymOf(program, ids), users, today);
    }
  }

  private static Long gymOf(DemoPrograms.Program program, Ids ids) {
    return "ZEUS".equals(program.key()) ? ids.zeus() : ids.sparta();
  }

  private Map<String, Long> users() {
    Map<String, Long> users = new HashMap<>();
    for (DemoProgramScores.Persona p : DemoProgramScores.PERSONAS) {
      Long id = findId("SELECT id FROM antares.users WHERE email = ?", p.email());
      if (id != null) {
        users.put(p.email(), id);
      }
    }
    for (String judge : List.of(DemoProgramScores.SCORER, DemoProgramScores.COACH)) {
      Long id = findId("SELECT id FROM antares.users WHERE email = ?", judge);
      if (id != null) {
        users.put(judge, id);
      }
    }
    return users;
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

  /** The athlete's personal private WOD (no gym), once. */
  private void seedPersonalWod(Ids ids) {
    if (wodRepository.existsByAuthorIdAndGymIdIsNullAndTitle(ids.athlete(), PERSONAL_WOD)) {
      return;
    }
    Map<String, Movement> catalog = byName(movementRepository.findAll());
    Wod wod =
        wod(
            catalog,
            base(PERSONAL_WOD, WodType.AMRAP, ScoreType.REPS, null, ids.athlete())
                .repScheme("AMRAP 20 min")
                .timeCapSeconds(1200)
                .descriptionEn("Personal session: as many rounds as possible in 20 minutes.")
                .descriptionFr("Séance perso : le plus de tours possible en 20 minutes."),
            mv("Running", null).distance(400.0).distanceUnit(Unit.METERS),
            mv("Air Squat", "20"));
    wodRepository.save(wod);
    log.info("Dev gym WOD seeding: personal WOD created");
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
      MovementUsualName usual = usualNames(catalog).get(name);
      Movement movement = usual != null ? usual.getMovement() : catalog.get(name);
      if (movement == null) {
        log.warn("Dev gym WOD '{}': unknown movement '{}', skipped", wod.getTitle(), name);
        continue;
      }
      wm.setEquipment(new HashSet<>(wm.getEquipment()));
      wm.setTechniques(new HashSet<>(wm.getTechniques()));
      if (usual != null) {
        wm.getEquipment().addAll(usual.getEquipment());
        wm.getTechniques().addAll(usual.getTechniques());
      }
      wm.checkVariantsAllowed(
          movement.getName(), movement.getEquipment(), movement.getTechniques());
      wm.setMovement(movement);
      wm.setWod(wod);
      wm.setOrderIndex(order++);
      wod.getMovements().add(wm);
    }
    wod.getModalities().addAll(CatalogRules.modalities(wod.getMovements()));
    return wod;
  }

  /** The catalog's usual names: demo WODs are written the way athletes name movements. */
  private static Map<String, MovementUsualName> usualNames(Map<String, Movement> catalog) {
    Map<String, MovementUsualName> names = new HashMap<>();
    catalog.values().forEach(m -> m.getUsualNames().forEach(u -> names.put(u.getNameEn(), u)));
    return names;
  }

  private record Ids(Long sparta, Long zeus, Long owner, Long programmer, Long athlete) {}

  /** The catalog movements keyed by name (the demo WODs name their movements). */
  private static Map<String, Movement> byName(List<Movement> movements) {
    return movements.stream()
        .collect(Collectors.toMap(Movement::getName, Function.identity(), (a, b) -> a));
  }
}
