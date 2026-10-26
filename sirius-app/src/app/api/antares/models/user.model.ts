import {MembershipSummary} from './membership.model';

/** Display unit for loads: METRIC (kg) or IMPERIAL (lb). */
export type UnitSystem = 'METRIC' | 'IMPERIAL';

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
  // Display unit for loads; null follows the language (fr: kg)
  unitSystem?: UnitSystem | null;
  // Name shown on public leaderboards (members of the user's gyms always see it)
  publicLeaderboardName?: boolean;
  createdAt: string;
  // Set when a platform admin is acting as this user through a support session
  supportSession?: SupportSessionInfo | null;
}

export interface SupportActor {
  id: number;
  firstName: string;
  lastName: string;
}

export interface SupportSessionInfo {
  id: number;
  actor: SupportActor;
  reason: string;
  expiresAt: string;
}

export interface ProfileUpdateRequest {
  firstName: string;
  lastName: string;
  email: string;
}

export interface PreferencesUpdateRequest {
  locale: string;
  theme: Theme;
  unitSystem?: UnitSystem | null;
  // Omitted (undefined) keeps the current choice
  publicLeaderboardName?: boolean;
}

/** "Clément R.": a name the current user may see (gym mates, opted-in users, themselves). */
export interface DisplayName {
  id: number;
  displayName: string;
}
