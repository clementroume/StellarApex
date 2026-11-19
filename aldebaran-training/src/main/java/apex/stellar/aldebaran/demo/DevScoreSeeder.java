package apex.stellar.aldebaran.demo;

import apex.stellar.aldebaran.dto.ScoreRequest;
import apex.stellar.aldebaran.model.entities.Score.ScalingLevel;
import apex.stellar.aldebaran.model.entities.Wod;
import apex.stellar.aldebaran.model.enums.Unit;
import apex.stellar.aldebaran.repository.ScoreRepository;
import apex.stellar.aldebaran.repository.WodRepository;
import apex.stellar.aldebaran.service.ScoreService;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Demo data (profile {@code demo}): logs scores for the demo accounts on the public benchmarks, the
 * public "N RM" tests and the athlete's personal WOD, so public leaderboards, lift leaderboards and
 * records have data. Every score goes through {@link ScoreService} as its athlete ({@link
 * DemoActor}): division, age group, personal records and records come from the service. The demo
 * programs' scores come from {@link DemoProgramScores}. Called by {@link DevGymWodSeeder}.
 *
 * <p>Idempotent: the benchmark scores are skipped when {@code owner.sparta@demo.test} already has a
 * score on the public "Fran", the lift tests when it has one on "Back Squat 1RM", the personal WOD's
 * when its athlete has one on it. Accounts or WODs
 * that cannot be found are skipped with a warning. Dates are relative to today.
 */
@Component
@Profile("demo")
@Slf4j
public class DevScoreSeeder {

  static final String OWNER = DevGymWodSeeder.SPARTA_OWNER;
  static final String COACH = "coach.sparta@demo.test";
  static final String SCORER = "scorer.sparta@demo.test";
  static final String ATHLETE = DevGymWodSeeder.SPARTA_ATHLETE;
  static final String HYBRID = "hybrid.sparta@demo.test";
  static final String MULTI = "multi@demo.test";
  static final String PROGRAMMER = DevGymWodSeeder.ZEUS_PROGRAMMER;
  static final String ACTIVE = "active.zeus@demo.test";
  static final String FREE = "free@demo.test";

  /** Public benchmark whose owner score marks the benchmark scores as seeded. */
  static final String BENCHMARK_MARKER = "Fran";

  /** Public "N RM" test whose owner score marks the lift scores as seeded. */
  static final String LIFT_MARKER = "Back Squat 1RM";

  private static final List<String> ACCOUNTS =
      List.of(OWNER, COACH, SCORER, ATHLETE, HYBRID, MULTI, PROGRAMMER, ACTIVE, FREE);

  /** Gym where a public WOD was performed. */
  private enum Gym {
    SPARTA,
    ZEUS,
    NONE
  }

  private final JdbcTemplate jdbcTemplate;
  private final WodRepository wodRepository;
  private final ScoreRepository scoreRepository;
  private final ScoreService scoreService;
  private final DemoActor actor;
  private final TransactionTemplate ownTransaction;
  private final Clock clock;

  /**
   * Creates the seeder.
   *
   * @param jdbcTemplate Access to the Antares users (another schema).
   * @param wodRepository WOD lookup by title.
   * @param scoreRepository The "already seeded" checks.
   * @param scoreService Logs each score as its athlete.
   * @param actor Identity of the demo accounts.
   * @param transactionManager One transaction per score: a refused one never undoes the others.
   * @param clock The application clock.
   */
  public DevScoreSeeder(
      JdbcTemplate jdbcTemplate,
      WodRepository wodRepository,
      ScoreRepository scoreRepository,
      ScoreService scoreService,
      DemoActor actor,
      PlatformTransactionManager transactionManager,
      Clock clock) {
    this.jdbcTemplate = jdbcTemplate;
    this.wodRepository = wodRepository;
    this.scoreRepository = scoreRepository;
    this.scoreService = scoreService;
    this.actor = actor;
    this.ownTransaction = new TransactionTemplate(transactionManager);
    this.ownTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    this.clock = clock;
  }

  /**
   * Logs the test scores unless they already exist.
   *
   * @param spartaGymId ID of "Spartacus CrossFit".
   * @param zeusGymId ID of "Zeus Programming".
   */
  public void seed(Long spartaGymId, Long zeusGymId) {
    Map<String, Long> users = new HashMap<>();
    for (String email : ACCOUNTS) {
      List<Long> ids =
          jdbcTemplate.queryForList("SELECT id FROM antares.users WHERE email = ?", Long.class, email);
      if (!ids.isEmpty()) {
        users.put(email, ids.getFirst());
      }
    }
    Long owner = users.get(OWNER);
    if (owner == null) {
      log.warn("Dev score seeding skipped: {} not found", OWNER);
      return;
    }
    // Public WODs and the personal one only: the box copies of the benchmarks share their titles
    Map<String, Wod> wods = new HashMap<>();
    for (Wod w : wodRepository.findAll()) {
      boolean personal = w.getGymId() == null && !w.isPublic() && DevGymWodSeeder.PERSONAL_WOD.equals(w.getTitle());
      if ((w.isPublic() && w.getGymId() == null) || personal) {
        wods.putIfAbsent(w.getTitle(), w);
      }
    }
    Seed seed = new Seed(users, wods, spartaGymId, zeusGymId, LocalDate.now(clock));
    if (seeded(owner, wods.get(BENCHMARK_MARKER))) {
      log.info("Dev score seeding: benchmark scores already logged");
    } else {
      addPublicScores(seed);
    }
    // The personal WOD always exists: its own marker, whatever the public catalog holds
    Long athlete = users.get(ATHLETE);
    if (athlete != null && !seeded(athlete, wods.get(DevGymWodSeeder.PERSONAL_WOD))) {
      addPersonalScores(seed);
    }
    // The lift tests come in a second step (their own marker), so an older database gets them
    if (!seeded(owner, wods.get(LIFT_MARKER))) {
      addLiftScores(seed);
    }
    int logged = 0;
    for (Planned p : seed.planned) {
      logged += log(p) ? 1 : 0;
    }
    if (logged > 0) {
      log.info("Dev score seeding: logged {} scores", logged);
    }
  }

  private boolean seeded(Long owner, @Nullable Wod marker) {
    return marker == null || scoreRepository.existsByUserIdAndWodId(owner, marker.getId());
  }

  private boolean log(Planned p) {
    int time = p.timeSeconds == null ? 0 : p.timeSeconds;
    ScoreRequest request =
        new ScoreRequest(
            null,
            p.date,
            p.wod.getId(),
            p.timeSeconds == null ? null : time / 60,
            p.timeSeconds == null ? null : time % 60,
            p.rounds,
            p.reps,
            p.maxWeightKg,
            p.totalLoadKg,
            p.maxWeightKg != null || p.totalLoadKg != null ? Unit.KG : null,
            null,
            null,
            null,
            p.scaling,
            p.timeCapped,
            p.tiebreakSeconds,
            null,
            p.gymId,
            null,
            null,
            null,
            null,
            false,
            null);
    try {
      actor.run(p.userId, () -> ownTransaction.executeWithoutResult(s -> scoreService.logScore(request)));
      return true;
    } catch (RuntimeException e) {
      log.warn("Dev score on '{}' for user id={} skipped ({})", p.wod.getTitle(), p.userId, e.getMessage());
      return false;
    }
  }

  // --- Data set ---

  private static void addPersonalScores(Seed s) {
    // Personal WOD (no reps per round); the programs' scores come from DemoProgramScores
    s.add(s.on(DevGymWodSeeder.PERSONAL_WOD, ATHLETE, 30, ScalingLevel.RX, null).reps(180));
    s.add(s.on(DevGymWodSeeder.PERSONAL_WOD, ATHLETE, 2, ScalingLevel.RX, null).reps(196));
  }

  /**
   * Scores on the public "N RM" tests (no sets: a score on such a test feeds its N record), so the
   * records, lift leaderboards and the box board have data. Two attempts for some athletes show the
   * previous load and the progression.
   */
  private static void addLiftScores(Seed s) {
    lift(s, "Back Squat 1RM", OWNER, 90, 150.0);
    lift(s, "Back Squat 1RM", OWNER, 30, 160.0);
    lift(s, "Back Squat 1RM", COACH, 20, 110.0);
    lift(s, "Back Squat 1RM", SCORER, 25, 145.0);
    lift(s, "Back Squat 1RM", ATHLETE, 40, 85.0);
    lift(s, "Back Squat 1RM", ATHLETE, 7, 90.0);
    lift(s, "Back Squat 1RM", HYBRID, 18, 105.0);
    lift(s, "Back Squat 1RM", MULTI, 21, 150.0);
    lift(s, "Back Squat 1RM", FREE, 14, 170.0);
    lift(s, "Back Squat 1RM", PROGRAMMER, 12, 180.0);
    lift(s, "Back Squat 1RM", ACTIVE, 16, 95.0);
    lift(s, "Back Squat 5RM", OWNER, 28, 130.0);
    lift(s, "Back Squat 5RM", COACH, 19, 90.0);
    lift(s, "Back Squat 5RM", ATHLETE, 8, 70.0);
    lift(s, "Back Squat 5RM", MULTI, 20, 120.0);
    lift(s, "Front Squat 3RM", OWNER, 26, 120.0);
    lift(s, "Front Squat 3RM", ATHLETE, 9, 65.0);
    lift(s, "Deadlift 1RM", OWNER, 33, 200.0);
    lift(s, "Deadlift 1RM", COACH, 22, 140.0);
    lift(s, "Deadlift 1RM", SCORER, 24, 190.0);
    lift(s, "Deadlift 1RM", ATHLETE, 10, 110.0);
    lift(s, "Deadlift 1RM", HYBRID, 17, 130.0);
    lift(s, "Deadlift 1RM", MULTI, 21, 185.0);
    lift(s, "Deadlift 1RM", FREE, 13, 210.0);
    lift(s, "Deadlift 1RM", PROGRAMMER, 11, 220.0);
    lift(s, "Deadlift 1RM", ACTIVE, 15, 120.0);
    lift(s, "Clean and Jerk 1RM", OWNER, 27, 110.0);
    lift(s, "Clean and Jerk 1RM", COACH, 23, 80.0);
    lift(s, "Clean and Jerk 1RM", SCORER, 25, 100.0);
    lift(s, "Clean and Jerk 1RM", ATHLETE, 11, 55.0);
    lift(s, "Clean and Jerk 1RM", HYBRID, 16, 75.0);
    lift(s, "Clean and Jerk 1RM", MULTI, 19, 105.0);
    lift(s, "Clean and Jerk 1RM", FREE, 12, 120.0);
    lift(s, "Snatch 1RM", OWNER, 29, 90.0);
    lift(s, "Snatch 1RM", COACH, 21, 65.0);
    lift(s, "Snatch 1RM", SCORER, 26, 80.0);
    lift(s, "Snatch 1RM", ATHLETE, 12, 45.0);
    lift(s, "Snatch 1RM", MULTI, 18, 85.0);
    lift(s, "Snatch 1RM", FREE, 10, 95.0);
    lift(s, "Shoulder Press 1RM", OWNER, 31, 80.0);
    lift(s, "Shoulder Press 1RM", COACH, 22, 50.0);
    lift(s, "Shoulder Press 1RM", ATHLETE, 13, 35.0);
    lift(s, "Shoulder Press 1RM", MULTI, 17, 75.0);
    lift(s, "Bench Press 1RM", OWNER, 32, 110.0);
    lift(s, "Bench Press 1RM", SCORER, 23, 100.0);
    lift(s, "Bench Press 1RM", MULTI, 16, 105.0);
    lift(s, "Bench Press 1RM", FREE, 11, 120.0);
  }

  /** One lift test score: the gym is the athlete's (Zeus for its athletes, none for the free one). */
  private static void lift(Seed s, String title, String email, int daysAgo, double kg) {
    Gym gym =
        switch (email) {
          case PROGRAMMER, ACTIVE -> Gym.ZEUS;
          case FREE -> Gym.NONE;
          default -> Gym.SPARTA;
        };
    s.add(s.on(title, email, daysAgo, ScalingLevel.RX, gym).maxWeightKg(kg));
  }

  private static void addPublicScores(Seed s) {
    s.add(s.on("Fran", OWNER, 60, ScalingLevel.RX, Gym.SPARTA).timeSeconds(185));
    s.add(s.on("Fran", OWNER, 10, ScalingLevel.RX, Gym.SPARTA).timeSeconds(178));
    s.add(s.on("Fran", COACH, 30, ScalingLevel.RX, Gym.SPARTA).timeSeconds(161));
    s.add(s.on("Fran", ATHLETE, 45, ScalingLevel.SCALED, Gym.SPARTA).timeSeconds(330));
    s.add(s.on("Fran", ATHLETE, 5, ScalingLevel.RX, Gym.SPARTA).timeSeconds(372));
    s.add(s.on("Fran", SCORER, 28, ScalingLevel.RX, Gym.SPARTA).timeSeconds(260));
    s.add(s.on("Fran", MULTI, 27, ScalingLevel.RX, Gym.ZEUS).timeSeconds(230));
    s.add(s.on("Fran", ACTIVE, 26, ScalingLevel.RX, Gym.ZEUS).timeSeconds(245));
    s.add(s.on("Fran", FREE, 25, ScalingLevel.RX, Gym.NONE).timeSeconds(213));
    s.add(s.on("Fran", HYBRID, 24, ScalingLevel.SCALED, Gym.SPARTA).timeSeconds(285));

    s.add(s.on("Chelsea", OWNER, 35, ScalingLevel.RX, Gym.SPARTA).rounds(30));
    s.add(s.on("Chelsea", COACH, 35, ScalingLevel.RX, Gym.SPARTA).rounds(24));
    s.add(s.on("Chelsea", ATHLETE, 50, ScalingLevel.RX, Gym.SPARTA).rounds(17));
    s.add(s.on("Chelsea", ATHLETE, 8, ScalingLevel.RX, Gym.SPARTA).rounds(20));
    s.add(s.on("Chelsea", FREE, 33, ScalingLevel.RX, Gym.NONE).rounds(26));
    s.add(s.on("Chelsea", MULTI, 32, ScalingLevel.SCALED, Gym.ZEUS).rounds(30));
    s.add(s.on("Chelsea", ACTIVE, 31, ScalingLevel.RX, Gym.ZEUS).rounds(22));

    // Cindy: 30 reps per round
    s.add(s.on("Cindy", OWNER, 18, ScalingLevel.RX, Gym.SPARTA).reps(635));
    s.add(s.on("Cindy", COACH, 18, ScalingLevel.RX, Gym.SPARTA).reps(590));
    s.add(s.on("Cindy", ATHLETE, 55, ScalingLevel.RX, Gym.SPARTA).reps(462));
    s.add(s.on("Cindy", ATHLETE, 12, ScalingLevel.RX, Gym.SPARTA).reps(513));
    s.add(s.on("Cindy", ACTIVE, 17, ScalingLevel.RX, Gym.ZEUS).reps(540));
    s.add(s.on("Cindy", FREE, 16, ScalingLevel.SCALED, Gym.NONE).reps(610));
    s.add(s.on("Cindy", HYBRID, 16, ScalingLevel.RX, Gym.ZEUS).reps(505));

    s.add(s.on("Grace", COACH, 40, ScalingLevel.RX, Gym.SPARTA).timeSeconds(130));
    s.add(s.on("Grace", OWNER, 40, ScalingLevel.RX, Gym.SPARTA).timeSeconds(165));
    s.add(s.on("Grace", ATHLETE, 40, ScalingLevel.SCALED, Gym.SPARTA).timeSeconds(240));
    s.add(s.on("Grace", PROGRAMMER, 38, ScalingLevel.RX, Gym.ZEUS).timeSeconds(115));

    s.add(s.on("Murph", OWNER, 70, ScalingLevel.RX, Gym.SPARTA).timeSeconds(2550));
    s.add(s.on("Murph", ATHLETE, 70, ScalingLevel.SCALED, Gym.SPARTA).timeSeconds(3312));
    s.add(s.on("Murph", ACTIVE, 69, ScalingLevel.RX, Gym.ZEUS).timeSeconds(2880));
    s.add(s.on("Murph", FREE, 68, ScalingLevel.RX, Gym.NONE).timeSeconds(2380));

    s.add(s.on("Open 25.1", OWNER, 22, ScalingLevel.RX, Gym.SPARTA).reps(180));
    s.add(s.on("Open 25.1", COACH, 22, ScalingLevel.RX, Gym.SPARTA).reps(210));
    s.add(s.on("Open 25.1", ATHLETE, 22, ScalingLevel.SCALED, Gym.SPARTA).reps(165));
    s.add(s.on("Open 25.1", MULTI, 21, ScalingLevel.RX, Gym.ZEUS).reps(172));
    s.add(s.on("Open 25.1", ACTIVE, 21, ScalingLevel.RX, Gym.ZEUS).reps(150));
    s.add(s.on("Open 25.1", FREE, 20, ScalingLevel.RX, Gym.NONE).reps(198));
    s.add(s.on("Open 25.1", HYBRID, 20, ScalingLevel.RX, Gym.SPARTA).reps(176));

    // Open 25.2: time cap with an official tiebreak
    s.add(s.on("Open 25.2", COACH, 14, ScalingLevel.RX, Gym.SPARTA).timeSeconds(642));
    s.add(
        s.on("Open 25.2", OWNER, 14, ScalingLevel.RX, Gym.SPARTA)
            .timeCapped(true)
            .reps(290)
            .tiebreakSeconds(555));
    s.add(
        s.on("Open 25.2", ATHLETE, 14, ScalingLevel.SCALED, Gym.SPARTA)
            .timeCapped(true)
            .reps(240)
            .tiebreakSeconds(620));
    s.add(s.on("Open 25.2", FREE, 13, ScalingLevel.RX, Gym.NONE).timeSeconds(690));

    s.add(s.on("CrossFit Total", OWNER, 90, ScalingLevel.RX, Gym.SPARTA).totalLoadKg(450.0));
    s.add(s.on("CrossFit Total", COACH, 90, ScalingLevel.RX, Gym.SPARTA).totalLoadKg(420.0));
    s.add(s.on("CrossFit Total", ATHLETE, 90, ScalingLevel.RX, Gym.SPARTA).totalLoadKg(310.0));
    s.add(s.on("CrossFit Total", FREE, 85, ScalingLevel.RX, Gym.NONE).totalLoadKg(400.0));
  }

  /** One score to log, written fluently like the data set reads. */
  private static final class Planned {
    private final Wod wod;
    private final Long userId;
    private final LocalDate date;
    private final ScalingLevel scaling;
    private final @Nullable Long gymId;
    private @Nullable Integer timeSeconds;
    private @Nullable Integer rounds;
    private @Nullable Integer reps;
    private @Nullable Double maxWeightKg;
    private @Nullable Double totalLoadKg;
    private boolean timeCapped;
    private @Nullable Integer tiebreakSeconds;

    private Planned(Wod wod, Long userId, LocalDate date, ScalingLevel scaling, @Nullable Long gymId) {
      this.wod = wod;
      this.userId = userId;
      this.date = date;
      this.scaling = scaling;
      this.gymId = gymId;
    }

    Planned timeSeconds(int value) {
      timeSeconds = value;
      return this;
    }

    Planned rounds(int value) {
      rounds = value;
      return this;
    }

    Planned reps(int value) {
      reps = value;
      return this;
    }

    Planned maxWeightKg(double value) {
      maxWeightKg = value;
      return this;
    }

    Planned totalLoadKg(double value) {
      totalLoadKg = value;
      return this;
    }

    Planned timeCapped(boolean value) {
      timeCapped = value;
      return this;
    }

    Planned tiebreakSeconds(int value) {
      tiebreakSeconds = value;
      return this;
    }
  }

  /** Collects the scores to log; a score whose account or WOD is unknown is skipped. */
  private static final class Seed {
    private final Map<String, Long> users;
    private final Map<String, Wod> wods;
    private final Long sparta;
    private final Long zeus;
    private final LocalDate today;
    private final List<Planned> planned = new ArrayList<>();

    private Seed(Map<String, Long> users, Map<String, Wod> wods, Long sparta, Long zeus, LocalDate today) {
      this.users = users;
      this.wods = wods;
      this.sparta = sparta;
      this.zeus = zeus;
      this.today = today;
    }

    /**
     * Starts a score. The gym is the athlete's for a public WOD ({@code gym}), none for the personal
     * WOD ({@code gym} null). Unknown WOD or account: a detached score, never logged.
     */
    Planned on(String title, String email, int daysAgo, ScalingLevel scaling, @Nullable Gym gym) {
      Wod wod = wods.get(title);
      Long userId = users.get(email);
      Long gymId =
          switch (gym) {
            case null -> null;
            case SPARTA -> sparta;
            case ZEUS -> zeus;
            case NONE -> null;
          };
      Planned p = new Planned(wod, userId, today.minusDays(daysAgo), scaling, gymId);
      if (wod == null || userId == null) {
        log.warn("Dev score seeding: WOD '{}' or account {} not found, score skipped", title, email);
      }
      return p;
    }

    void add(Planned p) {
      if (p.wod != null && p.userId != null) {
        planned.add(p);
      }
    }
  }
}
