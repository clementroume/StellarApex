package com.antares.api.exception;

import lombok.Getter;

/**
 * Custom exception for critical hashing errors (e.g., SHA-256 algorithm not available).
 *
 * <p>This results in an HTTP 500 Internal Server Error status.
 */
@Getter
public class HashingException extends RuntimeException {

  private final String messageKey;

  /**
   * Constructs a new HashingException.
   *
   * @param messageKey The key for the i18n error message (e.g., "error.hashing.unavailable").
   */
  public HashingException(String messageKey) {
    super(messageKey);
    this.messageKey = messageKey;
  }
}
