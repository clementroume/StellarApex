package apex.stellar.aldebaran.dto;

import apex.stellar.aldebaran.model.enums.Category.Modality;
import apex.stellar.aldebaran.model.enums.MovementPattern;
import apex.stellar.aldebaran.model.enums.MovementPattern.Axis;
import apex.stellar.aldebaran.model.enums.TimeDomain;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * The balance of a gym's programming over a cycle, for its staff (lot D', D161-D181): the full
 * week, a summary per profile size, and the profile asked for. Never sent to an athlete (D89).
 *
 * @param gymId The gym.
 * @param start Monday of the cycle.
 * @param end Sunday of its last week.
 * @param weeks Weeks of the cycle.
 * @param scaled True when the official Scaled versions were read (D162).
 * @param optional True when optional blocks were counted (D161).
 * @param trainingDays Days of the week with a CrossFit session in the cycle.
 * @param sessions CrossFit sessions counted in the cycle.
 * @param windows Windows of 4 weeks evaluated.
 * @param uncountedSessions Sessions of the cycle left out: specialties and other disciplines.
 * @param uncountedLines Lines of counted blocks that count nothing (unreadable text, no set value).
 * @param fullWeek The athlete who comes every training day.
 * @param summaries One per profile size (3, 4, 5 days) smaller than the training days.
 * @param profile The profile asked for ({@code days}), or null.
 * @param concentratedAxes Axes an athlete without fixed days often misses (information, D181).
 * @param unusualSessions Sessions unusual against the weeks before (information, D173).
 * @param capabilities What the user may do on this programming.
 */
public record ProgrammingBalanceResponse(
    Long gymId,
    LocalDate start,
    LocalDate end,
    int weeks,
    boolean scaled,
    boolean optional,
    List<DayOfWeek> trainingDays,
    int sessions,
    int windows,
    int uncountedSessions,
    int uncountedLines,
    ProfileResponse fullWeek,
    List<ProfileSummary> summaries,
    @Nullable ProfileResponse profile,
    List<Axis> concentratedAxes,
    List<UnusualSessionResponse> unusualSessions,
    Capabilities capabilities) {

  /**
   * The balance of one profile over its window shown.
   *
   * @param days Its days.
   * @param windowStart Monday of the window shown (the worst one, else the last).
   * @param windowEnd Sunday of that window.
   * @param inAlert Whether any window of the cycle raises an alert.
   * @param axes The radar: share of each pattern axis, against the full week.
   * @param details Share of each pattern detail, against the full week.
   * @param regions Share of the moving volume per region, against the full week (information).
   * @param alerts At most 3 alerts, the furthest first.
   * @param minutesPerWeek Metcon minutes per time domain, average per week of that window.
   * @param modalities Share of the sets per modality over that window, in percent.
   */
  public record ProfileResponse(
      List<DayOfWeek> days,
      LocalDate windowStart,
      LocalDate windowEnd,
      boolean inAlert,
      List<AxisShare> axes,
      List<DetailShare> details,
      List<RegionResponse> regions,
      List<AlertResponse> alerts,
      Map<TimeDomain, Integer> minutesPerWeek,
      Map<Modality, Double> modalities) {}

  /**
   * An axis of the radar.
   *
   * @param axis The axis.
   * @param share Percent of the sets of every axis.
   * @param fullShare The same in the full week.
   * @param judged Whether a gap on this axis raises an alert for this profile: above the floor and
   *     regular (D181); always false for the full week.
   */
  public record AxisShare(Axis axis, double share, double fullShare, boolean judged) {}

  /**
   * A detail of an axis (Push V, Core Flexion...).
   *
   * @param pattern The detail.
   * @param share Percent of the sets of every axis (a line may count on two details of one axis).
   * @param fullShare The same in the full week.
   */
  public record DetailShare(MovementPattern pattern, double share, double fullShare) {}

  /**
   * A region against the full week (information, D180).
   *
   * @param region The region ({@code MuscleRegion}).
   * @param share Percent of the moving volume.
   * @param fullShare The same in the full week.
   * @param trend {@code BEHIND}, {@code IN_LINE} or {@code AHEAD}.
   */
  public record RegionResponse(String region, double share, double fullShare, String trend) {}

  /**
   * Profiles of one size.
   *
   * @param size Days per week.
   * @param total Profiles of that size.
   * @param inAlert The profiles in alert, each with its main alert.
   */
  public record ProfileSummary(int size, int total, List<ProfileRow> inAlert) {}

  /**
   * A profile in alert.
   *
   * @param days Its days.
   * @param main Its first alert.
   * @param alerts Its alerts shown.
   */
  public record ProfileRow(List<DayOfWeek> days, AlertResponse main, int alerts) {}

  /**
   * An alert.
   *
   * @param kind {@code PATTERN_GAP} or {@code GROUP_MINIMUM}.
   * @param subject The axis or the group's muscle code.
   * @param value The profile's share of the axis (percent), or the sets per week.
   * @param limit The threshold crossed: 0.6 times the full-week share, or 4 sets per week.
   */
  public record AlertResponse(String kind, String subject, double value, double limit) {}

  /**
   * An unusual session (information, D173).
   *
   * @param date The day.
   * @param region The region ({@code MuscleRegion}).
   * @param sets Its moving sets that day.
   * @param base Its biggest day over the 4 weeks before.
   */
  public record UnusualSessionResponse(LocalDate date, String region, double sets, double base) {}

  /**
   * What the user may do.
   *
   * @param canReorder Apply a proposed order of the sessions (WOD_WRITE, lot D' PR 2).
   */
  public record Capabilities(boolean canReorder) {}
}
