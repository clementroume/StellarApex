package apex.stellar.antares.exception;

import lombok.Getter;

/**
 * Thrown when a request is well-formed but breaks a business rule (e.g. a gym role not allowed for
 * the gym type).
 *
 * <p>This results in an HTTP 400 Bad Request status.
 */
@Getter
public class BusinessRuleException extends RuntimeException {

  private final String messageKey;
  private final transient Object[] args;

  /**
   * Constructs a new BusinessRuleException.
   *
   * @param messageKey The key for the i18n error message (e.g., "error.membership.role.not.allowed").
   * @param args Optional arguments to be formatted into the message.
   */
  public BusinessRuleException(String messageKey, Object... args) {
    super(messageKey);
    this.messageKey = messageKey;
    this.args = args;
  }
}
