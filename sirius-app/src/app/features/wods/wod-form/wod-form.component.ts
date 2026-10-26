import {ChangeDetectionStrategy, Component, computed, inject, OnInit, signal} from '@angular/core';
import {AbstractControl, FormArray, FormBuilder, ReactiveFormsModule, ValidationErrors, Validators} from '@angular/forms';
import {ActivatedRoute, Router, RouterModule} from '@angular/router';
import {HttpErrorResponse} from '@angular/common/http';
import {TranslatePipe, TranslateService} from '@ngx-translate/core';
import {NgIconComponent} from '@ng-icons/core';
import {forkJoin} from 'rxjs';
import {WodService} from '../../../api/aldebaran/services/wod.service';
import {MovementService} from '../../../api/aldebaran/services/movement.service';
import {NotificationService} from '../../../core/services/notification.service';
import {AccessService} from '../../../core/services/access.service';
import {MovementResponse, MovementSummaryResponse} from '../../../api/aldebaran/models/movement.model';
import {WodMovementRequest, WodMovementResponse, WodRequest, WodResponse} from '../../../api/aldebaran/models/wod.model';
import {ProblemDetail} from '../../../core/models/problem-detail.model';
import {UnitService} from '../../../core/services/unit.service';

export type WodFormVisibility = 'PRIVATE' | 'GYM';

/** Variant fields of a WOD movement, limited to the values allowed by its movement. */
export type WodMovementVariant = 'equipment' | 'techniques';

const minLengthArray = (min: number) => (control: AbstractControl): ValidationErrors | null =>
  (control as FormArray).length >= min ? null : {minLengthArray: {required: min}};

const gymRequiredForGymVisibility = (group: AbstractControl): ValidationErrors | null =>
  group.get('visibility')?.value === 'GYM' && group.get('gymId')?.value == null ? {gymRequired: true} : null;

@Component({
  selector: 'app-wod-form',
  standalone: true,
  imports: [ReactiveFormsModule, RouterModule, TranslatePipe, NgIconComponent],
  templateUrl: './wod-form.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class WodFormComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly wodService = inject(WodService);
  private readonly movementService = inject(MovementService);
  private readonly accessService = inject(AccessService);
  private readonly notificationService = inject(NotificationService);
  private readonly translate = inject(TranslateService);
  private readonly unitService = inject(UnitService);

  isEditMode = signal<boolean>(false);
  isLoading = signal<boolean>(false);
  private readonly wodId = signal<number | null>(null);
  // Ownership of the edited WOD, kept so an edition never silently moves it to another gym/author
  readonly originalWod = signal<WodResponse | null>(null);

  wodTypes = signal<string[]>([]);
  categories = signal<string[]>([]);
  scoreTypes = signal<string[]>([]);
  unitGroups = signal<Record<string, string[]>>({});
  availableMovements = signal<MovementSummaryResponse[]>([]);
  readonly variantFields: readonly WodMovementVariant[] = ['equipment', 'techniques'];
  /** Full movements (allowed equipment and techniques), loaded on demand by id. */
  readonly movementDetails = signal<Record<number, MovementResponse>>({});
  private readonly movementsByName = computed(
    () => new Map(this.availableMovements().map(m => [m.name.toLowerCase(), m])));

  /** The user's language first: a coach writes in their language, the other one is optional. */
  readonly primaryLanguage = computed<'FR' | 'EN'>(() =>
    (this.translate.currentLang() ?? this.translate.fallbackLang()) === 'fr' ? 'FR' : 'EN');
  readonly secondaryLanguage = computed<'FR' | 'EN'>(() => this.primaryLanguage() === 'FR' ? 'EN' : 'FR');
  readonly languages = computed(() => [this.primaryLanguage(), this.secondaryLanguage()]);

  massUnits = computed(() => this.unitGroups()['MASS'] ?? []);
  distanceUnits = computed(() => this.unitGroups()['DISTANCE'] ?? []);
  // Box and target heights: cm / in for boxes, m / ft for wall-ball targets
  heightUnits = computed(() =>
    ['CENTIMETERS', 'INCHES', 'METERS', 'FEET'].filter(u => this.distanceUnits().includes(u)));

  /** Gyms where the user may program WODs (WOD_WRITE), as Aldebaran checks on creation. */
  writableGyms = computed(() => this.accessService.gymsWithPermission('WOD_WRITE'));
  originalGymName = computed(() => {
    const gymId = this.originalWod()?.gymId;
    return this.accessService.memberships().find(m => m.gymId === gymId)?.gymName ?? null;
  });
  canTargetGym = computed(() => this.writableGyms().length > 0);

  wodForm = this.fb.nonNullable.group({
    // --- Identification ---
    title: ['', [Validators.required, Validators.maxLength(100)]],
    wodType: ['', Validators.required],
    category: [null as string | null],
    scoreType: ['', Validators.required],
    // --- Authorship and Visibility ---
    visibility: ['PRIVATE' as WodFormVisibility],
    gymId: [null as number | null],
    isPublic: [false],
    // --- Description and Notes ---
    descriptionFr: ['', [Validators.maxLength(4000)]],
    descriptionEn: ['', [Validators.maxLength(4000)]],
    notesFr: ['', [Validators.maxLength(4000)]],
    notesEn: ['', [Validators.maxLength(4000)]],
    // --- Structure and Prescription ---
    timeCapMinutes: [null as number | null, [Validators.min(0)]],
    timeCapSeconds: [null as number | null, [Validators.min(0), Validators.max(59)]],
    emomInterval: [null as number | null, [Validators.min(0)]],
    emomRounds: [null as number | null, [Validators.min(0)]],
    repScheme: ['', [Validators.maxLength(100)]],
    movements: this.fb.array([], minLengthArray(1))
  }, {validators: gymRequiredForGymVisibility});

  ngOnInit(): void {
    forkJoin({
      wodRef: this.wodService.getReferenceData(),
      movements: this.movementService.getMovements()
    }).subscribe(({wodRef, movements}) => {
      this.wodTypes.set(wodRef.wodTypes);
      this.categories.set(wodRef.categories ?? []);
      this.scoreTypes.set(wodRef.scoreTypes);
      this.unitGroups.set(wodRef.unitGroups);
      this.availableMovements.set([...movements].sort((a, b) => a.name.localeCompare(b.name)));
    });

    const idParam = this.route.snapshot.paramMap.get('id');
    if (idParam && !Number.isNaN(Number(idParam))) {
      const id = Number(idParam);
      this.isEditMode.set(true);
      this.wodId.set(id);
      this.loadWod(id);
    } else {
      this.applyCreationPreset();
    }
  }

  /**
   * Presets the visibility from the list's create button: `?gymId=` targets that gym when the user
   * may program it, `?visibility=PRIVATE` a personal WOD; otherwise the first writable gym.
   */
  private applyCreationPreset(): void {
    const query = this.route.snapshot.queryParamMap;
    const requestedGymId = Number(query?.get('gymId'));
    const writable = this.writableGyms();

    if (query?.get('visibility') === 'PRIVATE' || writable.length === 0) {
      this.wodForm.patchValue({visibility: 'PRIVATE', gymId: null});
    } else if (writable.some(g => g.gymId === requestedGymId)) {
      this.wodForm.patchValue({visibility: 'GYM', gymId: requestedGymId});
    } else {
      this.wodForm.patchValue({visibility: 'GYM', gymId: writable[0].gymId});
    }
  }

  // --- MOVEMENTS FORM ARRAY ---
  get movementsFormArray(): FormArray {
    return this.wodForm.get('movements') as FormArray;
  }

  get isEmom(): boolean {
    return this.wodForm.controls.wodType.value === 'EMOM';
  }

  addMovement(source?: WodMovementResponse): void {
    this.movementsFormArray.push(this.fb.group({
      movementId: [source?.movement.id ?? null as number | null, Validators.required],
      // Search box (name or abbreviation), resolved to movementId; not sent to the API
      movementQuery: [source?.movement.name ?? ''],
      repsScheme: [source?.repsScheme ?? '', [Validators.maxLength(50)]],
      weight: [source?.weight ?? null as number | null, [Validators.min(0)]],
      weightFemale: [source?.weightFemale ?? null as number | null, [Validators.min(0)]],
      // New movements default to the user's display unit (kg in French)
      weightUnit: [source?.weightUnit ?? this.unitService.massUnit()],
      distance: [source?.distance ?? null as number | null, [Validators.min(0)]],
      distanceUnit: [source?.distanceUnit ?? this.distanceUnits()[0] ?? 'METERS'],
      durationSeconds: [source?.durationSeconds ?? null as number | null, [Validators.min(0)]],
      durationDisplayUnit: [source?.durationDisplayUnit ?? null as string | null],
      calories: [source?.calories ?? null as number | null, [Validators.min(0)]],
      caloriesFemale: [source?.caloriesFemale ?? null as number | null, [Validators.min(0)]],
      height: [source?.height ?? null as number | null, [Validators.min(0)]],
      heightFemale: [source?.heightFemale ?? null as number | null, [Validators.min(0)]],
      heightUnit: [source?.heightUnit ?? (this.unitService.massUnit() === 'KG' ? 'CENTIMETERS' : 'INCHES')],
      // Variants: only values allowed by the movement (checked again by Aldebaran)
      equipment: [source?.equipment ?? null as string[] | null],
      techniques: [source?.techniques ?? null as string[] | null],
      notesFr: [source?.notesFr ?? '', [Validators.maxLength(4000)]],
      notesEn: [source?.notesEn ?? '', [Validators.maxLength(4000)]],
      scalingOptionsFr: [source?.scalingOptionsFr ?? '', [Validators.maxLength(4000)]],
      scalingOptionsEn: [source?.scalingOptionsEn ?? '', [Validators.maxLength(4000)]]
    }));
    if (source) {
      this.loadMovementDetails(source.movement.id);
    }
  }

  /** Resolves the search box to a catalog movement; changing the movement clears its variants. */
  onMovementQuery(index: number): void {
    const group = this.movementsFormArray.at(index);
    const query = String(group.get('movementQuery')?.value ?? '').trim().toLowerCase();
    const match = this.movementsByName().get(query) ?? null;
    if ((match?.id ?? null) === group.get('movementId')?.value) return;
    group.patchValue({movementId: match?.id ?? null, equipment: [], techniques: []});
    if (match) {
      this.loadMovementDetails(match.id);
    }
  }

  /** Full movement of a row, once loaded. */
  movementDetail(index: number): MovementResponse | null {
    const id = this.movementsFormArray.at(index)?.get('movementId')?.value as number | null;
    return id == null ? null : this.movementDetails()[id] ?? null;
  }

  /** Allowed values of a variant field for a row, sorted for a stable display. */
  allowedVariants(index: number, field: WodMovementVariant): string[] {
    return [...(this.movementDetail(index)?.[field] ?? [])].sort();
  }

  isVariantSelected(index: number, field: WodMovementVariant, value: string): boolean {
    const selected = this.movementsFormArray.at(index).get(field)?.value as string[] | null;
    return selected?.includes(value) ?? false;
  }

  toggleVariant(index: number, field: WodMovementVariant, value: string): void {
    const control = this.movementsFormArray.at(index).get(field)!;
    const selected = (control.value as string[] | null) ?? [];
    control.setValue(selected.includes(value) ? selected.filter(v => v !== value) : [...selected, value]);
    control.markAsDirty();
  }

  private loadMovementDetails(id: number): void {
    if (this.movementDetails()[id]) return;
    this.movementService.getMovement(id).subscribe(movement => {
      this.movementDetails.update(details => ({...details, [id]: movement}));
      this.dropDisallowedVariants(movement);
    });
  }

  /** Removes stored variants that the movement no longer allows (Aldebaran would reject them). */
  private dropDisallowedVariants(movement: MovementResponse): void {
    this.movementsFormArray.controls
      .filter(group => group.get('movementId')?.value === movement.id)
      .forEach(group => (['equipment', 'techniques'] as const).forEach(field => {
        const control = group.get(field)!;
        const selected = control.value as string[] | null;
        if (selected?.some(v => !movement[field].includes(v))) {
          control.setValue(selected.filter(v => movement[field].includes(v)));
        }
      }));
  }

  /** Form control of a bilingual movement text, e.g. ('notes', 'FR') -> 'notesFr'. */
  textControl(field: 'notes' | 'scalingOptions', lang: 'FR' | 'EN'): string {
    return field + (lang === 'FR' ? 'Fr' : 'En');
  }

  removeMovement(index: number): void {
    this.movementsFormArray.removeAt(index);
  }

  moveMovement(index: number, delta: -1 | 1): void {
    const target = index + delta;
    if (target < 0 || target >= this.movementsFormArray.length) return;
    const control = this.movementsFormArray.at(index);
    this.movementsFormArray.removeAt(index);
    this.movementsFormArray.insert(target, control);
  }

  // --- DATA FETCHING ---
  private loadWod(id: number): void {
    this.isLoading.set(true);
    this.wodForm.disable();

    this.wodService.getWod(id).subscribe({
      next: (wod) => {
        if (wod.capabilities && (!wod.capabilities.canModify || wod.capabilities.locked)) {
          const key = wod.capabilities.locked ? 'WOD.MESSAGES.LOCKED' : 'WOD.MESSAGES.FORBIDDEN';
          this.notificationService.showError(this.translate.instant(key));
          void this.router.navigate(['/wods', id]);
          return;
        }
        this.originalWod.set(wod);
        const timeCap = wod.timeCapSeconds ?? null;
        this.wodForm.patchValue({
          // --- Identification ---
          title: wod.title,
          wodType: wod.wodType,
          category: wod.category ?? null,
          scoreType: wod.scoreType,
          // --- Authorship and Visibility ---
          visibility: wod.gymId == null ? 'PRIVATE' : 'GYM',
          gymId: wod.gymId ?? null,
          isPublic: wod.isPublic,
          // --- Description and Notes ---
          descriptionFr: wod.descriptionFr || '',
          descriptionEn: wod.descriptionEn || '',
          notesFr: wod.notesFr || '',
          notesEn: wod.notesEn || '',
          // --- Structure and Prescription ---
          timeCapMinutes: timeCap === null ? null : Math.floor(timeCap / 60),
          timeCapSeconds: timeCap === null ? null : timeCap % 60,
          emomInterval: wod.emomInterval ?? null,
          emomRounds: wod.emomRounds ?? null,
          repScheme: wod.repScheme || ''
        });

        this.movementsFormArray.clear();
        [...wod.movements]
          .sort((a, b) => a.orderIndex - b.orderIndex)
          .forEach(wm => this.addMovement(wm));

        this.wodForm.enable();
        // Aldebaran ignores ownership changes on update (WodMapper.updateEntity)
        this.wodForm.controls.visibility.disable();
        this.wodForm.controls.gymId.disable();
        this.isLoading.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.handleError(err);
        void this.router.navigate(['/wods']);
      }
    });
  }

  onSubmit(): void {
    if (this.wodForm.invalid) {
      this.wodForm.markAllAsTouched();
      return;
    }

    this.isLoading.set(true);
    const payload = this.buildPayload();

    const request$ = this.isEditMode() && this.wodId()
      ? this.wodService.updateWod(this.wodId()!, payload)
      : this.wodService.createWod(payload);

    request$.subscribe({
      next: (saved) => {
        const key = this.isEditMode() ? 'WOD.MESSAGES.SUCCESS_UPDATE' : 'WOD.MESSAGES.SUCCESS_CREATE';
        this.notificationService.showSuccess(this.translate.instant(key));
        this.wodService.notifyRefresh();
        void this.router.navigate(['/wods', saved.id]);
      },
      error: (err: HttpErrorResponse) => {
        this.isLoading.set(false);
        this.handleError(err);
      }
    });
  }

  buildPayload(): WodRequest {
    const v = this.wodForm.getRawValue();
    const original = this.originalWod();
    const isGym = v.visibility === 'GYM';
    const timeCap = (v.timeCapMinutes ?? 0) * 60 + (v.timeCapSeconds ?? 0);
    const isEmom = v.wodType === 'EMOM';

    const movements: WodMovementRequest[] = (v.movements as any[]).map((m, index) => ({
      // --- Identification ---
      movementId: m.movementId,
      orderIndex: index + 1,
      // --- Prescription ---
      repsScheme: m.repsScheme?.trim() || undefined,
      weight: m.weight ?? undefined,
      weightFemale: m.weightFemale ?? undefined,
      weightUnit: m.weight == null && m.weightFemale == null ? undefined : m.weightUnit,
      durationSeconds: m.durationSeconds ?? undefined,
      durationDisplayUnit: m.durationSeconds == null ? undefined : (m.durationDisplayUnit ?? undefined),
      distance: m.distance ?? undefined,
      distanceUnit: m.distance == null ? undefined : m.distanceUnit,
      calories: m.calories ?? undefined,
      caloriesFemale: m.caloriesFemale ?? undefined,
      height: m.height ?? undefined,
      heightFemale: m.heightFemale ?? undefined,
      heightUnit: m.height == null && m.heightFemale == null ? undefined : m.heightUnit,
      // --- Characteristics ---
      equipment: m.equipment ?? undefined,
      techniques: m.techniques ?? undefined,
      // --- Instructions ---
      notesEn: m.notesEn?.trim() || undefined,
      notesFr: m.notesFr?.trim() || undefined,
      scalingOptionsEn: m.scalingOptionsEn?.trim() || undefined,
      scalingOptionsFr: m.scalingOptionsFr?.trim() || undefined
    }));

    return {
      // --- Identification ---
      title: v.title.trim(),
      wodType: v.wodType,
      category: v.category ?? null,
      scoreType: v.scoreType,
      // --- Authorship and Visibility ---
      authorId: isGym ? undefined : (original?.authorId ?? this.accessService.userId() ?? undefined),
      gymId: isGym ? (original?.gymId ?? v.gymId ?? undefined) : undefined,
      isPublic: v.isPublic,
      // --- Description and Notes ---
      descriptionEn: v.descriptionEn.trim() || undefined,
      descriptionFr: v.descriptionFr.trim() || undefined,
      notesEn: v.notesEn.trim() || undefined,
      notesFr: v.notesFr.trim() || undefined,
      // --- Structure and Prescription ---
      timeCapSeconds: timeCap > 0 ? timeCap : undefined,
      emomInterval: isEmom ? (v.emomInterval ?? undefined) : undefined,
      emomRounds: isEmom ? (v.emomRounds ?? undefined) : undefined,
      repScheme: v.repScheme.trim() || undefined,
      movements
    };
  }

  private handleError(err: HttpErrorResponse): void {
    const problem: ProblemDetail = err.error;
    if (err.status === 403) {
      this.notificationService.showError(this.translate.instant('WOD.MESSAGES.FORBIDDEN'));
      return;
    }
    if (err.status === 409) {
      this.notificationService.showError(problem?.detail || this.translate.instant('WOD.MESSAGES.LOCKED'));
      return;
    }
    const message = problem?.detail || problem?.title || this.translate.instant('GLOBAL.ERROR_UNEXPECTED');
    this.notificationService.showError(message);
  }
}
