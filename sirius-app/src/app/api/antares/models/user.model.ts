import {MembershipSummary} from './membership.model';

export type PlatformRole = 'USER' | 'ADMIN';
export type Theme = 'light' | 'dark';

export interface UserSummary {
  id: number;
  firstName: string;
  lastName: string;
  email: string;
  platformRole: PlatformRole;
}

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

export interface ProfileUpdateRequest {
  firstName: string;
  lastName: string;
  email: string;
}

export interface PreferencesUpdateRequest {
  locale: string;
  theme: Theme;
}
