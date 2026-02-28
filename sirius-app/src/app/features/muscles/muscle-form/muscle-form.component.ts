import {ChangeDetectionStrategy, Component, inject, OnInit, signal} from '@angular/core';
import {FormBuilder, ReactiveFormsModule, Validators} from '@angular/forms';
import {ActivatedRoute, Router, RouterModule} from '@angular/router';
import {HttpErrorResponse} from '@angular/common/http';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {MuscleService} from '../../../api/aldebaran/services/muscle.service';
import {NotificationService} from '../../../core/services/notification.service';
import {MuscleRequest} from '../../../api/aldebaran/models/muscle.model';
import {ProblemDetail} from '../../../core/models/problem-detail.model';

@Component({
  selector: 'app-muscle-form',
  standalone: true,
  imports: [ReactiveFormsModule, RouterModule, TranslateModule],
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
  private readonly databaseId = signal<number | null>(null);

  muscleGroups = ['CHEST', 'BACK', 'LEGS', 'ARMS', 'SHOULDERS', 'CORE', 'FULL_BODY'];

  muscleForm = this.fb.nonNullable.group({
    medicalName: ['', [Validators.required, Validators.maxLength(100)]],
    commonNameEn: ['', [Validators.maxLength(100)]],
    commonNameFr: ['', [Validators.maxLength(100)]],
    descriptionEn: ['', [Validators.maxLength(2000)]],
    descriptionFr: ['', [Validators.maxLength(2000)]],
    muscleGroup: ['', [Validators.required]]
  });

  ngOnInit(): void {
    const medicalNameParam = this.route.snapshot.paramMap.get('id');
    if (medicalNameParam) {
      this.isEditMode.set(true);
      this.loadMuscle(medicalNameParam);
    }
  }

  private loadMuscle(medicalName: string): void {
    this.isLoading.set(true);
    this.muscleForm.disable();

    this.muscleService.getMuscle(medicalName).subscribe({
      next: (muscle) => {
        this.databaseId.set(muscle.id);
        this.muscleForm.patchValue({
          medicalName: muscle.medicalName,
          commonNameEn: muscle.commonNameEn || '',
          commonNameFr: muscle.commonNameFr || '',
          descriptionEn: muscle.descriptionEn || '',
          descriptionFr: muscle.descriptionFr || '',
          muscleGroup: muscle.muscleGroup as string
        });

        this.muscleForm.enable();
        this.muscleForm.controls.medicalName.disable();
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
    const payload = this.muscleForm.getRawValue() as unknown as MuscleRequest;

    const request$ = this.isEditMode() && this.databaseId()
      ? this.muscleService.updateMuscle(this.databaseId()!, payload)
      : this.muscleService.createMuscle(payload);

    request$.subscribe({
      next: () => {
        const key = this.isEditMode() ? 'MUSCLE.FORM.SUCCESS_UPDATE' : 'MUSCLE.FORM.SUCCESS_CREATE';
        this.translate.get(key).subscribe((message: string) => {
          this.notificationService.showSuccess(message);
        });
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

    // On extrait le détail du backend, s'il n'y a rien on utilise la clé de traduction générique
    const message = problem?.detail || problem?.title || this.translate.instant('GLOBAL.ERROR_UNEXPECTED');

    this.notificationService.showError(message);
  }
}
