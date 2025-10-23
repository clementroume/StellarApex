package com.antares.api.exception;

import java.time.LocalDateTime;

/**
 * A standard, structured error response DTO returned by the API. This record ensures that all error
 * responses have a consistent format, which simplifies error handling on the client side.
 *
 * @param timestamp The exact time the error occurred.
 * @param status The HTTP status code (e.g., 404, 500).
 * @param error A short, technical description of the error (e.g., "Resource Not Found").
 * @param message A more detailed, user-friendly message explaining the error.
 * @param path The API endpoint path where the error occurred.
 */
public record ErrorResponse(
    LocalDateTime timestamp, int status, String error, String message, String path) {
  /**
   * A static factory method for creating a new ErrorResponse instance. It automatically captures
   * the current timestamp.
   *
   * @param status The HTTP status code.
   * @param error The short error description.
   * @param message The detailed error message.
   * @param path The request path.
   * @return A new instance of {@link ErrorResponse}.
   */
  public static ErrorResponse of(int status, String error, String message, String path) {

    return new ErrorResponse(LocalDateTime.now(), status, error, message, path);
  }
}
