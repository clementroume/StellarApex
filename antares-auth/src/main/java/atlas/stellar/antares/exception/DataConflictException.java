package atlas.stellar.antares.exception;

import lombok.Getter;

/**
 * Thrown when a request cannot be completed because of a conflict with the current state of the
 * resource (e.g., trying to register with an email that already exists).
 *
 * <p>This results in an HTTP 409 Conflict status.
 */
@Getter
public class DataConflictException extends RuntimeException {

  private final String messageKey;
  private final transient Object[] args;

  /**
   * Constructs a new DataConflictException.
   *
   * @param messageKey The key for the i18n error message (e.g., "error.email.in.use").
   * @param args Optional arguments to be formatted into the message.
   */
  public DataConflictException(String messageKey, Object... args) {
    super(messageKey);
    this.messageKey = messageKey;
    this.args = args;
  }
}
