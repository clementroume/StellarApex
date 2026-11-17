import {ChangeDetectionStrategy, Component, computed, DestroyRef, DOCUMENT, effect, inject, signal, untracked} from '@angular/core';
import {toSignal} from '@angular/core/rxjs-interop';
import {FormArray, FormBuilder, FormControl, FormGroup, ReactiveFormsModule} from '@angular/forms';
import {TranslatePipe, TranslateService} from '@ngx-translate/core';
import {NgIconComponent} from '@ng-icons/core';
import {
  CardioEquivalent,
  ScoreLineRequest,
  ScoreRequest,
  ScoreResponse,
  ScoreSet
} from '../../../../api/aldebaran/models/score.model';
import {WodMovementResponse} from '../../../../api/aldebaran/models/wod.model';
import {UsualNameOption} from '../../../../api/aldebaran/models/movement.model';
import {UnitService} from '../../../../core/services/unit.service';
import {AccessService} from '../../../../core/services/access.service';
import {AuthService} from '../../../../api/antares/services/auth.service';
import {GymService} from '../../../../api/antares/services/gym.service';
import {MovementService} from '../../../../api/aldebaran/services/movement.service';
import {ScoreService} from '../../../../api/aldebaran/services/score.service';
import {DisplayName} from '../../../../api/antares/models/user.model';
import {WodScoresStore} from '../wod-scores.store';
import {splitRounds} from '../score.utils';
import {formatTimeCapShort, localizedText} from '../../wod.utils';
import {boardName} from '../../movement-board-name';
import {cardioQuantity, countedLines, hasOfficialScaled, isMaxLine, setLines, sortedLines} from './score-lines.utils';
import {HlmSpinner} from '@spartan-ng/helm/spinner';
import {NativeSelectDirective} from '../../../../shared/component/native-select/native-select.directive';
import {MovementComboboxComponent} from '../../../../shared/component/movement-combobox/movement-combobox.component';
import {HlmButton} from '@spartan-ng/helm/button';
import {HlmInput} from '@spartan-ng/helm/input';
import {HlmTextarea} from '@spartan-ng/helm/textarea';
import {CdkTrapFocus} from '@angular/cdk/a11y';

/** Score types that can be entered set by set (strength work, D14). */
const SET_SCORE_TYPES = ['WEIGHT', 'LOAD', 'NONE'];

/** Level chosen in the form: RX, the WOD's official Scaled version, or a Scaled of one's own (D140, D141). */
export type FormLevel = 'RX' | 'OFFICIAL' | 'SCALED';

/** One set row of the form: its line, reps, load in the user's unit, done or failed. */
type SetForm = FormGroup<{
  wodMovementId: FormControl<number | null>;
  reps: FormControl<number | null>;
  weight: FormControl<number | null>;
  completed: FormControl<boolean>;
}>;

/**
 * One line of the WOD as done (D142): changed or not, the variant done and its quantities (load and height in the
 * user's unit, distance and calories as typed). On a counted "max" line, `reps` holds the reps counted (D147).
 */
type LineForm = FormGroup<{
  wodMovementId: FormControl<number>;
  changed: FormControl<boolean>;
  movementId: FormControl<number>;
  equipment: FormControl<string[]>;
  techniques: FormControl<string[]>;
  reps: FormControl<string>;
  load: FormControl<number | null>;
  distance: FormControl<number | null>;
  distanceUnit: FormControl<string | null>;
  calories: FormControl<number | null>;
  height: FormControl<number | null>;
  duration: FormControl<number | null>;
}>;

/** Units offered for a distance score (stored in meters by Aldebaran). */
const DISTANCE_UNITS = ['METERS', 'KILOMETERS', 'MILES'];

/** Today in the browser's time zone, as Aldebaran expects it (yyyy-MM-dd). */
function today(): string {
  const now = new Date();
  return new Date(now.getTime() - now.getTimezoneOffset() * 60_000).toISOString().slice(0, 10);
}

/**
 * Log or edit a score on the WOD of the detail (modal): the current user's own score, or, for the judge of a
 * competition WOD (`capabilities.canJudge`), the score of a member of its gym. The level is RX (nothing changed),
 * the WOD's official Scaled version (lines prefilled) or a Scaled of one's own, detailed line by line: the movement
 * done (any catalog usual name), its reps, its real load (lot S, D140-D142). Strength sets are entered under their
 * line (D143). Loads are typed in the user's unit, prefilled as boxes load them; Aldebaran checks the level, the
 * value required by the score type and the right to log for someone else.
 */
@Component({
  selector: 'app-score-form',
  standalone: true,
  imports: [CdkTrapFocus, HlmTextarea, HlmInput, HlmButton, NativeSelectDirective, HlmSpinner, ReactiveFormsModule, TranslatePipe,
    NgIconComponent, MovementComboboxComponent],
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
  private readonly authService = inject(AuthService);
  private readonly gymService = inject(GymService);
  private readonly movementService = inject(MovementService);
  private readonly scoreService = inject(ScoreService);

  // --- STATE (SIGNALS & VARIABLES) ---
  readonly distanceUnits = DISTANCE_UNITS;
  readonly maxDate = today();
  /** How an AMRAP score with a fixed round is typed; both are stored as total reps. */
  readonly repsEntry = signal<'ROUNDS' | 'TOTAL'>('ROUNDS');
  /** Members of the competition WOD's gym, offered to its judge (Antares, SCORE_VERIFY). */
  readonly athletes = signal<DisplayName[]>([]);
  /** Team WOD: members of the box offered as teammates, and the ones chosen. */
  readonly gymMates = signal<DisplayName[]>([]);
  readonly teammates = signal<number[]>([]);
  /** Usual names of the catalog, to pick the movement done (loaded on the first change of a line). */
  readonly usualNames = signal<UsualNameOption[]>([]);
  /** Box tables between cardio machines (D151), to propose a substitution's quantity. */
  private readonly cardio = signal<CardioEquivalent[]>([]);

  readonly form = this.fb.nonNullable.group({
    date: [today()],
    level: ['RX' as FormLevel],
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
    userComment: [''],
    // Competition WOD: the athlete submits the score to the judges
    verificationRequested: [false],
    // The WOD's lines as done
    lines: this.fb.nonNullable.array<LineForm>([]),
    // Strength sets, set by set, each on its line (load in the user's unit)
    sets: this.fb.nonNullable.array<SetForm>([])
  });

  // Form value as a signal, so that OnPush follows programmatic changes too (prefill)
  private readonly formValue = toSignal(this.form.valueChanges, {initialValue: this.form.getRawValue()});

  // --- COMPUTED STATE ---
  readonly level = computed(() => this.formValue().level as FormLevel);
  readonly timeCapped = computed(() => !!this.formValue().timeCapped);
  readonly hasTiebreak = computed(() => !!(this.formValue().tiebreakMinutes || this.formValue().tiebreakSeconds));
  readonly valueMissing = computed(() => {
    this.formValue();
    return this.isValueMissing();
  });
  readonly scoreType = computed(() => this.store.wod()?.scoreType ?? 'NONE');
  readonly isEdit = computed(() => this.store.editing() !== 'new');
  /** Logged within a session journal: its day and gym apply. */
  readonly inSession = computed(() => this.store.sessionLogId() != null);
  /** The WOD's lines in order, and whether it has an official Scaled version (three levels then). */
  readonly wodLines = computed(() => sortedLines(this.store.wod()));
  readonly hasOfficial = computed(() => hasOfficialScaled(this.store.wod()));
  /** "max" lines whose reps are counted one by one (D147); the score is their sum. */
  readonly countedIds = computed(() => new Set(countedLines(this.store.wod()).map(l => l.id)));
  readonly countedTotal = computed(() => {
    if (this.countedIds().size === 0) return null;
    const counts = (this.formValue().lines ?? [])
      .filter(l => l.wodMovementId != null && this.countedIds().has(l.wodMovementId))
      .map(l => Number(l.reps));
    return counts.every(n => Number.isInteger(n) && n >= 0) ? counts.reduce((a, b) => a + b, 0) : null;
  });
  /** Set-by-set entry is offered for loads (and NONE, e.g. accessories), under each line or complex. */
  readonly canUseSets = computed(() => SET_SCORE_TYPES.includes(this.scoreType()));
  readonly setLines = computed(() => setLines(this.store.wod()));
  readonly setRows = computed(() => (this.formValue().sets ?? []) as Partial<ScoreSet>[]);
  readonly useSets = computed(() => this.setRows().length > 0);
  /** Performance derived from the sets, as Aldebaran computes it: best set, or the sum of each line's best (D145). */
  readonly setsValue = computed(() => {
    const best = new Map<number | null, number>();
    for (const r of this.setRows()) {
      if (r.completed === false || (r.reps ?? 0) <= 0 || r.weight == null) continue;
      const key = r.wodMovementId ?? null;
      best.set(key, Math.max(best.get(key) ?? 0, r.weight));
    }
    if (best.size === 0) return null;
    const values = [...best.values()];
    return this.scoreType() === 'LOAD' ? values.reduce((a, b) => a + b, 0) : Math.max(...values);
  });
  /** An athlete may submit their own score on a competition WOD (a judge's score is verified at once). */
  readonly canSubmit = computed(() => {
    if (!this.store.wod()?.competition || this.formValue().athleteId != null) return false;
    const editing = this.store.editing();
    return editing === 'new' || !editing?.verifiedAt;
  });
  /** The judge of a competition WOD logs a new score for one of its gym's members. */
  readonly canChooseAthlete = computed(() => !this.isEdit() && !!this.store.wod()?.capabilities?.canJudge);
  readonly myId = computed(() => this.accessService.userId());
  readonly massUnitLabel = computed(() => this.unitService.massUnit() === 'KG' ? 'kg' : 'lb');
  readonly heightUnitLabel = computed(() => this.unitService.unitSystem() === 'METRIC' ? 'cm' : 'in');
  readonly timeCap = computed(() => formatTimeCapShort(this.store.wod()?.timeCapSeconds));
  /** The athlete's division: the women's loads apply to women (a judge's score: the men's, the athlete corrects). */
  private readonly division = computed(() =>
    this.formValue().athleteId == null ? this.authService.currentUser()?.division ?? null : null);
  /**
   * A gym WOD is always scored in its gym; a public or personal WOD in one of the user's gyms, a
   * box for a team WOD (no team on a program).
   */
  readonly gymChoices = computed(() => {
    if (this.store.wod()?.gymId != null || this.inSession()) return [];
    const memberships = this.accessService.memberships();
    return this.isTeam() ? memberships.filter(m => !m.gymProgramming) : memberships;
  });
  /** The gym is only asked to members of several gyms; a single gym is used automatically. */
  readonly showGymChoice = computed(() => this.gymChoices().length > 1);

  /** Reps of one round as done: reduced reps make a shorter round (D147). */
  readonly repsPerRound = computed(() => {
    const perRound = this.store.wod()?.repsPerRound ?? null;
    if (!perRound) return null;
    let reps = perRound;
    for (const line of this.formValue().lines ?? []) {
      if (!line.changed || line.wodMovementId == null) continue;
      const rx = this.wodLines().find(l => l.id === line.wodMovementId);
      const before = Number(rx?.repsScheme);
      const after = Number(line.reps);
      if (Number.isInteger(before) && Number.isInteger(after) && rx?.repsScheme?.trim() && line.reps?.trim()) {
        reps += after - before;
      }
    }
    return reps > 0 ? reps : perRound;
  });
  /** Whether the round as done differs from the WOD's. */
  readonly roundChanged = computed(() => this.repsPerRound() !== (this.store.wod()?.repsPerRound ?? null));
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
  /** Team WOD (new score): teammates of the box are chosen, and get the same score. */
  readonly isTeam = computed(() => !this.isEdit() && !!this.store.wod()?.teamSize);
  readonly teamSize = computed(() => this.store.wod()?.teamSize ?? 0);
  /** Box where the team did the WOD: the WOD's, else the box chosen (or the only one). */
  private readonly teamGymId = computed(() => {
    const chosen = this.formValue().gymId;
    return this.store.wod()?.gymId ?? (this.gymChoices().some(c => c.gymId === chosen) ? chosen : null);
  });

  /** The tiebreak is optional and only offered when it applies: never for a finished For Time. */
  readonly showTiebreak = computed(() =>
    !!this.tiebreakRule() && (this.scoreType() !== 'TIME' || this.timeCapped()));

  // --- LIFECYCLE ---
  constructor() {
    // The dialog gives focus back to the button that opened it
    const opener = inject(DOCUMENT).activeElement as HTMLElement | null;
    inject(DestroyRef).onDestroy(() => opener?.focus());
    effect(() => {
      // One row per WOD line, as prescribed
      const lines = this.wodLines();
      untracked(() => this.resetLines(lines));
    });
    effect(() => {
      const editing = this.store.editing();
      if (editing && editing !== 'new') untracked(() => this.fill(editing));
    });
    effect(() => {
      const gymId = this.store.wod()?.gymId;
      if (this.canChooseAthlete() && gymId != null) this.loadAthletes(gymId);
    });
    effect(() => {
      // The chosen gym stays one of the choices (a team WOD drops the programs)
      const choices = this.gymChoices();
      const control = this.form.controls.gymId;
      if (choices.length > 0 && !choices.some(c => c.gymId === control.value)) {
        untracked(() => control.setValue(choices[0].gymId));
      }
    });
    effect(() => {
      const gymId = this.teamGymId();
      if (this.isTeam() && gymId != null) untracked(() => this.loadGymMates(gymId));
    });
    effect(() => {
      // The counted "max" lines make the total
      const total = this.countedTotal();
      if (total != null) untracked(() => this.form.controls.reps.setValue(total, {emitEvent: false}));
    });
  }

  // --- DATA FETCHING ---
  private loadAthletes(gymId: number): void {
    this.gymService.getAthletes(gymId).subscribe({
      next: (athletes) => this.athletes.set(athletes.filter(a => a.id !== this.myId())),
      error: () => this.athletes.set([])
    });
  }

  /** Members of the box where the team did the WOD (Antares lists them to its members). */
  private loadGymMates(gymId: number): void {
    this.teammates.set([]);
    this.gymService.getAthletes(gymId).subscribe({
      next: (mates) => this.gymMates.set(mates.filter(a => a.id !== this.myId())),
      error: () => this.gymMates.set([])
    });
  }

  /** The catalog's usual names and the cardio tables, once, when a line is first changed. */
  private loadCatalog(): void {
    if (this.usualNames().length > 0) return;
    this.movementService.getUsualNames().subscribe({next: list => this.usualNames.set(list), error: () => {}});
    this.scoreService.getReferenceData().subscribe({next: data => this.cardio.set(data.cardio ?? []), error: () => {}});
  }

  // --- ACTIONS & EVENTS ---
  /** Chooses the level: the official Scaled version prefills its lines, RX puts every line back. */
  setLevel(level: FormLevel): void {
    this.form.controls.level.setValue(level);
    this.lineControls().forEach((group, i) => {
      const rx = this.wodLines()[i];
      if (this.countedIds().has(rx.id)) return;
      if (level === 'OFFICIAL' && rx.scaled) {
        this.applyOfficial(group, rx);
      } else if (level !== 'SCALED') {
        this.resetLine(group, rx);
      }
    });
    if (level !== 'RX') this.loadCatalog();
  }

  /** Opens a line for changes (a Scaled of one's own), prefilled with the prescription. */
  changeLine(index: number): void {
    if (this.level() === 'OFFICIAL') this.form.controls.level.setValue('SCALED');
    const group = this.lineControls()[index];
    if (!group.controls.changed.value) {
      this.resetLine(group, this.wodLines()[index]);
      group.controls.changed.setValue(true);
    }
    this.loadCatalog();
  }

  /** Back to the prescription for one line. */
  restoreLine(index: number): void {
    this.resetLine(this.lineControls()[index], this.wodLines()[index]);
  }

  /** The movement done on a line: a usual name of the catalog; a cardio machine gets its quantity proposed (D151). */
  onLinePicked(index: number, option: UsualNameOption | null): void {
    if (!option) return;
    const group = this.lineControls()[index];
    const rx = this.wodLines()[index];
    group.patchValue({movementId: option.movementId, equipment: [...option.equipment], techniques: [...option.techniques]});
    const quantity = cardioQuantity(rx,
      {movementName: rx.movement.name, usualName: rx.display?.usualNameEn},
      {movementName: option.movementName, usualName: option.nameEn},
      this.cardio(), this.division() === 'WOMEN');
    if (quantity?.distance != null) group.patchValue({distance: quantity.distance, distanceUnit: quantity.distanceUnit ?? 'METERS', calories: null});
    if (quantity?.calories != null) group.patchValue({calories: quantity.calories, distance: null});
  }

  /** Adds or removes a teammate; at most the team size (the extra one mirrors a member). */
  toggleTeammate(id: number): void {
    this.teammates.update(ids => ids.includes(id)
      ? ids.filter(i => i !== id)
      : ids.length < this.teamSize() ? [...ids, id] : ids);
  }

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

  /** Starts a line's sets with its prescribed sets (e.g. 5 x 5), or adds one more set like the last. */
  addSet(line: WodMovementResponse): void {
    const sets = this.form.controls.sets;
    const own = sets.controls.filter(s => s.controls.wodMovementId.value === line.id);
    if (own.length === 0) {
      const reps = Number(line.repsScheme);
      const count = Math.min(Math.max(line.sets ?? 1, 1), 20);
      for (let i = 0; i < count; i++) {
        sets.push(this.setGroup({wodMovementId: line.id, reps: Number.isInteger(reps) ? reps : null}));
      }
    } else {
      const last = own[own.length - 1].getRawValue();
      sets.insert(sets.controls.indexOf(own[own.length - 1]) + 1,
        this.setGroup({wodMovementId: line.id, reps: last.reps, weight: last.weight}));
    }
  }

  removeSet(index: number): void {
    this.form.controls.sets.removeAt(index);
  }

  onSubmit(): void {
    const wod = this.store.wod();
    if (!wod || this.isValueMissing() || this.store.isSaving()) return;
    this.store.save(this.buildRequest(wod.id));
  }

  // --- HELPERS ---
  lineControls(): LineForm[] {
    return this.form.controls.lines.controls;
  }

  /** Indexes of the sets of a line, in order. */
  setIndexes(line: WodMovementResponse): number[] {
    return this.setRows().map((r, i) => r.wodMovementId === line.id ? i : -1).filter(i => i >= 0);
  }

  /** The best complete set of a line, in the user's unit, or null. */
  bestOf(line: WodMovementResponse): number | null {
    const loads = this.setRows()
      .filter(r => r.wodMovementId === line.id && r.completed !== false && (r.reps ?? 0) > 0 && r.weight != null)
      .map(r => r.weight as number);
    return loads.length ? Math.max(...loads) : null;
  }

  /** Board name of a line: "Thrusters", "Ring Rows". */
  lineName(line: WodMovementResponse): string {
    const reps = line.repsScheme?.trim();
    return boardName({name: line.movement.name, allowed: line.movement.equipment, equipment: line.equipment,
      techniques: line.techniques, display: line.display}, !!reps && !/^1(?![\d-])/.test(reps));
  }

  /** What a line prescribes: "21-15-9 · 43/29 kg"; and the official Scaled version of it, when shown. */
  linePrescription(line: WodMovementResponse): string {
    const division = this.authService.currentUser()?.division;
    const load = this.unitService.lineLoadPair(line, division);
    const height = this.unitService.lineHeightPair(line, division);
    const calories = [line.calories, line.caloriesFemale].filter(v => v != null).join('/');
    return [
      line.repsScheme?.replace(/^max\b/i, 'Max') ?? '',
      calories ? `${calories} cal` : '',
      this.unitService.formatDistance(line.distance, null, line.distanceUnit),
      load ? `${load.values.join('/')} ${load.unit}` : '',
      height ? `${height.values.join('/')} ${height.unit}` : ''
    ].filter(Boolean).join(' · ');
  }

  /** The official Scaled version of a line: "Jumping Pull-ups", "21 · 30/20 kg". */
  officialText(line: WodMovementResponse): string {
    const s = line.scaled;
    if (!s) return '';
    const name = boardName({name: s.movement.name, equipment: s.equipment, techniques: s.techniques, display: s.display}, true);
    const division = this.authService.currentUser()?.division;
    const load = this.unitService.lineLoadPair(s, division);
    return [name, s.repsScheme && s.repsScheme !== line.repsScheme ? s.repsScheme : '',
      load ? `${load.values.join('/')} ${load.unit}` : ''].filter(Boolean).join(' · ');
  }

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
        return this.useSets() ? this.setsValue() == null : !v.load;
      case 'CALORIES':
        return v.calories == null;
      case 'DISTANCE':
        return v.distance == null;
      default:
        return false;
    }
  }

  private resetLines(lines: WodMovementResponse[]): void {
    const array = this.form.controls.lines as FormArray<LineForm>;
    array.clear();
    for (const line of lines) {
      const group = this.lineGroup(line);
      array.push(group);
    }
  }

  private lineGroup(line: WodMovementResponse): LineForm {
    return new FormGroup({
      wodMovementId: new FormControl<number>(line.id, {nonNullable: true}),
      changed: new FormControl<boolean>(false, {nonNullable: true}),
      movementId: new FormControl<number>(line.movement.id, {nonNullable: true}),
      equipment: new FormControl<string[]>([...(line.equipment ?? [])], {nonNullable: true}),
      techniques: new FormControl<string[]>([...(line.techniques ?? [])], {nonNullable: true}),
      reps: new FormControl<string>(this.countedIds().has(line.id) ? '' : line.repsScheme ?? '', {nonNullable: true}),
      load: new FormControl<number | null>(this.unitService.lineLoadFor(line, this.division())),
      distance: new FormControl<number | null>(line.distance ?? null),
      distanceUnit: new FormControl<string | null>(line.distanceUnit ?? null),
      calories: new FormControl<number | null>(this.division() === 'WOMEN' ? line.caloriesFemale ?? line.calories ?? null : line.calories ?? null),
      height: new FormControl<number | null>(this.heightFor(line)),
      duration: new FormControl<number | null>(line.durationSeconds ?? null)
    });
  }

  /** A line back to its prescription. */
  private resetLine(group: LineForm, line: WodMovementResponse): void {
    const fresh = this.lineGroup(line).getRawValue();
    group.setValue({...fresh, reps: this.countedIds().has(line.id) ? group.controls.reps.value : fresh.reps});
  }

  /** A line as the official Scaled version writes it. */
  private applyOfficial(group: LineForm, line: WodMovementResponse): void {
    const s = line.scaled!;
    const women = this.division() === 'WOMEN';
    group.setValue({
      wodMovementId: line.id,
      changed: true,
      movementId: s.movement.id,
      equipment: [...(s.equipment ?? [])],
      techniques: [...(s.techniques ?? [])],
      reps: s.repsScheme ?? line.repsScheme ?? '',
      load: this.unitService.lineLoadFor(s, this.division()),
      distance: s.distance ?? null,
      distanceUnit: s.distanceUnit ?? null,
      calories: women ? s.caloriesFemale ?? s.calories ?? null : s.calories ?? null,
      height: this.heightFor(s),
      duration: s.durationSeconds ?? null
    });
  }

  /** A box or target height in the user's unit (cm or in), as boxes have them. */
  private heightFor(line: Parameters<UnitService['lineHeightPair']>[0]): number | null {
    const pair = this.unitService.lineHeightPair(line, null);
    if (!pair) return null;
    const women = this.division() === 'WOMEN' && pair.values.length === 2;
    return Number(String(pair.values[women ? 1 : 0]).replace(',', '.'));
  }

  private fill(score: ScoreResponse): void {
    const load = score.maxWeight ?? score.totalLoad;
    const perRound = score.roundReps ?? this.store.wod()?.repsPerRound ?? null;
    const split = this.scoreType() === 'REPS' ? splitRounds(score.reps, perRound) : null;
    const level: FormLevel = score.scaling === 'RX' ? 'RX' : score.scaledOfficial ? 'OFFICIAL' : 'SCALED';
    this.form.patchValue({
      date: score.date,
      level,
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
      userComment: score.userComment ?? '',
      verificationRequested: !!score.verificationRequested
    });
    this.resetLines(this.wodLines());
    this.lineControls().forEach((group, i) => {
      const rx = this.wodLines()[i];
      const done = score.lines?.find(l => l.wodMovementId === rx.id);
      if (!done) return;
      group.patchValue({
        changed: !this.countedIds().has(rx.id),
        movementId: done.movement.id,
        equipment: [...done.equipment],
        techniques: [...done.techniques],
        reps: done.repsScheme ?? (this.countedIds().has(rx.id) ? '' : rx.repsScheme ?? ''),
        load: done.weight != null ? this.unitService.convertScoreLoad(done.weight, score.weightUnit) : group.controls.load.value,
        distance: done.distanceMeters ?? group.controls.distance.value,
        distanceUnit: done.distanceMeters != null ? 'METERS' : group.controls.distanceUnit.value,
        calories: done.calories ?? group.controls.calories.value,
        duration: done.durationSeconds ?? group.controls.duration.value
      });
    });
    if (level !== 'RX') this.loadCatalog();
    const sets = this.form.controls.sets;
    sets.clear();
    for (const set of score.sets ?? []) {
      sets.push(this.setGroup({
        wodMovementId: set.wodMovementId ?? null,
        reps: set.reps,
        weight: set.weight != null ? this.unitService.convertScoreLoad(set.weight, score.weightUnit) : null,
        completed: set.completed
      }));
    }
  }

  /** One set row: its line, reps, load (user's unit), done or failed. */
  private setGroup(value: {wodMovementId?: number | null; reps?: number | null; weight?: number | null; completed?: boolean} = {}): SetForm {
    return new FormGroup({
      wodMovementId: new FormControl<number | null>(value.wodMovementId ?? null),
      reps: new FormControl<number | null>(value.reps ?? null),
      weight: new FormControl<number | null>(value.weight ?? null),
      completed: new FormControl<boolean>(value.completed ?? true, {nonNullable: true})
    });
  }

  /**
   * The lines sent (D142): each changed line with what differs from the prescription (null = as prescribed), and the
   * reps counted on "max" lines (D147).
   */
  private linesRequest(): ScoreLineRequest[] {
    const level = this.level();
    const requests: ScoreLineRequest[] = [];
    this.lineControls().forEach((group, i) => {
      const rx = this.wodLines()[i];
      const v = group.getRawValue();
      if (this.countedIds().has(rx.id)) {
        if (v.reps.trim()) {
          requests.push({wodMovementId: rx.id, movementId: rx.movement.id, equipment: rx.equipment ?? [],
            techniques: rx.techniques ?? [], repsScheme: v.reps.trim()});
        }
        return;
      }
      if (level === 'RX' || !v.changed) return;
      const prescribed = this.lineGroup(rx).getRawValue();
      const differs = <T>(a: T, b: T) => a != null && a !== b;
      requests.push({
        wodMovementId: rx.id,
        movementId: v.movementId,
        equipment: v.equipment,
        techniques: v.techniques,
        repsScheme: differs(v.reps.trim(), prescribed.reps) ? v.reps.trim() : null,
        weight: differs(v.load, prescribed.load) ? v.load : null,
        distance: differs(v.distance, prescribed.distance) ? v.distance : null,
        distanceUnit: differs(v.distance, prescribed.distance) ? v.distanceUnit ?? 'METERS' : null,
        calories: differs(v.calories, prescribed.calories) ? v.calories : null,
        height: differs(v.height, prescribed.height) ? v.height : null,
        heightUnit: differs(v.height, prescribed.height) ? (this.unitService.unitSystem() === 'METRIC' ? 'CENTIMETERS' : 'INCHES') : null,
        durationSeconds: differs(v.duration, prescribed.duration) ? v.duration : null
      });
    });
    return requests;
  }

  private buildRequest(wodId: number): ScoreRequest {
    const v = this.form.getRawValue();
    const type = this.scoreType();
    const capped = type === 'TIME' && v.timeCapped;
    const tiebreak = (v.tiebreakMinutes ?? 0) * 60 + (v.tiebreakSeconds ?? 0);
    const lines = this.linesRequest();
    const request: ScoreRequest = {
      userId: this.canChooseAthlete() && v.athleteId != null ? v.athleteId : undefined,
      date: v.date,
      wodId,
      scaling: v.level === 'RX' ? 'RX' : 'SCALED',
      timeCapped: capped,
      gymId: this.gymChoices().length > 0 ? v.gymId : null,
      tiebreakSeconds: this.showTiebreak() && tiebreak > 0 ? tiebreak : undefined,
      userComment: v.userComment.trim() || undefined,
      verificationRequested: this.canSubmit() && v.verificationRequested,
      teammateIds: this.isTeam() && this.teammates().length > 0 ? this.teammates() : undefined,
      lines: lines.length ? lines : undefined,
      // Loads of the lines are typed in the user's unit
      weightUnit: this.unitService.massUnit()
    };
    if (this.useSets()) {
      // Aldebaran derives the performance from the sets (best complete set, or the sum of each line's best)
      const sets: ScoreSet[] = v.sets
        .filter(r => r.reps != null)
        .map(r => ({reps: r.reps as number, weight: r.weight, completed: r.completed, wodMovementId: r.wodMovementId}));
      return {...request, sets};
    }
    switch (type) {
      case 'TIME':
        return capped
          ? {...request, reps: v.reps ?? undefined}
          : {...request, timeMinutes: v.minutes ?? 0, timeSeconds: v.seconds ?? 0};
      case 'ROUNDS':
        return {...request, rounds: v.rounds ?? undefined};
      case 'REPS':
        // Rounds + reps are converted to total reps by Aldebaran, with the round as done
        return this.byRounds()
          ? {...request, rounds: v.rounds ?? 0, reps: v.reps ?? 0}
          : {...request, reps: v.reps ?? undefined};
      case 'WEIGHT':
        return {...request, maxWeight: v.load ?? undefined};
      case 'LOAD':
        return {...request, totalLoad: v.load ?? undefined};
      case 'CALORIES':
        return {...request, totalCalories: v.calories ?? undefined};
      case 'DISTANCE':
        return {...request, totalDistance: v.distance ?? undefined, distanceUnit: v.distanceUnit};
      default:
        return request;
    }
  }

  protected readonly isMaxLine = isMaxLine;
}
