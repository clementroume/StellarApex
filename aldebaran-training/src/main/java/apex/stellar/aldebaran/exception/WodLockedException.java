package apex.stellar.aldebaran.exception;

import lombok.Getter;

@Getter
public class WodLockedException extends RuntimeException {
  private final String messageKey;
  private final transient Object[] args;

  public WodLockedException(String messageKey, Object... args) {
    super(messageKey);
    this.messageKey = messageKey;
    this.args = args;
  }
}