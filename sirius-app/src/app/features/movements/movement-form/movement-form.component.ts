import {ChangeDetectionStrategy, Component, inject, OnInit, signal} from '@angular/core';
import {CommonModule} from '@angular/common';
import {FormArray, FormBuilder, ReactiveFormsModule, Validators} from '@angular/forms';
import {ActivatedRoute, Router, RouterModule} from '@angular/router';
import {HttpErrorResponse} from '@angular/common/http';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {NgIconComponent} from '@ng-icons/core';
import {MovementService} from '../../../api/aldebaran/services/movement.service';
import {MuscleService} from '../../../api/aldebaran/services/muscle.service';
import {NotificationService} from '../../../core/services/notification.service';
import {MuscleResponse} from '../../../api/aldebaran/models/muscle.model';
import {MovementRequest} from '../../../api/aldebaran/models/movement.model';
import {ProblemDetail} from '../../../core/models/problem-detail.model';
import {forkJoin} from 'rxjs';

@Component({
  selector: 'app-movement-form',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterModule, TranslateModule, NgIconComponent],
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
    forkJoin({
      muscles: this.muscleService.getMuscles(),
      muscleRef: this.muscleService.getReferenceData(),
      movementRef: this.movementService.getReferenceData()
    }).subscribe(({muscles, muscleRef, movementRef}) => {
      this.availableMuscles.set(muscles);
      this.muscleRoles.set(muscleRef.muscleRoles);

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
          imageUrl: movement.imageUrl || ''
        });

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

    const payload = {
      ...formValue,
      nameAbbreviation: formValue.nameAbbreviation?.trim() || null,
    } as unknown as MovementRequest;

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
    this.notificationService.showError(message);
  }
}
