import {ChangeDetectionStrategy, Component, computed, inject, OnInit, signal} from '@angular/core';
import {CommonModule} from '@angular/common';
import {FormArray, FormBuilder, ReactiveFormsModule, Validators} from '@angular/forms';
import {ActivatedRoute, Router, RouterModule} from '@angular/router';
import {HttpErrorResponse} from '@angular/common/http';
import {TranslatePipe, TranslateService} from '@ngx-translate/core';
import {NgIconComponent} from '@ng-icons/core';
import {MovementService} from '../../../api/aldebaran/services/movement.service';
import {MuscleService} from '../../../api/aldebaran/services/muscle.service';
import {NotificationService} from '../../../core/services/notification.service';
import {MuscleResponse} from '../../../api/aldebaran/models/muscle.model';
import {MovementRequest, MovementComponent, MovementJointAction, MovementResponse, MovementSummaryResponse, RoleAdjustment, UsualName, UsualNameOption} from '../../../api/aldebaran/models/movement.model';
import {ProblemDetail} from '../../../core/models/problem-detail.model';
import {forkJoin} from 'rxjs';
import {MovementComboboxComponent} from '../../../shared/component/movement-combobox/movement-combobox.component';
import {toggleVariant, VariantPickerComponent} from '../../../shared/component/variant-picker/variant-picker.component';

import {HlmSpinner} from '@spartan-ng/helm/spinner';
import {NativeSelectDirective} from '../../../shared/component/native-select/native-select.directive';
import {HlmBadge} from '@spartan-ng/helm/badge';
import {HlmButton} from '@spartan-ng/helm/button';
import {HlmInput} from '@spartan-ng/helm/input';
import {HlmTextarea} from '@spartan-ng/helm/textarea';
import {HlmCardImports} from '@spartan-ng/helm/card';
@Component({
  selector: 'app-movement-form',
  standalone: true,
  imports: [MovementComboboxComponent, VariantPickerComponent, HlmCardImports, HlmTextarea, HlmInput, HlmButton, HlmBadge, NativeSelectDirective, HlmSpinner, CommonModule, ReactiveFormsModule, RouterModule, TranslatePipe, NgIconComponent],
  templateUrl: './movement-form.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MovementFormComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly movementService = inject(MovementService);
  private readonly muscleService = inject(MuscleService);
  private readonly notificationService = inject(NotificationService);
  private readonly translate = inject(TranslateService);

  isEditMode = signal<boolean>(false);
  isLoading = signal<boolean>(false);
  private readonly movementId = signal<number | null>(null);

  availableMuscles = signal<MuscleResponse[]>([]);
  availableMovements = signal<MovementSummaryResponse[]>([]);
  /** The movements as combobox entries. */
  readonly movementOptions = computed<UsualNameOption[]>(() => this.availableMovements().map(m => ({
    movementId: m.id, movementName: m.name, category: m.category, nameEn: m.name, searchTerms: m.nameAbbreviation, techniques: [], equipment: []
  })));
  private readonly componentDetails = signal<Record<number, MovementResponse>>({});
  readonly techniqueTypes = signal<Record<string, string[]> | null>(null);
  private readonly exclusiveSets = signal<string[][]>([]);
  private readonly acceptedIds = signal<number[]>([]);
  readonly acceptedMovements = computed(() => this.acceptedIds()
    .map(id => this.availableMovements().find(m => m.id === id))
    .filter((m): m is MovementSummaryResponse => !!m));
  readonly acceptedPickerKey = signal<number>(0);
  readonly tabs = ['identity', 'variants', 'muscles', 'names', 'composition'] as const;
  readonly tab = signal<(typeof this.tabs)[number]>('identity');
  readonly requiredRuleValues = ['LOAD_POSITION_IF_EQUIPMENT', 'AMPLITUDE', 'ROTATIONS', 'SWING_STYLE', 'EQUIPMENT'];
  /** Last refusal of the catalog rules, shown next to the save button. */
  readonly formError = signal<string | null>(null);
  /** The joint layer has no editor yet: it goes back unchanged. */
  private jointActions: MovementJointAction[] = [];

  // On injecte les groupes structurés
  categoryGroups = signal<{ modality: string; items: string[] }[]>([]);
  equipmentGroups = signal<{ category: string; items: string[] }[]>([]);
  techniqueGroups = signal<{ category: string; items: string[] }[]>([]);
  muscleRoles = signal<string[]>([]);

  movementForm = this.fb.nonNullable.group({
    // --- Identification ---
    name: ['', [Validators.required, Validators.maxLength(100)]],
    nameAbbreviation: ['', [Validators.maxLength(20)]],
    category: ['', Validators.required],
    // --- Characteristics ---
    equipment: [[] as string[]],
    techniques: [[] as string[]],
    muscles: this.fb.array([]),
    impliedEquipment: [null as string | null],
    impliedRequiresPosition: [false],
    requiredRules: [[] as string[]],
    dumbbellBases: [''],
    roleAdjustments: this.fb.array([]),
    usualNames: this.fb.array([]),
    components: this.fb.array([]),
    acceptedMovementIds: [[] as number[]],
    // --- Internationalized Content ---
    descriptionEn: ['', [Validators.maxLength(2000)]],
    descriptionFr: ['', [Validators.maxLength(2000)]],
    coachingCuesEn: ['', [Validators.maxLength(2000)]],
    coachingCuesFr: ['', [Validators.maxLength(2000)]],
    // --- Media ---
    videoUrl: ['', [Validators.maxLength(255)]],
    imageUrl: ['', [Validators.maxLength(255)]]
  });

  ngOnInit(): void {
    this.movementService.getMovements().subscribe(list => this.availableMovements.set(list));
    forkJoin({
      muscles: this.muscleService.getMuscles(),
      muscleRef: this.muscleService.getReferenceData(),
      movementRef: this.movementService.getReferenceData()
    }).subscribe(({muscles, muscleRef, movementRef}) => {
      this.availableMuscles.set(muscles);
      this.muscleRoles.set(muscleRef.muscleRoles);
      this.techniqueTypes.set(movementRef.techniqueGroups);
      this.exclusiveSets.set(movementRef.exclusiveTechniqueSets ?? []);

      // On convertit les Record (Map Java) en tableaux d'objets pour le HTML
      this.categoryGroups.set(Object.entries(movementRef.categoryGroups).map(([k, v]) => ({
        modality: k,
        items: v
      })));
      this.equipmentGroups.set(Object.entries(movementRef.equipmentGroups).map(([k, v]) => ({
        category: k,
        items: v
      })));
      this.techniqueGroups.set(Object.entries(movementRef.techniqueGroups).map(([k, v]) => ({
        category: k,
        items: v
      })));
    });

    const idParam = this.route.snapshot.paramMap.get('id');
    if (idParam && !Number.isNaN(Number(idParam))) {
      const id = Number(idParam);
      this.isEditMode.set(true);
      this.movementId.set(id);
      this.loadMovement(id);
    }
  }

  get musclesFormArray(): FormArray {
    return this.movementForm.get('muscles') as FormArray;
  }

  get adjustments(): FormArray {
    return this.movementForm.get('roleAdjustments') as FormArray;
  }

  get usualNamesArray(): FormArray {
    return this.movementForm.get('usualNames') as FormArray;
  }

  get componentsArray(): FormArray {
    return this.movementForm.get('components') as FormArray;
  }

  /** Techniques and equipment the movement allows: what can trigger an adjustment. */
  triggerValues(): string[] {
    const v = this.movementForm.getRawValue();
    return [...v.techniques, ...v.equipment];
  }

  triggerLabel(value: string): string {
    return (this.movementForm.getRawValue().techniques.includes(value) ? 'MOVEMENT.TECHNIQUES.' : 'MOVEMENT.EQUIPMENT.') + value;
  }

  toggleRule(rule: string): void {
    const control = this.movementForm.controls.requiredRules;
    control.setValue(control.value.includes(rule) ? control.value.filter(r => r !== rule) : [...control.value, rule]);
  }

  addAdjustment(value?: RoleAdjustment): void {
    this.adjustments.push(this.fb.group({
      trigger: [value?.trigger ?? null as string | null, Validators.required],
      muscleId: [value?.muscleId ?? null as number | null, Validators.required],
      role: [value?.role ?? 'SYNERGIST', Validators.required]
    }));
  }

  addUsualName(value?: UsualName): void {
    this.usualNamesArray.push(this.fb.group({
      nameEn: [value?.nameEn ?? '', [Validators.required, Validators.maxLength(80)]],
      nameFr: [value?.nameFr ?? '', Validators.maxLength(80)],
      searchTerms: [value?.searchTerms ?? '', Validators.maxLength(255)],
      techniques: [[...(value?.techniques ?? [])] as string[]],
      equipment: [[...(value?.equipment ?? [])] as string[]],
      descriptionEn: [value?.descriptionEn ?? null],
      descriptionFr: [value?.descriptionFr ?? null],
      videoUrl: [value?.videoUrl ?? null]
    }));
  }

  addComponent(value?: MovementComponent): void {
    this.componentsArray.push(this.fb.group({
      movementId: [value?.movementId ?? null as number | null, Validators.required],
      techniques: [(value?.techniques ?? []).join(' ')],
      equipment: [(value?.equipment ?? []).join(' ')],
      countsForRecords: [value?.countsForRecords ?? false]
    }));
  }

  /** A step's movement: its allowed techniques are loaded for the chips, its former techniques dropped. */
  onComponentPicked(index: number, option: UsualNameOption | null): void {
    const group = this.componentsArray.at(index);
    group.patchValue({movementId: option?.movementId ?? null, techniques: [], equipment: []});
    if (option) this.loadComponent(option.movementId);
  }

  componentTechniques(index: number): string[] {
    const id = this.componentsArray.at(index).get('movementId')?.value as number | null;
    return (id != null ? this.componentDetails()[id]?.techniques : null) ?? [];
  }

  toggleComponentTechnique(index: number, value: string): void {
    const control = this.componentsArray.at(index).get('techniques')!;
    control.setValue(toggleVariant((control.value as string[]) ?? [], value, this.exclusiveSets()));
  }

  private loadComponent(id: number): void {
    if (this.componentDetails()[id]) return;
    this.movementService.getMovement(id).subscribe(detail => this.componentDetails.update(d => ({...d, [id]: detail})));
  }

  addAccepted(option: UsualNameOption | null): void {
    if (option) {
      const control = this.movementForm.controls.acceptedMovementIds;
      if (!control.value.includes(option.movementId)) control.setValue([...control.value, option.movementId]);
      this.acceptedIds.set(control.value);
    }
    // The picker comes back empty for the next one
    this.acceptedPickerKey.update(k => k + 1);
  }

  removeAccepted(id: number): void {
    const control = this.movementForm.controls.acceptedMovementIds;
    control.setValue(control.value.filter(v => v !== id));
    this.acceptedIds.set(control.value);
  }

  addMuscle(): void {
    this.musclesFormArray.push(this.fb.group({
      muscleId: [null as number | null, Validators.required],
      role: ['AGONIST', Validators.required],
      impactFactor: [1]
    }));
  }

  removeMuscle(index: number): void {
    this.musclesFormArray.removeAt(index);
  }

  toggleSelection(controlName: 'equipment' | 'techniques', item: string): void {
    const current = this.movementForm.get(controlName)?.value as string[];
    const index = current.indexOf(item);
    if (index === -1) {
      this.movementForm.patchValue({[controlName]: [...current, item]});
    } else {
      const updated = [...current];
      updated.splice(index, 1);
      this.movementForm.patchValue({[controlName]: updated});
    }
  }

  isItemSelected(controlName: 'equipment' | 'techniques', item: string): boolean {
    const current = this.movementForm.get(controlName)?.value as string[];
    return current.includes(item);
  }

  private loadMovement(id: number): void {
    this.isLoading.set(true);
    this.movementForm.disable();

    this.movementService.getMovement(id).subscribe({
      next: (movement) => {
        this.movementForm.patchValue({
          // --- Identification ---
          name: movement.name,
          nameAbbreviation: movement.nameAbbreviation || '',
          category: movement.category,
          // --- Characteristics ---
          equipment: movement.equipment,
          techniques: movement.techniques,
          // --- Internationalized Content ---
          descriptionEn: movement.descriptionEn || '',
          descriptionFr: movement.descriptionFr || '',
          coachingCuesEn: movement.coachingCuesEn || '',
          coachingCuesFr: movement.coachingCuesFr || '',

          // --- Media ---
          videoUrl: movement.videoUrl || '',
          imageUrl: movement.imageUrl || '',
          // --- Catalog model ---
          impliedEquipment: movement.impliedEquipment ?? null,
          impliedRequiresPosition: movement.impliedRequiresPosition ?? false,
          requiredRules: movement.requiredRules ?? [],
          dumbbellBases: (movement.dumbbellBases ?? [])
            .map(b => `${b.loadPosition}=${b.implementCount ?? ''}`).join(', '),
          acceptedMovementIds: (movement.acceptedMovements ?? []).map(a => a.id)
        });
        this.jointActions = movement.jointActions ?? [];
        (movement.roleAdjustments ?? []).forEach(a => this.addAdjustment(a));
        (movement.usualNames ?? []).forEach(u => this.addUsualName(u));
        (movement.components ?? []).forEach(c => {
          this.addComponent(c);
          this.loadComponent(c.movementId);
        });
        this.acceptedIds.set(this.movementForm.controls.acceptedMovementIds.value);

        if (movement.targetedMuscles) {
          movement.targetedMuscles.forEach(tm => {
            this.musclesFormArray.push(this.fb.group({
              muscleId: [tm.muscle.id, Validators.required],
              role: [tm.role, Validators.required],
              impactFactor: [tm.impactFactor]
            }));
          });
        }
        this.movementForm.enable();
        this.isLoading.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.handleError(err);
        void this.router.navigate(['/movements']);
      }
    });
  }

  onSubmit(): void {
    if (this.movementForm.invalid) {
      this.movementForm.markAllAsTouched();
      return;
    }

    this.isLoading.set(true);
    const formValue = this.movementForm.getRawValue();

    const words = (text: string | null | undefined) => (text ?? '').trim().split(/\s+/).filter(Boolean);
    const techniques = new Set(formValue.techniques);
    const payload = {
      ...formValue,
      nameAbbreviation: formValue.nameAbbreviation?.trim() || null,
      dumbbellBases: formValue.dumbbellBases.split(',').map(p => p.trim()).filter(p => p.includes('=')).map(p => {
        const [loadPosition, count] = p.split('=');
        return {loadPosition: loadPosition.trim(), implementCount: count.trim() ? Number(count) : null};
      }),
      roleAdjustments: (formValue.roleAdjustments as RoleAdjustment[]).map(a => ({
        ...a, triggerKind: techniques.has(a.trigger) ? 'TECHNIQUE' : 'EQUIPMENT'
      })),
      usualNames: (formValue.usualNames as (Omit<UsualName, 'techniques' | 'equipment'> & {techniques: string; equipment: string})[])
        .map(u => ({...u, nameFr: u.nameFr || null, searchTerms: u.searchTerms || null, techniques: words(u.techniques), equipment: words(u.equipment)})),
      components: (formValue.components as Omit<MovementComponent, 'componentOrder'>[]).map((c, i) => ({...c, componentOrder: i + 1})),
      jointActions: this.jointActions
    } as unknown as MovementRequest;
    this.formError.set(null);

    const request$ = this.isEditMode() && this.movementId()
      ? this.movementService.updateMovement(this.movementId()!, payload)
      : this.movementService.createMovement(payload);

    request$.subscribe({
      next: () => {
        const key = this.isEditMode() ? 'MOVEMENT.MESSAGES.SUCCESS_UPDATE' : 'MOVEMENT.MESSAGES.SUCCESS_CREATE';
        this.notificationService.showSuccess(this.translate.instant(key));
        void this.router.navigate(['/movements']);
      },
      error: (err: HttpErrorResponse) => {
        this.isLoading.set(false);
        this.handleError(err);
      }
    });
  }

  private handleError(err: HttpErrorResponse): void {
    const problem: ProblemDetail = err.error;
    const message = problem?.detail || problem?.title || this.translate.instant('GLOBAL.ERROR_UNEXPECTED');
    this.formError.set(message);
    this.notificationService.showError(message);
  }
}
