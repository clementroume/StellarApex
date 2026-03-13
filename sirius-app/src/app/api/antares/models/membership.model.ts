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
}

export interface MembershipUpdateRequest {
  status: MembershipStatus;
  gymRole: GymRole;
  permissions: Permission[];
}

export interface MembershipResponse {
  id: number;
  user: UserSummary;
  gymRole: GymRole;
  status: MembershipStatus;
  permissions: Permission[];
}
