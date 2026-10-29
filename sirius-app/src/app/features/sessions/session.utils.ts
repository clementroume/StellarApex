import {BlockKind, SessionSummaryResponse, SessionType} from '../../api/aldebaran/models/session.model';
import {localizedText} from '../wods/wod.utils';

/** A day in the browser's time zone as Aldebaran expects it (yyyy-MM-dd). */
export function localDate(date: Date = new Date()): string {
  return new Date(date.getTime() - date.getTimezoneOffset() * 60_000).toISOString().slice(0, 10);
}

/** The day `days` after (or before) a yyyy-MM-dd day. */
export function addDays(day: string, days: number): string {
  const date = new Date(`${day}T12:00:00`);
  date.setDate(date.getDate() + days);
  return localDate(date);
}

/** Monday of the week of a yyyy-MM-dd day (a specialty session is dated by it). */
export function mondayOf(day: string): string {
  const weekday = new Date(`${day}T12:00:00`).getDay(); // 0 = Sunday
  return addDays(day, weekday === 0 ? -6 : 1 - weekday);
}

/** ISO instant to a datetime-local value in the browser's time zone (yyyy-MM-ddTHH:mm). */
export function toLocalInput(instant?: string | null): string | null {
  if (!instant) return null;
  const date = new Date(instant);
  return new Date(date.getTime() - date.getTimezoneOffset() * 60_000).toISOString().slice(0, 16);
}

/** "mardi 3 novembre" / "Tuesday, November 3", in the language shown. */
export function dayLabel(day: string, lang: string, withYear = false): string {
  return new Date(`${day}T12:00:00`).toLocaleDateString(lang, {
    weekday: 'long', day: 'numeric', month: 'long', ...(withYear ? {year: 'numeric'} : {})
  });
}

/** "3 nov." / "Nov 3", for week ranges. */
export function shortDay(day: string, lang: string): string {
  return new Date(`${day}T12:00:00`).toLocaleDateString(lang, {day: 'numeric', month: 'short'});
}

/** The day of a WOD session, or the week of a specialty ("semaine du 2 nov. au 8 nov."). */
export function sessionDateLabel(
  session: {type: SessionType; date: string; endDate: string},
  lang: string,
  translate: (key: string, params?: object) => string
): string {
  return session.type === 'SPECIALTY'
    ? translate('SESSION.WEEK_OF', {from: shortDay(session.date, lang), to: shortDay(session.endDate, lang)})
    : dayLabel(session.date, lang);
}

/** Thematic summary of a block in the language shown, the other one as fallback. */
export function blockTheme(lang: string, block: {themeEn?: string | null; themeFr?: string | null}): string {
  return localizedText(lang, block.themeEn ?? undefined, block.themeFr ?? undefined);
}

/** Warm-up and cool down are text only (no WOD). */
export function isTextKind(kind: BlockKind): boolean {
  return kind === 'WARM_UP' || kind === 'COOL_DOWN';
}

/** Link to a session: personal sessions live in My training, gym sessions in My box. */
export function sessionLink(session: {id: number; gymId?: number | null}): string[] {
  return session.gymId == null ? ['/training/sessions', String(session.id)] : ['/box/sessions', String(session.id)];
}

/** Sessions of a list sorted for display: WOD sessions by day, specialties after them. */
export function sortSessions(sessions: SessionSummaryResponse[]): SessionSummaryResponse[] {
  return [...sessions].sort((a, b) =>
    a.type === b.type ? a.date.localeCompare(b.date) || a.id - b.id : a.type === 'WOD' ? -1 : 1);
}
