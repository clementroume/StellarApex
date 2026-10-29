package apex.stellar.aldebaran.exception;

import lombok.Getter;

/**
 * Thrown when a session breaks one of its rules: composition, visibility policy, journal date.
 *
 * <p>This results in an HTTP 400 Bad Request status.
 */
@Getter
public class InvalidSessionException extends RuntimeException {

  private final String messageKey;
  private final transient Object[] args;

  /**
   * Constructs a new InvalidSessionException.
   *
   * @param messageKey The key for the i18n error message.
   * @param args Optional arguments to be formatted into the message.
   */
  public InvalidSessionException(String messageKey, Object... args) {
    super(messageKey);
    this.messageKey = messageKey;
    this.args = args;
  }
}
