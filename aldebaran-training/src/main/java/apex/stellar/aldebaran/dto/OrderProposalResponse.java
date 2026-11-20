package apex.stellar.aldebaran.dto;

import apex.stellar.aldebaran.model.enums.HeavyDays;
import apex.stellar.aldebaran.model.enums.OrderMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * A session-order proposal (lot D', D170): the programming as it is and in the proposed order,
 * week by week. Nothing is written: the coach applies the weeks to keep ({@link OrderApplyRequest}).
 *
 * @param start Monday of the cycle.
 * @param end Sunday of its last week.
 * @param mode What may move.
 * @param heavy The strength-day option.
 * @param before The programming as it is.
 * @param after The proposed order.
 * @param weeks Each week, its days before and after.
 */
public record OrderProposalResponse(
    LocalDate start,
    LocalDate end,
    OrderMode mode,
    HeavyDays heavy,
    Score before,
    Score after,
    List<WeekProposal> weeks) {

  /**
   * How good an order is (D170, D171).
   *
   * @param profilesInAlert Profiles of 3, 4 and 5 days in alert on any window.
   * @param excess Total distance beyond the thresholds.
   * @param sameDomain Consecutive days with the same metcon time domain or dominant modality.
   * @param moves Parts away from their day.
   */
  public record Score(int profilesInAlert, double excess, int sameDomain, int moves) {}

  /**
   * One week.
   *
   * @param monday Its Monday.
   * @param changed Whether the proposal moves something in it.
   * @param days Its training days.
   */
  public record WeekProposal(LocalDate monday, boolean changed, List<DayProposal> days) {}

  /**
   * One training day.
   *
   * @param day The day of the week.
   * @param date The date.
   * @param sessionId The session of that day.
   * @param locked Never moved (past, journal, scores, competition, two sessions that day).
   * @param skill Its Skill as it is, or null.
   * @param wod Its WOD part as it is, or null.
   * @param skillAfter Its Skill in the proposed order, or null.
   * @param wodAfter Its WOD part in the proposed order, or null.
   */
  public record DayProposal(
      DayOfWeek day,
      LocalDate date,
      Long sessionId,
      boolean locked,
      @Nullable PartView skill,
      @Nullable PartView wod,
      @Nullable PartView skillAfter,
      @Nullable PartView wodAfter) {}

  /**
   * A part of a session.
   *
   * @param blockIds Its blocks (the Skill block; the WOD, Accessory and Finisher blocks).
   * @param title Its WODs' titles.
   * @param pinned Kept on its day by the request.
   * @param strength A strength Skill (D169).
   * @param fromDay The day it comes from in the proposed order (its own day when it stays).
   */
  public record PartView(List<Long> blockIds, String title, boolean pinned, boolean strength, DayOfWeek fromDay) {}
}
