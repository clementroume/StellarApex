package apex.stellar.aldebaran.dto;

/**
 * A movement accepted by an "either" movement.
 *
 * @param id Its ID.
 * @param name Its name.
 */
public record AcceptedMovementResponse(Long id, String name) {}
