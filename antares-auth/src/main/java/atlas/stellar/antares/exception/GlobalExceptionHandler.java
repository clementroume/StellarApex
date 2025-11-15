package atlas.stellar.antares.exception;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Locale;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Centralized exception handler for the entire REST API. This class ensures that all error
 * responses are consistent, secure, structured, and internationalized.
 */
@RestControllerAdvice
@RequiredArgsConstructor
@Slf4j
public class GlobalExceptionHandler {

  private final MessageSource messageSource;

  /**
   * Handles {@link ResourceNotFoundException} and returns a translated 404 Not Found response.
   *
   * @param ex the exception thrown
   * @param request the HTTP request
   * @param locale the locale for message translation
   * @return a standardized error response
   */
  @ExceptionHandler(ResourceNotFoundException.class)
  public ResponseEntity<ErrorResponse> handleNotFound(
      ResourceNotFoundException ex, HttpServletRequest request, Locale locale) {

    log.debug("Resource not found: {}", ex.getMessage());
    String message = messageSource.getMessage(ex.getMessageKey(), ex.getArgs(), locale);

    return buildErrorResponse(HttpStatus.NOT_FOUND, "Resource Not Found", message, request);
  }

  /**
   * Handles {@link DataConflictException} and returns a translated 409 Conflict response.
   *
   * @param ex the exception thrown
   * @param request the HTTP request
   * @param locale the locale for message translation
   * @return a standardized error response
   */
  @ExceptionHandler(DataConflictException.class)
  public ResponseEntity<ErrorResponse> handleConflict(
      DataConflictException ex, HttpServletRequest request, Locale locale) {

    log.warn("Data conflict on request [{}]: {}", request.getRequestURI(), ex.getMessage());
    String message = messageSource.getMessage(ex.getMessageKey(), ex.getArgs(), locale);

    return buildErrorResponse(HttpStatus.CONFLICT, "Data Conflict", message, request);
  }

  /**
   * Handles {@link InvalidPasswordException} and returns a translated 400 Bad Request response.
   *
   * @param ex the exception thrown
   * @param request the HTTP request
   * @param locale the locale for message translation
   * @return a standardized error response
   */
  @ExceptionHandler(InvalidPasswordException.class)
  public ResponseEntity<ErrorResponse> handleInvalidPassword(
      InvalidPasswordException ex, HttpServletRequest request, Locale locale) {

    log.info("Invalid password attempt for {}", request.getRequestURI());
    String message = messageSource.getMessage(ex.getMessageKey(), null, locale);

    return buildErrorResponse(HttpStatus.BAD_REQUEST, "Invalid Input", message, request);
  }

  /**
   * Handles {@link BadCredentialsException} and returns a translated 401 Unauthorized response.
   *
   * @param request the HTTP request
   * @param locale the locale for message translation
   * @return a standardized error response
   */
  @ExceptionHandler(BadCredentialsException.class)
  public ResponseEntity<ErrorResponse> handleBadCredentials(
      HttpServletRequest request, Locale locale) {

    log.info("Failed login attempt for {}", request.getRequestURI());
    String message = messageSource.getMessage("error.credentials.bad", null, locale);

    return buildErrorResponse(HttpStatus.UNAUTHORIZED, "Bad Credentials", message, request);
  }

  /**
   * Handles {@link AccessDeniedException} and returns a translated 403 Forbidden response.
   *
   * @param request the HTTP request
   * @param locale the locale for message translation
   * @return a standardized error response
   */
  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<ErrorResponse> handleAccessDenied(
      HttpServletRequest request, Locale locale) {

    log.warn("Access denied for {}", request.getRequestURI());
    String message = messageSource.getMessage("error.access.denied", null, locale);

    return buildErrorResponse(HttpStatus.FORBIDDEN, "Access Denied", message, request);
  }

  /**
   * Handles validation errors from DTOs. It formats all field errors into a single message and
   * returns a 400 Bad Request response.
   *
   * @param ex the exception thrown
   * @param request the HTTP request
   * @param locale the locale for message translation
   * @return a standardized error response
   */
  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ErrorResponse> handleValidationErrors(
      MethodArgumentNotValidException ex, HttpServletRequest request, Locale locale) {

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

    log.info("Validation errors on [{}]: {}", request.getRequestURI(), errors);
    String title = messageSource.getMessage("error.validation", null, locale);

    return buildErrorResponse(HttpStatus.BAD_REQUEST, title, errors, request);
  }

  /**
   * Handles {@link InvalidTokenException} and returns a translated 401 Unauthorized response.
   *
   * @param ex the exception thrown
   * @param request the HTTP request
   * @param locale the locale for message translation
   * @return a standardized error response
   */
  @ExceptionHandler(InvalidTokenException.class)
  public ResponseEntity<ErrorResponse> handleInvalidToken(
      InvalidTokenException ex, HttpServletRequest request, Locale locale) {

    log.info("Invalid token on request [{}]: {}", request.getRequestURI(), ex.getMessage());
    String message = messageSource.getMessage(ex.getMessageKey(), null, locale);

    return buildErrorResponse(HttpStatus.UNAUTHORIZED, "Invalid Token", message, request);
  }

  /**
   * Handles {@link HashingException} specifically. Logs the full exception but returns a generic,
   * translated 500 Internal Server Error.
   *
   * @param ex the exception thrown
   * @param request the HTTP request
   * @param locale the locale for message translation
   * @return a standardized error response
   */
  @ExceptionHandler(HashingException.class)
  public ResponseEntity<ErrorResponse> handleHashingException(
      HashingException ex, HttpServletRequest request, Locale locale) {

    log.error("Critical hashing failure", ex);
    String message = messageSource.getMessage(ex.getMessageKey(), null, locale);

    return buildErrorResponse(
        HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error", message, request);
  }

  /**
   * A generic catch-all handler for any other unhandled {@link Exception}. Logs the full exception
   * but returns a generic, translated 500 Internal Server Error.
   *
   * @param request the HTTP request
   * @param locale the locale for message translation
   * @return a standardized error response
   */
  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorResponse> handleGeneric(
      Exception ex, HttpServletRequest request, Locale locale) {

    log.error("Unhandled exception caught for request {}", request.getRequestURI(), ex);
    String message = messageSource.getMessage("error.internal.server", null, locale);

    return buildErrorResponse(
        HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error", message, request);
  }

  /**
   * Helper method to build a standardized {@link ErrorResponse}.
   *
   * @param status the HTTP status to return
   * @param error the error title
   * @param message the error message
   * @param request the HTTP request
   * @return a standardized error response
   */
  private ResponseEntity<ErrorResponse> buildErrorResponse(
      HttpStatus status, String error, String message, HttpServletRequest request) {

    ErrorResponse response =
        ErrorResponse.of(status.value(), error, message, request.getRequestURI());

    return ResponseEntity.status(status).body(response);
  }
}
