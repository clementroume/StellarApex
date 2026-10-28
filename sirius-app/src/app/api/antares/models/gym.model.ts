import {UserSummary} from './user.model';

export type GymStatus = 'PENDING_APPROVAL' | 'ACTIVE' | 'REJECTED' | 'SUSPENDED';
/** Search filter: physical box or virtual programming. */
export type GymType = 'BOX' | 'PROGRAM';

/** Creation request: any user, PENDING_APPROVAL until a platform admin approves it. */
export interface GymRequest {
  name: string;
  description?: string;
  isProgramming: boolean;
}

export interface GymUpdateRequest {
  name: string;
  description?: string | null;
}

export interface GymResponse {
  id: number;
  name: string;
  description?: string;
  isProgramming: boolean;
  isAutoSubscription: boolean;
  status: GymStatus;
  createdAt: string;
}

/** What any user sees of an ACTIVE gym when looking for one to join (never its code). */
export interface GymPublicResponse {
  id: number;
  name: string;
  description?: string | null;
  isProgramming: boolean;
}

/** Settings view of the staff holding MANAGE_SETTINGS. */
export interface GymSettingsResponse {
  id: number;
  name: string;
  description?: string | null;
  isProgramming: boolean;
  status: GymStatus;
  enrollmentCode: string;
  isAutoSubscription: boolean;
}

export interface GymSettingsRequest {
  enrollmentCode: string;
  isAutoSubscription: boolean;
}

/** A gym as platform admins review it, with its owner/programmer. */
export interface AdminGymResponse {
  id: number;
  name: string;
  description?: string | null;
  isProgramming: boolean;
  status: GymStatus;
  createdAt: string;
  holder: UserSummary | null;
  memberCount: number;
}

export interface JoinGymRequest {
  gymId: number;
  enrollmentCode: string;
}

export interface TransferOwnershipRequest {
  membershipId: number;
}
