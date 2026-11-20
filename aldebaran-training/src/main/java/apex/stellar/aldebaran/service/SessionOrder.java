package apex.stellar.aldebaran.service;

import apex.stellar.aldebaran.model.enums.Category.Modality;
import apex.stellar.aldebaran.model.enums.HeavyDays;
import apex.stellar.aldebaran.model.enums.OrderMode;
import apex.stellar.aldebaran.model.enums.TimeDomain;
import apex.stellar.aldebaran.service.ProgrammingAlerts.MuscleInfo;
import apex.stellar.aldebaran.service.ProgrammingBalance.Plan;
import apex.stellar.aldebaran.service.ProgrammingBalance.ProfileBalance;
import apex.stellar.aldebaran.service.ProgrammingBalance.Window;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.stream.IntStream;
import org.jspecify.annotations.Nullable;

/**
 * The session-order proposal of the coach's programming (lot D', D168-D171): within each week of a
 * cycle, the order of the sessions' parts that leaves the fewest attendance profiles in alert. Pure:
 * a cycle is weeks of training days, each holding a Skill part (its Skill block) and a WOD part (its
 * WOD, Accessory and Finisher blocks), each with its balance vector; nothing is read or written here.
 *
 * <p>Modes (D168): the WOD parts move and the Skills stay; whole sessions move (Skill and WOD
 * together); or Skills and WODs move separately, searched in turn until stable. A locked day (past,
 * journal, scores, competition) never changes, a pinned part stays on its day. When the Skills move,
 * two strength days in a row are refused (strict) or allowed only without a main group agonist in
 * both (soft, D169). The search is exhaustive within a week (at most 6! orders a step), week after
 * week until stable; the order is compared on, in turn (D170, D171): profiles in alert (3, 4 and 5
 * days, any 4-week window), total excess over the thresholds, pairs of consecutive days in the same
 * metcon time domain or the same dominant modality, parts moved. Strength days in a row: never more
 * than the coach programmed.
 */
public final class SessionOrder {

  /** Passes over the weeks before giving up on stability. */
  static final int MAX_PASSES = 3;

  /**
   * A movable part of a training day: its Skill, or its WOD part.
   *
   * @param key A stable identity (the caller's blocks).
   * @param vector Its balance vector.
   * @param pinned The coach keeps it on its day.
   * @param strength A Skill counted as strength (For Load, or scored by weight or load, D169).
   * @param agonistGroups Main groups where it has an agonist role (D169, soft option).
   */
  public record Part(
      String key, WeekTotals vector, boolean pinned, boolean strength, Set<String> agonistGroups) {}

  /**
   * A training day as programmed.
   *
   * @param day The day of the week.
   * @param skill Its Skill part, or null.
   * @param wod Its WOD part, or null.
   * @param locked Never changes (past, journal, scores, competition, two sessions that day).
   */
  public record Day(DayOfWeek day, @Nullable Part skill, @Nullable Part wod, boolean locked) {}

  /**
   * A week of the cycle.
   *
   * @param monday Its Monday.
   * @param days Its training days, in calendar order.
   */
  public record Week(LocalDate monday, List<Day> days) {}

  /**
   * How good an order is, compared field after field.
   *
   * @param profilesInAlert Profiles of 3, 4 and 5 days in alert on any window.
   * @param excess Total distance beyond the thresholds, over every profile and window.
   * @param sameDomain Pairs of consecutive days with the same metcon time domain or dominant
   *     modality (D171).
   * @param moves Parts away from their day.
   */
  public record Score(int profilesInAlert, double excess, int sameDomain, int moves) {

    static final Comparator<Score> ORDER =
        Comparator.comparingInt(Score::profilesInAlert)
            .thenComparingDouble(s -> Math.round(s.excess() * 1000) / 1000.0)
            .thenComparingInt(Score::sameDomain)
            .thenComparingInt(Score::moves);
  }

  /**
   * The proposal.
   *
   * @param weeks Each week's days with the parts they hold in the proposed order.
   * @param before The programming as it is.
   * @param after The proposed order.
   */
  public record Result(List<Week> weeks, Score before, Score after) {}

  private SessionOrder() {}

  /**
   * Proposes an order.
   *
   * @param weeks The cycle, at least one window ({@link ProgrammingBalance#WINDOW_WEEKS} weeks).
   * @param muscles The muscles, by id.
   * @param mode What may move.
   * @param heavy Two strength days in a row, when the Skills move.
   * @param dayBefore The Skill of the day before the cycle when it was trained, or null.
   * @return The proposal (the same days when nothing better exists).
   */
  public static Result propose(
      List<Week> weeks, Map<Long, MuscleInfo> muscles, OrderMode mode, HeavyDays heavy, @Nullable Part dayBefore) {
    State state = new State(weeks, muscles, mode, heavy, dayBefore);
    Score before = state.score();
    Score best = before;
    for (int pass = 0; pass < MAX_PASSES; pass++) {
      boolean improved = false;
      for (int w = 0; w < weeks.size(); w++) {
        for (Kind kind : mode == OrderMode.WOD_AND_SKILLS ? List.of(Kind.WOD, Kind.SKILL) : List.of(kindOf(mode))) {
          Score found = state.bestWeek(w, kind);
          if (Score.ORDER.compare(found, best) < 0) {
            best = found;
            improved = true;
          }
        }
      }
      if (!improved) {
        break;
      }
    }
    return new Result(state.weeks(), before, state.score());
  }

  private static Kind kindOf(OrderMode mode) {
    return mode == OrderMode.WHOLE_SESSIONS ? Kind.SESSION : Kind.WOD;
  }

  /** What one search step permutes. */
  private enum Kind {
    WOD,
    SKILL,
    SESSION
  }

  /** The current order, its plan and the windows reused between candidates. */
  private static final class State {
    private final List<Week> original;
    private final Map<Long, MuscleInfo> muscles;
    private final OrderMode mode;
    private final HeavyDays heavy;
    private final @Nullable Part dayBefore;
    /** Current parts: week, then day index: [skill, wod]. */
    private final Part[][][] parts;
    private final List<LocalDate> cycle;

    State(List<Week> weeks, Map<Long, MuscleInfo> muscles, OrderMode mode, HeavyDays heavy, @Nullable Part dayBefore) {
      this.original = weeks;
      this.muscles = muscles;
      this.mode = mode;
      this.heavy = heavy;
      this.dayBefore = dayBefore;
      this.parts = new Part[weeks.size()][][];
      for (int w = 0; w < weeks.size(); w++) {
        List<Day> days = weeks.get(w).days();
        parts[w] = new Part[days.size()][];
        for (int d = 0; d < days.size(); d++) {
          parts[w][d] = new Part[] {days.get(d).skill(), days.get(d).wod()};
        }
      }
      this.cycle = weeks.stream().map(Week::monday).toList();
    }

    List<Week> weeks() {
      List<Week> out = new ArrayList<>();
      for (int w = 0; w < original.size(); w++) {
        List<Day> days = new ArrayList<>();
        List<Day> was = original.get(w).days();
        for (int d = 0; d < was.size(); d++) {
          days.add(new Day(was.get(d).day(), parts[w][d][0], parts[w][d][1], was.get(d).locked()));
        }
        out.add(new Week(original.get(w).monday(), days));
      }
      return out;
    }

    /** The score of the current order. */
    Score score() {
      Plan plan = plan(parts);
      List<Window> windows = ProgrammingBalance.windows(plan);
      Partial all = Partial.of(plan, windows);
      return new Score(all.inAlert(), all.excess(), sameDomain(parts), moves(parts));
    }

    /**
     * Searches the best order of one week for one kind of part, keeps it, and returns the score of
     * the whole order.
     */
    Score bestWeek(int w, Kind kind) {
      List<Integer> free = freeDays(w, kind);
      if (free.size() < 2) {
        return score();
      }
      Plan plan = plan(parts);
      List<Window> windows = ProgrammingBalance.windows(plan);
      LocalDate monday = cycle.get(w);
      List<Window> kept = windows.stream().filter(win -> !win.mondays().contains(monday)).toList();
      List<Integer> touched =
          IntStream.range(0, windows.size()).filter(i -> windows.get(i).mondays().contains(monday)).boxed().toList();
      // The windows kept are read concurrently below: their lazy caches filled first
      for (Window win : kept) {
        ProgrammingBalance.PROFILE_SIZES.forEach(win::judged);
      }
      Partial fixed = kept.isEmpty() ? Partial.NONE : Partial.of(plan, kept);
      Part[][] current = parts[w];
      List<int[]> orders = permutations(free.size());
      Candidate best =
          orders.parallelStream()
              .map(order -> candidate(w, kind, free, current, order, touched, fixed))
              .filter(c -> c != null)
              .min(Comparator.comparing(Candidate::score, Score.ORDER))
              .orElse(null);
      if (best != null && Score.ORDER.compare(best.score(), score()) < 0) {
        parts[w] = best.week();
      }
      return score();
    }

    private @Nullable Candidate candidate(
        int w, Kind kind, List<Integer> free, Part[][] current, int[] order, List<Integer> touched, Partial fixed) {
      Part[][] week = new Part[current.length][];
      for (int d = 0; d < current.length; d++) {
        week[d] = current[d].clone();
      }
      for (int i = 0; i < free.size(); i++) {
        int to = free.get(i);
        int from = free.get(order[i]);
        if (kind != Kind.WOD) {
          week[to][0] = current[from][0];
        }
        if (kind != Kind.SKILL) {
          week[to][1] = current[from][1];
        }
      }
      Part[][][] all = parts.clone();
      all[w] = week;
      // Skills moved: never more strength days in a row than the coach programmed (D169)
      if (kind != Kind.WOD && heavyPairs(all, w) > heavyPairs(parts, w)) {
        return null;
      }
      Plan plan = plan(all);
      List<Window> windows = ProgrammingBalance.windows(plan);
      List<Window> changed = touched.stream().map(windows::get).toList();
      Partial partial = Partial.of(plan, changed).with(fixed);
      return new Candidate(week, new Score(partial.inAlert(), partial.excess(), sameDomain(all), moves(all)));
    }

    /** Days of the week whose part of that kind may move: not locked, not pinned, present. */
    private List<Integer> freeDays(int w, Kind kind) {
      List<Integer> free = new ArrayList<>();
      List<Day> days = original.get(w).days();
      for (int d = 0; d < days.size(); d++) {
        if (days.get(d).locked()) {
          continue;
        }
        Part skill = parts[w][d][0];
        Part wod = parts[w][d][1];
        boolean ok =
            switch (kind) {
              case WOD -> wod != null && !wod.pinned();
              case SKILL -> skill != null && !skill.pinned();
              case SESSION -> wod != null && skill != null && !wod.pinned() && !skill.pinned();
            };
        if (ok) {
          free.add(d);
        }
      }
      return free;
    }

    private Plan plan(Part[][][] all) {
      Map<LocalDate, Map<DayOfWeek, WeekTotals>> days = new HashMap<>();
      for (int w = 0; w < original.size(); w++) {
        Map<DayOfWeek, WeekTotals> week = new EnumMap<>(DayOfWeek.class);
        List<Day> was = original.get(w).days();
        for (int d = 0; d < was.size(); d++) {
          List<WeekTotals> vectors = new ArrayList<>();
          for (Part p : all[w][d]) {
            if (p != null) {
              vectors.add(p.vector());
            }
          }
          week.put(was.get(d).day(), BalanceCalculator.sum(vectors));
        }
        days.put(original.get(w).monday(), week);
      }
      return new Plan(days, cycle, muscles);
    }

    /**
     * Pairs of strength Skills on consecutive calendar days around a week (D169): any such pair when
     * strict, a pair sharing a main agonist group when soft.
     */
    private int heavyPairs(Part[][][] all, int w) {
      List<Strength> line = new ArrayList<>();
      if (dayBefore != null) {
        line.add(new Strength(cycle.getFirst().minusDays(1), dayBefore));
      }
      for (int week = Math.max(0, w - 1); week <= Math.min(original.size() - 1, w + 1); week++) {
        List<Day> was = original.get(week).days();
        for (int d = 0; d < was.size(); d++) {
          Part skill = all[week][d][0];
          if (skill != null && skill.strength()) {
            line.add(new Strength(cycle.get(week).plusDays(was.get(d).day().ordinal()), skill));
          }
        }
      }
      line.sort(Comparator.comparing(Strength::date));
      int pairs = 0;
      for (int i = 1; i < line.size(); i++) {
        Strength a = line.get(i - 1);
        Strength b = line.get(i);
        if (!a.part().strength() || !b.date().equals(a.date().plusDays(1))) {
          continue;
        }
        if (heavy == HeavyDays.STRICT || !Collections.disjoint(a.part().agonistGroups(), b.part().agonistGroups())) {
          pairs++;
        }
      }
      return pairs;
    }

    /** Consecutive training days whose WOD parts share their metcon domain or dominant modality (D171). */
    private int sameDomain(Part[][][] all) {
      int pairs = 0;
      for (int w = 0; w < original.size(); w++) {
        List<Day> was = original.get(w).days();
        for (int d = 1; d < was.size(); d++) {
          if (was.get(d).day().ordinal() != was.get(d - 1).day().ordinal() + 1) {
            continue;
          }
          Part a = all[w][d - 1][1];
          Part b = all[w][d][1];
          if (a == null || b == null) {
            continue;
          }
          TimeDomain da = domain(a.vector());
          Modality ma = modality(a.vector());
          if ((da != null && da == domain(b.vector())) || (ma != null && ma == modality(b.vector()))) {
            pairs++;
          }
        }
      }
      return pairs;
    }

    private int moves(Part[][][] all) {
      int moved = 0;
      for (int w = 0; w < original.size(); w++) {
        List<Day> was = original.get(w).days();
        for (int d = 0; d < was.size(); d++) {
          moved += all[w][d][0] != was.get(d).skill() ? 1 : 0;
          moved += all[w][d][1] != was.get(d).wod() ? 1 : 0;
        }
      }
      return moved;
    }
  }

  /** A strength Skill on a day (D169). */
  private record Strength(LocalDate date, Part part) {}

  /** A week candidate with the score of the whole order. */
  private record Candidate(Part[][] week, Score score) {}

  /** Profiles in alert and excess over some windows, mergeable with other windows. */
  private record Partial(Map<Set<DayOfWeek>, Boolean> alerted, double excess) {

    static final Partial NONE = new Partial(Map.of(), 0);

    static Partial of(Plan plan, List<Window> windows) {
      Map<Set<DayOfWeek>, Boolean> alerted = new HashMap<>();
      double excess = 0;
      if (windows.isEmpty()) {
        return NONE;
      }
      SortedSet<DayOfWeek> training = plan.trainingDays();
      for (int size : ProgrammingBalance.PROFILE_SIZES) {
        for (Set<DayOfWeek> profile : ProgrammingBalance.profiles(training, size)) {
          ProfileBalance balance = ProgrammingBalance.evaluate(plan, windows, profile);
          alerted.put(profile, balance.inAlert());
          excess += balance.excess();
        }
      }
      return new Partial(alerted, excess);
    }

    Partial with(Partial other) {
      Map<Set<DayOfWeek>, Boolean> merged = new HashMap<>(alerted);
      other.alerted().forEach((k, v) -> merged.merge(k, v, Boolean::logicalOr));
      return new Partial(merged, excess + other.excess());
    }

    int inAlert() {
      return (int) alerted.values().stream().filter(Boolean::booleanValue).count();
    }
  }

  private static @Nullable TimeDomain domain(WeekTotals vector) {
    return vector.seconds().entrySet().stream()
        .filter(e -> e.getValue() > 0)
        .max(Map.Entry.comparingByValue())
        .map(Map.Entry::getKey)
        .orElse(null);
  }

  private static @Nullable Modality modality(WeekTotals vector) {
    return vector.modalities().entrySet().stream()
        .filter(e -> e.getValue() > 0)
        .max(Map.Entry.comparingByValue())
        .map(Map.Entry::getKey)
        .orElse(null);
  }

  /** Every order of n items (n! arrays), the identity first. */
  static List<int[]> permutations(int n) {
    List<int[]> out = new ArrayList<>();
    permute(new int[n], new HashSet<>(), 0, n, out);
    return out;
  }

  private static void permute(int[] current, Set<Integer> used, int i, int n, List<int[]> out) {
    if (i == n) {
      out.add(current.clone());
      return;
    }
    for (int k = 0; k < n; k++) {
      if (used.add(k)) {
        current[i] = k;
        permute(current, used, i + 1, n, out);
        used.remove(k);
      }
    }
  }
}
