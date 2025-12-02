package apex.stellar.bellatrix;

import org.jspecify.annotations.NonNull;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientResponseException;

/**
 * Global exception handler for handling exceptions across the application.
 *
 * <p>This class is annotated with {@code @RestControllerAdvice} to allow centralized exception
 * handling for all {@code @RestController} components. It captures and processes specific
 * exceptions, providing customized responses.
 *
 * <p>The current implementation focuses on handling {@code RestClientResponseException}, which
 * occurs during HTTP interactions via a {@code RestClient}.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

  /**
   * Handles exceptions of type {@link RestClientResponseException} that occur during upstream HTTP
   * interactions. This method captures the status code and response body of the exception and
   * returns it as an HTTP response.
   *
   * @param ex the exception instance containing details about the upstream HTTP error
   * @return a {@link ResponseEntity} containing the HTTP status code and response body of the
   *     exception
   */
  @ExceptionHandler(RestClientResponseException.class)
  public ResponseEntity<@NonNull String> handleUpstreamError(RestClientResponseException ex) {
    return ResponseEntity.status(ex.getStatusCode()).body(ex.getResponseBodyAsString());
  }
}
