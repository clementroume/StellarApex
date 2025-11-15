/**
 * This file contains the data transfer object (DTO) interfaces that define the contract
 * for user-related operations between the Angular frontend and the Spring Boot backend.
 */

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
 * Defines the data structure for a user login request.
 * Mirrors the `AuthenticationRequest` DTO in the backend.
 */
export interface AuthenticationRequest {
  email: string;
  password: string;
}

/**
 * Defines the data structure for a user profile update request.
 * Mirrors the `ProfileUpdateRequest` DTO in the backend.
 */
export interface ProfileUpdateRequest {
  firstName: string;
  lastName: string;
  email: string;
}

/**
 * Defines the data structure for a user's preferences update request.
 * Mirrors the `PreferencesUpdateRequest` DTO in the backend.
 */
export interface PreferencesUpdateRequest {
  locale: 'en' | 'fr';
  theme: 'light' | 'dark';
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

/**
 * Represents the public-facing user data returned from the backend.
 * This is the primary model for representing a user within the frontend application.
 * Mirrors the `UserResponse` DTO in the backend.
 */
export interface User {
  id: number;
  firstName: string;
  lastName: string;
  email: string;
  role: 'ROLE_USER' | 'ROLE_ADMIN';
  enabled: boolean;
  locale: string;
  theme: 'light' | 'dark';
  createdAt: string; // Using string for ISO date format
  updatedAt: string; // Using string for ISO date format
}
