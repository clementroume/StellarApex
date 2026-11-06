package apex.stellar.aldebaran.dto;

import java.util.List;

/**
 * An athlete's records of one movement, grouped by variant, base version first (D61).
 *
 * @param movementId The movement.
 * @param movementName Its catalog name.
 * @param variants The variants.
 */
public record MovementRecordsResponse(
    Long movementId, String movementName, List<VariantRecordsResponse> variants) {}
