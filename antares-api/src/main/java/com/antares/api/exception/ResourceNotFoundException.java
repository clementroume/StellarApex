package com.antares.api.exception;

import lombok.Getter;

/**
 * Thrown when a requested resource cannot be found. Example: trying to fetch a user by ID that does
 * not exist.
 */
@Getter
public class ResourceNotFoundException extends RuntimeException {

  private final String messageKey;
  private final transient Object[] args;

  /**
   * Constructs a new ResourceNotFoundException with the specified message key and arguments.
   *
   * @param messageKey the key identifying the error message
   * @param args optional arguments for the error message
   */
  public ResourceNotFoundException(String messageKey, Object... args) {
    super(messageKey);
    this.messageKey = messageKey;
    this.args = args;
  }
}
