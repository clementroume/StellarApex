/**
 * This file contains the data transfer object (DTO) interfaces that define the contract
 * for user-related operations between the Angular frontend and the Spring Boot backend.
 */
import {MembershipSummary} from './membership.model';

export type PlatformRole = 'USER' | 'ADMIN';
export type Theme = 'light' | 'dark';

/**
 * Represents the public-facing user data returned from the backend.
 * This is the primary model for representing a user within the frontend application.
 * Mirrors the `UserResponse` DTO in the backend.
 */
export interface UserResponse {
  id: number;
  firstName: string;
  lastName: string;
  email: string;
  platformRole: PlatformRole;
  memberships: MembershipSummary[];
  locale: string;
  theme: Theme;
  createdAt: string;
}

/**
 * A lightweight representation of a user.
 */
export interface UserSummary {
  id: number;
  firstName: string;
  lastName: string;
  email: string;
  platformRole: PlatformRole;
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
  locale: string;
  theme: Theme;
}
