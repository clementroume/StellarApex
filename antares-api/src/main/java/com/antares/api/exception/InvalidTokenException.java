package com.antares.api.exception;

import lombok.Getter;

/** InvalidTokenException is thrown when a provided token is invalid or cannot be processed. */
@Getter
public class InvalidTokenException extends RuntimeException {

  private final String messageKey;

  /**
   * Constructs a new InvalidTokenException with the specified message key.
   *
   * @param messageKey the key for the error message
   */
  public InvalidTokenException(String messageKey) {
    super(messageKey);
    this.messageKey = messageKey;
  }
}
