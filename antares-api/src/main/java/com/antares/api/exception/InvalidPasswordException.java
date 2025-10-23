package com.antares.api.exception;

import lombok.Getter;

/**
 * Thrown when a password-related operation fails. Example: current password is wrong, or new
 * password does not match confirmation.
 */
@Getter
public class InvalidPasswordException extends RuntimeException {

  private final String messageKey;

  /**
   * Constructs a new InvalidPasswordException with the specified message key.
   *
   * @param messageKey the key identifying the error message
   */
  public InvalidPasswordException(String messageKey) {
    super(messageKey);
    this.messageKey = messageKey;
  }
}
