package apex.stellar.aldebaran.exception;

import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
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
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Centralized exception handler for Aldebaran Training API.
 *
 * <p>Converts application exceptions into standard RFC 7807 Problem Details. Titles are
 * standardized (English) while details are internationalized via {@link MessageSource}.
 */
@RestControllerAdvice
@RequiredArgsConstructor
@Slf4j
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

  private final MessageSource messageSource;

  /**
   * Handles {@link ResourceNotFoundException} when a requested resource cannot be found. Returns a
   * 404 Not Found status.
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
    String message = getLocalizedMessage(ex.getMessageKey(), ex.getArgs(), locale);
    return createProblemResponse(HttpStatus.NOT_FOUND, "Resource Not Found", message, request);
  }

  /**
   * Handles {@link DataConflictException} when a request conflicts with the current state of the
   * server. Returns a 409 Conflict status.
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
    String message = getLocalizedMessage(ex.getMessageKey(), ex.getArgs(), locale);
    return createProblemResponse(HttpStatus.CONFLICT, "Data Conflict", message, request);
  }

  /**
   * Handles {@link WodLockedException} when modification is attempted on a locked WOD. Returns 409
   * Conflict.
   */
  @ExceptionHandler(WodLockedException.class)
  public ResponseEntity<@NonNull ProblemDetail> handleWodLocked(
      WodLockedException ex, HttpServletRequest request, Locale locale) {

    log.warn("WOD Locked: {}", ex.getMessage());
    String message = getLocalizedMessage(ex.getMessageKey(), ex.getArgs(), locale);
    return createProblemResponse(HttpStatus.CONFLICT, "Resource Locked", message, request);
  }

  /**
   * Handles {@link ConstraintViolationException} triggered by JPA/Hibernate Validator
   * (e.g. @ValidScore). Returns 400 Bad Request with the validation message.
   */
  @ExceptionHandler(ConstraintViolationException.class)
  public ResponseEntity<@NonNull ProblemDetail> handleConstraintViolation(
      ConstraintViolationException ex, HttpServletRequest request, Locale locale) {

    String errors =
        ex.getConstraintViolations().stream()
            .map(ConstraintViolation::getMessage)
            .collect(Collectors.joining("; "));

    log.warn("Constraint violation: {}", errors);
    // Use generic validation title, detail contains specifics
    return createProblemResponse(HttpStatus.BAD_REQUEST, "Validation Error", errors, request);
  }

  /**
   * Handles {@link EntityNotFoundException} (JPA standard 404). Acts as a fallback for standard JPA
   * errors.
   */
  @ExceptionHandler(EntityNotFoundException.class)
  public ResponseEntity<@NonNull ProblemDetail> handleEntityNotFound(
      EntityNotFoundException ex, HttpServletRequest request, Locale locale) {

    log.debug("Entity not found: {}", ex.getMessage());
    return createProblemResponse(
        HttpStatus.NOT_FOUND, "Entity Not Found", ex.getMessage(), request);
  }

  /**
   * Handles {@link AccessDeniedException} when a user lacks the necessary permissions. Returns a
   * 403 Forbidden status.
   *
   * @param ex The exception.
   * @param request The current HTTP request.
   * @param locale The locale for message translation.
   * @return A {@link ResponseEntity} containing the {@link ProblemDetail}.
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
   * Overrides the standard Spring MVC validation handler to provide detailed field error logging
   * and a customized ProblemDetail response.
   */
  @Override
  protected ResponseEntity<@NonNull Object> handleMethodArgumentNotValid(
      MethodArgumentNotValidException ex,
      @NonNull HttpHeaders headers,
      @NonNull HttpStatusCode status,
      WebRequest request) {

    String errors =
        ex.getBindingResult().getFieldErrors().stream()
            .map(error -> error.getField() + ": " + error.getDefaultMessage())
            .collect(Collectors.joining("; "));

    String requestUri = ((ServletWebRequest) request).getRequest().getRequestURI();
    log.info("Validation errors on [{}]: {}", requestUri, errors);

    ProblemDetail problemDetail = ex.getBody();
    // We use the localized message for title if available, otherwise default
    // Note: Antares uses a specific key for validation title, we keep it consistent here
    problemDetail.setTitle(getLocalizedMessage("error.validation", null, request.getLocale()));
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
    String message = getLocalizedMessage("error.internal.server", null, locale);

    return createProblemResponse(
        HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error", message, request);
  }

  // --- Helpers ---

  private ResponseEntity<@NonNull ProblemDetail> createProblemResponse(
      HttpStatus status, String title, String detail, HttpServletRequest request) {

    ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, detail);
    problemDetail.setTitle(title);
    problemDetail.setInstance(URI.create(request.getRequestURI()));

    return ResponseEntity.status(status).body(problemDetail);
  }

  /** Safely retrieves a localized message. Returns the key itself if not found. */
  private String getLocalizedMessage(String key, Object[] args, Locale locale) {
    try {
      return messageSource.getMessage(key, args, locale);
    } catch (NoSuchMessageException e) {
      log.warn("No message found for key '{}'", key, e);
      return key;
    }
  }
}
