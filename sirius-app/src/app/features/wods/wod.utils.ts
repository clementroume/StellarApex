import {WodScaledLineRequest, WodScaledLineResponse} from '../../api/aldebaran/models/wod.model';


/** Formats a duration in seconds as "m:ss" (e.g. 754 -> "12:34"). */
export function formatDuration(totalSeconds: number | null | undefined): string {
  if (totalSeconds === null || totalSeconds === undefined || totalSeconds < 0) return '';
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  return `${minutes}:${seconds.toString().padStart(2, '0')}`;
}

/** Formats a time cap as written on a whiteboard: 900 -> "15'", 425 -> "7'05". */
export function formatTimeCapShort(totalSeconds: number | null | undefined): string {
  if (totalSeconds === null || totalSeconds === undefined || totalSeconds <= 0) return '';
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  return seconds === 0 ? `${minutes}'` : `${minutes}'${seconds.toString().padStart(2, '0')}`;
}

/** Text in the user's language, falling back on the other one (a WOD may be written in one language). */
export function localizedText(lang: string, en?: string | null, fr?: string | null): string {
  const text = lang === 'fr' ? (fr || en) : (en || fr);
  return text?.trim() ?? '';
}

/** "Girls / For Time": the category (when any) then the format, as written on a whiteboard. */
export function wodTypeLabel(translate: (key: string) => string, wod: {wodType: string; category?: string | null}): string {
  const format = translate('WOD.WOD_TYPES.' + wod.wodType);
  return wod.category ? `${translate('WOD.CATEGORIES.' + wod.category)} / ${format}` : format;
}

/** The request that keeps a line's official Scaled version as it is (editing a WOD, lot S). */
export function scaledRequestOf(scaled: WodScaledLineResponse | null | undefined): WodScaledLineRequest | null {
  if (!scaled) return null;
  return {
    movementId: scaled.movement.id,
    equipment: scaled.equipment ?? [],
    techniques: scaled.techniques ?? [],
    repsScheme: scaled.repsScheme ?? null,
    weight: scaled.weight ?? null,
    weightFemale: scaled.weightFemale ?? null,
    weightUnit: scaled.weightUnit ?? null,
    distance: scaled.distance ?? null,
    distanceUnit: scaled.distanceUnit ?? null,
    calories: scaled.calories ?? null,
    caloriesFemale: scaled.caloriesFemale ?? null,
    height: scaled.height ?? null,
    heightFemale: scaled.heightFemale ?? null,
    heightUnit: scaled.heightUnit ?? null,
    durationSeconds: scaled.durationSeconds ?? null
  };
}

/** A Scaled line request shaped as a response (a WOD written in a form filled again). */
export function scaledResponseOf(scaled: WodScaledLineRequest | null | undefined): WodScaledLineResponse | null {
  if (!scaled) return null;
  return {
    ...scaled,
    movement: {id: scaled.movementId, name: ''} as WodScaledLineResponse['movement'],
    equipment: scaled.equipment ?? [],
    techniques: scaled.techniques ?? []
  };
}
