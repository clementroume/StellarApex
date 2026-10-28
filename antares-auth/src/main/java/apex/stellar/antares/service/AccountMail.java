package apex.stellar.antares.service;

import java.time.Duration;

/**
 * An account e-mail to send once the current transaction commits (see {@link AccountMailService}).
 *
 * @param kind Which e-mail.
 * @param to The recipient address.
 * @param locale The recipient's language ("fr", "en").
 * @param firstName The recipient's first name, for the greeting.
 * @param token The raw single-use token, only put in the link.
 * @param validity How long the token stays valid.
 */
public record AccountMail(
    Kind kind, String to, String locale, String firstName, String token, Duration validity) {

  /** The account e-mails, with their Sirius page and message key prefix. */
  public enum Kind {
    PASSWORD_RESET("/auth/reset-password", "mail.passwordReset"),
    EMAIL_VERIFICATION("/auth/verify-email", "mail.emailVerification");

    private final String path;
    private final String messagePrefix;

    Kind(String path, String messagePrefix) {
      this.path = path;
      this.messagePrefix = messagePrefix;
    }

    /** Sirius page receiving the token. */
    public String path() {
      return path;
    }

    /** Prefix of the message keys of this e-mail. */
    public String messagePrefix() {
      return messagePrefix;
    }
  }

  /** Masks the token in logs and debuggers. */
  @Override
  public String toString() {
    return "AccountMail{kind=" + kind + "}";
  }
}
