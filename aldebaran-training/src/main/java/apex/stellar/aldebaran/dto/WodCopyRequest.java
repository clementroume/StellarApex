package apex.stellar.aldebaran.dto;

/**
 * Copies a readable WOD (e.g. a public Open WOD) into a gym or as a personal WOD. The copy is
 * private, belongs to the caller and can be scored independently of its source.
 *
 * @param gymId Target gym (WOD_WRITE required), or null for a personal copy.
 * @param competition Whether the copy is a competition WOD (gym copies only).
 */
public record WodCopyRequest(Long gymId, boolean competition) {}
