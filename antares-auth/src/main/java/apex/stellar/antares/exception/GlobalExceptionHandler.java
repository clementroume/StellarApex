package apex.stellar.antares.exception;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.Locale;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;
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
 * Centralized exception handler for the Antares API.
 *
 * <p>This component intercepts exceptions thrown by Controllers and Services to generate
 * standardized HTTP responses compliant with <b>RFC 7807 (Problem Details for HTTP APIs)</b>.
 *
 * <p>It handles:
 *
 * <ul>
 *   <li>Business logic exceptions (Not Found, Conflict, Locked).
 *   <li>Security exceptions (Authentication, Authorization).
 *   <li>Validation errors (@Valid).
 *   <li>Unexpected runtime exceptions.
 * </ul>
 */
@RestControllerAdvice
@RequiredArgsConstructor
@Slf4j
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

  private final MessageSource messageSource;

  /**
   * Handles {@link ResourceNotFoundException}.
   *
   * @param ex The exception containing the error key and arguments.
   * @param request The current HTTP request.
   * @param locale The locale for message translation.
   * @return A {@link ResponseEntity} with status <b>404 Not Found</b>.
   */
  @ExceptionHandler(ResourceNotFoundException.class)
  public ResponseEntity<@NonNull ProblemDetail> handleNotFound(
      ResourceNotFoundException ex, HttpServletRequest request, Locale locale) {
    log.debug("Resource not found: {}", ex.getMessage());
    String message = getLocalizedMessage(ex.getMessageKey(), ex.getArgs(), locale);
    return createProblemResponse(HttpStatus.NOT_FOUND, "Resource Not Found", message, request);
  }

  /**
   * Handles {@link DataConflictException}.
   *
   * <p>Occurs when a request attempts to create a resource that violates a unique constraint (e.g.,
   * duplicate email).
   *
   * @param ex The exception containing the conflict details.
   * @param request The current HTTP request.
   * @param locale The locale for message translation.
   * @return A {@link ResponseEntity} with status <b>409 Conflict</b>.
   */
  @ExceptionHandler(DataConflictException.class)
  public ResponseEntity<@NonNull ProblemDetail> handleConflict(
      DataConflictException ex, HttpServletRequest request, Locale locale) {
    log.warn("Data conflict on request [{}]: {}", request.getRequestURI(), ex.getMessage());
    String message = getLocalizedMessage(ex.getMessageKey(), ex.getArgs(), locale);
    return createProblemResponse(HttpStatus.CONFLICT, "Data Conflict", message, request);
  }

  /**
   * Handles {@link InvalidPasswordException}.
   *
   * <p>Occurs when password validation fails (e.g., mismatch or complexity rules).
   *
   * @param ex The exception details.
   * @param request The current HTTP request.
   * @param locale The locale for message translation.
   * @return A {@link ResponseEntity} with status <b>400 Bad Request</b>.
   */
  @ExceptionHandler(InvalidPasswordException.class)
  public ResponseEntity<@NonNull ProblemDetail> handleInvalidPassword(
      InvalidPasswordException ex, HttpServletRequest request, Locale locale) {
    log.info("Invalid password attempt for {}", request.getRequestURI());
    String message = getLocalizedMessage(ex.getMessageKey(), null, locale);
    return createProblemResponse(HttpStatus.BAD_REQUEST, "Invalid Input", message, request);
  }

  /**
   * Handles {@link BadCredentialsException}.
   *
   * <p>Occurs during failed login attempts. Returns a generic message to prevent user enumeration.
   *
   * @param request The current HTTP request.
   * @param locale The locale for message translation.
   * @return A {@link ResponseEntity} with status <b>401 Unauthorized</b>.
   */
  @ExceptionHandler(BadCredentialsException.class)
  public ResponseEntity<@NonNull ProblemDetail> handleBadCredentials(
      HttpServletRequest request, Locale locale) {
    log.info("Failed login attempt for {}", request.getRequestURI());
    String message = getLocalizedMessage("error.credentials.bad", null, locale);
    return createProblemResponse(HttpStatus.UNAUTHORIZED, "Bad Credentials", message, request);
  }

  /**
   * Handles {@link AccessDeniedException}.
   *
   * <p>Attempts to resolve the exception message as an i18n key if it matches the "error.*"
   * pattern. Otherwise, falls back to a generic "Access Denied" message to avoid leaking internal
   * details.
   *
   * @param ex The security exception.
   * @param request The current HTTP request.
   * @param locale The locale for message translation.
   * @return A {@link ResponseEntity} with status <b>403 Forbidden</b>.
   */
  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<@NonNull ProblemDetail> handleAccessDenied(
      AccessDeniedException ex, HttpServletRequest request, Locale locale) {
    log.warn("Access denied for {}: {}", request.getRequestURI(), ex.getMessage());

    String defaultMessage = getLocalizedMessage("error.access.denied", null, locale);
    String message = defaultMessage;

    if (ex.getMessage() != null && ex.getMessage().startsWith("error.")) {
      message = messageSource.getMessage(ex.getMessage(), null, defaultMessage, locale);
    }

    return createProblemResponse(HttpStatus.FORBIDDEN, "Access Denied", message, request);
  }

  /**
   * Handles {@link AccountLockedException}.
   *
   * <p>Occurs when the user has exceeded the maximum number of login attempts.
   *
   * @param ex The exception containing lock duration details.
   * @param request The current HTTP request.
   * @param locale The locale for message translation.
   * @return A {@link ResponseEntity} with status <b>429 Too Many Requests</b>.
   */
  @ExceptionHandler(AccountLockedException.class)
  public ResponseEntity<@NonNull ProblemDetail> handleAccountLocked(
      AccountLockedException ex, HttpServletRequest request, Locale locale) {
    log.warn("Login blocked for locked account on request {}", request.getRequestURI());
    String message = getLocalizedMessage(ex.getMessageKey(), ex.getArgs(), locale);
    return createProblemResponse(HttpStatus.TOO_MANY_REQUESTS, "Account Locked", message, request);
  }

  /**
   * Customizes the handling of {@link MethodArgumentNotValidException} (Validation errors).
   *
   * <p>Aggregates all field errors into a single string for the 'detail' field of the
   * ProblemDetail.
   *
   * @param ex The validation exception.
   * @param headers The HTTP headers.
   * @param status The HTTP status code.
   * @param request The current request.
   * @return A {@link ResponseEntity} with status <b>400 Bad Request</b> and detailed error
   *     messages.
   */
  @Override
  protected ResponseEntity<@NonNull Object> handleMethodArgumentNotValid(
      MethodArgumentNotValidException ex,
      @NonNull HttpHeaders headers,
      @NonNull HttpStatusCode status,
      WebRequest request) {

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

    String requestUri = ((ServletWebRequest) request).getRequest().getRequestURI();
    log.info("Validation errors on [{}]: {}", requestUri, errors);

    ProblemDetail problemDetail = ex.getBody();
    problemDetail.setTitle(getLocalizedMessage("error.validation", null, request.getLocale()));
    problemDetail.setDetail(errors);
    problemDetail.setInstance(URI.create(requestUri));

    return createResponseEntity(problemDetail, headers, status, request);
  }

  /**
   * Global fallback handler for unexpected exceptions.
   *
   * <p>Logs the full stack trace for debugging and returns a generic error message to the client.
   *
   * @param ex The unexpected exception.
   * @param request The current HTTP request.
   * @param locale The locale for message translation.
   * @return A {@link ResponseEntity} with status <b>500 Internal Server Error</b>.
   */
  @ExceptionHandler(Exception.class)
  public ResponseEntity<@NonNull ProblemDetail> handleGeneric(
      Exception ex, HttpServletRequest request, Locale locale) {
    log.error("Unhandled exception caught for request {}", request.getRequestURI(), ex);
    String message = getLocalizedMessage("error.internal.server", null, locale);
    return createProblemResponse(
        HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error", message, request);
  }

  // --- Helpers ---

  /** Constructs a {@link ResponseEntity} containing a standardized {@link ProblemDetail}. */
  private ResponseEntity<@NonNull ProblemDetail> createProblemResponse(
      HttpStatus status, String title, String detail, HttpServletRequest request) {
    ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, detail);
    problemDetail.setTitle(title);
    problemDetail.setInstance(URI.create(request.getRequestURI()));
    return ResponseEntity.status(status).body(problemDetail);
  }

  /**
   * Safely retrieves a localized message from the MessageSource. Returns the key itself if the
   * message is not found (dev-friendly fallback).
   */
  private String getLocalizedMessage(String key, Object[] args, Locale locale) {
    try {
      return messageSource.getMessage(key, args, locale);
    } catch (NoSuchMessageException e) {
      log.warn("No message found for key '{}'", key, e);
      return key;
    }
  }
}
