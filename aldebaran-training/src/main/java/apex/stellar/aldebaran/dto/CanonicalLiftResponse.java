package apex.stellar.aldebaran.dto;

/**
 * A public "N RM" test of the catalog (D51): logging it sets the record, its leaderboard is the
 * lift's leaderboard (D54).
 *
 * @param wodId The canonical WOD.
 * @param movementId Its movement (base version).
 * @param movementName The movement's name.
 * @param repMax N of the N RM.
 */
public record CanonicalLiftResponse(Long wodId, Long movementId, String movementName, int repMax) {}
