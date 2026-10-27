import {ChangeDetectionStrategy, Component, computed, effect, inject, signal} from '@angular/core';
import {toSignal} from '@angular/core/rxjs-interop';
import {FormBuilder, ReactiveFormsModule} from '@angular/forms';
import {TranslatePipe, TranslateService} from '@ngx-translate/core';
import {NgIconComponent} from '@ng-icons/core';
import {ScalingLevel, ScoreRequest, ScoreResponse} from '../../../../api/aldebaran/models/score.model';
import {UnitService} from '../../../../core/services/unit.service';
import {AccessService} from '../../../../core/services/access.service';
import {GymService} from '../../../../api/antares/services/gym.service';
import {DisplayName} from '../../../../api/antares/models/user.model';
import {WodScoresStore} from '../wod-scores.store';
import {SCALING_LEVELS, splitRounds} from '../score.utils';
import {formatTimeCapShort, localizedText} from '../../wod.utils';

/** Units offered for a distance score (stored in meters by Aldebaran). */
const DISTANCE_UNITS = ['METERS', 'KILOMETERS', 'MILES'];

/** Today in the browser's time zone, as Aldebaran expects it (yyyy-MM-dd). */
function today(): string {
  const now = new Date();
  return new Date(now.getTime() - now.getTimezoneOffset() * 60_000).toISOString().slice(0, 10);
}

/**
 * Log or edit a score on the WOD of the detail (modal): the current user's own score, or, for the
 * judge of a competition WOD (`capabilities.canJudge`), the score of a member of its gym. The fields
 * follow the WOD's score type; loads are typed in the user's unit and stored in kg by Aldebaran,
 * which also checks the value required by the score type and the right to log for someone else.
 */
@Component({
  selector: 'app-score-form',
  standalone: true,
  imports: [ReactiveFormsModule, TranslatePipe, NgIconComponent],
  templateUrl: './score-form.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ScoreFormComponent {
  // --- INJECTIONS ---
  readonly store = inject(WodScoresStore);
  private readonly fb = inject(FormBuilder);
  private readonly unitService = inject(UnitService);
  private readonly translate = inject(TranslateService);
  private readonly accessService = inject(AccessService);
  private readonly gymService = inject(GymService);

  // --- STATE (SIGNALS & VARIABLES) ---
  readonly scalingLevels = SCALING_LEVELS;
  readonly distanceUnits = DISTANCE_UNITS;
  readonly maxDate = today();
  /** How an AMRAP score with a fixed round is typed; both are stored as total reps. */
  readonly repsEntry = signal<'ROUNDS' | 'TOTAL'>('ROUNDS');
  /** Members of the competition WOD's gym, offered to its judge (Antares, SCORE_VERIFY). */
  readonly athletes = signal<DisplayName[]>([]);

  readonly form = this.fb.nonNullable.group({
    date: [today()],
    scaling: ['RX' as ScalingLevel],
    minutes: [null as number | null],
    seconds: [null as number | null],
    timeCapped: [false],
    rounds: [null as number | null],
    reps: [null as number | null],
    load: [null as number | null],
    distance: [null as number | null],
    distanceUnit: ['METERS'],
    calories: [null as number | null],
    tiebreakMinutes: [null as number | null],
    tiebreakSeconds: [null as number | null],
    // Gym where it was performed (public or personal WOD): one of the user's gyms, or none
    gymId: [(this.accessService.memberships()[0]?.gymId ?? null) as number | null],
    // Athlete of a new competition score logged by its judge; null = the current user
    athleteId: [null as number | null],
    scalingNotes: [''],
    userComment: ['']
  });

  // Form value as a signal, so that OnPush follows programmatic changes too (prefill)
  private readonly formValue = toSignal(this.form.valueChanges, {initialValue: this.form.getRawValue()});

  // --- COMPUTED STATE ---
  readonly timeCapped = computed(() => !!this.formValue().timeCapped);
  readonly isRx = computed(() => this.formValue().scaling === 'RX');
  readonly hasTiebreak = computed(() => !!(this.formValue().tiebreakMinutes || this.formValue().tiebreakSeconds));
  readonly valueMissing = computed(() => {
    this.formValue();
    return this.isValueMissing();
  });
  readonly scoreType = computed(() => this.store.wod()?.scoreType ?? 'NONE');
  readonly isEdit = computed(() => this.store.editing() !== 'new');
  /** The judge of a competition WOD logs a new score for one of its gym's members. */
  readonly canChooseAthlete = computed(() => !this.isEdit() && !!this.store.wod()?.capabilities?.canJudge);
  readonly myId = computed(() => this.accessService.userId());
  readonly massUnitLabel = computed(() => this.unitService.massUnit() === 'KG' ? 'kg' : 'lb');
  readonly timeCap = computed(() => formatTimeCapShort(this.store.wod()?.timeCapSeconds));
  /** A gym WOD is always scored in its gym; a public or personal WOD in one of the user's gyms. */
  readonly gymChoices = computed(() => this.store.wod()?.gymId != null ? [] : this.accessService.memberships());
  /** The gym is only asked to members of several gyms; a single gym is used automatically. */
  readonly showGymChoice = computed(() => this.gymChoices().length > 1);

  /** Reps of one round of the AMRAP, when its round is fixed. */
  readonly repsPerRound = computed(() => this.store.wod()?.repsPerRound ?? null);
  readonly byRounds = computed(() => !!this.repsPerRound() && this.repsEntry() === 'ROUNDS');
  /** The other way of writing the typed score: "= 312 reps" or "= 10 rounds + 12". */
  readonly conversion = computed(() => {
    const perRound = this.repsPerRound();
    const v = this.formValue();
    if (!perRound) return null;
    if (this.byRounds()) {
      if (v.rounds == null && v.reps == null) return null;
      return {total: (v.rounds ?? 0) * perRound + (v.reps ?? 0), rounds: null, reps: null};
    }
    const split = splitRounds(v.reps, perRound);
    return split ? {total: null, ...split} : null;
  });
  /** Rounds of the EMOM, the best possible ROUNDS score. */
  readonly maxRounds = computed(() => this.store.wod()?.emomRounds ?? null);
  /** Official tiebreak rule of the WOD, in the user's language; none for most WODs. */
  readonly tiebreakRule = computed(() => {
    const wod = this.store.wod();
    const lang = this.translate.currentLang() ?? this.translate.fallbackLang() ?? 'en';
    return localizedText(lang, wod?.tiebreakEn, wod?.tiebreakFr);
  });
  /** The tiebreak is optional and only offered when it applies: never for a finished For Time. */
  readonly showTiebreak = computed(() =>
    !!this.tiebreakRule() && (this.scoreType() !== 'TIME' || this.timeCapped()));

  // --- LIFECYCLE ---
  constructor() {
    effect(() => {
      const editing = this.store.editing();
      if (editing && editing !== 'new') this.fill(editing);
    });
    effect(() => {
      const gymId = this.store.wod()?.gymId;
      if (this.canChooseAthlete() && gymId != null) this.loadAthletes(gymId);
    });
  }

  // --- DATA FETCHING ---
  private loadAthletes(gymId: number): void {
    this.gymService.getAthletes(gymId).subscribe({
      next: (athletes) => this.athletes.set(athletes.filter(a => a.id !== this.myId())),
      error: () => this.athletes.set([])
    });
  }

  // --- ACTIONS & EVENTS ---
  /** Switches between rounds + reps and total reps, keeping the typed score. */
  setRepsEntry(mode: 'ROUNDS' | 'TOTAL'): void {
    const perRound = this.repsPerRound();
    if (!perRound || mode === this.repsEntry()) return;
    const v = this.form.getRawValue();
    if (mode === 'TOTAL') {
      const typed = v.rounds != null || v.reps != null;
      this.form.patchValue({rounds: null, reps: typed ? (v.rounds ?? 0) * perRound + (v.reps ?? 0) : null});
    } else {
      const split = splitRounds(v.reps, perRound);
      this.form.patchValue({rounds: split?.rounds ?? null, reps: split?.reps ?? null});
    }
    this.repsEntry.set(mode);
  }

  onSubmit(): void {
    const wod = this.store.wod();
    if (!wod || this.isValueMissing() || this.store.isSaving()) return;
    this.store.save(this.buildRequest(wod.id));
  }

  // --- HELPERS ---
  /** True while the value required by the score type is empty (Aldebaran checks it too). */
  isValueMissing(): boolean {
    const v = this.form.getRawValue();
    switch (this.scoreType()) {
      case 'TIME':
        return v.timeCapped ? v.reps == null : !((v.minutes ?? 0) * 60 + (v.seconds ?? 0));
      case 'ROUNDS': {
        const max = this.maxRounds();
        return v.rounds == null || (max != null && v.rounds > max);
      }
      case 'REPS':
        return this.byRounds() ? v.rounds == null && v.reps == null : v.reps == null;
      case 'WEIGHT':
      case 'LOAD':
        return !v.load;
      case 'CALORIES':
        return v.calories == null;
      case 'DISTANCE':
        return v.distance == null;
      default:
        return false;
    }
  }

  private fill(score: ScoreResponse): void {
    const load = score.maxWeight ?? score.totalLoad;
    const split = this.scoreType() === 'REPS' ? splitRounds(score.reps, this.repsPerRound()) : null;
    this.form.setValue({
      date: score.date,
      scaling: score.scaling,
      minutes: score.timeMinutesPart ?? null,
      seconds: score.timeSecondsPart ?? null,
      timeCapped: score.timeCapped,
      rounds: split ? split.rounds : score.rounds ?? null,
      reps: split ? split.reps : score.reps ?? null,
      load: load != null ? this.unitService.convertScoreLoad(load, score.weightUnit) : null,
      distance: score.totalDistance ?? null,
      distanceUnit: score.distanceUnit && DISTANCE_UNITS.includes(score.distanceUnit) ? score.distanceUnit : 'METERS',
      calories: score.totalCalories ?? null,
      tiebreakMinutes: score.tiebreakSeconds ? Math.floor(score.tiebreakSeconds / 60) : null,
      tiebreakSeconds: score.tiebreakSeconds ? score.tiebreakSeconds % 60 : null,
      gymId: score.gymId ?? null,
      athleteId: null,
      scalingNotes: score.scalingNotes ?? '',
      userComment: score.userComment ?? ''
    });
  }

  private buildRequest(wodId: number): ScoreRequest {
    const v = this.form.getRawValue();
    const type = this.scoreType();
    const capped = type === 'TIME' && v.timeCapped;
    const tiebreak = (v.tiebreakMinutes ?? 0) * 60 + (v.tiebreakSeconds ?? 0);
    const request: ScoreRequest = {
      userId: this.canChooseAthlete() && v.athleteId != null ? v.athleteId : undefined,
      date: v.date,
      wodId,
      scaling: v.scaling,
      timeCapped: capped,
      gymId: this.gymChoices().length > 0 ? v.gymId : null,
      tiebreakSeconds: this.showTiebreak() && tiebreak > 0 ? tiebreak : undefined,
      scalingNotes: v.scaling === 'RX' ? undefined : v.scalingNotes.trim() || undefined,
      userComment: v.userComment.trim() || undefined
    };
    switch (type) {
      case 'TIME':
        return capped
          ? {...request, reps: v.reps ?? undefined}
          : {...request, timeMinutes: v.minutes ?? 0, timeSeconds: v.seconds ?? 0};
      case 'ROUNDS':
        return {...request, rounds: v.rounds ?? undefined};
      case 'REPS':
        // Rounds + reps are converted to total reps by Aldebaran
        return this.byRounds()
          ? {...request, rounds: v.rounds ?? 0, reps: v.reps ?? 0}
          : {...request, reps: v.reps ?? undefined};
      case 'WEIGHT':
        return {...request, maxWeight: v.load ?? undefined, weightUnit: this.unitService.massUnit()};
      case 'LOAD':
        return {...request, totalLoad: v.load ?? undefined, weightUnit: this.unitService.massUnit()};
      case 'CALORIES':
        return {...request, totalCalories: v.calories ?? undefined};
      case 'DISTANCE':
        return {...request, totalDistance: v.distance ?? undefined, distanceUnit: v.distanceUnit};
      default:
        return request;
    }
  }
}
