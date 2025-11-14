package com.antares.api.exception;

import lombok.Getter;

/**
 * Thrown when a provided JWT token is invalid, expired, or cannot be processed.
 *
 * <p>This results in an HTTP 401 Unauthorized status.
 */
@Getter
public class InvalidTokenException extends RuntimeException {

  private final String messageKey;

  /**
   * Constructs a new InvalidTokenException.
   *
   * @param messageKey The key for the i18n error message (e.g., "error.token.invalid").
   */
  public InvalidTokenException(String messageKey) {
    super(messageKey);
    this.messageKey = messageKey;
  }
}
