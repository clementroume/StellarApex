package apex.stellar.antares.exception;

import org.springframework.security.access.AccessDeniedException;

/**
 * Thrown when something tries to give platform administration to a demo account (or to turn an
 * administrator into a demo account). Demo accounts have a public password, so this is never
 * allowed, whoever asks. Results in an HTTP 403 through {@code GlobalExceptionHandler}.
 */
public class DemoAccountException extends AccessDeniedException {

  /** Creates the exception. */
  public DemoAccountException() {
    super("error.demo.account.admin");
  }
}
