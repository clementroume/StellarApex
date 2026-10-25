import {SupportActor, UserSummary} from './user.model';

export interface SupportSessionRequest {
  userId: number;
  reason: string;
}

export interface SupportSessionResponse {
  id: number;
  actor: SupportActor;
  target: UserSummary;
  reason: string;
  startedAt: string;
  expiresAt: string;
}

/** ProblemDetail `code` values returned by Antares for support sessions. */
export const SUPPORT_SESSION_EXPIRED = 'SUPPORT_SESSION_EXPIRED';
export const SUPPORT_SESSION_FORBIDDEN = 'SUPPORT_SESSION_FORBIDDEN';
