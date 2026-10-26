package apex.stellar.aldebaran.exception;

import lombok.Getter;

/**
 * Thrown when a score is inconsistent with its WOD, e.g. a missing value for the WOD's score type
 * or a request pointing at another WOD than the score being updated.
 *
 * <p>This results in an HTTP 400 Bad Request status.
 */
@Getter
public class InvalidScoreException extends RuntimeException {

  private final String messageKey;
  private final transient Object[] args;

  /**
   * Constructs a new InvalidScoreException.
   *
   * @param messageKey The key for the i18n error message.
   * @param args Optional arguments to be formatted into the message.
   */
  public InvalidScoreException(String messageKey, Object... args) {
    super(messageKey);
    this.messageKey = messageKey;
    this.args = args;
  }
}
