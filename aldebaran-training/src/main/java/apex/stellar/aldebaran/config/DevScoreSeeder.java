package apex.stellar.aldebaran.config;

import apex.stellar.aldebaran.model.entities.Score;
import apex.stellar.aldebaran.model.entities.Score.ScalingLevel;
import apex.stellar.aldebaran.model.entities.Wod;
import apex.stellar.aldebaran.repository.ScoreRepository;
import apex.stellar.aldebaran.repository.WodRepository;
import apex.stellar.aldebaran.service.ScoreRanking;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Temporary development tool (profile {@code dev} only): logs scores for the Antares test accounts
 * on gym WODs and a few public benchmarks, so histories, personal records and leaderboards (per
 * scaling level and per gym) can be checked without typing them. Called by {@link
 * DevGymWodSeeder} once the WODs exist.
 *
 * <p>Idempotent: nothing is done when {@code owner@sparta.com} already has a score. Accounts or WODs
 * that cannot be found are skipped with a warning. Dates are relative to today; personal records are
 * computed with {@link ScoreRanking} like the service does. Values are stored canonically (AMRAP =
 * total reps, capped TIME = reps at the cap).
 */
@Component
@Profile("dev")
@Slf4j
public class DevScoreSeeder {

  static final String OWNER = DevGymWodSeeder.SPARTA_OWNER;
  static final String COACH = "coach@sparta.com";
  static final String SCORER = "scorer@sparta.com";
  static final String ATHLETE = DevGymWodSeeder.SPARTA_ATHLETE;
  static final String HYBRID = "hybrid@sparta.com";
  static final String MULTI = "multi@athlete.com";
  static final String PROGRAMMER = DevGymWodSeeder.ZEUS_PROGRAMMER;
  static final String ACTIVE = "active@zeus.com";
  static final String FREE = "free@athlete.com";

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
  private final Clock clock;

  /**
   * Creates the seeder.
   *
   * @param jdbcTemplate Access to the Antares users and existing scores.
   * @param wodRepository WOD lookup by title.
   * @param scoreRepository Score persistence.
   */
  @Autowired
  public DevScoreSeeder(
      JdbcTemplate jdbcTemplate, WodRepository wodRepository, ScoreRepository scoreRepository) {
    this(jdbcTemplate, wodRepository, scoreRepository, Clock.systemDefaultZone());
  }

  DevScoreSeeder(
      JdbcTemplate jdbcTemplate,
      WodRepository wodRepository,
      ScoreRepository scoreRepository,
      Clock clock) {
    this.jdbcTemplate = jdbcTemplate;
    this.wodRepository = wodRepository;
    this.scoreRepository = scoreRepository;
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
          jdbcTemplate.queryForList(
              "SELECT id FROM antares.users WHERE email = ?", Long.class, email);
      if (!ids.isEmpty()) {
        users.put(email, ids.getFirst());
      }
    }
    Long owner = users.get(OWNER);
    if (owner == null) {
      log.warn("Dev score seeding skipped: {} not found", OWNER);
      return;
    }
    if (!jdbcTemplate
        .queryForList(
            "SELECT id FROM aldebaran.scores WHERE user_id = ? LIMIT 1", Long.class, owner)
        .isEmpty()) {
      log.info("Dev score seeding skipped: {} already has scores", OWNER);
      return;
    }

    Map<String, Wod> wods =
        wodRepository.findAll().stream()
            .collect(Collectors.toMap(Wod::getTitle, Function.identity(), (a, b) -> a));
    Seed seed = new Seed(users, wods, spartaGymId, zeusGymId, LocalDate.now(clock));
    addGymScores(seed);
    addPublicScores(seed);

    seed.scores.stream()
        .collect(Collectors.groupingBy(s -> List.of(s.getWod().getId(), s.getUserId())))
        .values()
        .forEach(DevScoreSeeder::markPersonalRecord);
    scoreRepository.saveAll(seed.scores);
    log.info("Dev score seeding: logged {} scores", seed.scores.size());
  }

  private static void markPersonalRecord(List<Score> scores) {
    Score best =
        scores.stream()
            .min(ScoreRanking.bestFirst(scores.getFirst().getWod().getScoreType()))
            .orElseThrow();
    scores.forEach(s -> s.setPersonalRecord(s == best));
  }

  // --- Data set ---

  private static void addGymScores(Seed s) {
    // Spartacus CrossFit (Sparta Burner: 30 reps per round)
    s.add(s.on("Sparta Burner", OWNER, 9, ScalingLevel.RX, null).reps(252));
    s.add(s.on("Sparta Burner", COACH, 9, ScalingLevel.RX, null).reps(275));
    s.add(s.on("Sparta Burner", ATHLETE, 40, ScalingLevel.RX, null).reps(200));
    s.add(s.on("Sparta Burner", ATHLETE, 6, ScalingLevel.RX, null).reps(220));
    s.add(s.on("Sparta Burner", SCORER, 9, ScalingLevel.RX, null).reps(210));
    s.add(s.on("Sparta Burner", MULTI, 9, ScalingLevel.RX, null).reps(243));
    s.add(s.on("Sparta Burner", HYBRID, 9, ScalingLevel.SCALED, null).reps(270));

    s.add(s.on("Hoplite Chipper", COACH, 15, ScalingLevel.RX, null).timeSeconds(680));
    s.add(s.on("Hoplite Chipper", OWNER, 15, ScalingLevel.RX, null).timeSeconds(765));
    s.add(s.on("Hoplite Chipper", ATHLETE, 15, ScalingLevel.RX, null).timeCapped(true).reps(130));
    s.add(s.on("Hoplite Chipper", SCORER, 15, ScalingLevel.SCALED, null).timeSeconds(790));
    s.add(s.on("Hoplite Chipper", MULTI, 14, ScalingLevel.RX, null).timeSeconds(842));

    s.add(s.on("Sparta Back Squat 5x5", OWNER, 20, ScalingLevel.RX, null).maxWeightKg(140.0));
    s.add(s.on("Sparta Back Squat 5x5", COACH, 20, ScalingLevel.RX, null).maxWeightKg(130.0));
    s.add(s.on("Sparta Back Squat 5x5", ATHLETE, 60, ScalingLevel.RX, null).maxWeightKg(95.0));
    s.add(s.on("Sparta Back Squat 5x5", ATHLETE, 20, ScalingLevel.RX, null).maxWeightKg(100.0));
    s.add(s.on("Sparta Back Squat 5x5", SCORER, 20, ScalingLevel.RX, null).maxWeightKg(110.0));
    s.add(s.on("Sparta Back Squat 5x5", HYBRID, 19, ScalingLevel.RX, null).maxWeightKg(120.0));
    s.add(s.on("Sparta Back Squat 5x5", MULTI, 19, ScalingLevel.RX, null).maxWeightKg(105.0));

    // Zeus Programming (Titan AMRAP: 32 reps per round)
    s.add(s.on("Olympus Deadlift", PROGRAMMER, 25, ScalingLevel.RX, null).maxWeightKg(200.0));
    s.add(s.on("Olympus Deadlift", ACTIVE, 24, ScalingLevel.RX, null).maxWeightKg(150.0));
    s.add(s.on("Olympus Deadlift", MULTI, 23, ScalingLevel.RX, null).maxWeightKg(160.0));
    s.add(s.on("Olympus Deadlift", HYBRID, 23, ScalingLevel.RX, null).maxWeightKg(180.0));

    s.add(s.on("Titan AMRAP", PROGRAMMER, 12, ScalingLevel.RX, null).reps(324));
    s.add(s.on("Titan AMRAP", ACTIVE, 12, ScalingLevel.RX, null).reps(239));
    s.add(s.on("Titan AMRAP", MULTI, 11, ScalingLevel.RX, null).reps(256));
    s.add(s.on("Titan AMRAP", HYBRID, 11, ScalingLevel.SCALED, null).reps(308));

    s.add(s.on("Pantheon 21-15-9", PROGRAMMER, 4, ScalingLevel.RX, null).timeSeconds(390));
    s.add(s.on("Pantheon 21-15-9", ACTIVE, 4, ScalingLevel.RX, null).timeSeconds(585));
    s.add(s.on("Pantheon 21-15-9", MULTI, 3, ScalingLevel.RX, null).timeCapped(true).reps(70));
    s.add(s.on("Pantheon 21-15-9", HYBRID, 3, ScalingLevel.RX, null).timeSeconds(490));

    // Personal WOD (no reps per round)
    s.add(s.on("Athlete Engine Builder", ATHLETE, 30, ScalingLevel.RX, null).reps(180));
    s.add(s.on("Athlete Engine Builder", ATHLETE, 2, ScalingLevel.RX, null).reps(196));
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

  /** Collects the scores; a score whose account or WOD is unknown is skipped. */
  private static final class Seed {
    private final Map<String, Long> users;
    private final Map<String, Wod> wods;
    private final Long sparta;
    private final Long zeus;
    private final LocalDate today;
    private final List<Score> scores = new ArrayList<>();

    private Seed(
        Map<String, Long> users, Map<String, Wod> wods, Long sparta, Long zeus, LocalDate today) {
      this.users = users;
      this.wods = wods;
      this.sparta = sparta;
      this.zeus = zeus;
      this.today = today;
    }

    /**
     * Starts a score. The gym is the WOD's own gym when {@code gym} is null (gym or personal WOD),
     * else the gym where the athlete performed a public WOD.
     */
    Score.ScoreBuilder on(String title, String email, int daysAgo, ScalingLevel scaling, Gym gym) {
      Wod wod = wods.get(title);
      if (wod == null || !users.containsKey(email)) {
        log.warn("Dev score seeding: WOD '{}' or account {} not found, score skipped", title, email);
      }
      Long gymId =
          switch (gym) {
            case null -> wod != null ? wod.getGymId() : null;
            case SPARTA -> sparta;
            case ZEUS -> zeus;
            case NONE -> null;
          };
      return Score.builder()
          .wod(wod)
          .userId(users.get(email))
          .date(today.minusDays(daysAgo))
          .scaling(scaling)
          .gymId(gymId);
    }

    void add(Score.ScoreBuilder builder) {
      Score score = builder.build();
      if (score.getWod() != null && score.getUserId() != null) {
        scores.add(score);
      }
    }
  }
}
