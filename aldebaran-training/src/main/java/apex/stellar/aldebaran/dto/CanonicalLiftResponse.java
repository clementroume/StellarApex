package apex.stellar.aldebaran.dto;

/**
 * A public "N RM" test of the catalog (D51): logging it sets the record, its leaderboard is the
 * lift's leaderboard (D54).
 *
 * @param wodId The canonical WOD.
 * @param movementId Its movement.
 * @param movementName The movement's name.
 * @param repMax N of the N RM.
 * @param variantKey The variant tested.
 * @param usualNameEn Usual name of that variant, or null.
 * @param usualNameFr Its French name, null when identical or none.
 */
public record CanonicalLiftResponse(
    Long wodId,
    Long movementId,
    String movementName,
    int repMax,
    String variantKey,
    String usualNameEn,
    String usualNameFr) {}
