package apex.stellar.aldebaran.exception;

import lombok.Getter;

/**
 * Thrown when a WOD breaks a rule of its own, e.g. a competition WOD without a gym or made public.
 *
 * <p>This results in an HTTP 400 Bad Request status.
 */
@Getter
public class InvalidWodException extends RuntimeException {

  private final String messageKey;
  private final transient Object[] args;

  /**
   * Constructs a new InvalidWodException.
   *
   * @param messageKey The key for the i18n error message.
   * @param args Optional arguments to be formatted into the message.
   */
  public InvalidWodException(String messageKey, Object... args) {
    super(messageKey);
    this.messageKey = messageKey;
    this.args = args;
  }
}
