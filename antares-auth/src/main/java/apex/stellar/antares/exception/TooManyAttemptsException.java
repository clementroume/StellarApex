package apex.stellar.antares.exception;

import lombok.Getter;

/**
 * Thrown when a user repeats a guessable action (e.g. an enrollment code) too often. Results in an
 * HTTP 429 "Too Many Requests" with a {@code Retry-After} header.
 */
@Getter
public class TooManyAttemptsException extends RuntimeException {

  private final String messageKey;
  private final long retryAfterSeconds;
  private final transient Object[] args;

  /**
   * Constructs a new TooManyAttemptsException.
   *
   * @param messageKey The key for the i18n error message.
   * @param retryAfterSeconds Seconds before a new attempt is accepted.
   * @param args Optional arguments formatted into the message.
   */
  public TooManyAttemptsException(String messageKey, long retryAfterSeconds, Object... args) {
    super(messageKey);
    this.messageKey = messageKey;
    this.retryAfterSeconds = retryAfterSeconds;
    this.args = args;
  }
}
