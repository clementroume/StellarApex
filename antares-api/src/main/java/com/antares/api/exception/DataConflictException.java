package com.antares.api.exception;

import lombok.Getter;

/**
 * Thrown when a request cannot be completed because of a conflict with the current state of the
 * resource. Example: trying to register with an email that already exists.
 */
@Getter
public class DataConflictException extends RuntimeException {

  private final String messageKey;
  private final transient Object[] args;

  /**
   * Constructs a new DataConflictException with the specified message key and arguments.
   *
   * @param messageKey the key identifying the error message
   * @param args optional arguments for the error message
   */
  public DataConflictException(String messageKey, Object... args) {

    super(messageKey);
    this.messageKey = messageKey;
    this.args = args;
  }
}
