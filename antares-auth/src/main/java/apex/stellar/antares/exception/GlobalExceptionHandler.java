package apex.stellar.antares.exception;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.Locale;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Centralized exception handler for the Antares Auth API.
 *
 * <p>This class extends {@link ResponseEntityExceptionHandler} to provide standard handling for
 * Spring MVC exceptions (compliant with RFC 7807 Problem Details) while injecting custom logging
 * and business-specific error handling logic.
 *
 * <p>All responses are formatted as {@link ProblemDetail} objects.
 */
@RestControllerAdvice
@RequiredArgsConstructor
@Slf4j
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

  private final MessageSource messageSource;

  /**
   * Handles {@link ResourceNotFoundException} when a requested resource (e.g., User) cannot be
   * found. Returns a 404 Not Found status with a localized error message.
   *
   * @param ex The thrown exception containing the message key and arguments.
   * @param request The current HTTP request.
   * @param locale The locale for message translation.
   * @return A {@link ResponseEntity} containing the {@link ProblemDetail}.
   */
  @ExceptionHandler(ResourceNotFoundException.class)
  public ResponseEntity<@NonNull ProblemDetail> handleNotFound(
      ResourceNotFoundException ex, HttpServletRequest request, Locale locale) {

    log.debug("Resource not found: {}", ex.getMessage());
    String message = messageSource.getMessage(ex.getMessageKey(), ex.getArgs(), locale);

    return createProblemResponse(HttpStatus.NOT_FOUND, "Resource Not Found", message, request);
  }

  /**
   * Handles {@link DataConflictException} when a request conflicts with the current state of the
   * server (e.g., duplicate email registration). Returns a 409 Conflict status.
   *
   * @param ex The thrown exception.
   * @param request The current HTTP request.
   * @param locale The locale for message translation.
   * @return A {@link ResponseEntity} containing the {@link ProblemDetail}.
   */
  @ExceptionHandler(DataConflictException.class)
  public ResponseEntity<@NonNull ProblemDetail> handleConflict(
      DataConflictException ex, HttpServletRequest request, Locale locale) {

    log.warn("Data conflict on request [{}]: {}", request.getRequestURI(), ex.getMessage());
    String message = messageSource.getMessage(ex.getMessageKey(), ex.getArgs(), locale);

    return createProblemResponse(HttpStatus.CONFLICT, "Data Conflict", message, request);
  }

  /**
   * Handles {@link InvalidPasswordException} during password updates. Returns a 400 Bad Request
   * status.
   *
   * @param ex The thrown exception.
   * @param request The current HTTP request.
   * @param locale The locale for message translation.
   * @return A {@link ResponseEntity} containing the {@link ProblemDetail}.
   */
  @ExceptionHandler(InvalidPasswordException.class)
  public ResponseEntity<@NonNull ProblemDetail> handleInvalidPassword(
      InvalidPasswordException ex, HttpServletRequest request, Locale locale) {

    log.info("Invalid password attempt for {}", request.getRequestURI());
    String message = messageSource.getMessage(ex.getMessageKey(), null, locale);

    return createProblemResponse(HttpStatus.BAD_REQUEST, "Invalid Input", message, request);
  }

  /**
   * Handles {@link BadCredentialsException} during login attempts. Returns a 401 Unauthorized
   * status to prevent user enumeration.
   *
   * @param request The current HTTP request.
   * @param locale The locale for message translation.
   * @return A {@link ResponseEntity} containing the {@link ProblemDetail}.
   */
  @ExceptionHandler(BadCredentialsException.class)
  public ResponseEntity<@NonNull ProblemDetail> handleBadCredentials(
      HttpServletRequest request, Locale locale) {

    log.info("Failed login attempt for {}", request.getRequestURI());
    String message = messageSource.getMessage("error.credentials.bad", null, locale);

    return createProblemResponse(HttpStatus.UNAUTHORIZED, "Bad Credentials", message, request);
  }

  /**
   * Handles {@link AccessDeniedException} when a user lacks the necessary permissions. Returns a
   * 403 Forbidden status.
   *
   * @param request The current HTTP request.
   * @param locale The locale for message translation.
   * @return A {@link ResponseEntity} containing the {@link ProblemDetail}.
   */
  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<@NonNull ProblemDetail> handleAccessDenied(
      HttpServletRequest request, Locale locale) {

    log.warn("Access denied for {}", request.getRequestURI());
    String message = messageSource.getMessage("error.access.denied", null, locale);

    return createProblemResponse(HttpStatus.FORBIDDEN, "Access Denied", message, request);
  }

  /**
   * Overrides the standard Spring MVC validation handler to provide detailed field error logging
   * and a customized ProblemDetail response.
   *
   * @param ex The exception containing validation errors.
   * @param headers The headers to be written to the response.
   * @param status The selected response status code (400 Bad Request).
   * @param request The current web request.
   * @return A {@link ResponseEntity} with the validation errors formatted in the 'detail' field.
   */
  @Override
  protected ResponseEntity<@NonNull Object> handleMethodArgumentNotValid(
      MethodArgumentNotValidException ex,
      @NonNull HttpHeaders headers,
      @NonNull HttpStatusCode status,
      WebRequest request) {

    // Aggregates all field errors into a single string for logging and the response detail.
    String errors =
        ex.getBindingResult().getAllErrors().stream()
            .map(
                error -> {
                  if (error instanceof FieldError fieldError) {
                    return fieldError.getField() + ": " + error.getDefaultMessage();
                  }
                  return error.getDefaultMessage();
                })
            .collect(Collectors.joining("; "));

    // Cast to ServletWebRequest to access the raw HttpServletRequest URI
    String requestUri = ((ServletWebRequest) request).getRequest().getRequestURI();
    log.info("Validation errors on [{}]: {}", requestUri, errors);

    // Customize the standard ProblemDetail provided by the exception
    ProblemDetail problemDetail = ex.getBody();
    problemDetail.setTitle(messageSource.getMessage("error.validation", null, request.getLocale()));
    problemDetail.setDetail(errors);
    problemDetail.setInstance(URI.create(requestUri));

    return createResponseEntity(problemDetail, headers, status, request);
  }

  /**
   * Catch-all handler for any unexpected runtime exceptions. Logs the full stack trace and returns
   * a generic 500 Internal Server Error.
   *
   * @param ex The unexpected exception.
   * @param request The current HTTP request.
   * @param locale The locale for message translation.
   * @return A {@link ResponseEntity} containing the {@link ProblemDetail}.
   */
  @ExceptionHandler(Exception.class)
  public ResponseEntity<@NonNull ProblemDetail> handleGeneric(
      Exception ex, HttpServletRequest request, Locale locale) {

    log.error("Unhandled exception caught for request {}", request.getRequestURI(), ex);
    String message = messageSource.getMessage("error.internal.server", null, locale);

    return createProblemResponse(
        HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error", message, request);
  }

  /**
   * Helper method to construct a consistent {@link ResponseEntity} containing a {@link
   * ProblemDetail}.
   *
   * @param status The HTTP status code.
   * @param title The short title of the error type.
   * @param detail The human-readable explanation of the specific error.
   * @param request The HTTP request (used to set the 'instance' URI).
   * @return A fully constructed ResponseEntity.
   */
  private ResponseEntity<@NonNull ProblemDetail> createProblemResponse(
      HttpStatus status, String title, String detail, HttpServletRequest request) {

    ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, detail);
    problemDetail.setTitle(title);
    problemDetail.setInstance(URI.create(request.getRequestURI()));

    return ResponseEntity.status(status).body(problemDetail);
  }
}
