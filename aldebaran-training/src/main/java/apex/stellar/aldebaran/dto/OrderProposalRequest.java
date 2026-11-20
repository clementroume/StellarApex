package apex.stellar.aldebaran.dto;

import apex.stellar.aldebaran.model.enums.HeavyDays;
import apex.stellar.aldebaran.model.enums.OrderMode;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.Set;

/**
 * What the coach asks for a session-order proposal (lot D', D168-D171): the cycle and switches of
 * the balance shown, the mode, the strength-day option and the blocks pinned to their day.
 *
 * @param start Any day of the first week (Sirius sends its Monday).
 * @param weeks Weeks of the cycle, 4 to 8.
 * @param scaled Read the official Scaled versions.
 * @param optional Count the optional blocks.
 * @param mode What may move.
 * @param heavy Two strength days in a row, when the Skills move.
 * @param pinnedBlockIds Blocks kept on their day (never stored).
 */
public record OrderProposalRequest(
    @NotNull(message = "{validation.date.required}") LocalDate start,
    int weeks,
    boolean scaled,
    boolean optional,
    @NotNull OrderMode mode,
    @NotNull HeavyDays heavy,
    Set<Long> pinnedBlockIds) {

  /** No pin: an empty set. */
  public OrderProposalRequest {
    pinnedBlockIds = pinnedBlockIds == null ? Set.of() : Set.copyOf(pinnedBlockIds);
  }
}
