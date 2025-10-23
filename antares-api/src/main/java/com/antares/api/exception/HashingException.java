package com.antares.api.exception;

import lombok.Getter;

/** Custom exception for hashing errors. */
@Getter
public class HashingException extends RuntimeException {

  private final String messageKey;

  /**
   * Constructs a new HashingException with the specified message key.
   *
   * @param messageKey the key identifying the error message
   */
  public HashingException(String messageKey) {

    super(messageKey);
    this.messageKey = messageKey;
  }
}
