export type GymStatus = 'PENDING_APPROVAL' | 'ACTIVE' | 'REJECTED' | 'SUSPENDED';

/**
 * Payload for creating a new Gym.
 * Mirrors `GymRequest`.
 */
export interface GymRequest {
  name: string;
  description?: string;
  isProgramming: boolean;
  creationToken: string;
}

/**
 * Payload for a user joining a gym.
 * Mirrors `JoinGymRequest`.
 */
export interface JoinGymRequest {
  gymId: number;
  enrollmentCode: string;
}

/**
 * Represents the Gym data returned from the backend.
 * Mirrors `GymResponse`.
 */
export interface GymResponse {
  id: number;
  name: string;
  description?: string;
  isProgramming: boolean;
  isAutoSubscription: boolean;
  status: GymStatus;
  createdAt: string;
}

/**
 * Payload for updating gym settings.
 * Mirrors `GymSettingsRequest`.
 */
export interface GymSettingsRequest {
  enrollmentCode: string;
  isAutoSubscription: boolean;
}

