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
import {MovementSummaryResponse} from '../../../api/aldebaran/models/movement.model';
import {WodMovementRequest, WodMovementResponse, WodRequest, WodResponse} from '../../../api/aldebaran/models/wod.model';
import {ProblemDetail} from '../../../core/models/problem-detail.model';

export type WodFormVisibility = 'PRIVATE' | 'GYM';

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

  isEditMode = signal<boolean>(false);
  isLoading = signal<boolean>(false);
  private readonly wodId = signal<number | null>(null);
  // Ownership of the edited WOD, kept so an edition never silently moves it to another gym/author
  readonly originalWod = signal<WodResponse | null>(null);

  wodTypes = signal<string[]>([]);
  scoreTypes = signal<string[]>([]);
  unitGroups = signal<Record<string, string[]>>({});
  availableMovements = signal<MovementSummaryResponse[]>([]);

  massUnits = computed(() => this.unitGroups()['MASS'] ?? []);
  distanceUnits = computed(() => this.unitGroups()['DISTANCE'] ?? []);

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
    scoreType: ['', Validators.required],
    // --- Authorship and Visibility ---
    visibility: ['PRIVATE' as WodFormVisibility],
    gymId: [null as number | null],
    isPublic: [false],
    // --- Description and Notes ---
    description: ['', [Validators.maxLength(4000)]],
    notes: ['', [Validators.maxLength(4000)]],
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
      repsScheme: [source?.repsScheme ?? '', [Validators.maxLength(50)]],
      weight: [source?.weight ?? null as number | null, [Validators.min(0)]],
      weightUnit: [source?.weightUnit ?? this.massUnits()[0] ?? 'KG'],
      distance: [source?.distance ?? null as number | null, [Validators.min(0)]],
      distanceUnit: [source?.distanceUnit ?? this.distanceUnits()[0] ?? 'METERS'],
      durationSeconds: [source?.durationSeconds ?? null as number | null, [Validators.min(0)]],
      durationDisplayUnit: [source?.durationDisplayUnit ?? null as string | null],
      calories: [source?.calories ?? null as number | null, [Validators.min(0)]],
      // Not editable here, preserved on edition
      equipment: [source?.equipment ?? null as string[] | null],
      techniques: [source?.techniques ?? null as string[] | null],
      notes: [source?.notes ?? '', [Validators.maxLength(4000)]],
      scalingOptions: [source?.scalingOptions ?? '', [Validators.maxLength(4000)]]
    }));
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
          scoreType: wod.scoreType,
          // --- Authorship and Visibility ---
          visibility: wod.gymId == null ? 'PRIVATE' : 'GYM',
          gymId: wod.gymId ?? null,
          isPublic: wod.isPublic,
          // --- Description and Notes ---
          description: wod.description || '',
          notes: wod.notes || '',
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
      weightUnit: m.weight == null ? undefined : m.weightUnit,
      durationSeconds: m.durationSeconds ?? undefined,
      durationDisplayUnit: m.durationSeconds == null ? undefined : (m.durationDisplayUnit ?? undefined),
      distance: m.distance ?? undefined,
      distanceUnit: m.distance == null ? undefined : m.distanceUnit,
      calories: m.calories ?? undefined,
      // --- Characteristics ---
      equipment: m.equipment ?? undefined,
      techniques: m.techniques ?? undefined,
      // --- Instructions ---
      notes: m.notes?.trim() || undefined,
      scalingOptions: m.scalingOptions?.trim() || undefined
    }));

    return {
      // --- Identification ---
      title: v.title.trim(),
      wodType: v.wodType,
      scoreType: v.scoreType,
      // --- Authorship and Visibility ---
      authorId: isGym ? undefined : (original?.authorId ?? this.accessService.userId() ?? undefined),
      gymId: isGym ? (original?.gymId ?? v.gymId ?? undefined) : undefined,
      isPublic: v.isPublic,
      // --- Description and Notes ---
      description: v.description.trim() || undefined,
      notes: v.notes.trim() || undefined,
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
