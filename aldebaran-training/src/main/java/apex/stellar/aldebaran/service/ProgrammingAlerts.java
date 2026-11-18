package apex.stellar.aldebaran.service;

import apex.stellar.aldebaran.model.entities.Muscle.MuscleRegion;
import apex.stellar.aldebaran.model.enums.MovementPattern;
import apex.stellar.aldebaran.model.enums.MovementPattern.Axis;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * The coach's alerts and information on a programming (lot D', D173-D181). Pure: the volumes come
 * as {@link WeekTotals} (sets per pattern axis, moving sets per muscle id). Never shown to an
 * athlete (D89).
 *
 * <p>Two alerts only (D180): a <b>pattern gap</b>, an axis of a profile under {@link #PATTERN_GAP}
 * times its share in the coach's full week (relative to the coach, never a norm), and the
 * <b>group minimum</b>, the one absolute threshold (Pelland 2026). Regions compared with the full
 * week and the unusual session are information. Every constant is provisional, calibrated during
 * the beta (review: {@code docs-private/programmation/revue-seuils-lot-d-prime.md}).
 */
public final class ProgrammingAlerts {

  /** A profile's axis under this multiple of its full-week share is in alert (D174, D176). */
  public static final double PATTERN_GAP = 0.6;

  /** An axis is judged only when its full-week share reaches this percent (D176, D181). */
  public static final double PATTERN_FLOOR = 5.0;

  /**
   * An axis is judged only when an athlete coming the same number of days at random misses it
   * (under {@link #PATTERN_GAP}) less often than this (regularity, D181).
   */
  public static final double REGULAR_MAX_MISS = 0.10;

  /** Fewer moving sets per week on average for a main group raises an alert (D180, Pelland 2026). */
  public static final double GROUP_MIN_SETS = 4.0;

  /** Main groups checked against {@link #GROUP_MIN_SETS}, by {@code muscles.code} (D180). */
  public static final List<String> MAIN_GROUPS =
      List.of(
          "quadriceps-femoris",
          "musculi-ischiocrurales",
          "musculi-glutei",
          "latissimus-dorsi",
          "pectoralis-major",
          "deltoideus",
          "triceps-brachii");

  /** The minimum applies only when at least this many weeks of the window were trained. */
  public static final int MIN_TRAINED_WEEKS = 3;

  /** At most this many alerts per profile, the furthest first. */
  public static final int MAX_ALERTS = 3;

  /** A region of a profile at most this multiple of its full-week share is behind (information). */
  public static final double REGION_BEHIND = 0.8;

  /** A region of a profile above this multiple of its full-week share is ahead (information). */
  public static final double REGION_AHEAD = 1.25;

  /** A session's region above this multiple of its biggest session before is unusual (D173, D180). */
  public static final double UNUSUAL_RATIO = 1.3;

  /** Weeks before a session it is compared with; each must hold a counted session (D180). */
  public static final int UNUSUAL_HISTORY_WEEKS = 4;

  /** The unusual session is judged only above this many sets of the region's biggest session. */
  public static final double UNUSUAL_MIN_BASE = 3;

  /** Kind of alert. */
  public enum Kind {
    /** A pattern axis of a profile falls far under its full-week share. */
    PATTERN_GAP,
    /** A main group gets too few sets per week to progress. */
    GROUP_MINIMUM
  }

  /** Where a region of a profile stands against the full week (information). */
  public enum Trend {
    BEHIND,
    IN_LINE,
    AHEAD
  }

  /**
   * What the rules need to know of a muscle.
   *
   * @param code Its frozen code.
   * @param region Its region.
   * @param groupCode The code of its functional group (parent), or null.
   */
  public record MuscleInfo(String code, MuscleRegion region, @Nullable String groupCode) {}

  /**
   * One alert.
   *
   * @param kind Its kind.
   * @param subject The axis ({@link Axis} name) or the group's muscle code.
   * @param value The profile's share of the axis (percent), or the average sets per week.
   * @param limit The threshold crossed: {@link #PATTERN_GAP} times the full-week share, or {@link
   *     #GROUP_MIN_SETS}.
   * @param severity How far under: limit / value (above 1).
   */
  public record Alert(Kind kind, String subject, double value, double limit, double severity) {}

  /**
   * A region of a profile against the full week (information, D180).
   *
   * @param region The region.
   * @param share Its share of the profile's moving volume, in percent.
   * @param fullShare Its share in the full week.
   * @param trend Behind, in line or ahead.
   */
  public record RegionGap(MuscleRegion region, double share, double fullShare, Trend trend) {}

  /**
   * A session whose region is unusual against the weeks before (information, D173).
   *
   * @param date The day.
   * @param region The region.
   * @param sets Its moving sets that day.
   * @param base Its biggest day over the weeks before.
   */
  public record UnusualSession(LocalDate date, MuscleRegion region, double sets, double base) {}

  private ProgrammingAlerts() {}

  // -------------------------------------------------------------------------
  // Shares
  // -------------------------------------------------------------------------

  /**
   * Share of each pattern axis, in percent of the sets of every axis (a compound movement counts
   * on each of its axes, so the shares are taken among axes and add up to 100).
   *
   * @param totals The volumes.
   * @return Shares per axis, every axis present (0 for an empty window).
   */
  public static Map<Axis, Double> axisShares(WeekTotals totals) {
    double sum = totals.axes().values().stream().mapToDouble(Double::doubleValue).sum();
    Map<Axis, Double> shares = new EnumMap<>(Axis.class);
    for (Axis axis : Axis.values()) {
      shares.put(axis, sum > 0 ? totals.axes().getOrDefault(axis, 0.0) / sum * 100 : 0);
    }
    return shares;
  }

  /**
   * Share of each pattern detail, in percent of the same sum as {@link #axisShares}: a line with two
   * details of one axis counts on both, so the details of an axis may add up to more than it.
   *
   * @param totals The volumes.
   * @return Shares per detail, every detail present.
   */
  public static Map<MovementPattern, Double> detailShares(WeekTotals totals) {
    double sum = totals.axes().values().stream().mapToDouble(Double::doubleValue).sum();
    Map<MovementPattern, Double> shares = new EnumMap<>(MovementPattern.class);
    for (MovementPattern pattern : MovementPattern.values()) {
      shares.put(pattern, sum > 0 ? totals.patterns().getOrDefault(pattern, 0.0) / sum * 100 : 0);
    }
    return shares;
  }

  /**
   * Moving sets per region.
   *
   * @param totals The volumes.
   * @param muscles The muscles, by id.
   * @return Sets per region, every region present.
   */
  public static Map<MuscleRegion, Double> regionSets(WeekTotals totals, Map<Long, MuscleInfo> muscles) {
    Map<MuscleRegion, Double> sets = new EnumMap<>(MuscleRegion.class);
    for (MuscleRegion region : MuscleRegion.values()) {
      sets.put(region, 0.0);
    }
    totals
        .muscles()
        .forEach(
            (id, s) -> {
              MuscleInfo info = muscles.get(id);
              if (info != null) {
                sets.merge(info.region(), s.moving(), Double::sum);
              }
            });
    return sets;
  }

  /**
   * Share of the moving volume per region, in percent.
   *
   * @param totals The volumes.
   * @param muscles The muscles, by id.
   * @return Shares per region (0 everywhere for an empty window).
   */
  public static Map<MuscleRegion, Double> shares(WeekTotals totals, Map<Long, MuscleInfo> muscles) {
    Map<MuscleRegion, Double> sets = regionSets(totals, muscles);
    double total = sets.values().stream().mapToDouble(Double::doubleValue).sum();
    Map<MuscleRegion, Double> shares = new EnumMap<>(MuscleRegion.class);
    sets.forEach((region, s) -> shares.put(region, total > 0 ? s / total * 100 : 0));
    return shares;
  }

  /**
   * Moving sets of a functional group: the muscle of that code and its members.
   *
   * @param totals The volumes.
   * @param muscles The muscles, by id.
   * @param code The group's code.
   * @return Its sets.
   */
  public static double groupSets(WeekTotals totals, Map<Long, MuscleInfo> muscles, String code) {
    double sets = 0;
    for (Map.Entry<Long, WeekTotals.MuscleSets> e : totals.muscles().entrySet()) {
      MuscleInfo info = muscles.get(e.getKey());
      if (info != null && (code.equals(info.code()) || code.equals(info.groupCode()))) {
        sets += e.getValue().moving();
      }
    }
    return sets;
  }

  // -------------------------------------------------------------------------
  // Alerts
  // -------------------------------------------------------------------------

  /**
   * Pattern gaps of a profile (D174, D176): a judged axis whose share falls under {@link
   * #PATTERN_GAP} times its full-week share.
   *
   * @param profile The profile's axis shares over a window.
   * @param full The full week's axis shares over the same window.
   * @param judged The axes regular enough to be judged for this profile size (D181).
   * @return The alerts, unsorted.
   */
  public static List<Alert> patternGaps(
      Map<Axis, Double> profile, Map<Axis, Double> full, Set<Axis> judged) {
    List<Alert> alerts = new ArrayList<>();
    for (Axis axis : judged) {
      double limit = PATTERN_GAP * full.getOrDefault(axis, 0.0);
      double share = profile.getOrDefault(axis, 0.0);
      if (limit > 0 && share < limit) {
        alerts.add(new Alert(Kind.PATTERN_GAP, axis.name(), share, limit, limit / Math.max(share, 0.1)));
      }
    }
    return alerts;
  }

  /**
   * Main groups under {@link #GROUP_MIN_SETS} sets per week on average (D180), when at least {@link
   * #MIN_TRAINED_WEEKS} weeks of the window were trained.
   *
   * @param window The volumes summed over the window.
   * @param weeks Weeks of the window.
   * @param trainedWeeks Weeks of the window with something to do.
   * @param muscles The muscles, by id.
   * @return The alerts, unsorted.
   */
  public static List<Alert> groupMinimum(
      WeekTotals window, int weeks, int trainedWeeks, Map<Long, MuscleInfo> muscles) {
    List<Alert> alerts = new ArrayList<>();
    if (trainedWeeks < MIN_TRAINED_WEEKS || weeks <= 0) {
      return alerts;
    }
    for (String group : MAIN_GROUPS) {
      double average = groupSets(window, muscles, group) / weeks;
      if (average < GROUP_MIN_SETS) {
        alerts.add(
            new Alert(
                Kind.GROUP_MINIMUM, group, average, GROUP_MIN_SETS, GROUP_MIN_SETS / Math.max(average, 0.1)));
      }
    }
    return alerts;
  }

  /**
   * The alerts to show: at most {@link #MAX_ALERTS}, the furthest first.
   *
   * @param alerts Every alert.
   * @return The alerts shown.
   */
  public static List<Alert> top(List<Alert> alerts) {
    return alerts.stream()
        .sorted(
            Comparator.comparingDouble((Alert a) -> -a.severity())
                .thenComparing(Alert::kind)
                .thenComparing(Alert::subject))
        .limit(MAX_ALERTS)
        .toList();
  }

  // -------------------------------------------------------------------------
  // Information
  // -------------------------------------------------------------------------

  /**
   * Regions of a profile against the full week (D180): behind at most {@link #REGION_BEHIND} times
   * the full-week share, ahead above {@link #REGION_AHEAD} times; never an alert.
   *
   * @param profile The profile's region shares.
   * @param full The full week's region shares.
   * @return One entry per region, the largest share first.
   */
  public static List<RegionGap> regionGaps(Map<MuscleRegion, Double> profile, Map<MuscleRegion, Double> full) {
    List<RegionGap> gaps = new ArrayList<>();
    for (MuscleRegion region : MuscleRegion.values()) {
      double share = profile.getOrDefault(region, 0.0);
      double reference = full.getOrDefault(region, 0.0);
      Trend trend = Trend.IN_LINE;
      if (reference > 0 && share <= REGION_BEHIND * reference) {
        trend = Trend.BEHIND;
      } else if (reference > 0 && share > REGION_AHEAD * reference) {
        trend = Trend.AHEAD;
      }
      gaps.add(new RegionGap(region, share, reference, trend));
    }
    gaps.sort(Comparator.comparingDouble(RegionGap::share).reversed());
    return gaps;
  }

  /**
   * Unusual sessions (D173, D180): a day whose region exceeds {@link #UNUSUAL_RATIO} times its
   * biggest day of the {@link #UNUSUAL_HISTORY_WEEKS} weeks before, when that day had at least
   * {@link #UNUSUAL_MIN_BASE} sets and each of those weeks held a counted session.
   *
   * @param days The vector of each day with a counted session, history included.
   * @param from First day to judge (Monday of the cycle).
   * @param to Last day to judge.
   * @param muscles The muscles, by id.
   * @return The unusual sessions, by date then region.
   */
  public static List<UnusualSession> unusualSessions(
      Map<LocalDate, WeekTotals> days, LocalDate from, LocalDate to, Map<Long, MuscleInfo> muscles) {
    Map<LocalDate, Map<MuscleRegion, Double>> sets = new HashMap<>();
    days.forEach((date, vector) -> sets.put(date, regionSets(vector, muscles)));
    List<UnusualSession> unusual = new ArrayList<>();
    for (LocalDate date : sets.keySet().stream().sorted().toList()) {
      if (date.isBefore(from) || date.isAfter(to)) {
        continue;
      }
      LocalDate monday = date.minusDays(date.getDayOfWeek().getValue() - 1L);
      LocalDate historyStart = monday.minusWeeks(UNUSUAL_HISTORY_WEEKS);
      boolean programmed = true;
      for (int w = 0; w < UNUSUAL_HISTORY_WEEKS && programmed; w++) {
        LocalDate weekStart = historyStart.plusWeeks(w);
        programmed =
            sets.keySet().stream().anyMatch(d -> !d.isBefore(weekStart) && d.isBefore(weekStart.plusWeeks(1)));
      }
      if (!programmed) {
        continue;
      }
      for (MuscleRegion region : MuscleRegion.values()) {
        double base =
            sets.entrySet().stream()
                .filter(e -> !e.getKey().isBefore(historyStart) && e.getKey().isBefore(monday))
                .mapToDouble(e -> e.getValue().getOrDefault(region, 0.0))
                .max()
                .orElse(0);
        double own = sets.get(date).getOrDefault(region, 0.0);
        if (base >= UNUSUAL_MIN_BASE && own > UNUSUAL_RATIO * base) {
          unusual.add(new UnusualSession(date, region, own, base));
        }
      }
    }
    unusual.sort(Comparator.comparing(UnusualSession::date).thenComparing(UnusualSession::region));
    return unusual;
  }

  /**
   * Whether an axis's full-week share reaches {@link #PATTERN_FLOOR}.
   *
   * @param full The full week's axis shares.
   * @param axis The axis.
   * @return True when large enough to be judged.
   */
  static boolean aboveFloor(Map<Axis, Double> full, Axis axis) {
    return Objects.requireNonNullElse(full.get(axis), 0.0) >= PATTERN_FLOOR;
  }
}
