package apex.stellar.aldebaran.exception;

/**
 * Thrown when Antares cannot answer a question Aldebaran needs to authorize a request (e.g. whether
 * an athlete belongs to a gym). The request is refused, never authorized by default.
 *
 * <p>This results in an HTTP 503 Service Unavailable status.
 */
public class AntaresUnavailableException extends RuntimeException {

  /**
   * Constructs a new AntaresUnavailableException.
   *
   * @param message Technical description, logged only.
   * @param cause The underlying error.
   */
  public AntaresUnavailableException(String message, Throwable cause) {
    super(message, cause);
  }
}
