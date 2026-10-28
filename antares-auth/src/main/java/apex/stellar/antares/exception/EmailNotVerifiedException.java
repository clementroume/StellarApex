package apex.stellar.antares.exception;

/**
 * Thrown when an action needs a verified e-mail address (joining a gym, requesting a gym). Results
 * in an HTTP 403 with {@code code = EMAIL_NOT_VERIFIED}, so Sirius can offer to resend the link.
 */
public class EmailNotVerifiedException extends RuntimeException {

  /** Creates the exception. */
  public EmailNotVerifiedException() {
    super("error.email.not.verified");
  }
}
