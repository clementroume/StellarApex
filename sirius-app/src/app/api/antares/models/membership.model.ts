import {UserSummary} from './user.model';
import {GymStatus} from './gym.model';

export type GymRole = 'OWNER' | 'PROGRAMMER' | 'COACH' | 'ATHLETE';
export type MembershipStatus = 'PENDING' | 'ACTIVE' | 'INACTIVE' | 'BANNED';
export type Permission = 'WOD_WRITE' | 'SCORE_VERIFY' | 'MANAGE_MEMBERSHIPS' | 'MANAGE_SETTINGS';


/**
 * Detailed representation of a membership.
 * Mirrors `MembershipResponse`.
 */
export interface MembershipResponse {
  id: number;
  user: UserSummary;
  gymRole: GymRole;
  status: MembershipStatus;
  permissions: Permission[];
}

/**
 * Lightweight summary of a membership, useful for lists.
 */
export interface MembershipSummary {
  gymId: number;
  gymName: string;
  gymStatus: GymStatus;
  gymRole: GymRole;
  status: MembershipStatus;
}

/**
 * Payload for updating a membership (role, status, permissions).
 * Mirrors `MembershipUpdateRequest`.
 */
export interface MembershipUpdateRequest {
  status: MembershipStatus;
  gymRole: GymRole;
  permissions: Permission[];
}
