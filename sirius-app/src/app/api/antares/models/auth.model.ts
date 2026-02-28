/**
 * Defines the data structure for a user login request.
 * Mirrors the `AuthenticationRequest` DTO in the backend.
 */
export interface AuthenticationRequest {
  email: string;
  password: string;
}

/**
 * Defines the data structure for a new user registration request.
 * Mirrors the `RegisterRequest` DTO in the backend.
 */
export interface RegisterRequest {
  firstName: string;
  lastName: string;
  email: string;
  password: string;
}

/**
 * Defines the data structure for a user password change request.
 * Mirrors the `ChangePasswordRequest` DTO in the backend.
 */
export interface ChangePasswordRequest {
  currentPassword: string;
  newPassword: string;
  confirmationPassword: string;
}

/**
 * Defines the data structure for the response when a token is successfully refreshed.
 * Mirrors the `TokenRefreshResponse` DTO in the backend.
 */
export interface TokenRefreshResponse {
  accessToken: string;
}
