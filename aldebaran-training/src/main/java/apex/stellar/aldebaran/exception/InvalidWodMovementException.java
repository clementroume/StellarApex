package apex.stellar.aldebaran.exception;

import lombok.Getter;

/**
 * Thrown when a WOD movement is inconsistent with its catalog movement, e.g. an equipment or a
 * technique that the movement does not allow.
 *
 * <p>This results in an HTTP 400 Bad Request status.
 */
@Getter
public class InvalidWodMovementException extends RuntimeException {

  private final String messageKey;
  private final transient Object[] args;

  /**
   * Constructs a new InvalidWodMovementException.
   *
   * @param messageKey The key for the i18n error message.
   * @param args Optional arguments to be formatted into the message.
   */
  public InvalidWodMovementException(String messageKey, Object... args) {
    super(messageKey);
    this.messageKey = messageKey;
    this.args = args;
  }
}
