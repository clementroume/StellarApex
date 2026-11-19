package apex.stellar.aldebaran.demo;

import apex.stellar.aldebaran.dto.ScoreLineRequest;
import apex.stellar.aldebaran.dto.ScoreRequest;
import apex.stellar.aldebaran.dto.ScoreResponse;
import apex.stellar.aldebaran.dto.ScoreSetRequest;
import apex.stellar.aldebaran.dto.SessionLogRequest;
import apex.stellar.aldebaran.dto.SessionLogResponse;
import apex.stellar.aldebaran.model.entities.Score.ScalingLevel;
import apex.stellar.aldebaran.model.entities.Session;
import apex.stellar.aldebaran.model.entities.SessionBlock;
import apex.stellar.aldebaran.model.entities.Wod;
import apex.stellar.aldebaran.model.entities.Wod.ScoreType;
import apex.stellar.aldebaran.model.entities.WodMovement;
import apex.stellar.aldebaran.model.enums.Division;
import apex.stellar.aldebaran.model.enums.Unit;
import apex.stellar.aldebaran.repository.SessionLogRepository;
import apex.stellar.aldebaran.repository.SessionRepository;
import apex.stellar.aldebaran.service.LineVersions;
import apex.stellar.aldebaran.service.ScoreService;
import apex.stellar.aldebaran.service.SessionLogService;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.Predicate;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Journals and scores of the demo programs' past sessions (design in {@code docs-private/demo}):
 * each demo account attends its own days, logs its Skill and WOD through the score service as
 * itself ({@link DemoActor}), at its own level (loads from its own maxima, times and reps from its
 * engine, the official Scaled version when it scales). Saturdays at Spartacus are team WODs; the
 * autumn throwdown has judges, verified scores and one score left in the verification queue.
 * Deterministic (fixed seeds): two databases get the same story.
 *
 * <p>Idempotent per program: nothing is done when a journal of the program already exists.
 */
@Slf4j
@Component
@Profile("demo")
class DemoProgramScores {

  static final String OWNER = DevGymWodSeeder.SPARTA_OWNER;
  static final String COACH = "coach.sparta@demo.test";
  static final String SCORER = "scorer.sparta@demo.test";
  static final String ATHLETE = DevGymWodSeeder.SPARTA_ATHLETE;
  static final String MULTI = "multi@demo.test";
  static final String HYBRID = "hybrid.sparta@demo.test";
  static final String ACTIVE = "active.zeus@demo.test";

  private static final Set<DayOfWeek> SPARTA_DAYS =
      EnumSet.range(DayOfWeek.MONDAY, DayOfWeek.SATURDAY);
  private static final List<String> COMMENTS =
      List.of("Bonne séance", "Genoux lourds aujourd'hui", "PR !", "Rythme tenu jusqu'au bout", "Dur mais propre");

  /**
   * A demo athlete of a program.
   *
   * @param email The account.
   * @param program Its program key (SPARTA, ZEUS).
   * @param backSquat Its back squat 1RM at the start (kg): every load is a share of it.
   * @param engine Its pace, 1 = the box's best athlete.
   * @param scales Takes the official Scaled version when the WOD has one (1 = always).
   * @param attends Whether it comes on a day (week of the program, day).
   */
  record Persona(
      String email, String program, double backSquat, double engine, double scales, Attendance attends) {}

  /** Whether a persona comes on a day of a week of the program (week 1 = the first one). */
  interface Attendance {
    boolean on(int week, DayOfWeek day);
  }

  static final List<Persona> PERSONAS =
      List.of(
          // Six days, a day off every two or three weeks
          new Persona(OWNER, "SPARTA", 150, 1.0, 0, DemoProgramScores::ownerDays),
          // Fixed Monday, Wednesday, Friday
          new Persona(COACH, "SPARTA", 90, 0.95, 0, (w, d) -> EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY).contains(d)),
          // Tuesday, Thursday, Saturday, plus Friday every other week
          new Persona(SCORER, "SPARTA", 130, 0.9, 0, (w, d) -> EnumSet.of(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY, DayOfWeek.SATURDAY).contains(d) || (w % 2 == 0 && d == DayOfWeek.FRIDAY)),
          // Three days a week, Saturday and two others drawn (fixed draw)
          new Persona(ATHLETE, "SPARTA", 65, 0.75, 1, DemoProgramScores::randomThreeDays),
          // Two gyms: Spartacus on Monday and Saturday, Zeus on Tuesday and Thursday
          new Persona(MULTI, "SPARTA", 120, 0.85, 0.3, (w, d) -> d == DayOfWeek.MONDAY || d == DayOfWeek.SATURDAY),
          new Persona(MULTI, "ZEUS", 120, 0.85, 0, (w, d) -> d == DayOfWeek.TUESDAY || d == DayOfWeek.THURSDAY),
          // Coach at Spartacus, athlete of the Zeus program on Monday, Wednesday and Friday
          new Persona(HYBRID, "ZEUS", 75, 0.8, 0, (w, d) -> EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY).contains(d)),
          // Monday to Friday
          new Persona(ACTIVE, "ZEUS", 85, 0.9, 0, (w, d) -> d != DayOfWeek.SATURDAY && d != DayOfWeek.SUNDAY));

  private final SessionLogRepository sessionLogRepository;
  private final SessionRepository sessionRepository;
  private final ScoreService scoreService;
  private final SessionLogService sessionLogService;
  /** Reads a session (lazy blocks and lines) while its scores are logged. */
  private final TransactionTemplate readTransaction;
  /** One score or journal per transaction: a refused one never undoes the others. */
  private final TransactionTemplate ownTransaction;
  private final DemoActor actor;
  private int scores;

  DemoProgramScores(
      SessionLogRepository sessionLogRepository,
      SessionRepository sessionRepository,
      ScoreService scoreService,
      SessionLogService sessionLogService,
      PlatformTransactionManager transactionManager,
      DemoActor actor) {
    this.sessionLogRepository = sessionLogRepository;
    this.sessionRepository = sessionRepository;
    this.scoreService = scoreService;
    this.sessionLogService = sessionLogService;
    this.readTransaction = new TransactionTemplate(transactionManager);
    this.ownTransaction = new TransactionTemplate(transactionManager);
    this.ownTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    this.actor = actor;
  }

  /**
   * Logs the past sessions of a program.
   *
   * @param program The program.
   * @param gymId Its gym.
   * @param users Demo account ids by e-mail.
   * @param today The seed day: sessions before it are past.
   */
  void seed(DemoPrograms.Program program, Long gymId, Map<String, Long> users, LocalDate today) {
    if (logged(program, gymId)) {
      log.info("Demo program {}: journals already logged", program.key());
      return;
    }
    LocalDate first = DemoPrograms.firstMonday(program, today);
    List<Long> sessions =
        sessionRepository
            .findByGymIdAndTitleStartingWithAndDateBeforeOrderByDateAscIdAsc(gymId, program.titlePrefix(), today)
            .stream()
            .map(Session::getId)
            .toList();
    List<Persona> personas =
        PERSONAS.stream().filter(p -> p.program().equals(program.key()) && users.containsKey(p.email())).toList();
    scores = 0;
    for (Long sessionId : sessions) {
      try {
        readTransaction.executeWithoutResult(status -> session(sessionId, first, personas, users, gymId));
      } catch (RuntimeException e) {
        log.warn("Demo program {}: session id={} not logged ({})", program.key(), sessionId, e.getMessage());
      }
    }
    log.info("Demo program {}: {} scores on {} past sessions", program.key(), scores, sessions.size());
  }

  private boolean logged(DemoPrograms.Program program, Long gymId) {
    return sessionLogRepository.existsBySessionGymIdAndSessionTitleStartingWith(gymId, program.titlePrefix());
  }

  /** One past session: who came, their scores and journals. */
  private void session(Long sessionId, LocalDate first, List<Persona> personas, Map<String, Long> users, Long gymId) {
    Session session = sessionRepository.findById(sessionId).orElseThrow();
    LocalDate date = session.getDate();
    int week = (int) ChronoUnit.WEEKS.between(first, date) + 1;
    Random rnd = new Random(sessionId * 7919L);
    boolean competition = session.getBlocks().stream().anyMatch(b -> b.getWod() != null && b.getWod().isCompetition());
    if (competition) {
      throwdown(session, users, gymId, week, rnd);
      return;
    }
    List<Persona> came = personas.stream().filter(p -> p.attends().on(week, date.getDayOfWeek())).toList();
    for (SessionBlock block : session.getBlocks()) {
      Wod wod = block.getWod();
      if (wod == null || wod.getScoreType() == ScoreType.NONE || block.isOptional()) {
        continue;
      }
      if (wod.getTeamSize() != null) {
        // Teams in the order of arrival; the first of each team logs for all
        for (int i = 0; i < came.size(); i += wod.getTeamSize()) {
          List<Persona> team = came.subList(i, Math.min(came.size(), i + wod.getTeamSize()));
          Persona logger = team.getFirst();
          List<Long> mates = team.stream().skip(1).map(p -> users.get(p.email())).toList();
          log(logger, users, block, wod, date, week, gymId, rnd, null, mates, false);
        }
        continue;
      }
      for (Persona p : came) {
        log(p, users, block, wod, date, week, gymId, rnd, null, List.of(), false);
      }
    }
    for (Persona p : came) {
      journal(p, users, session, date);
    }
  }

  /**
   * The autumn throwdown: the judge logs (and so verifies) the scores of two athletes, two others
   * submit theirs, the second judge verifies one of them; the other stays in the queue.
   */
  private void throwdown(Session session, Map<String, Long> users, Long gymId, int week, Random rnd) {
    Persona owner = persona(OWNER, "SPARTA");
    Persona athlete = persona(ATHLETE, "SPARTA");
    Persona multi = persona(MULTI, "SPARTA");
    Persona hybrid = new Persona(HYBRID, "SPARTA", 75, 0.8, 1, (w, d) -> true);
    Long scorer = users.get(SCORER);
    Long coach = users.get(COACH);
    LocalDate date = session.getDate();
    for (SessionBlock block : session.getBlocks()) {
      Wod wod = block.getWod();
      if (wod == null || !wod.isCompetition()) {
        continue;
      }
      if (wod.getTeamSize() != null) {
        // Final by two: the judge logs one team (verified), the other team submits its score
        log(owner, users, block, wod, date, week, gymId, rnd, scorer, List.of(users.get(ATHLETE)), false);
        log(multi, users, block, wod, date, week, gymId, rnd, null, List.of(users.get(HYBRID)), true);
        continue;
      }
      log(athlete, users, block, wod, date, week, gymId, rnd, scorer, List.of(), false);
      log(multi, users, block, wod, date, week, gymId, rnd, scorer, List.of(), false);
      ScoreResponse own = log(owner, users, block, wod, date, week, gymId, rnd, null, List.of(), true);
      log(hybrid, users, block, wod, date, week, gymId, rnd, null, List.of(), true);
      if (own != null && coach != null) {
        actor.run(coach, () -> ownTransaction.executeWithoutResult(s -> scoreService.setVerified(own.id(), true)));
      }
    }
    for (Persona p : List.of(owner, athlete, multi, hybrid)) {
      journal(p, users, session, date);
    }
  }

  private static Persona persona(String email, String program) {
    return PERSONAS.stream()
        .filter(p -> p.email().equals(email) && p.program().equals(program))
        .findFirst()
        .orElseThrow();
  }

  /** "Séance faite" (found or created by the scores), optional blocks unchecked but for the Owner. */
  private void journal(Persona p, Map<String, Long> users, Session session, LocalDate date) {
    Long userId = users.get(p.email());
    actor.run(
        userId,
        () -> ownTransaction.executeWithoutResult(status -> {
          SessionLogResponse journal =
              sessionLogService.logSession(session.getId(), new SessionLogRequest(date, null), actor.principal(userId));
          if (!OWNER.equals(p.email())) {
            session.getBlocks().stream()
                .filter(SessionBlock::isOptional)
                .forEach(b -> sessionLogService.setBlockDone(journal.id(), b.getId(), false, actor.principal(userId)));
          }
        }));
  }

  /**
   * Logs one score.
   *
   * @param judge The judge logging it for the athlete, or null (the athlete logs it).
   * @param submit Submit it to the judges (competition).
   * @return The score, or null when the service refused it (logged).
   */
  private @Nullable ScoreResponse log(
      Persona p,
      Map<String, Long> users,
      SessionBlock block,
      Wod wod,
      LocalDate date,
      int week,
      Long gymId,
      Random rnd,
      @Nullable Long judge,
      List<Long> mates,
      boolean submit) {
    Long athleteId = users.get(p.email());
    if (athleteId == null) {
      return null;
    }
    boolean female = actor.principal(athleteId).getDivision() == Division.WOMEN;
    boolean scaled = hasScaled(wod) && rnd.nextDouble() < p.scales();
    Performance perf = performance(p, wod, block, week, female, scaled, rnd);
    String comment = rnd.nextDouble() < 0.06 ? COMMENTS.get(rnd.nextInt(COMMENTS.size())) : null;
    ScoreRequest request =
        new ScoreRequest(
            judge != null ? athleteId : null,
            date,
            wod.getId(),
            perf.minutes,
            perf.seconds,
            perf.rounds,
            perf.reps,
            perf.maxWeight,
            null,
            Unit.KG,
            null,
            null,
            null,
            scaled ? ScalingLevel.SCALED : ScalingLevel.RX,
            perf.capped,
            null,
            comment,
            gymId,
            null,
            block.getId(),
            perf.sets.isEmpty() ? null : perf.sets,
            scaled ? scaledLines(wod, female) : null,
            submit,
            mates.isEmpty() ? null : mates);
    try {
      ScoreResponse saved =
          actor.as(judge != null ? judge : athleteId, () -> ownTransaction.execute(s -> scoreService.logScore(request)));
      scores += 1 + mates.size();
      return saved;
    } catch (RuntimeException e) {
      log.warn("Demo score of {} on '{}' skipped ({})", p.email(), wod.getTitle(), e.getMessage());
      return null;
    }
  }

  private static boolean hasScaled(Wod wod) {
    return wod.getMovements().stream().anyMatch(l -> l.getScaled() != null);
  }

  /** The official Scaled version as the lines done otherwise (the conformity check finds it official). */
  private static List<ScoreLineRequest> scaledLines(Wod wod, boolean female) {
    List<ScoreLineRequest> lines = new ArrayList<>();
    for (WodMovement rx : wod.getMovements()) {
      if (rx.getScaled() == null) {
        continue;
      }
      WodMovement s = LineVersions.scaled(rx);
      Double weight = pick(female, s.getWeight(), s.getWeightFemale());
      Double height = pick(female, s.getHeight(), s.getHeightFemale());
      Integer calories = female && s.getCaloriesFemale() != null ? s.getCaloriesFemale() : s.getCalories();
      lines.add(
          new ScoreLineRequest(
              rx.getId(),
              s.getMovement().getId(),
              new HashSet<>(s.getEquipment()),
              new HashSet<>(s.getTechniques()),
              s.getRepsScheme(),
              weight == null || s.getWeightUnit() == null ? null : round(s.getWeightUnit().toBase(weight), 0.5),
              s.getDistance(),
              s.getDistance() == null ? null : s.getDistanceUnit(),
              calories,
              height,
              height == null ? null : s.getHeightUnit(),
              s.getDurationSeconds()));
    }
    return lines;
  }

  private static @Nullable Double pick(boolean female, @Nullable Double male, @Nullable Double woman) {
    return female && woman != null ? woman : male;
  }

  // --- Performances ---

  /** What a persona did on a WOD: a time, reps, rounds or loads. */
  private static final class Performance {
    Integer minutes;
    Integer seconds;
    Integer rounds;
    Integer reps;
    Double maxWeight;
    boolean capped;
    final List<ScoreSetRequest> sets = new ArrayList<>();
  }

  private static Performance performance(
      Persona p, Wod wod, SessionBlock block, int week, boolean female, boolean scaled, Random rnd) {
    Performance perf = new Performance();
    double noise = 0.92 + rnd.nextDouble() * 0.16;
    // Scaled athletes move like RX ones on their own version
    double engine = scaled ? Math.max(p.engine(), 0.85) : p.engine();
    int planned = block.getPlannedMinutes() != null ? block.getPlannedMinutes() : 12;
    switch (wod.getScoreType()) {
      case TIME -> {
        int cap = wod.getTimeCapSeconds() != null ? wod.getTimeCapSeconds() : planned * 60;
        int seconds = (int) Math.round(cap * 0.78 / engine * noise);
        if (seconds >= cap && wod.getTimeCapSeconds() != null) {
          perf.capped = true;
          perf.reps = (int) Math.round(planned * 9 * engine);
        } else {
          seconds = Math.min(seconds, cap - 5);
          perf.minutes = seconds / 60;
          perf.seconds = seconds % 60;
        }
      }
      case REPS -> {
        int minutes = wod.getTimeCapSeconds() != null ? wod.getTimeCapSeconds() / 60 : planned;
        int total = (int) Math.round(minutes * 13 * engine * noise);
        if (wod.getRepsPerRound() != null) {
          perf.rounds = total / wod.getRepsPerRound();
          perf.reps = total % wod.getRepsPerRound();
        } else {
          perf.reps = total;
        }
      }
      case ROUNDS -> {
        int max = wod.getEmomRounds() != null ? wod.getEmomRounds() : 10;
        perf.rounds = Math.min(max, (int) Math.round(max * engine * noise));
      }
      case WEIGHT -> weight(perf, p, wod, week, female, rnd);
      default -> perf.reps = (int) Math.round(planned * 10 * engine);
    }
    return perf;
  }

  /** A load score: an "N RM" test, or the sets of a strength block at the persona's loads. */
  private static void weight(Performance perf, Persona p, Wod wod, int week, boolean female, Random rnd) {
    List<WodMovement> lines = wod.getMovements().stream().sorted(java.util.Comparator.comparing(WodMovement::getOrderIndex)).toList();
    if (wod.getRepMax() != null || "1-1-1".equals(wod.getRepScheme())) {
      int n = wod.getRepMax() != null ? wod.getRepMax() : 1;
      double oneRm = rm(p, lines.getFirst(), week);
      perf.maxWeight = round(oneRm / (1 + n / 30.0) * (0.97 + rnd.nextDouble() * 0.05), 2.5);
      return;
    }
    Set<Integer> groupsDone = new HashSet<>();
    double top = 0;
    for (WodMovement line : lines) {
      if (line.getComplexGroup() != null && !groupsDone.add(line.getComplexGroup())) {
        continue; // a complex: its sets on its first line
      }
      Double load = load(p, line, week, female);
      if (load == null) {
        continue;
      }
      int sets = line.getSets() != null ? line.getSets() : 1;
      int reps = firstNumber(line.getRepsScheme(), 1);
      for (int i = 0; i < sets; i++) {
        // Building up to the working load on the first sets
        double w = round(i == 0 && sets > 2 ? load * 0.9 : load, 2.5);
        perf.sets.add(new ScoreSetRequest(reps, w, true, line.getId()));
        top = Math.max(top, w);
      }
    }
    perf.maxWeight = top > 0 ? top : 20.0;
  }

  /** The load of a strength line for a persona: its percent of the RM, a fixed load, or a feel. */
  private static @Nullable Double load(Persona p, WodMovement line, int week, boolean female) {
    Double fixed = pick(female, line.getWeight(), line.getWeightFemale());
    if (fixed != null && line.getWeightUnit() != null) {
      return line.getWeightUnit().toBase(fixed);
    }
    String notes = line.getNotesFr() == null ? "" : line.getNotesFr();
    double share;
    if (line.getPercentOfRm() != null) {
      share = line.getPercentOfRm() / 100;
    } else if (notes.contains("barre vide")) {
      return female ? 15.0 : 20.0;
    } else if (notes.contains("léger") || notes.contains("légère")) {
      share = 0.55;
    } else if (notes.contains("modéré")) {
      share = 0.7;
    } else if (notes.contains("lourd")) {
      share = 0.85;
    } else if (lifts(line)) {
      share = 0.75;
    } else {
      return null; // bodyweight work: no load to write
    }
    return rm(p, line, week) * share;
  }

  private static boolean lifts(WodMovement line) {
    String name = line.getMovement().getName();
    return List.of("Squat", "Deadlift", "Shoulder Press", "Jerk", "Clean", "Snatch", "Clean and Jerk", "Thruster",
            "Bench Press", "Sumo Deadlift High Pull", "Overhead Squat", "Romanian Deadlift")
        .contains(name)
        || line.getTechniques().contains("WEIGHTED");
  }

  /**
   * The persona's 1RM on a line: its back squat times the lift's usual ratio, a little higher week
   * after week (records progress over the cycle).
   */
  static double rm(Persona p, WodMovement line, int week) {
    String name = line.getMovement().getName();
    Set<String> te = line.getTechniques();
    Predicate<String> has = te::contains;
    double ratio =
        switch (name) {
          case "Squat" -> has.test("FRONT_RACK") ? 0.85 : has.test("OVERHEAD") ? 0.65 : 1.0;
          case "Deadlift" -> 1.25;
          case "Romanian Deadlift", "Stiff-Leg Deadlift" -> 0.8;
          case "Shoulder Press" -> has.test("PUSH") ? 0.62 : 0.47;
          case "Jerk" -> 0.66;
          case "Clean" -> (has.test("POWER") ? 0.62 : 0.72) * (has.test("HANG") ? 0.95 : 1);
          case "Clean and Jerk" -> 0.68;
          case "Snatch" -> (has.test("POWER") ? 0.5 : 0.57) * (has.test("HANG") ? 0.95 : 1);
          case "Thruster" -> 0.55;
          case "Bench Press" -> 0.75;
          case "Pull-up" -> 0.22; // added load of a weighted pull-up
          default -> 0.4;
        };
    return p.backSquat() * ratio * (1 + 0.004 * week);
  }

  private static int firstNumber(@Nullable String scheme, int fallback) {
    if (scheme == null) {
      return fallback;
    }
    java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\d+").matcher(scheme);
    return m.find() ? Integer.parseInt(m.group()) : fallback;
  }

  private static double round(double value, double step) {
    return Math.max(step, Math.round(value / step) * step);
  }

  /** Saturday and two other days each week, drawn with a fixed seed per week. */
  static boolean randomThreeDays(int week, DayOfWeek day) {
    if (day == DayOfWeek.SATURDAY) {
      return true;
    }
    List<DayOfWeek> days = new ArrayList<>(EnumSet.range(DayOfWeek.MONDAY, DayOfWeek.FRIDAY));
    java.util.Collections.shuffle(days, new Random(week * 31L + 7));
    return days.subList(0, 2).contains(day);
  }

  /** Monday to Saturday; every second or third week one day off, a different one each time. */
  static boolean ownerDays(int week, DayOfWeek day) {
    if (!SPARTA_DAYS.contains(day)) {
      return false;
    }
    boolean restWeek = week % 5 == 2 || week % 5 == 4;
    return !(restWeek && day.getValue() == 1 + (week * 2) % 5);
  }
}
