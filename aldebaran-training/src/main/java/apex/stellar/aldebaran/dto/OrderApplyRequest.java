package apex.stellar.aldebaran.dto;

import apex.stellar.aldebaran.model.enums.OrderMode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;

/**
 * The weeks of a proposal the coach keeps (lot D', D170): for each day, its session and the blocks
 * it holds in the proposed order. Aldebaran checks again rights, locks and that each week only
 * swaps its own parts, then writes every week in one transaction.
 *
 * @param mode What moves: blocks between the week's sessions, or whole sessions between days.
 * @param weeks The weeks kept.
 */
public record OrderApplyRequest(@NotNull OrderMode mode, @NotEmpty @Valid List<WeekOrder> weeks) {

  /**
   * A week kept.
   *
   * @param monday Its Monday.
   * @param days Its training days in the proposed order.
   */
  public record WeekOrder(@NotNull LocalDate monday, @NotEmpty List<DayOrder> days) {}

  /**
   * A day in the proposed order.
   *
   * @param sessionId The session of that day.
   * @param skillBlockIds The Skill blocks it holds.
   * @param wodBlockIds The WOD, Accessory and Finisher blocks it holds.
   */
  public record DayOrder(@NotNull Long sessionId, List<Long> skillBlockIds, List<Long> wodBlockIds) {

    /** Missing lists: none. */
    public DayOrder {
      skillBlockIds = skillBlockIds == null ? List.of() : List.copyOf(skillBlockIds);
      wodBlockIds = wodBlockIds == null ? List.of() : List.copyOf(wodBlockIds);
    }
  }
}
