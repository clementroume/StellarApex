import {ChangeDetectionStrategy, Component, computed, inject, input, OnInit, output, signal} from '@angular/core';
import {toSignal} from '@angular/core/rxjs-interop';
import {FormArray, FormControl, FormGroup, ReactiveFormsModule, Validators} from '@angular/forms';
import {TranslatePipe, TranslateService} from '@ngx-translate/core';
import {NgIcon} from '@ng-icons/core';
import {HlmButton} from '@spartan-ng/helm/button';
import {HlmInput} from '@spartan-ng/helm/input';
import {HlmTextarea} from '@spartan-ng/helm/textarea';
import {MovementService} from '../../../api/aldebaran/services/movement.service';
import {MovementReferenceData, MovementResponse, MovementSummaryResponse, UsualNameOption} from '../../../api/aldebaran/models/movement.model';
import {
  WodMovementRequest,
  WodMovementResponse,
  WodRequest,
  WodResponse,
  TeamMode,
  TeamSplit,
  WodSegment,
  ComponentTechnique
} from '../../../api/aldebaran/models/wod.model';
import {UnitService} from '../../../core/services/unit.service';
import {MovementComboboxComponent, usualNameOf} from '../../../shared/component/movement-combobox/movement-combobox.component';
import {toggleVariant, VariantPickerComponent} from '../../../shared/component/variant-picker/variant-picker.component';
import {NativeSelectDirective} from '../../../shared/component/native-select/native-select.directive';
import {
  ALL_EXTRAS,
  BOX_CM,
  BOX_IN,
  defaultExtras,
  implementOf,
  loadPresets,
  obviousEquipment,
  onBox,
  withCurrent,
  defaultMeasure,
  Extra,
  ladderValues,
  Measure,
  MEASURES,
  monotone,
  parseSets,
  roundsOf,
  scoreTypeOf,
  STRUCTURES,
  CATEGORIES,
  timed,
  WodCategory,
  withSegments,
  withSets
} from './wod-quick.utils';

const SCORE_TYPES = ['TIME', 'ROUNDS', 'REPS', 'WEIGHT', 'LOAD', 'CALORIES', 'DISTANCE', 'NONE'];
/** Values suggested per format (a tap fills the field). */
const LADDER_HINTS = ['21-15-9', '15-12-9', '10-9-8-7-6-5-4-3-2-1', '50-40-30-20-10'];
const PYRAMID_HINTS = ['1-2-3-4-5-4-3-2-1', '5-10-15-10-5'];
const SETS_HINTS: Record<string, string[]> = {
  SETS: ['5x5', '3x3', '4x8'], SCHEME: ['5-5-3-3-1', '3-3-3-3-3', '1-1-1'], MAX: ['1RM', '3RM'], COMPLEX: ['5x1', '4x2'],
  PRACTICE: ['3x10']
};
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
  componentTechniques: FormControl<ComponentTechnique[]>;
  segment: FormControl<WodSegment>;
  teamSplit: FormControl<TeamSplit>;
  // For Load: load as a percentage of the RM of rmReps reps (1RM unless set elsewhere)
  percentOfRm: FormControl<number | null>;
  rmReps: FormControl<number | null>;
}>;

/**
 * Quick WOD editor for coaches and athletes (session blocks, personal WODs). Two rows of chips: the
 * category (For Time, AMRAP, EMOM, Tabata, For Load, For Quality) then its sub-category (For Time:
 * Rounds, Une fois, Schéma, Chipper, Ladder...), which brings the usual score type and the few
 * structure fields it needs (rounds, rest, scheme values or start + step, interval, sets); the rep
 * scheme is generated, never typed freely. Each movement
 * line has a quantity measure (reps, max, distance, calories, time) chosen from its catalogue
 * movement, the load / height it usually needs in the user's units, folded equipment and technique, and a buy-in /
 * cash-out part on Rounds and AMRAP. A weight vest and notes are folded options. Writes in the
 * user's language only; the full WOD form stays for public WODs. Hands the `WodRequest` back
 * (`submitted`); the caller saves it.
 */
@Component({
  selector: 'app-wod-quick-form',
  standalone: true,
  imports: [ReactiveFormsModule, TranslatePipe, NgIcon, HlmButton, HlmInput, HlmTextarea, MovementComboboxComponent, VariantPickerComponent,
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
  /** Category of a new WOD (e.g. FOR_LOAD for a Skill block). */
  readonly defaultFormat = input('FOR_TIME');
  readonly submitted = output<WodRequest>();
  readonly cancelled = output<void>();

  protected readonly categories = CATEGORIES;
  protected readonly scoreTypes = SCORE_TYPES;
  protected readonly measures = MEASURES;
  protected readonly ladderHints = LADDER_HINTS;
  protected readonly pyramidHints = PYRAMID_HINTS;
  protected readonly movements = signal<MovementSummaryResponse[]>([]);
  protected readonly usualNames = signal<UsualNameOption[]>([]);
  protected readonly referenceData = signal<MovementReferenceData | null>(null);
  /** Full movements (allowed variants), loaded when picked. */
  private readonly details = signal<Record<number, MovementResponse>>({});
  protected readonly showScoreType = signal(false);
  protected readonly showNotes = signal(false);
  protected readonly showVest = signal(false);
  /** Team WOD option unfolded (team size, mode, total / each / together per line). */
  protected readonly showTeam = signal(false);
  protected readonly teamSizes = [2, 3, 4, 5, 6];
  protected readonly teamModes: TeamMode[] = ['YOU_GO_I_GO', 'SPLIT', 'SYNCHRO'];
  protected readonly teamSplits: TeamSplit[] = ['TOTAL', 'EACH', 'TOGETHER'];
  /** Rows showing every extra measure. */
  protected readonly expanded = signal<Set<number>>(new Set());
  /** Open equipment / technique choices, keyed "row:field". */
  protected readonly variantsOpen = signal<Set<string>>(new Set());
  protected readonly variantFields: VariantField[] = ['equipment', 'techniques'];

  readonly form = new FormGroup({
    category: new FormControl<WodCategory>('FOR_TIME', {nonNullable: true}),
    structure: new FormControl<string>('ROUNDS', {nonNullable: true}),
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
    // Team WOD: athletes per team and how they share the work
    teamSize: new FormControl<number>(2, {nonNullable: true}),
    teamMode: new FormControl<TeamMode>('YOU_GO_I_GO', {nonNullable: true}),
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
  protected readonly category = computed<WodCategory>(() => this.value().category ?? 'FOR_TIME');
  protected readonly structure = computed<string>(() => this.value().structure ?? 'ROUNDS');
  /** Sub-categories of the chosen category (second row of chips). */
  protected readonly structures = computed(() => STRUCTURES[this.category()]);
  /** "FOR_TIME:ROUNDS": one key for the template switches. */
  protected readonly format = computed(() => `${this.category()}:${this.structure()}`);
  protected readonly timed = computed(() => timed(this.category(), this.structure()));
  protected readonly withVest = computed(() => this.category() !== 'FOR_LOAD' && this.category() !== 'FOR_QUALITY');
  protected readonly withSegments = computed(() => withSegments(this.category(), this.structure()));
  protected readonly withSets = computed(() => withSets(this.category()));
  protected readonly setsHints = computed(() => this.withSets() ? SETS_HINTS[this.structure()] ?? [] : []);
  protected readonly parsedSets = computed(() => this.withSets() ? parseSets(this.value().sets ?? '') : null);
  /** Shared rep values (For Time › Schéma, Ladder by values) or a start + step ladder. */
  protected readonly usesValues = computed(() =>
    this.category() === 'FOR_TIME' && (this.structure() === 'SCHEME' || (this.structure() === 'LADDER' && !this.value().ladderUp)));
  protected readonly usesStartStep = computed(() =>
    (this.structure() === 'LADDER' && (this.category() === 'AMRAP' || !!this.value().ladderUp)) || this.structure() === 'DEATH_BY');
  /** Reps shared by every movement (a scheme, a ladder, a Death by, sets): none asked per line. */
  protected readonly sharedReps = computed(() => {
    if (this.usesStartStep()) return true;
    if (this.usesValues()) return ladderValues(this.value().ladder ?? '') != null;
    return this.parsedSets() != null;
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
    this.movementService.getUsualNames().subscribe(list => this.usualNames.set(list));
    this.movementService.getReferenceData().subscribe(reference => this.referenceData.set(reference));
    const initial = this.initial();
    if (initial) {
      this.fill(initial);
    } else {
      const category = this.defaultFormat() as WodCategory;
      this.setCategory(CATEGORIES.includes(category) ? category : 'FOR_TIME');
      this.addRow();
    }
  }

  // --- ACTIONS & EVENTS ---
  /** A category brings its usual sub-category (first chip). */
  setCategory(category: WodCategory): void {
    this.form.controls.category.setValue(category);
    this.setStructure(STRUCTURES[category][0]);
  }

  /** A sub-category brings its usual score type (changeable apart) and its usual structure values. */
  setStructure(structure: string): void {
    const v = this.form.getRawValue();
    const patch: Partial<typeof v> = {structure};
    if (v.category === 'TABATA') {
      Object.assign(patch, {rounds: 8, workSeconds: 20, restSeconds: 10});
    } else if (v.structure === 'CLASSIC' || v.structure === 'CUSTOM') {
      Object.assign(patch, {rounds: null, workSeconds: null, restSeconds: null});
    }
    const rounds = 'rounds' in patch ? patch.rounds : v.rounds;
    if (structure === 'ROUNDS' && v.category === 'FOR_TIME' && rounds == null) patch.rounds = 5;
    if (structure === 'MAX_REPS' && rounds == null) patch.rounds = 5;
    if (v.category === 'EMOM' && structure !== 'DEATH_BY' && rounds == null) patch.rounds = 10;
    if (structure === 'EVERY_MINUTE') patch.everyMinutes = 1;
    if (structure === 'EVERY_X' && (v.everyMinutes ?? 1) <= 1) patch.everyMinutes = 2;
    if (structure === 'INTERVALS' && v.workSeconds == null) Object.assign(patch, {workSeconds: 40, restSeconds: 20});
    if (structure === 'DEATH_BY') Object.assign(patch, {ladderStart: 1, ladderStep: 1});
    else if (v.structure === 'DEATH_BY') Object.assign(patch, {ladderStart: 3, ladderStep: 3});
    if (structure === 'SCHEME' && v.category === 'FOR_TIME' && !ladderValues(v.ladder)) patch.ladder = '21-15-9';
    this.form.patchValue(patch);
    this.refreshScoreType();
  }

  setLadderUp(up: boolean): void {
    this.form.controls.ladderUp.setValue(up);
  }

  setLadder(values: string): void {
    this.form.controls.ladder.setValue(values);
  }

  setSets(sets: string): void {
    this.form.controls.sets.setValue(sets);
  }

  /**
   * Adds a movement line: a buy-in after the other buy-in lines, a cash-out at the end, a line of the
   * rounds before the cash-out (at the end when the WOD has no parts).
   */
  addRow(segment: WodSegment = 'MAIN'): void {
    const rows = this.form.controls.rows;
    const segments = rows.controls.map(r => r.controls.segment.value);
    let index = rows.length;
    if (segment === 'BUY_IN') index = segments.filter(s => s === 'BUY_IN').length;
    else if (segment === 'MAIN' && this.withSegments()) index = rows.length - segments.filter(s => s === 'BUY_OUT').length;
    this.shiftRowState(index, 1);
    rows.insert(index, this.row({segment}));
  }

  removeRow(index: number): void {
    this.form.controls.rows.removeAt(index);
    this.shiftRowState(index, -1);
  }

  /** Name picked: its movement and variants, its usual measure, and the allowed variants loaded. */
  onPicked(index: number, option: UsualNameOption | null): void {
    const row = this.form.controls.rows.at(index);
    const movementId = option?.movementId ?? null;
    const r = row.getRawValue();
    const untouched = !r.reps.trim() && r.distance == null && r.calories == null && r.durationSeconds == null;
    row.patchValue({
      movementId,
      equipment: [...(option?.equipment ?? [])],
      techniques: (option?.techniques ?? []).filter(t => !t.includes(':')),
      componentTechniques: (option?.techniques ?? []).filter(t => t.includes(':')).map(t => {
        const [order, technique] = t.split(':');
        return {componentOrder: Number(order), technique};
      }),
      ...(untouched ? {measure: defaultMeasure(option?.nameEn, option?.category)} : {})
    });
    if (movementId != null) {
      // The equipment the movement implies is picked at once (kettlebell swing, box jump)
      this.loadDetail(movementId, detail => {
        if (row.controls.movementId.value === movementId && row.controls.equipment.value.length === 0) {
          row.controls.equipment.setValue(obviousEquipment(option?.nameEn ?? detail.name, detail.equipment));
        }
      });
    }
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
    const reference = this.referenceData();
    control.setValue(toggleVariant(control.value, value,
      field === 'techniques' ? reference?.exclusiveTechniqueSets : null));
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
  /** Heading before a line opening a part of the WOD (buy-in, the rounds, cash-out), when it has parts. */
  segmentHeading(index: number): WodSegment | null {
    if (!this.withSegments()) return null;
    const segments = this.form.controls.rows.controls.map(r => r.controls.segment.value);
    if (segments.every(s => s === 'MAIN')) return null;
    return index === 0 || segments[index] !== segments[index - 1] ? segments[index] : null;
  }

  /** Keeps the unfolded measures and variants on their line when lines move from `index` on. */
  private shiftRowState(index: number, delta: number): void {
    const shift = (i: number) => (i >= index ? i + delta : i);
    this.expanded.update(s => new Set([...s].filter(i => delta > 0 || i !== index).map(shift)));
    this.variantsOpen.update(s => new Set([...s]
      .filter(key => delta > 0 || Number(key.split(':')[0]) !== index)
      .map(key => {
        const [i, field] = key.split(':');
        return `${shift(Number(i))}:${field}`;
      })));
  }

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

  /** Standard weights of the row's kettlebell or dumbbell in the user's unit, or null for a free load. */
  loadChoices(index: number): number[] | null {
    const row = this.form.controls.rows.at(index).getRawValue();
    const movement = this.movements().find(m => m.id === row.movementId);
    const allowed = row.movementId == null ? null : this.details()[row.movementId]?.equipment;
    const presets = loadPresets(implementOf(row.equipment, allowed, movement?.name), this.metric());
    return presets && withCurrent(presets, row.weight, row.weightFemale);
  }

  /** The three box heights (20/24/30 in, 51/61/76 cm) for a movement on a box, or null for a free height. */
  heightChoices(index: number): number[] | null {
    const row = this.form.controls.rows.at(index).getRawValue();
    const movement = this.movements().find(m => m.id === row.movementId);
    if (!onBox(row.equipment, movement?.name)) return null;
    return withCurrent(this.metric() ? BOX_CM : BOX_IN, row.height, row.heightFemale);
  }

  /** Equipment or techniques the row's movement allows, once loaded. */
  /** The techniques of a line, a component's written 1:POWER as the usual names do. */
  lineTechniques(line: {techniques?: string[] | null; componentTechniques?: {componentOrder: number; technique: string}[] | null}): string[] {
    return [...(line.techniques ?? []), ...(line.componentTechniques ?? []).map(c => `${c.componentOrder}:${c.technique}`)];
  }

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
    const category = v.category;
    const structure = v.structure;
    const values = this.usesValues() ? ladderValues(v.ladder) : null;
    const startStep = this.usesStartStep();
    const sets = withSets(category) ? parseSets(v.sets) : null;
    const metric = this.metric();
    const segments = withSegments(category, structure);
    const complex = category === 'FOR_LOAD' && structure === 'COMPLEX';
    const movements: WodMovementRequest[] = v.rows.map((r, i) => {
      const measure = r.measure;
      let repsScheme: string | undefined;
      if (measure === 'MAX') repsScheme = 'max';
      else if (measure === 'REPS') {
        if (values) repsScheme = values.join('-');
        else if (sets) repsScheme = sets.reps;
        else if (!startStep) repsScheme = r.reps.trim() || undefined;
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
        componentTechniques: r.componentTechniques,
        segment: segments ? r.segment : 'MAIN',
        teamSplit: this.showTeam() ? r.teamSplit : 'TOTAL',
        complexGroup: complex ? 1 : null,
        percentOfRm: category === 'FOR_LOAD' ? r.percentOfRm ?? null : null,
        rmReps: category === 'FOR_LOAD' && r.percentOfRm != null ? r.rmReps ?? 1 : null
      };
    });
    const minutes = v.minutes != null && v.minutes > 0 ? Math.round(v.minutes * 60) : undefined;
    const vest = this.withVest() && (v.vestWeight != null || v.vestWeightFemale != null);
    const notes = v.notes.trim() || undefined;
    const fr = this.lang() === 'fr';
    const emom = category === 'EMOM';
    const tabata = category === 'TABATA';
    const intervals = emom && structure === 'INTERVALS';
    return {
      title: v.title.trim() || this.generateTitle(),
      wodType: category,
      structure,
      scoreType: v.scoreType,
      isPublic: false,
      competition: false,
      notesFr: fr ? notes : undefined,
      notesEn: fr ? undefined : notes,
      timeCapSeconds: timed(category, structure) ? minutes : undefined,
      repsPerRound: category === 'AMRAP' && structure === 'ROUNDS' && v.scoreType === 'REPS'
        ? this.repsPerRound(v.rows) : undefined,
      rounds: structure === 'ROUNDS' && category === 'FOR_TIME' || structure === 'MAX_REPS' ? v.rounds : null,
      emomRounds: (emom && structure !== 'DEATH_BY') || tabata ? v.rounds ?? undefined : undefined,
      emomInterval: structure === 'EVERY_MINUTE' || structure === 'DEATH_BY' ? 60
        : structure === 'EVERY_X' && v.everyMinutes ? Math.round(v.everyMinutes * 60) : undefined,
      workSeconds: tabata || intervals ? v.workSeconds : null,
      restSeconds: tabata || intervals || (category === 'FOR_TIME' && structure === 'ROUNDS') ? v.restSeconds : null,
      ladderStart: startStep ? v.ladderStart : null,
      ladderStep: startStep ? v.ladderStep : null,
      repScheme: values ? values.join('-') : withSets(category) ? v.sets.trim() || undefined : undefined,
      vestWeight: vest ? v.vestWeight : null,
      vestWeightFemale: vest ? v.vestWeightFemale : null,
      vestWeightUnit: vest ? this.unitService.massUnit() : null,
      teamSize: this.showTeam() ? v.teamSize : null,
      teamMode: this.showTeam() ? v.teamMode : null,
      movements
    };
  }

  /** The score type follows the category and sub-category. */
  private refreshScoreType(): void {
    const v = this.form.getRawValue();
    this.form.controls.scoreType.setValue(scoreTypeOf(v.category, v.structure));
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

  /** "21-15-9", "5 rounds", "3-6-9…", "EMOM 10", "5x5", or nothing (the category name is used). */
  private structureLabel(): string {
    const v = this.form.getRawValue();
    if (this.usesStartStep() && v.structure !== 'DEATH_BY' && v.ladderStart && v.ladderStep) {
      return [0, 1, 2].map(i => (v.ladderStart as number) + i * (v.ladderStep as number)).join('-') + '…';
    }
    if (this.usesValues()) return ladderValues(v.ladder)?.join('-') ?? '';
    if (withSets(v.category)) return v.sets.trim();
    if ((v.structure === 'ROUNDS' && v.category === 'FOR_TIME') || v.structure === 'MAX_REPS') {
      return v.rounds ? this.translate.instant('WOD.QUICK.N_ROUNDS', {count: v.rounds}) : '';
    }
    if (v.category === 'EMOM' && v.structure !== 'DEATH_BY') return v.rounds ? `EMOM ${v.rounds}` : '';
    return '';
  }

  private generateTitle(): string {
    const v = this.form.getRawValue();
    const names = v.rows
      .map(r => usualNameOf(this.usualNames(), r.movementId, this.lineTechniques(r), r.equipment)?.nameEn
        ?? this.movements().find(m => m.id === r.movementId)?.name)
      .filter((n): n is string => !!n);
    const formatName = this.translate.instant('WOD.WOD_TYPES.' + v.category);
    const structure = this.structureLabel();
    if (names.length === 1 && structure) return `${names[0]} ${structure}`.slice(0, 100);
    const parts = [structure || formatName, names.join(' / ')].filter(Boolean);
    return parts.join(' ').slice(0, 100) || formatName;
  }

  /** Category and sub-category of a saved WOD; without a sub-category, the closest one from its fields. */
  private formatOf(wod: WodResponse): {category: WodCategory; structure: string} {
    const category = CATEGORIES.includes(wod.wodType as WodCategory) ? wod.wodType as WodCategory : 'FOR_TIME';
    const structure = wod.structure && STRUCTURES[category].includes(wod.structure)
      ? wod.structure : this.guessStructure(category, wod);
    return {category, structure};
  }

  private guessStructure(category: WodCategory, wod: WodResponse): string {
    const values = ladderValues(wod.repScheme ?? '');
    switch (category) {
      case 'FOR_TIME':
        if ((wod.rounds ?? 0) > 1 || roundsOf(wod.repScheme) != null) return 'ROUNDS';
        if (wod.ladderStart != null) return 'LADDER';
        if (values) return monotone(values) ? 'SCHEME' : 'LADDER';
        return 'SINGLE';
      case 'AMRAP':
        return wod.ladderStart != null ? 'LADDER' : 'ROUNDS';
      case 'EMOM':
        if (wod.workSeconds != null) return 'INTERVALS';
        return wod.emomInterval && wod.emomInterval !== 60 ? 'EVERY_X' : 'EVERY_MINUTE';
      case 'TABATA':
        return (wod.workSeconds ?? 20) === 20 && (wod.restSeconds ?? 10) === 10 ? 'CLASSIC' : 'CUSTOM';
      case 'FOR_LOAD':
        if (/^\s*\d+\s*[x×]\s*\d+\s*$/i.test(wod.repScheme ?? '')) return 'SETS';
        return values ? 'SCHEME' : 'MAX';
      default:
        return STRUCTURES[category][0];
    }
  }

  private fill(wod: WodResponse): void {
    const {category, structure} = this.formatOf(wod);
    const forTimeLadderUp = category === 'FOR_TIME' && structure === 'LADDER' && wod.ladderStart != null;
    const usesValues = category === 'FOR_TIME' && (structure === 'SCHEME' || (structure === 'LADDER' && !forTimeLadderUp));
    this.form.patchValue({
      category,
      structure,
      scoreType: wod.scoreType,
      minutes: wod.timeCapSeconds ? Math.round(wod.timeCapSeconds / 6) / 10 : null,
      rounds: (category === 'FOR_TIME' && structure === 'ROUNDS') || structure === 'MAX_REPS'
        ? wod.rounds ?? roundsOf(wod.repScheme) : wod.emomRounds ?? null,
      everyMinutes: wod.emomInterval ? wod.emomInterval / 60 : 1,
      workSeconds: wod.workSeconds ?? null,
      restSeconds: wod.restSeconds ?? null,
      ladderUp: forTimeLadderUp,
      ladder: usesValues ? wod.repScheme ?? '' : '21-15-9',
      ladderStart: wod.ladderStart ?? (structure === 'DEATH_BY' ? 1 : 3),
      ladderStep: wod.ladderStep ?? (structure === 'DEATH_BY' ? 1 : 3),
      sets: withSets(category) ? wod.repScheme ?? '' : '',
      vestWeight: wod.vestWeight ?? null,
      vestWeightFemale: wod.vestWeightFemale ?? null,
      title: wod.title ?? '',
      notes: (this.lang() === 'fr' ? wod.notesFr || wod.notesEn : wod.notesEn || wod.notesFr) ?? ''
    });
    this.showNotes.set(!!this.form.controls.notes.value);
    this.showVest.set(wod.vestWeight != null || wod.vestWeightFemale != null);
    this.showTeam.set(!!wod.teamSize);
    if (wod.teamSize) this.form.patchValue({teamSize: wod.teamSize, teamMode: wod.teamMode ?? 'YOU_GO_I_GO'});
    const shared = this.sharedReps();
    // Buy-in lines first, cash-out lines last, whatever their order in older WODs
    const rank = (wm: WodMovementResponse) => SEGMENTS.indexOf(wm.segment ?? 'MAIN');
    [...wod.movements].sort((a, b) => rank(a) - rank(b) || a.orderIndex - b.orderIndex).forEach(wm => {
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
        componentTechniques: wm.componentTechniques ?? [],
        segment: wm.segment ?? 'MAIN',
        teamSplit: wm.teamSplit ?? 'TOTAL',
        percentOfRm: wm.percentOfRm ?? null,
        rmReps: wm.rmReps ?? null
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
      componentTechniques: new FormControl<ComponentTechnique[]>(v.componentTechniques ?? [], {nonNullable: true}),
      segment: new FormControl<WodSegment>(v.segment ?? 'MAIN', {nonNullable: true}),
      teamSplit: new FormControl<TeamSplit>(v.teamSplit ?? 'TOTAL', {nonNullable: true}),
      percentOfRm: new FormControl<number | null>(v.percentOfRm ?? null, [Validators.min(1), Validators.max(150)]),
      rmReps: new FormControl<number | null>(v.rmReps ?? null)
    });
  }

  private loadDetail(id: number, then?: (detail: MovementResponse) => void): void {
    const known = this.details()[id];
    if (known) {
      then?.(known);
      return;
    }
    this.movementService.getMovement(id).subscribe(detail => {
      this.details.update(d => ({...d, [id]: detail}));
      then?.(detail);
    });
  }
}
