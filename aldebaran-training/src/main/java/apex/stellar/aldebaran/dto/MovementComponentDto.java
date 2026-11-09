package apex.stellar.aldebaran.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.Set;

/**
 * One step of a chained movement.
 *
 * @param componentOrder Position in the chain, from 1.
 * @param movementId The movement of this step.
 * @param movementName Its name (responses only).
 * @param countsForRecords Whether the chain also counts for this movement's record.
 * @param techniques Techniques this step always has.
 * @param equipment Equipment this step always has, empty to follow the line.
 */
public record MovementComponentDto(
    @NotNull @Min(1) Integer componentOrder,
    @NotNull Long movementId,
    String movementName,
    boolean countsForRecords,
    Set<String> techniques,
    Set<String> equipment) {}
