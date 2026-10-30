import {ChangeDetectionStrategy, Component, computed, inject, input} from '@angular/core';
import {RouterLink} from '@angular/router';
import {TranslatePipe, TranslateService} from '@ngx-translate/core';
import {HlmBadge} from '@spartan-ng/helm/badge';
import {WodMovementResponse, WodResponse, WodSegment} from '../../../api/aldebaran/models/wod.model';
import {MeasurePair, UnitService} from '../../../core/services/unit.service';
import {AuthService} from '../../../api/antares/services/auth.service';
import {formatDuration, formatTimeCapShort, localizedText} from '../wod.utils';

// Rep schemes that only restate the format ("AMRAP 20 min", "For time", "Tabata x 4")
const FORMAT_SCHEME = /^(amrap|emom|tabata|for time)\b/i;
// Rep schemes that describe the structure rather than list the movement quantities
const STRUCTURE_SCHEME = /round|tier|window|rm\b| x /i;

/**
 * The prescription of a WOD as written on the box whiteboard: format and duration, time cap, rep
 * scheme, then one line per movement (quantity, name, variants, men/women load with the user's
 * division highlighted, notes and scaling). Used by the WOD detail and by each block of a session.
 */
@Component({
  selector: 'app-wod-whiteboard',
  standalone: true,
  imports: [RouterLink, TranslatePipe, HlmBadge],
  templateUrl: './wod-whiteboard.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class WodWhiteboardComponent {
  // --- INJECTIONS ---
  private readonly translate = inject(TranslateService);
  private readonly unitService = inject(UnitService);
  private readonly authService = inject(AuthService);
  readonly wod = input.required<WodResponse>();

  // --- COMPUTED STATE ---
  readonly activeLang = computed(() => this.translate.currentLang() ?? this.translate.fallbackLang() ?? 'en');
  readonly sortedMovements = computed<WodMovementResponse[]>(() =>
    [...(this.wod().movements ?? [])].sort((a, b) => a.orderIndex - b.orderIndex)
  );
  /** Movements by part of the WOD: buy-in, rounds, buy-out (only the parts that have movements). */
  readonly segments = computed<{segment: WodSegment; movements: WodMovementResponse[]}[]>(() =>
    (['BUY_IN', 'MAIN', 'BUY_OUT'] as WodSegment[])
      .map(segment => ({
        segment,
        movements: this.sortedMovements().filter(wm => (wm.segment ?? 'MAIN') === segment)
      }))
      .filter(part => part.movements.length > 0)
  );

  // --- HELPERS ---
  /**
   * First line of the whiteboard: "5 rounds for time", "Death by", "For Load · Complex", the format with
   * its duration when the duration is the workout itself ("AMRAP 20 minutes", "EMOM 10 minutes", "EMOM
   * 5 x 2:00"), else the format alone ("For Time").
   */
  formatLine(wod: WodResponse): string {
    const format = this.translate.instant('WOD.WOD_TYPES.' + wod.wodType);
    if (wod.rounds && wod.rounds > 1 && wod.wodType === 'FOR_TIME') {
      return this.translate.instant('WOD.BOARD.ROUNDS_FOR_TIME', {count: wod.rounds});
    }
    if (wod.structure === 'DEATH_BY') {
      return this.translate.instant('WOD.STRUCTURES.EMOM.DEATH_BY');
    }
    if (wod.structure === 'COMPLEX') {
      return `${format} · ${this.translate.instant('WOD.STRUCTURES.FOR_LOAD.COMPLEX')}`;
    }
    if (wod.structure === 'LADDER' && wod.ladderStart == null && wod.wodType === 'FOR_TIME') {
      return `${format} · ${this.translate.instant('WOD.STRUCTURES.FOR_TIME.LADDER')}`;
    }
    if (wod.workSeconds) {
      // Tabata 8 x 20s / 10s, Intervals 5 x (3:00 on / 1:00 off)
      const rounds = wod.emomRounds ? `${wod.emomRounds} x ` : '';
      const rest = wod.restSeconds ? ` / ${this.seconds(wod.restSeconds)}` : '';
      return `${format} ${rounds}${this.seconds(wod.workSeconds)}${rest}`;
    }
    if (wod.emomInterval && wod.emomRounds) {
      return wod.emomInterval === 60
        ? `${format} ${this.minutes(wod.emomRounds * 60)}`
        : `${format} ${wod.emomRounds} x ${formatDuration(wod.emomInterval)}`;
    }
    if (wod.wodType === 'AMRAP' && wod.timeCapSeconds) return `${format} ${this.minutes(wod.timeCapSeconds)}`;
    const scheme = wod.repScheme?.trim() ?? '';
    // "EMOM 30 min", "Tabata x 4": the rep scheme already is the format line
    return FORMAT_SCHEME.test(scheme) && !/^for time$/i.test(scheme) ? scheme : format;
  }

  /**
   * Rep scheme line, only when it adds something: the structure ("5 rounds", "3 x (15 / 30 / 15)") or
   * reps shared by the movements ("21-15-9"). A restated format or a list of the movement quantities
   * ("1 mile / 100 / 200 / 300 / 1 mile") is left out, the movement lines already say it.
   */
  repSchemeLine(wod: WodResponse): string {
    const scheme = wod.repScheme?.trim() ?? '';
    if (!scheme || FORMAT_SCHEME.test(scheme)) return '';
    const shared = wod.movements.some(wm => wm.repsScheme === wod.repScheme);
    return shared || STRUCTURE_SCHEME.test(scheme) ? scheme : '';
  }

  /**
   * Structure the format line does not say: an ascending ladder ("3-6-9-12..."), the progression
   * of a Death by ("1 rep the 1st minute, +1 each minute").
   */
  structureLine(wod: WodResponse): string {
    if (wod.structure === 'DEATH_BY') {
      return this.translate.instant('WOD.BOARD.DEATH_BY', {start: wod.ladderStart ?? 1, step: wod.ladderStep ?? 1});
    }
    if (wod.ladderStart && wod.ladderStep) {
      const rungs = [0, 1, 2, 3].map(i => (wod.ladderStart as number) + i * (wod.ladderStep as number));
      return `${rungs.join('-')}…`;
    }
    return '';
  }

  /** Rest between rounds ("Rest 2:00 between rounds"), not for intervals whose rest is on the format line. */
  restLabel(wod: WodResponse): string {
    if (!wod.restSeconds || wod.workSeconds) return '';
    return this.translate.instant('WOD.BOARD.REST', {rest: this.seconds(wod.restSeconds)});
  }

  /** Weight vest worn for the whole WOD, men's / women's in the user's units. */
  vest(wod: WodResponse): MeasurePair | null {
    return this.unitService.loadPair(wod.vestWeight, wod.vestWeightFemale, wod.vestWeightUnit,
      this.authService.currentUser()?.division);
  }

  /** "Time cap 15'", unless the time cap is already the duration of the format line (AMRAP). */
  timeCapLabel(wod: WodResponse): string {
    const cap = formatTimeCapShort(wod.timeCapSeconds);
    if (!cap || wod.wodType === 'AMRAP') return '';
    return `${this.translate.instant('WOD.FIELDS.TIME_CAP')} ${cap}`;
  }

  /**
   * Quantity written before the movement on the whiteboard: its sets and reps ("5 x 5"; reps omitted
   * when they repeat the WOD's rep scheme, as in "21-15-9 / Thruster / Pull-up"), men's/women's
   * calories or distance; the duration only when nothing else is given.
   */
  quantity(wm: WodMovementResponse, wod: WodResponse): string {
    // "max" is written "Max" on the board
    const reps = wm.repsScheme && wm.repsScheme !== wod.repScheme ? wm.repsScheme.replace(/^max\b/, 'Max') : '';
    const calories = [wm.calories, wm.caloriesFemale].filter(v => v != null).join('/');
    const parts = [
      wm.sets ? (reps ? `${wm.sets} x ${reps}` : `${wm.sets} x`) : reps,
      calories ? `${calories} cal` : '',
      this.unitService.formatDistance(wm.distance, null, wm.distanceUnit)
    ].filter(Boolean);
    if (parts.length === 0 && wm.durationSeconds) parts.push(formatDuration(wm.durationSeconds));
    return parts.join(' ');
  }

  /** Equipment and techniques of the movement in the WOD, e.g. "Rings, Strict". */
  variants(wm: WodMovementResponse): string {
    return [
      ...(wm.equipment ?? []).map(eq => this.translate.instant('MOVEMENT.EQUIPMENT.' + eq)),
      ...(wm.techniques ?? []).map(tech => this.translate.instant('MOVEMENT.TECHNIQUES.' + tech))
    ].join(', ');
  }

  /**
   * Men's / women's load and height in the user's units, written after the movement: "61/43 kg",
   * "60/50 cm", the value of the user's division highlighted (D31).
   */
  measures(wm: WodMovementResponse): MeasurePair[] {
    const division = this.authService.currentUser()?.division;
    return [
      this.unitService.loadPair(wm.weight, wm.weightFemale, wm.weightUnit, division),
      this.unitService.distancePair(wm.height, wm.heightFemale, wm.heightUnit, division)
    ].filter((pair): pair is MeasurePair => pair != null);
  }

  movementNotes(wm: WodMovementResponse): string {
    return localizedText(this.activeLang(), wm.notesEn, wm.notesFr);
  }

  scaling(wm: WodMovementResponse): string {
    return localizedText(this.activeLang(), wm.scalingOptionsEn, wm.scalingOptionsFr);
  }

  /** "20s" under a minute, "3:00" from a minute. */
  private seconds(value: number): string {
    return value < 60 ? `${value}s` : formatDuration(value);
  }

  /** "20 minutes", or "7'05" for a duration that is not a whole number of minutes. */
  private minutes(seconds: number): string {
    return seconds % 60 === 0
      ? this.translate.instant('WOD.FIELDS.DURATION_MINUTES', {count: seconds / 60})
      : formatTimeCapShort(seconds);
  }
}
