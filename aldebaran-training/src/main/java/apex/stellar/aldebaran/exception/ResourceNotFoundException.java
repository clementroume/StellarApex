package apex.stellar.aldebaran.exception;

import lombok.Getter;

/**
 * Thrown when a requested resource cannot be found (e.g., WOD, Score, Movement).
 *
 * <p>This results in an HTTP 404 Not Found status via the GlobalExceptionHandler.
 */
@Getter
public class ResourceNotFoundException extends RuntimeException {

  private final String messageKey;
  private final transient Object[] args;

  /**
   * Constructs a new ResourceNotFoundException.
   *
   * @param messageKey The key for the i18n error message (e.g., "wod.score.not.found").
   * @param args Optional arguments to be formatted into the message (e.g., the ID).
   */
  public ResourceNotFoundException(String messageKey, Object... args) {
    super(messageKey);
    this.messageKey = messageKey;
    this.args = args;
  }
}
