import {ChangeDetectionStrategy, Component, computed, inject, input, OnInit, output, signal} from '@angular/core';
import {toSignal} from '@angular/core/rxjs-interop';
import {FormArray, FormControl, FormGroup, ReactiveFormsModule, Validators} from '@angular/forms';
import {TranslatePipe, TranslateService} from '@ngx-translate/core';
import {NgIcon} from '@ng-icons/core';
import {HlmButton} from '@spartan-ng/helm/button';
import {HlmInput} from '@spartan-ng/helm/input';
import {HlmTextarea} from '@spartan-ng/helm/textarea';
import {MovementService} from '../../../api/aldebaran/services/movement.service';
import {MovementResponse, MovementSummaryResponse} from '../../../api/aldebaran/models/movement.model';
import {
  WodMovementRequest,
  WodMovementResponse,
  WodRequest,
  WodResponse,
  WodSegment
} from '../../../api/aldebaran/models/wod.model';
import {UnitService} from '../../../core/services/unit.service';
import {MovementComboboxComponent} from '../../../shared/component/movement-combobox/movement-combobox.component';
import {NativeSelectDirective} from '../../../shared/component/native-select/native-select.directive';
import {
  ALL_EXTRAS,
  defaultExtras,
  defaultMeasure,
  Extra,
  ladderValues,
  Measure,
  MEASURES,
  parseSets,
  QUICK_FORMATS,
  QuickFormat,
  roundsOf,
  scoreTypeOf,
  SEGMENT_FORMATS,
  TIMED_FORMATS
} from './wod-quick.utils';

const SCORE_TYPES = ['TIME', 'ROUNDS', 'REPS', 'WEIGHT', 'LOAD', 'CALORIES', 'DISTANCE', 'NONE'];
/** Values suggested per format (a tap fills the field). */
const LADDER_HINTS = ['21-15-9', '15-12-9', '10-9-8-7-6-5-4-3-2-1', '50-40-30-20-10'];
const SETS_HINTS: Partial<Record<QuickFormat, string[]>> = {STRENGTH: ['5x5', '5-5-3-3-1', '3x3'], SKILL: ['3x10']};
const SEGMENTS: WodSegment[] = ['BUY_IN', 'MAIN', 'BUY_OUT'];

type VariantField = 'equipment' | 'techniques';

type MovementRow = FormGroup<{
  movementId: FormControl<number | null>;
  measure: FormControl<Measure>;
  reps: FormControl<string>;
  distance: FormControl<number | null>;
  calories: FormControl<number | null>;
  caloriesFemale: FormControl<number | null>;
  durationSeconds: FormControl<number | null>;
  weight: FormControl<number | null>;
  weightFemale: FormControl<number | null>;
  height: FormControl<number | null>;
  heightFemale: FormControl<number | null>;
  equipment: FormControl<string[]>;
  techniques: FormControl<string[]>;
  segment: FormControl<WodSegment>;
}>;

/**
 * Quick WOD editor for coaches and athletes (session blocks, personal WODs). A format chip (For
 * Time, Rounds, Chipper, Ladder, AMRAP, EMOM / intervals, Tabata, Death by, Strength, Skill) brings
 * its usual score type and the few structure fields it needs (rounds, rest, ladder values or
 * start + step, interval, sets); the rep scheme is generated, never typed freely. Each movement
 * line has a quantity measure (reps, max, distance, calories, time) chosen from its catalogue
 * movement, the load / height it usually needs in the user's units, folded equipment and technique, and a buy-in /
 * buy-out part on Rounds and AMRAP. A weight vest and notes are folded options. Writes in the
 * user's language only; the full WOD form stays for public WODs. Hands the `WodRequest` back
 * (`submitted`); the caller saves it.
 */
@Component({
  selector: 'app-wod-quick-form',
  standalone: true,
  imports: [ReactiveFormsModule, TranslatePipe, NgIcon, HlmButton, HlmInput, HlmTextarea, MovementComboboxComponent,
    NativeSelectDirective],
  templateUrl: './wod-quick-form.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class WodQuickFormComponent implements OnInit {
  // --- INJECTIONS ---
  private readonly movementService = inject(MovementService);
  private readonly unitService = inject(UnitService);
  private readonly translate = inject(TranslateService);

  // --- STATE (SIGNALS & VARIABLES) ---
  /** WOD to edit (a block's own WOD), or null for a new one. */
  readonly initial = input<WodResponse | null>(null);
  /** Format of a new WOD (e.g. STRENGTH for a Skill block). */
  readonly defaultFormat = input('FOR_TIME');
  readonly submitted = output<WodRequest>();
  readonly cancelled = output<void>();

  protected readonly formats = QUICK_FORMATS;
  protected readonly scoreTypes = SCORE_TYPES;
  protected readonly measures = MEASURES;
  protected readonly segmentValues = SEGMENTS;
  protected readonly ladderHints = LADDER_HINTS;
  protected readonly movements = signal<MovementSummaryResponse[]>([]);
  /** Full movements (allowed variants), loaded when picked. */
  private readonly details = signal<Record<number, MovementResponse>>({});
  protected readonly showScoreType = signal(false);
  protected readonly showNotes = signal(false);
  protected readonly showVest = signal(false);
  /** EMOM: work / rest fields shown (intervals). */
  protected readonly showWorkRest = signal(false);
  /** Rows showing every extra measure. */
  protected readonly expanded = signal<Set<number>>(new Set());
  /** Open equipment / technique choices, keyed "row:field". */
  protected readonly variantsOpen = signal<Set<string>>(new Set());
  protected readonly variantFields: VariantField[] = ['equipment', 'techniques'];

  readonly form = new FormGroup({
    format: new FormControl<QuickFormat>('FOR_TIME', {nonNullable: true}),
    scoreType: new FormControl<string>('TIME', {nonNullable: true}),
    // Time cap (For Time, Rounds, Chipper, Ladder) or duration (AMRAP, ascending ladder), in minutes
    minutes: new FormControl<number | null>(null, Validators.min(0)),
    // Rounds (Rounds, EMOM, Tabata)
    rounds: new FormControl<number | null>(null, Validators.min(1)),
    // EMOM: every X minutes
    everyMinutes: new FormControl<number | null>(1, Validators.min(0.25)),
    workSeconds: new FormControl<number | null>(null, Validators.min(1)),
    restSeconds: new FormControl<number | null>(null, Validators.min(0)),
    // Ladder: descending values ("21-15-9") or ascending start + step (also the Death by progression)
    ladderUp: new FormControl<boolean>(false, {nonNullable: true}),
    ladder: new FormControl<string>('21-15-9', {nonNullable: true, validators: Validators.maxLength(50)}),
    ladderStart: new FormControl<number | null>(3, Validators.min(1)),
    ladderStep: new FormControl<number | null>(3, Validators.min(1)),
    // Strength / skill sets ("5x5", "5-5-3-3-1", or free text)
    sets: new FormControl<string>('', {nonNullable: true, validators: Validators.maxLength(50)}),
    vestWeight: new FormControl<number | null>(null, Validators.min(0)),
    vestWeightFemale: new FormControl<number | null>(null, Validators.min(0)),
    title: new FormControl<string>('', {nonNullable: true, validators: Validators.maxLength(100)}),
    notes: new FormControl<string>('', {nonNullable: true, validators: Validators.maxLength(4000)}),
    rows: new FormArray<MovementRow>([])
  });
  protected readonly value = toSignal(this.form.valueChanges, {initialValue: this.form.getRawValue()});

  // --- COMPUTED STATE ---
  private readonly lang = computed(() => this.translate.currentLang() ?? this.translate.fallbackLang() ?? 'en');
  private readonly metric = computed(() => this.unitService.unitSystem() === 'METRIC');
  protected readonly massLabel = computed(() => this.metric() ? 'kg' : 'lb');
  protected readonly heightLabel = computed(() => this.metric() ? 'cm' : 'in');
  protected readonly format = computed<QuickFormat>(() => this.value().format ?? 'FOR_TIME');
  protected readonly timed = computed(() => TIMED_FORMATS.includes(this.format()));
  protected readonly withVest = computed(() => this.format() !== 'STRENGTH' && this.format() !== 'SKILL');
  protected readonly withSegments = computed(() => SEGMENT_FORMATS.includes(this.format()));
  protected readonly setsHints = computed(() => SETS_HINTS[this.format()] ?? []);
  protected readonly parsedSets = computed(() =>
    this.format() === 'STRENGTH' || this.format() === 'SKILL' ? parseSets(this.value().sets ?? '') : null);
  /** Reps shared by every movement (a descending ladder, an ascending one, a Death by, sets): none asked per line. */
  protected readonly sharedReps = computed(() => {
    const v = this.value();
    switch (v.format) {
      case 'LADDER':
        return !!v.ladderUp || ladderValues(v.ladder ?? '') != null;
      case 'DEATH_BY':
        return true;
      default:
        return this.parsedSets() != null;
    }
  });
  protected readonly rowControls = computed(() => {
    this.value();
    return this.form.controls.rows.controls;
  });
  /** Title given to an untitled WOD: "Back Squat 5x5", "21-15-9 Thruster / Pull-up", "5 rounds Run / Pull-up". */
  protected readonly generatedTitle = computed(() => {
    this.value();
    this.movements();
    return this.generateTitle();
  });

  // --- LIFECYCLE ---
  ngOnInit(): void {
    this.movementService.getMovements().subscribe(list =>
      this.movements.set([...list].sort((a, b) => a.name.localeCompare(b.name))));
    const initial = this.initial();
    if (initial) {
      this.fill(initial);
    } else {
      const format = this.defaultFormat() === 'ACCESSORY' ? 'STRENGTH' : this.defaultFormat();
      this.setFormat(QUICK_FORMATS.includes(format as QuickFormat) ? format as QuickFormat : 'FOR_TIME');
      this.addRow();
    }
  }

  // --- ACTIONS & EVENTS ---
  /** A format brings its usual score type (changeable apart) and its usual structure values. */
  setFormat(format: QuickFormat): void {
    const v = this.form.getRawValue();
    const patch: Partial<typeof v> = {format};
    if (format === 'TABATA') {
      Object.assign(patch, {rounds: 8, workSeconds: 20, restSeconds: 10});
    } else if (v.format === 'TABATA') {
      Object.assign(patch, {rounds: null, workSeconds: null, restSeconds: null});
    }
    const rounds = 'rounds' in patch ? patch.rounds : v.rounds;
    if (format === 'ROUNDS' && rounds == null) patch.rounds = 5;
    if (format === 'EMOM' && rounds == null) patch.rounds = 10;
    if (format === 'DEATH_BY') Object.assign(patch, {ladderStart: 1, ladderStep: 1});
    if (format === 'LADDER' && v.format === 'DEATH_BY') Object.assign(patch, {ladderStart: 3, ladderStep: 3});
    this.form.patchValue(patch);
    this.refreshScoreType();
  }

  setLadderUp(up: boolean): void {
    this.form.controls.ladderUp.setValue(up);
    this.refreshScoreType();
  }

  setLadder(values: string): void {
    this.form.controls.ladder.setValue(values);
  }

  setSets(sets: string): void {
    this.form.controls.sets.setValue(sets);
  }

  toggleWorkRest(): void {
    this.showWorkRest.update(open => !open);
    if (!this.showWorkRest()) this.form.patchValue({workSeconds: null, restSeconds: null});
    this.refreshScoreType();
  }

  addRow(): void {
    this.form.controls.rows.push(this.row());
  }

  removeRow(index: number): void {
    this.form.controls.rows.removeAt(index);
  }

  /** Picking another movement clears its variants, brings its usual measure and loads the allowed variants. */
  onMovement(index: number, movementId: number | null): void {
    const row = this.form.controls.rows.at(index);
    if (row.controls.movementId.value === movementId) return;
    const movement = this.movements().find(m => m.id === movementId);
    const r = row.getRawValue();
    const untouched = !r.reps.trim() && r.distance == null && r.calories == null && r.durationSeconds == null;
    row.patchValue({
      movementId, equipment: [], techniques: [],
      ...(untouched ? {measure: defaultMeasure(movement?.name, movement?.category)} : {})
    });
    if (movementId != null) this.loadDetail(movementId);
  }

  toggleExpanded(index: number): void {
    this.expanded.update(s => {
      const next = new Set(s);
      if (next.has(index)) next.delete(index); else next.add(index);
      return next;
    });
  }

  /** Opens or folds the equipment or technique choices of a row. */
  toggleVariants(index: number, field: VariantField): void {
    const key = `${index}:${field}`;
    this.variantsOpen.update(s => {
      const next = new Set(s);
      if (next.has(key)) next.delete(key); else next.add(key);
      return next;
    });
  }

  isVariantsOpen(index: number, field: VariantField): boolean {
    return this.variantsOpen().has(`${index}:${field}`);
  }

  toggleVariant(index: number, field: VariantField, value: string): void {
    const control = this.form.controls.rows.at(index).controls[field];
    const selected = control.value;
    control.setValue(selected.includes(value) ? selected.filter(v => v !== value) : [...selected, value]);
  }

  onSubmit(): void {
    const rows = this.form.controls.rows.controls;
    if (this.form.invalid || rows.length === 0 || rows.some(r => r.controls.movementId.value == null)) {
      this.form.markAllAsTouched();
      rows.forEach(r => r.controls.movementId.markAsTouched());
      return;
    }
    this.submitted.emit(this.buildRequest());
  }

  // --- HELPERS ---
  measureOfRow(index: number): Measure {
    return this.form.controls.rows.at(index).controls.measure.value;
  }

  /** The reps field of a line: only for a reps measure when the structure does not give them. */
  showReps(index: number): boolean {
    return this.measureOfRow(index) === 'REPS' && !this.sharedReps();
  }

  /** Extra measures shown on a row: the usual ones of its movement, those filled, or all when unfolded. */
  extrasOf(index: number): Extra[] {
    const row = this.form.controls.rows.at(index).getRawValue();
    if (this.expanded().has(index)) return ALL_EXTRAS;
    const movement = this.movements().find(m => m.id === row.movementId);
    const filled: Extra[] = [];
    if (row.weight != null || row.weightFemale != null) filled.push('LOAD');
    if (row.height != null || row.heightFemale != null) filled.push('HEIGHT');
    const shown = new Set([...defaultExtras(movement?.category, movement?.name), ...filled]);
    return ALL_EXTRAS.filter(m => shown.has(m));
  }

  /** Equipment or techniques the row's movement allows, once loaded. */
  variantsOf(index: number, field: VariantField): string[] {
    const id = this.form.controls.rows.at(index).controls.movementId.value;
    const detail = id == null ? null : this.details()[id];
    return detail ? [...detail[field]].sort() : [];
  }

  /** "Rings, Bar" for the picked values, or the name of the choice ("Equipment"). */
  variantLabel(index: number, field: VariantField): string {
    const picked = this.form.controls.rows.at(index).controls[field].value
      .map(v => this.translate.instant(this.variantKey(field, v)));
    return picked.join(', ') || this.translate.instant(field === 'equipment' ? 'WOD.QUICK.EQUIPMENT' : 'WOD.QUICK.TECHNIQUE');
  }

  variantKey(field: VariantField, value: string): string {
    return (field === 'equipment' ? 'MOVEMENT.EQUIPMENT.' : 'MOVEMENT.TECHNIQUES.') + value;
  }

  isVariantSelected(index: number, field: VariantField, value: string): boolean {
    return this.form.controls.rows.at(index).controls[field].value.includes(value);
  }

  buildRequest(): WodRequest {
    const v = this.form.getRawValue();
    const format = v.format;
    const descending = format === 'LADDER' && !v.ladderUp ? ladderValues(v.ladder) : null;
    const ascending = format === 'LADDER' && v.ladderUp;
    const sets = format === 'STRENGTH' || format === 'SKILL' ? parseSets(v.sets) : null;
    const intervals = format === 'EMOM' && v.workSeconds != null;
    const metric = this.metric();
    const withSegments = SEGMENT_FORMATS.includes(format);
    const movements: WodMovementRequest[] = v.rows.map((r, i) => {
      const measure = r.measure;
      let repsScheme: string | undefined;
      if (measure === 'MAX') repsScheme = 'max';
      else if (measure === 'REPS') {
        if (descending) repsScheme = descending.join('-');
        else if (sets) repsScheme = sets.reps;
        else if (!ascending && format !== 'DEATH_BY') repsScheme = r.reps.trim() || undefined;
      }
      const load = r.weight != null || r.weightFemale != null;
      const height = r.height != null || r.heightFemale != null;
      return {
        movementId: r.movementId as number,
        orderIndex: i + 1,
        repsScheme,
        sets: sets ? sets.sets : undefined,
        distance: measure === 'DISTANCE' ? r.distance ?? undefined : undefined,
        distanceUnit: measure === 'DISTANCE' && r.distance != null ? 'METERS' : undefined,
        calories: measure === 'CALORIES' ? r.calories ?? undefined : undefined,
        caloriesFemale: measure === 'CALORIES' ? r.caloriesFemale ?? undefined : undefined,
        durationSeconds: measure === 'TIME' ? r.durationSeconds ?? undefined : undefined,
        weight: r.weight ?? undefined,
        weightFemale: r.weightFemale ?? undefined,
        weightUnit: load ? this.unitService.massUnit() : undefined,
        height: r.height ?? undefined,
        heightFemale: r.heightFemale ?? undefined,
        heightUnit: height ? (metric ? 'CENTIMETERS' : 'INCHES') : undefined,
        equipment: r.equipment,
        techniques: r.techniques,
        segment: withSegments ? r.segment : 'MAIN'
      };
    });
    const minutes = v.minutes != null && v.minutes > 0 ? Math.round(v.minutes * 60) : undefined;
    const conditioning = format !== 'STRENGTH' && format !== 'SKILL';
    const vest = conditioning && (v.vestWeight != null || v.vestWeightFemale != null);
    const notes = v.notes.trim() || undefined;
    const fr = this.lang() === 'fr';
    const wodType = format === 'ROUNDS' ? 'FOR_TIME' : intervals ? 'INTERVALS' : format;
    return {
      title: v.title.trim() || this.generateTitle(),
      wodType,
      scoreType: v.scoreType,
      isPublic: false,
      competition: false,
      notesFr: fr ? notes : undefined,
      notesEn: fr ? undefined : notes,
      timeCapSeconds: TIMED_FORMATS.includes(format) ? minutes : undefined,
      repsPerRound: format === 'AMRAP' && v.scoreType === 'REPS' ? this.repsPerRound(v.rows) : undefined,
      rounds: format === 'ROUNDS' ? v.rounds : null,
      emomRounds: format === 'EMOM' || format === 'TABATA' ? v.rounds ?? undefined : undefined,
      emomInterval: format === 'EMOM' && v.everyMinutes ? Math.round(v.everyMinutes * 60)
        : format === 'DEATH_BY' ? 60 : undefined,
      workSeconds: format === 'TABATA' || intervals ? v.workSeconds : null,
      restSeconds: format === 'ROUNDS' || format === 'TABATA' || intervals ? v.restSeconds : null,
      ladderStart: ascending || format === 'DEATH_BY' ? v.ladderStart : null,
      ladderStep: ascending || format === 'DEATH_BY' ? v.ladderStep : null,
      repScheme: descending ? descending.join('-')
        : format === 'STRENGTH' || format === 'SKILL' ? v.sets.trim() || undefined : undefined,
      vestWeight: vest ? v.vestWeight : null,
      vestWeightFemale: vest ? v.vestWeightFemale : null,
      vestWeightUnit: vest ? this.unitService.massUnit() : null,
      movements
    };
  }

  /** The score type follows the format, the ladder direction and the intervals. */
  private refreshScoreType(): void {
    const v = this.form.getRawValue();
    this.form.controls.scoreType.setValue(scoreTypeOf(v.format, v.ladderUp, v.format === 'EMOM' && this.showWorkRest()));
  }

  /** Reps of one AMRAP round when every line of the rounds has a whole number of reps or calories (Cindy: 30). */
  private repsPerRound(rows: {measure: Measure; reps: string; calories: number | null; segment: WodSegment}[]):
    number | undefined {
    const main = rows.filter(r => r.segment === 'MAIN');
    if (main.length === 0) return undefined;
    let total = 0;
    for (const r of main) {
      if (r.measure === 'REPS' && /^\d+$/.test(r.reps.trim())) total += Number(r.reps.trim());
      else if (r.measure === 'CALORIES' && r.calories) total += r.calories;
      else return undefined;
    }
    return total > 0 ? total : undefined;
  }

  /** "21-15-9", "5 rounds", "3-6-9…", "EMOM 10", "5x5", or nothing (the format name is used). */
  private structureLabel(): string {
    const v = this.form.getRawValue();
    switch (v.format) {
      case 'ROUNDS':
        return v.rounds ? this.translate.instant('WOD.QUICK.N_ROUNDS', {count: v.rounds}) : '';
      case 'LADDER':
        if (v.ladderUp && v.ladderStart && v.ladderStep) {
          return [0, 1, 2].map(i => (v.ladderStart as number) + i * (v.ladderStep as number)).join('-') + '…';
        }
        return ladderValues(v.ladder)?.join('-') ?? '';
      case 'EMOM':
        return v.rounds ? `EMOM ${v.rounds}` : '';
      case 'STRENGTH':
      case 'SKILL':
        return v.sets.trim();
      default:
        return '';
    }
  }

  private generateTitle(): string {
    const v = this.form.getRawValue();
    const names = v.rows
      .map(r => this.movements().find(m => m.id === r.movementId)?.name)
      .filter((n): n is string => !!n);
    const formatName = this.translate.instant('WOD.QUICK.FORMATS.' + v.format);
    const structure = this.structureLabel();
    if (names.length === 1 && structure) return `${names[0]} ${structure}`.slice(0, 100);
    const parts = [structure || formatName, names.join(' / ')].filter(Boolean);
    return parts.join(' ').slice(0, 100) || formatName;
  }

  /** Format chip of a saved WOD: former types and free rep schemes read as the closest structure. */
  private formatOf(wod: WodResponse): QuickFormat {
    switch (wod.wodType) {
      case 'FOR_TIME':
        if ((wod.rounds ?? 0) > 1 || roundsOf(wod.repScheme) != null) return 'ROUNDS';
        return ladderValues(wod.repScheme ?? '') ? 'LADDER' : 'FOR_TIME';
      case 'INTERVALS':
        return 'EMOM';
      case 'ACCESSORY':
        return 'STRENGTH';
      default:
        return QUICK_FORMATS.includes(wod.wodType as QuickFormat) ? wod.wodType as QuickFormat : 'FOR_TIME';
    }
  }

  private fill(wod: WodResponse): void {
    const format = this.formatOf(wod);
    const ladderUp = format === 'LADDER' && wod.ladderStart != null;
    this.form.patchValue({
      format,
      scoreType: wod.scoreType,
      minutes: wod.timeCapSeconds ? Math.round(wod.timeCapSeconds / 6) / 10 : null,
      rounds: format === 'ROUNDS' ? wod.rounds ?? roundsOf(wod.repScheme) : wod.emomRounds ?? null,
      everyMinutes: wod.emomInterval ? wod.emomInterval / 60 : 1,
      workSeconds: wod.workSeconds ?? null,
      restSeconds: wod.restSeconds ?? null,
      ladderUp,
      ladder: format === 'LADDER' && !ladderUp ? wod.repScheme ?? '' : '21-15-9',
      ladderStart: wod.ladderStart ?? (format === 'DEATH_BY' ? 1 : 3),
      ladderStep: wod.ladderStep ?? (format === 'DEATH_BY' ? 1 : 3),
      sets: format === 'STRENGTH' || format === 'SKILL' ? wod.repScheme ?? '' : '',
      vestWeight: wod.vestWeight ?? null,
      vestWeightFemale: wod.vestWeightFemale ?? null,
      title: wod.title ?? '',
      notes: (this.lang() === 'fr' ? wod.notesFr || wod.notesEn : wod.notesEn || wod.notesFr) ?? ''
    });
    this.showNotes.set(!!this.form.controls.notes.value);
    this.showVest.set(wod.vestWeight != null || wod.vestWeightFemale != null);
    this.showWorkRest.set(format === 'EMOM' && wod.workSeconds != null);
    const shared = this.sharedReps();
    [...wod.movements].sort((a, b) => a.orderIndex - b.orderIndex).forEach(wm => {
      const measure = this.measureOf(wm);
      this.form.controls.rows.push(this.row({
        movementId: wm.movement.id,
        measure,
        reps: measure === 'REPS' && !shared ? wm.repsScheme ?? '' : '',
        distance: wm.distance ?? null, calories: wm.calories ?? null, caloriesFemale: wm.caloriesFemale ?? null,
        durationSeconds: wm.durationSeconds ?? null,
        weight: wm.weight ?? null, weightFemale: wm.weightFemale ?? null,
        height: wm.height ?? null, heightFemale: wm.heightFemale ?? null,
        equipment: wm.equipment ?? [], techniques: wm.techniques ?? [],
        segment: wm.segment ?? 'MAIN'
      }));
      this.loadDetail(wm.movement.id);
    });
  }

  /** Measure of a saved line: max reps, distance, calories, a time without reps, else reps. */
  private measureOf(wm: WodMovementResponse): Measure {
    if (/^max\b/i.test(wm.repsScheme ?? '')) return 'MAX';
    if (wm.distance != null) return 'DISTANCE';
    if (wm.calories != null || wm.caloriesFemale != null) return 'CALORIES';
    if (wm.durationSeconds != null && !wm.repsScheme) return 'TIME';
    return 'REPS';
  }

  private row(v: Partial<ReturnType<MovementRow['getRawValue']>> = {}): MovementRow {
    const number = (value?: number | null) => new FormControl<number | null>(value ?? null, Validators.min(0));
    return new FormGroup({
      movementId: new FormControl<number | null>(v.movementId ?? null, Validators.required),
      measure: new FormControl<Measure>(v.measure ?? 'REPS', {nonNullable: true}),
      reps: new FormControl<string>(v.reps ?? '', {nonNullable: true, validators: Validators.maxLength(50)}),
      distance: number(v.distance), calories: number(v.calories), caloriesFemale: number(v.caloriesFemale),
      durationSeconds: number(v.durationSeconds),
      weight: number(v.weight), weightFemale: number(v.weightFemale),
      height: number(v.height), heightFemale: number(v.heightFemale),
      equipment: new FormControl<string[]>(v.equipment ?? [], {nonNullable: true}),
      techniques: new FormControl<string[]>(v.techniques ?? [], {nonNullable: true}),
      segment: new FormControl<WodSegment>(v.segment ?? 'MAIN', {nonNullable: true})
    });
  }

  private loadDetail(id: number): void {
    if (this.details()[id]) return;
    this.movementService.getMovement(id).subscribe(detail => this.details.update(d => ({...d, [id]: detail})));
  }
}
