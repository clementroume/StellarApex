import {ChangeDetectionStrategy, Component, inject, OnInit, signal} from '@angular/core';
import {FormBuilder, ReactiveFormsModule, Validators} from '@angular/forms';
import {ActivatedRoute, Router, RouterModule} from '@angular/router';
import {HttpErrorResponse} from '@angular/common/http';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {MuscleService} from '../../../api/aldebaran/services/muscle.service';
import {NotificationService} from '../../../core/services/notification.service';
import {MuscleRequest} from '../../../api/aldebaran/models/muscle.model';
import {ProblemDetail} from '../../../core/models/problem-detail.model';
import {NgIcon} from '@ng-icons/core';

@Component({
  selector: 'app-muscle-form',
  standalone: true,
  imports: [ReactiveFormsModule, RouterModule, TranslateModule, NgIcon],
  templateUrl: './muscle-form.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MuscleFormComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly muscleService = inject(MuscleService);
  private readonly notificationService = inject(NotificationService);
  private readonly translate = inject(TranslateService);

  isEditMode = signal<boolean>(false);
  isLoading = signal<boolean>(false);
  private readonly muscleId = signal<number | null>(null);

  muscleGroups = signal<string[]>([]);

  muscleForm = this.fb.nonNullable.group({
    // --- Identification ---
    medicalName: ['', [Validators.required, Validators.maxLength(100)]],
    // --- Characteristics ---
    muscleGroup: ['', [Validators.required]],
    // --- Internationalized Content ---
    commonNameEn: ['', [Validators.maxLength(100)]],
    commonNameFr: ['', [Validators.maxLength(100)]],
    descriptionEn: ['', [Validators.maxLength(2000)]],
    descriptionFr: ['', [Validators.maxLength(2000)]],
    // --- Media ---
    imageUrl: ['', [Validators.maxLength(512)]],
  });

  ngOnInit(): void {
    this.muscleService.getReferenceData().subscribe(ref => this.muscleGroups.set(ref.muscleGroups));

    const idParam = this.route.snapshot.paramMap.get('id');
    if (idParam && !Number.isNaN(Number(idParam))) {
      this.isEditMode.set(true);
      this.loadMuscle(Number(idParam));
    }
  }

  private loadMuscle(id: number): void {
    this.isLoading.set(true);
    this.muscleForm.disable();

    this.muscleService.getMuscle(id).subscribe({
      next: (muscle) => {
        this.muscleId.set(muscle.id);
        this.muscleForm.patchValue({
          // --- Identification ---
          medicalName: muscle.medicalName,
          // --- Characteristics ---
          muscleGroup: muscle.muscleGroup,
          // --- Internationalized Content ---
          commonNameEn: muscle.commonNameEn || '',
          commonNameFr: muscle.commonNameFr || '',
          descriptionEn: muscle.descriptionEn || '',
          descriptionFr: muscle.descriptionFr || '',
          // --- Media ---
          imageUrl: muscle.imageUrl || ''
        });

        this.muscleForm.enable();
        this.isLoading.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.handleError(err);
        void this.router.navigate(['/muscles']);
      }
    });
  }

  onSubmit(): void {
    if (this.muscleForm.invalid) {
      this.muscleForm.markAllAsTouched();
      return;
    }

    this.isLoading.set(true);
    const formValue = this.muscleForm.getRawValue();

    const payload = {
      ...formValue,
      commonNameEn: formValue.commonNameEn?.trim() || null,
      commonNameFr: formValue.commonNameFr?.trim() || null,
      descriptionEn: formValue.descriptionEn?.trim() || null,
      descriptionFr: formValue.descriptionFr?.trim() || null,
      imageUrl: formValue.imageUrl?.trim() || null
    } as unknown as MuscleRequest;

    const request$ = this.isEditMode() && this.muscleId()
      ? this.muscleService.updateMuscle(this.muscleId()!, payload)
      : this.muscleService.createMuscle(payload);

    request$.subscribe({
      next: () => {
        const key = this.isEditMode() ? 'MUSCLE.MESSAGES.SUCCESS_UPDATE' : 'MUSCLE.MESSAGES.SUCCESS_CREATE';
        this.notificationService.showSuccess(this.translate.instant(key));
        void this.router.navigate(['/muscles']);
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
