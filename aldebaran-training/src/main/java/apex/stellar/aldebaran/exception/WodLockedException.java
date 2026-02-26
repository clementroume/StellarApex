package apex.stellar.aldebaran.exception;

import lombok.Getter;

/**
 * Thrown when an operation is attempted on a locked WOD (Workout of the Day).
 *
 * <p>This exception is typically used to signal that the requested operation cannot be completed
 * because the WOD resource is in a state that prevents modification or further actions.
 */
@Getter
public class WodLockedException extends RuntimeException {
  private final String messageKey;
  private final transient Object[] args;

  /**
   * Constructs a new WodLockedException.
   *
   * <p>This exception is thrown to indicate that an operation cannot be completed because the
   * associated Workout of the Day (WOD) is in a locked state.
   *
   * @param messageKey The key for the internationalized error message.
   * @param args Optional arguments to be formatted into the message.
   */
  public WodLockedException(String messageKey, Object... args) {
    super(messageKey);
    this.messageKey = messageKey;
    this.args = args;
  }
}
