package atlas.stellar.antares.exception;

import lombok.Getter;

/**
 * Thrown when a requested resource cannot be found (e.g., trying to fetch a user by ID or email
 * that does not exist).
 *
 * <p>This results in an HTTP 404 Not Found status.
 */
@Getter
public class ResourceNotFoundException extends RuntimeException {

  private final String messageKey;
  private final transient Object[] args;

  /**
   * Constructs a new ResourceNotFoundException.
   *
   * @param messageKey The key for the i18n error message (e.g., "error.user.not.found.email").
   * @param args Optional arguments to be formatted into the message.
   */
  public ResourceNotFoundException(String messageKey, Object... args) {
    super(messageKey);
    this.messageKey = messageKey;
    this.args = args;
  }
}
