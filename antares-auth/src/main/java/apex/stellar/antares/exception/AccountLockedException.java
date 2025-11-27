package apex.stellar.antares.exception;

import lombok.Getter;

/**
 * Thrown when a user attempts to log in, but their account is temporarily locked due to too many
 * failed attempts.
 *
 * <p>This results in an HTTP 429 "Too Many Requests" status.
 */
@Getter
public class AccountLockedException extends RuntimeException {

  private final String messageKey;
  private final transient Object[] args;

  /**
   * Constructs a new AccountLockedException.
   *
   * @param messageKey The key for the i18n error message (e.g., "error.account.locked").
   * @param args Optional arguments to be formatted into the message (e.g., time remaining).
   */
  public AccountLockedException(String messageKey, Object... args) {
    super(messageKey);
    this.messageKey = messageKey;
    this.args = args;
  }
}
