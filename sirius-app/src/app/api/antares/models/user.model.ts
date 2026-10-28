import {MembershipSummary} from './membership.model';

/** Display unit for loads: METRIC (kg) or IMPERIAL (lb). */
export type UnitSystem = 'METRIC' | 'IMPERIAL';

export type PlatformRole = 'USER' | 'ADMIN';
/** Competition division of the athlete profile: men's or women's loads, separate leaderboards. */
export type Division = 'MEN' | 'WOMEN';
export const DIVISIONS: Division[] = ['MEN', 'WOMEN'];
export type Theme = 'light' | 'dark';
/** UI colour palette, combined with the theme (ocean is the default). */
export type Palette = 'ocean' | 'chalk' | 'volt' | 'rig';
export const PALETTES: Palette[] = ['ocean', 'chalk', 'volt', 'rig'];

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
  // Address confirmed through the mailed link (needed to join or request a box/prog)
  emailVerified: boolean;
  platformRole: PlatformRole;
  memberships: MembershipSummary[];
  locale: string;
  theme: Theme;
  palette?: Palette;
  // Display unit for loads; null follows the language (fr: kg)
  unitSystem?: UnitSystem | null;
  // Name shown on public leaderboards (members of the user's gyms always see it)
  publicLeaderboardName?: boolean;
  // Athlete profile, all optional; birth year and height are private
  division?: Division | null;
  birthYear?: number | null;
  heightCm?: number | null;
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
  // Omitted (undefined) keeps the current palette
  palette?: Palette;
  unitSystem?: UnitSystem | null;
  // Omitted (undefined) keeps the current choice
  publicLeaderboardName?: boolean;
}

/** Athlete profile; null clears a field. */
export interface AthleteProfileRequest {
  division: Division | null;
  birthYear: number | null;
  heightCm: number | null;
}

/** A body weight measure (private), always in kilograms. */
export interface BodyWeightResponse {
  id: number;
  measuredOn: string;
  weightKg: number;
}

export interface BodyWeightRequest {
  weightKg: number;
  // Today when omitted
  measuredOn?: string | null;
}

/** "Clément R.": a name the current user may see (gym mates, opted-in users, themselves). */
export interface DisplayName {
  id: number;
  displayName: string;
}
