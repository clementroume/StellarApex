package apex.stellar.aldebaran.service;

import apex.stellar.aldebaran.dto.ProgrammingBalanceResponse;
import apex.stellar.aldebaran.dto.ProgrammingBalanceResponse.AlertResponse;
import apex.stellar.aldebaran.dto.ProgrammingBalanceResponse.AxisShare;
import apex.stellar.aldebaran.dto.ProgrammingBalanceResponse.Capabilities;
import apex.stellar.aldebaran.dto.ProgrammingBalanceResponse.DetailShare;
import apex.stellar.aldebaran.dto.ProgrammingBalanceResponse.ProfileResponse;
import apex.stellar.aldebaran.dto.ProgrammingBalanceResponse.ProfileRow;
import apex.stellar.aldebaran.dto.ProgrammingBalanceResponse.ProfileSummary;
import apex.stellar.aldebaran.dto.ProgrammingBalanceResponse.RegionResponse;
import apex.stellar.aldebaran.dto.ProgrammingBalanceResponse.UnusualSessionResponse;
import apex.stellar.aldebaran.exception.InvalidSessionException;
import apex.stellar.aldebaran.model.entities.Muscle;
import apex.stellar.aldebaran.model.entities.Session;
import apex.stellar.aldebaran.model.entities.Session.Discipline;
import apex.stellar.aldebaran.model.entities.Session.SessionType;
import apex.stellar.aldebaran.model.entities.SessionBlock;
import apex.stellar.aldebaran.model.entities.WodMovement;
import apex.stellar.aldebaran.model.enums.Category.Modality;
import apex.stellar.aldebaran.model.enums.MovementPattern;
import apex.stellar.aldebaran.model.enums.MovementPattern.Axis;
import apex.stellar.aldebaran.model.enums.TimeDomain;
import apex.stellar.aldebaran.repository.MuscleRepository;
import apex.stellar.aldebaran.repository.SessionRepository;
import apex.stellar.aldebaran.security.AldebaranUserPrincipal;
import apex.stellar.aldebaran.security.SessionSecurity;
import apex.stellar.aldebaran.service.ProgrammingAlerts.Alert;
import apex.stellar.aldebaran.service.ProgrammingAlerts.MuscleInfo;
import apex.stellar.aldebaran.service.ProgrammingBalance.Plan;
import apex.stellar.aldebaran.service.ProgrammingBalance.ProfileBalance;
import apex.stellar.aldebaran.service.ProgrammingBalance.Window;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The balance of a gym's programming for its staff (lot D', D161-D167): the prescription of its
 * CrossFit sessions over a cycle, by attendance profile, with the coach's alerts. Computed on each
 * request from the sessions (no table: the coach edits the programming while reading it).
 */
@Service
@RequiredArgsConstructor
public class ProgrammingBalanceService {

  /** Shortest cycle: one window (D163). */
  public static final int MIN_WEEKS = ProgrammingBalance.WINDOW_WEEKS;

  /** Longest cycle (D163). */
  public static final int MAX_WEEKS = 8;

  private final SessionRepository sessionRepository;
  private final MuscleRepository muscleRepository;
  private final SessionSecurity sessionSecurity;

  /**
   * The balance of a cycle.
   *
   * @param gymId The gym (rights checked by the controller: staff or admin).
   * @param day Any day of the first week (Sirius sends its Monday).
   * @param weeks Weeks of the cycle, {@link #MIN_WEEKS} to {@link #MAX_WEEKS}.
   * @param scaled Read the official Scaled versions.
   * @param optional Count the optional blocks.
   * @param days The profile to detail, or null / empty.
   * @param principal The user (capabilities).
   * @return The balance.
   */
  @Transactional(readOnly = true)
  public ProgrammingBalanceResponse getBalance(
      Long gymId,
      LocalDate day,
      int weeks,
      boolean scaled,
      boolean optional,
      @Nullable Set<DayOfWeek> days,
      AldebaranUserPrincipal principal) {
    if (weeks < MIN_WEEKS || weeks > MAX_WEEKS) {
      throw new InvalidSessionException("error.programming.weeks", MIN_WEEKS, MAX_WEEKS);
    }
    LocalDate start = day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    LocalDate end = start.plusWeeks(weeks).minusDays(1);
    LocalDate historyStart = start.minusWeeks(ProgrammingAlerts.UNUSUAL_HISTORY_WEEKS);

    Map<LocalDate, Map<DayOfWeek, WeekTotals>> vectors = new HashMap<>();
    int sessions = 0;
    int uncountedSessions = 0;
    int uncountedLines = 0;
    for (Session session : sessionRepository.findProgramming(gymId, historyStart, end)) {
      boolean inCycle = !session.getEndDate().isBefore(start) && !session.getDate().isAfter(end);
      if (!counts(session)) {
        uncountedSessions += inCycle ? 1 : 0;
        continue;
      }
      sessions += inCycle ? 1 : 0;
      LocalDate date = session.getDate();
      WeekTotals vector = new WeekTotals();
      boolean counted = false;
      for (SessionBlock block : session.getBlocks()) {
        if (block.getWod() == null || (block.isOptional() && !optional)) {
          continue;
        }
        counted = true;
        BalanceCalculator.add(
            Workout.prescribed(block.getWod(), date, block.getKind(), scaled), BalanceCalculator.NearRecord.NEVER, vector);
        if (inCycle) {
          for (WodMovement line : block.getWod().getMovements()) {
            uncountedLines += BalanceCalculator.uncounted(block.getWod(), line) != null ? 1 : 0;
          }
        }
      }
      if (!counted) {
        continue; // only text or optional blocks: not a training day of the profiles
      }
      // Two CrossFit sessions on the same day make one day (D161)
      vectors
          .computeIfAbsent(monday(date), m -> new EnumMap<>(DayOfWeek.class))
          .merge(date.getDayOfWeek(), vector, (a, b) -> BalanceCalculator.sum(List.of(a, b)));
    }

    List<LocalDate> cycle = new ArrayList<>();
    for (int w = 0; w < weeks; w++) {
      cycle.add(start.plusWeeks(w));
    }
    Map<Long, MuscleInfo> muscles = muscles();
    Plan plan = new Plan(vectors, cycle, muscles);
    Set<DayOfWeek> training = plan.trainingDays();
    List<Window> windows = ProgrammingBalance.windows(plan);

    ProfileResponse fullWeek = toResponse(ProgrammingBalance.evaluate(plan, windows, training), muscles);
    List<ProfileSummary> summaries = new ArrayList<>();
    for (int size : ProgrammingBalance.PROFILE_SIZES) {
      List<Set<DayOfWeek>> profiles = ProgrammingBalance.profiles(training, size);
      if (profiles.isEmpty()) {
        continue;
      }
      List<ProfileRow> rows = new ArrayList<>();
      for (Set<DayOfWeek> profile : profiles) {
        ProfileBalance balance = ProgrammingBalance.evaluate(plan, windows, profile);
        if (balance.inAlert() && !balance.alerts().isEmpty()) {
          rows.add(new ProfileRow(List.copyOf(balance.days()), toAlert(balance.alerts().getFirst()), balance.alerts().size()));
        }
      }
      summaries.add(new ProfileSummary(size, profiles.size(), rows));
    }
    ProfileResponse profile = null;
    if (days != null && !days.isEmpty()) {
      Set<DayOfWeek> asked = EnumSet.copyOf(days);
      profile = toResponse(ProgrammingBalance.evaluate(plan, windows, asked), muscles);
    }
    return new ProgrammingBalanceResponse(
        gymId,
        start,
        end,
        weeks,
        scaled,
        optional,
        List.copyOf(training),
        sessions,
        weeks - ProgrammingBalance.WINDOW_WEEKS + 1,
        uncountedSessions,
        uncountedLines,
        fullWeek,
        summaries,
        profile,
        concentrated(windows),
        ProgrammingAlerts.unusualSessions(plan.byDate(), start, end, muscles).stream()
            .map(u -> new UnusualSessionResponse(u.date(), u.region().name(), round(u.sets()), round(u.base())))
            .toList(),
        new Capabilities(sessionSecurity.canReorder(gymId, principal)));
  }

  /** The days of a profile: CrossFit sessions of one day; other disciplines are specialties (D161). */
  private static boolean counts(Session session) {
    return session.getType() == SessionType.WOD && session.getDiscipline() == Discipline.CROSSFIT;
  }

  private static LocalDate monday(LocalDate date) {
    return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
  }

  private Map<Long, MuscleInfo> muscles() {
    Map<Long, MuscleInfo> infos = new HashMap<>();
    for (Muscle m : muscleRepository.findAll()) {
      Muscle parent = m.getParent();
      infos.put(m.getId(), new MuscleInfo(m.getCode(), m.getRegion(), parent == null ? null : parent.getCode()));
    }
    return infos;
  }

  /** Axes concentrated on few days in any window of the cycle (D181). */
  private static List<Axis> concentrated(List<Window> windows) {
    Set<Axis> axes = EnumSet.noneOf(Axis.class);
    windows.forEach(w -> axes.addAll(w.concentrated()));
    return List.copyOf(axes);
  }

  private static ProfileResponse toResponse(ProfileBalance balance, Map<Long, MuscleInfo> muscles) {
    WeekTotals window = balance.volumes();
    Window reference = balance.window();
    boolean full = balance.isFull();
    Map<Axis, Double> axisShares = ProgrammingAlerts.axisShares(window);
    Set<Axis> judged = full ? Set.of() : reference.judged(balance.days().size());
    List<AxisShare> axes =
        axisShares.entrySet().stream()
            .map(e -> new AxisShare(
                e.getKey(), round(e.getValue()), round(reference.fullAxes().get(e.getKey())), judged.contains(e.getKey())))
            .toList();
    Map<MovementPattern, Double> fullDetails = ProgrammingAlerts.detailShares(reference.full());
    List<DetailShare> details =
        ProgrammingAlerts.detailShares(window).entrySet().stream()
            .map(e -> new DetailShare(e.getKey(), round(e.getValue()), round(fullDetails.get(e.getKey()))))
            .toList();
    List<RegionResponse> regions =
        ProgrammingAlerts.regionGaps(ProgrammingAlerts.shares(window, muscles), reference.fullRegions()).stream()
            .map(g -> new RegionResponse(g.region().name(), round(g.share()), round(g.fullShare()), g.trend().name()))
            .toList();
    Map<TimeDomain, Integer> minutes = new EnumMap<>(TimeDomain.class);
    for (TimeDomain domain : TimeDomain.values()) {
      int seconds = window.seconds().getOrDefault(domain, 0);
      minutes.put(domain, Math.round(seconds / 60f / ProgrammingBalance.WINDOW_WEEKS));
    }
    double modalitySets = window.modalities().values().stream().mapToDouble(Double::doubleValue).sum();
    Map<Modality, Double> modalities = new EnumMap<>(Modality.class);
    for (Modality m : Modality.values()) {
      double sets = window.modalities().getOrDefault(m, 0.0);
      modalities.put(m, modalitySets > 0 ? round(sets / modalitySets * 100) : 0);
    }
    return new ProfileResponse(
        List.copyOf(balance.days()),
        reference.start(),
        reference.start().plusWeeks(ProgrammingBalance.WINDOW_WEEKS).minusDays(1),
        balance.inAlert(),
        axes,
        details,
        regions,
        balance.alerts().stream().map(ProgrammingBalanceService::toAlert).toList(),
        minutes,
        modalities);
  }

  private static AlertResponse toAlert(Alert alert) {
    return new AlertResponse(alert.kind().name(), alert.subject(), round(alert.value()), round(alert.limit()));
  }

  private static double round(double value) {
    return Math.round(value * 100) / 100.0;
  }
}
