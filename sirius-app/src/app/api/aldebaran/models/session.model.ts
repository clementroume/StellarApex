import {ScoreResponse} from './score.model';
import {WodRequest, WodResponse} from './wod.model';

/** WOD session (a day, several slots) or specialty session (a week, dated by its Monday). */
export type SessionType = 'WOD' | 'SPECIALTY';
export type Discipline = 'CROSSFIT' | 'WEIGHTLIFTING' | 'STRENGTH' | 'GYMNASTICS' | 'ENDURANCE' | 'HYROX'
  | 'MOBILITY' | 'BODYBUILDING' | 'STRONGMAN' | 'OTHER';
/** Warm-up and cool down are FR/EN text; the other blocks carry a WOD. */
export type BlockKind = 'WARM_UP' | 'SKILL' | 'WOD' | 'ACCESSORY' | 'FINISHER' | 'COOL_DOWN';
export type Stimulus = 'STRENGTH' | 'POWER' | 'SKILL' | 'AEROBIC' | 'THRESHOLD' | 'ANAEROBIC' | 'MIXED' | 'MOBILITY';
/** When the content opens to the gym's athletes; a program only uses ADVANCE and DAY_OF. */
export type VisibilityPolicy = 'ADVANCE' | 'DAY_OF' | 'AFTER' | 'AFTER_PARTICIPANTS' | 'PARTICIPANTS_ONLY';
export type SessionScope = 'ALL' | 'MINE' | 'GYM';

export const TEXT_KINDS: BlockKind[] = ['WARM_UP', 'COOL_DOWN'];
/** Policies that need an instant chosen by the staff (gym's local date-time). */
export const TIMED_POLICIES: VisibilityPolicy[] = ['ADVANCE', 'DAY_OF'];

/** Rights of the current user on a session, computed by Aldebaran (SessionSecurity). */
export interface SessionCapabilities {
  canModify: boolean;
  // Someone logged the session: blocks are frozen, visibility stays editable
  locked: boolean;
  contentVisible: boolean;
  // "Séance faite" available now (frame readable, first day reached, not logged yet)
  canLog: boolean;
}

export interface SessionBlockRequest {
  // Existing block on update, absent for a new one
  id?: number | null;
  kind: BlockKind;
  label?: string | null;
  themeEn?: string | null;
  themeFr?: string | null;
  optional: boolean;
  // Null: derived by Aldebaran
  stimulus?: Stimulus | null;
  plannedMinutes?: number | null;
  textEn?: string | null;
  textFr?: string | null;
  // Physical box only: the block's WOD becomes a competition WOD (a borrowed WOD is copied)
  competition: boolean;
  // Exactly one for a non-text block: a borrowed standalone WOD, or the WOD created / updated inline
  wodId?: number | null;
  wod?: WodRequest | null;
}

export interface SessionRequest {
  type: SessionType;
  discipline: Discipline;
  // Day of a WOD session; any day of the week of a specialty
  date: string;
  title?: string | null;
  descriptionEn?: string | null;
  descriptionFr?: string | null;
  // Null for a personal session; ignored on update
  gymId?: number | null;
  // Null: the gym's default
  visibilityPolicy?: VisibilityPolicy | null;
  // Local date-time (yyyy-MM-ddTHH:mm) for ADVANCE / DAY_OF
  contentVisibleAt?: string | null;
  blocks: SessionBlockRequest[];
}

export interface SessionVisibilityRequest {
  visibilityPolicy: VisibilityPolicy;
  contentVisibleAt?: string | null;
}

export interface SessionBlockResponse {
  id: number;
  orderIndex: number;
  kind: BlockKind;
  optional: boolean;
  themeEn?: string | null;
  themeFr?: string | null;
  stimulus?: Stimulus | null;
  plannedMinutes?: number | null;
  // Null while the content is hidden from the user
  label?: string | null;
  textEn?: string | null;
  textFr?: string | null;
  wod?: WodResponse | null;
  // Borrowed standalone WOD (scores join its leaderboard)
  borrowed: boolean;
  competition: boolean;
}

export interface BlockDone {
  blockId: number;
  kind: BlockKind;
  done: boolean;
}

export interface SessionLogResponse {
  id: number;
  userId: number;
  sessionId: number;
  sessionType: SessionType;
  discipline: Discipline;
  sessionDate: string;
  sessionTitle?: string | null;
  gymId?: number | null;
  // Day the athlete did the session
  date: string;
  source: 'MANUAL' | 'PLANNING';
  blocks: BlockDone[];
  scores: ScoreResponse[];
}

export interface SessionResponse {
  id: number;
  type: SessionType;
  discipline: Discipline;
  date: string;
  endDate: string;
  title?: string | null;
  descriptionEn?: string | null;
  descriptionFr?: string | null;
  gymId?: number | null;
  authorId: number;
  visibilityPolicy?: VisibilityPolicy | null;
  // Instant the content opens to the whole gym (ISO), null for participants only / personal
  contentVisibleFrom?: string | null;
  createdAt: string;
  updatedAt: string;
  blocks: SessionBlockResponse[];
  capabilities: SessionCapabilities;
  myLog?: SessionLogResponse | null;
}

export interface BlockFrame {
  kind: BlockKind;
  optional: boolean;
  themeEn?: string | null;
  themeFr?: string | null;
}

/** Frame of a session for lists (week planning, Today page): never any block content. */
export interface SessionSummaryResponse {
  id: number;
  type: SessionType;
  discipline: Discipline;
  date: string;
  endDate: string;
  title?: string | null;
  gymId?: number | null;
  authorId: number;
  visibilityPolicy?: VisibilityPolicy | null;
  contentVisibleFrom?: string | null;
  blocks: BlockFrame[];
  contentVisible: boolean;
  loggedByMe: boolean;
}

export interface GymSessionSettings {
  defaultVisibility: VisibilityPolicy;
  // IANA time zone (e.g. Europe/Paris)
  timeZone: string;
}

export interface SessionReferenceData {
  types: SessionType[];
  disciplines: Discipline[];
  kinds: BlockKind[];
  stimuli: Stimulus[];
  visibilityPolicies: VisibilityPolicy[];
}
