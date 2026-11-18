package apex.stellar.aldebaran.service;

import apex.stellar.aldebaran.model.entities.Muscle.MuscleRegion;
import apex.stellar.aldebaran.model.enums.MovementPattern.Axis;
import apex.stellar.aldebaran.service.ProgrammingAlerts.Alert;
import apex.stellar.aldebaran.service.ProgrammingAlerts.MuscleInfo;
import apex.stellar.aldebaran.service.RandomAttendance.Choice;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import org.jspecify.annotations.Nullable;

/**
 * The balance of a programming by attendance profile (lot D', D163, D164, D174-D181). Pure: a
 * programming is the vector of each day of each week ({@link BalanceCalculator#vectorOf} of its
 * prescribed blocks), a profile the days an athlete comes every week, a window 4 weeks of the
 * cycle. Linear sums only, so the session-order search (lot D', PR 2) can call it on many orders.
 */
public final class ProgrammingBalance {

  /** Weeks of a window, as for the athlete (D84, D125). */
  public static final int WINDOW_WEEKS = 4;

  /** Profile sizes evaluated, in days per week (D164, D180: no 2-day profile). */
  public static final List<Integer> PROFILE_SIZES = List.of(3, 4, 5);

  /**
   * A programming: the vector of each day, the weeks of the cycle and those read before it.
   *
   * @param days Vector per Monday and day; a day without a counted session is absent.
   * @param cycle The Mondays of the cycle, in order (at least {@link #WINDOW_WEEKS}).
   * @param muscles The muscles, by id.
   */
  public record Plan(
      Map<LocalDate, Map<DayOfWeek, WeekTotals>> days,
      List<LocalDate> cycle,
      Map<Long, MuscleInfo> muscles) {

    /**
     * The days of the week with a counted session in at least one week of the cycle.
     *
     * @return Those days, Monday first.
     */
    public SortedSet<DayOfWeek> trainingDays() {
      SortedSet<DayOfWeek> found = new TreeSet<>();
      for (LocalDate monday : cycle) {
        found.addAll(days.getOrDefault(monday, Map.of()).keySet());
      }
      return found;
    }

    /**
     * The volumes of a profile over one week.
     *
     * @param monday The week.
     * @param profile The days.
     * @return The sum of those days' vectors.
     */
    public WeekTotals week(LocalDate monday, Set<DayOfWeek> profile) {
      Map<DayOfWeek, WeekTotals> week = days.getOrDefault(monday, Map.of());
      List<WeekTotals> vectors = new ArrayList<>();
      profile.forEach(
          day -> {
            WeekTotals v = week.get(day);
            if (v != null) {
              vectors.add(v);
            }
          });
      return BalanceCalculator.sum(vectors);
    }

    /**
     * The vector of every day with a counted session, by date (history included).
     *
     * @return Day vectors.
     */
    public Map<LocalDate, WeekTotals> byDate() {
      Map<LocalDate, WeekTotals> out = new HashMap<>();
      days.forEach((monday, week) -> week.forEach((day, v) -> out.put(monday.plusDays(day.ordinal()), v)));
      return out;
    }
  }

  /**
   * The full week over one window of the cycle, the reference of every profile (D174), with the
   * axes regular enough to judge for each profile size (D181), computed once and on demand.
   */
  public static final class Window {
    private final Plan plan;
    private final List<LocalDate> mondays;
    private final WeekTotals full;
    private final int trainedWeeks;
    private final Map<Axis, Double> fullAxes;
    private final Map<Integer, Set<Axis>> judged = new HashMap<>();
    private @Nullable Set<Axis> concentrated;

    Window(Plan plan, List<LocalDate> mondays) {
      this.plan = plan;
      this.mondays = mondays;
      Set<DayOfWeek> training = plan.trainingDays();
      List<WeekTotals> weeks = mondays.stream().map(m -> plan.week(m, training)).toList();
      this.full = BalanceCalculator.sum(weeks);
      this.trainedWeeks = (int) weeks.stream().filter(w -> !w.isEmpty()).count();
      this.fullAxes = ProgrammingAlerts.axisShares(full);
    }

    /** @return Monday of the window. */
    public LocalDate start() {
      return mondays.getFirst();
    }

    /** @return The Mondays of the window. */
    public List<LocalDate> mondays() {
      return mondays;
    }

    /** @return The full week's volumes over the window. */
    public WeekTotals full() {
      return full;
    }

    /** @return Weeks of the window with a counted session. */
    public int trainedWeeks() {
      return trainedWeeks;
    }

    /** @return The full week's axis shares. */
    public Map<Axis, Double> fullAxes() {
      return fullAxes;
    }

    /**
     * The full week's region shares.
     *
     * @return Shares per region.
     */
    public Map<MuscleRegion, Double> fullRegions() {
      return ProgrammingAlerts.shares(full, plan.muscles());
    }

    /**
     * The axes judged for profiles of a size (D181): above the floor, and missed by an athlete
     * coming that many days at random less often than {@link ProgrammingAlerts#REGULAR_MAX_MISS}.
     *
     * @param size Days per week.
     * @return The axes judged.
     */
    public Set<Axis> judged(int size) {
      return judged.computeIfAbsent(size, s -> regular(List.of(s), true));
    }

    /**
     * Axes above the floor that an athlete without fixed days (3, 4 or 5 days, any of them, each
     * week) misses at least {@link ProgrammingAlerts#REGULAR_MAX_MISS} of the time: the axis sits on
     * few days (information, D181).
     *
     * @return The concentrated axes.
     */
    public Set<Axis> concentrated() {
      if (concentrated == null) {
        concentrated = regular(PROFILE_SIZES, false);
      }
      return concentrated;
    }

    /**
     * How often an athlete coming on random days, a number of days drawn among the sizes each week,
     * falls under {@link ProgrammingAlerts#PATTERN_GAP} times the full-week share of an axis.
     *
     * @param sizes Days per week, equally likely.
     * @param axis The axis.
     * @return The probability, 0 to 1.
     */
    double miss(List<Integer> sizes, Axis axis) {
      double limit = ProgrammingAlerts.PATTERN_GAP * fullAxes.get(axis);
      return RandomAttendance.missProbability(choices(sizes), axis, limit);
    }

    private Set<Axis> regular(List<Integer> sizes, boolean wanted) {
      Set<Axis> out = EnumSet.noneOf(Axis.class);
      for (Axis axis : Axis.values()) {
        if (!ProgrammingAlerts.aboveFloor(fullAxes, axis)) {
          continue;
        }
        boolean regular = miss(sizes, axis) < ProgrammingAlerts.REGULAR_MAX_MISS;
        if (regular == wanted) {
          out.add(axis);
        }
      }
      return out;
    }

    /** Each week's ways to come: every set of a size among the training days, sizes equally likely. */
    private List<List<Choice>> choices(List<Integer> sizes) {
      List<DayOfWeek> training = List.copyOf(plan.trainingDays());
      List<Integer> usable = sizes.stream().filter(s -> s >= 1 && s <= training.size()).toList();
      if (usable.isEmpty()) {
        usable = List.of(training.size());
      }
      List<List<Choice>> weeks = new ArrayList<>();
      for (LocalDate monday : mondays) {
        Map<DayOfWeek, WeekTotals> week = plan.days().getOrDefault(monday, Map.of());
        List<Choice> own = new ArrayList<>();
        for (int size : usable) {
          List<Set<DayOfWeek>> sets = combinations(training, size);
          for (Set<DayOfWeek> days : sets) {
            double[] axes = new double[Axis.values().length];
            for (DayOfWeek day : days) {
              WeekTotals v = week.get(day);
              if (v != null) {
                v.axes().forEach((axis, s) -> axes[axis.ordinal()] += s);
              }
            }
            own.add(new Choice(1.0 / usable.size() / sets.size(), axes));
          }
        }
        weeks.add(own);
      }
      return weeks;
    }
  }

  /**
   * The balance of a profile.
   *
   * @param days Its days.
   * @param window The window shown: the worst one, else the last.
   * @param volumes The profile's volumes over that window.
   * @param trainedWeeks Weeks of that window with something to do.
   * @param alerts The alerts shown (at most 3).
   * @param inAlert Whether any window of the cycle raises an alert.
   * @param excess Sum of the alerts' distance beyond their threshold over every window (the
   *     session-order search compares it).
   */
  public record ProfileBalance(
      Set<DayOfWeek> days,
      Window window,
      WeekTotals volumes,
      int trainedWeeks,
      List<Alert> alerts,
      boolean inAlert,
      double excess) {

    /** @return Whether this is the full week (no pattern gap: it is the reference). */
    public boolean isFull() {
      return days.equals(EnumSet.copyOf(window.plan.trainingDays()));
    }
  }

  private ProgrammingBalance() {}

  /**
   * Every profile of a size among the training days, in calendar order. No profile when the box
   * trains on that many days or fewer: it is the full week.
   *
   * @param trainingDays The training days.
   * @param size Days per week.
   * @return The profiles.
   */
  public static List<Set<DayOfWeek>> profiles(Collection<DayOfWeek> trainingDays, int size) {
    List<DayOfWeek> days = new ArrayList<>(new TreeSet<>(trainingDays));
    if (size >= days.size()) {
      return new ArrayList<>();
    }
    return combinations(days, size);
  }

  private static List<Set<DayOfWeek>> combinations(List<DayOfWeek> days, int size) {
    List<Set<DayOfWeek>> out = new ArrayList<>();
    combine(days, size, 0, new ArrayList<>(), out);
    return out;
  }

  private static void combine(
      List<DayOfWeek> days, int size, int from, List<DayOfWeek> current, List<Set<DayOfWeek>> out) {
    if (current.size() == size) {
      Set<DayOfWeek> set = EnumSet.noneOf(DayOfWeek.class);
      set.addAll(current);
      out.add(set);
      return;
    }
    for (int i = from; i < days.size(); i++) {
      current.add(days.get(i));
      combine(days, size, i + 1, current, out);
      current.removeLast();
    }
  }

  /**
   * The windows of 4 weeks of the cycle, each with its full-week reference (D163).
   *
   * @param plan The programming.
   * @return The windows, in order.
   */
  public static List<Window> windows(Plan plan) {
    List<Window> windows = new ArrayList<>();
    List<LocalDate> cycle = plan.cycle();
    for (int start = 0; start + WINDOW_WEEKS <= cycle.size(); start++) {
      windows.add(new Window(plan, List.copyOf(cycle.subList(start, start + WINDOW_WEEKS))));
    }
    return windows;
  }

  /**
   * Evaluates a profile over the cycle, window by window: pattern gaps against the full week of the
   * same window on the axes judged for its size (none for the full week itself), and the group
   * minimum.
   *
   * @param plan The programming.
   * @param windows Its windows ({@link #windows}).
   * @param profile The days; the training days for the full week.
   * @return The balance of the profile.
   */
  public static ProfileBalance evaluate(Plan plan, List<Window> windows, Set<DayOfWeek> profile) {
    Set<DayOfWeek> training = plan.trainingDays();
    Set<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
    days.addAll(profile.isEmpty() ? training : profile);
    boolean isFull = days.containsAll(training);
    double excess = 0;
    boolean inAlert = false;
    @Nullable Window worst = null;
    List<Alert> worstAlerts = List.of();
    WeekTotals worstVolumes = new WeekTotals();
    int worstTrained = 0;
    double worstExcess = -1;
    for (Window window : windows) {
      List<WeekTotals> own = window.mondays().stream().map(m -> plan.week(m, days)).toList();
      WeekTotals volumes = BalanceCalculator.sum(own);
      int trained = (int) own.stream().filter(w -> !w.isEmpty()).count();
      List<Alert> alerts = new ArrayList<>(
          ProgrammingAlerts.groupMinimum(volumes, WINDOW_WEEKS, trained, plan.muscles()));
      if (!isFull) {
        alerts.addAll(
            ProgrammingAlerts.patternGaps(
                ProgrammingAlerts.axisShares(volumes), window.fullAxes(), window.judged(days.size())));
      }
      double windowExcess = alerts.stream().mapToDouble(a -> a.severity() - 1).sum();
      excess += windowExcess;
      inAlert |= !alerts.isEmpty();
      // The worst window, else the last one
      if (windowExcess > worstExcess || (windowExcess == worstExcess && windowExcess == 0)) {
        worstExcess = windowExcess;
        worst = window;
        worstAlerts = alerts;
        worstVolumes = volumes;
        worstTrained = trained;
      }
    }
    if (worst == null) {
      throw new IllegalArgumentException("A cycle has at least one window");
    }
    return new ProfileBalance(
        days, worst, worstVolumes, worstTrained, ProgrammingAlerts.top(worstAlerts), inAlert, excess);
  }
}
