package com.antares.api.exception;

import lombok.Getter;

/** TooManyRequestsException is thrown when a user exceeds the allowed number of requests. */
@Getter
public class TooManyRequestsException extends RuntimeException {

  private final String messageKey;

  /**
   * Constructs a new TooManyRequestsException with the specified message key.
   *
   * @param messageKey the key for the error message
   */
  public TooManyRequestsException(String messageKey) {
    super(messageKey);
    this.messageKey = messageKey;
  }
}
