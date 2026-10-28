import {UserSummary} from './user.model';
import {GymStatus} from './gym.model';

export type GymRole = 'OWNER' | 'PROGRAMMER' | 'COACH' | 'ATHLETE';
export type MembershipStatus = 'PENDING' | 'ACTIVE' | 'INACTIVE' | 'BANNED';
export type Permission = 'WOD_WRITE' | 'SCORE_VERIFY' | 'MANAGE_MEMBERSHIPS' | 'MANAGE_SETTINGS';

export interface MembershipSummary {
  gymId: number;
  gymName: string;
  gymStatus: GymStatus;
  gymRole: GymRole;
  status: MembershipStatus;
  // Granted permissions, ACTIVE memberships only (OWNER/PROGRAMMER: all, COACH: granted ones, ATHLETE: none)
  permissions: Permission[];
  // Virtual programming (no competitions, no WOD copies), as opposed to a physical box
  gymProgramming: boolean;
  // Opens access to the gym (Antares Membership.isUsable): the memberships sent to Aldebaran
  usable: boolean;
}

export interface MembershipUpdateRequest {
  status: MembershipStatus;
  gymRole: GymRole;
  permissions: Permission[];
}

/** What the current user may do with a membership, computed by Antares (never recomputed here). */
export interface MembershipCapabilities {
  canChangeStatus: boolean;
  canChangeRole: boolean;
  canChangePermissions: boolean;
  canRemove: boolean;
  canBan: boolean;
  canBecomeHolder: boolean;
}

export interface MembershipResponse {
  id: number;
  user: UserSummary;
  gymRole: GymRole;
  status: MembershipStatus;
  permissions: Permission[];
  createdAt: string;
  // Null in answers the client reloads anyway (e.g. after an ownership transfer)
  capabilities: MembershipCapabilities | null;
}
