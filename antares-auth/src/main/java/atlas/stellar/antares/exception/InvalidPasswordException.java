package atlas.stellar.antares.exception;

import lombok.Getter;

/**
 * Thrown when a password-related operation fails (e.g., the current password is wrong, or the new
 * password does not match confirmation).
 *
 * <p>This results in an HTTP 400 Bad Request status.
 */
@Getter
public class InvalidPasswordException extends RuntimeException {

  private final String messageKey;

  /**
   * Constructs a new InvalidPasswordException.
   *
   * @param messageKey The key for the i18n error message (e.g., "error.password.incorrect").
   */
  public InvalidPasswordException(String messageKey) {
    super(messageKey);
    this.messageKey = messageKey;
  }
}
