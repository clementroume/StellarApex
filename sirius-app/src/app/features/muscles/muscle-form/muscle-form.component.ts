import {ChangeDetectionStrategy, Component, computed, inject, OnInit, signal} from '@angular/core';
import {toSignal} from '@angular/core/rxjs-interop';
import {FormBuilder, ReactiveFormsModule, Validators} from '@angular/forms';
import {ActivatedRoute, Router, RouterModule} from '@angular/router';
import {HttpErrorResponse} from '@angular/common/http';
import {TranslatePipe, TranslateService} from '@ngx-translate/core';
import {MuscleService} from '../../../api/aldebaran/services/muscle.service';
import {NotificationService} from '../../../core/services/notification.service';
import {MuscleRequest, MuscleResponse} from '../../../api/aldebaran/models/muscle.model';
import {ProblemDetail} from '../../../core/models/problem-detail.model';
import {NgIcon} from '@ng-icons/core';

import {SectionDividerComponent} from '../../../shared/component/section-divider/section-divider.component';
import {HlmSpinner} from '@spartan-ng/helm/spinner';
import {NativeSelectDirective} from '../../../shared/component/native-select/native-select.directive';
import {HlmButton} from '@spartan-ng/helm/button';
import {HlmInput} from '@spartan-ng/helm/input';
import {HlmLabel} from '@spartan-ng/helm/label';
import {HlmTextarea} from '@spartan-ng/helm/textarea';
@Component({
  selector: 'app-muscle-form',
  standalone: true,
  imports: [HlmTextarea, HlmLabel, HlmInput, HlmButton, NativeSelectDirective, HlmSpinner, SectionDividerComponent, ReactiveFormsModule, RouterModule, TranslatePipe, NgIcon],
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
  private readonly allMuscles = signal<MuscleResponse[]>([]);
  /** Number of muscles of the edited muscle when it is a functional group (it cannot join another group). */
  membersCount = signal<number>(0);

  muscleForm = this.fb.nonNullable.group({
    // --- Identification ---
    medicalName: ['', [Validators.required, Validators.maxLength(100)]],
    // --- Characteristics ---
    muscleGroup: ['', [Validators.required]],
    parentId: [null as number | null],
    // --- Internationalized Content ---
    commonNameEn: ['', [Validators.maxLength(100)]],
    commonNameFr: ['', [Validators.maxLength(100)]],
    descriptionEn: ['', [Validators.maxLength(2000)]],
    descriptionFr: ['', [Validators.maxLength(2000)]],
    // --- Media ---
    imageUrl: ['', [Validators.maxLength(512)]],
  });

  private readonly region = toSignal(this.muscleForm.controls.muscleGroup.valueChanges, {initialValue: ''});

  /** Possible functional groups: top-level muscles of the same region, except the muscle itself. */
  groupCandidates = computed(() => this.allMuscles()
    .filter(m => !m.parent && m.muscleGroup === this.region() && m.id !== this.muscleId())
    .sort((a, b) => a.medicalName.localeCompare(b.medicalName)));

  ngOnInit(): void {
    this.muscleService.getReferenceData().subscribe(ref => this.muscleGroups.set(ref.muscleGroups));
    this.muscleService.getMuscles().subscribe(muscles => this.allMuscles.set(muscles));
    // A functional group must be in the same region: changing the region drops a group that no longer fits
    this.muscleForm.controls.muscleGroup.valueChanges.subscribe(() => {
      const parentId = this.muscleForm.controls.parentId.value;
      if (parentId != null && this.allMuscles().length > 0 && !this.groupCandidates().some(g => g.id === parentId)) {
        this.muscleForm.controls.parentId.setValue(null);
      }
    });

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
          parentId: muscle.parent?.id ?? null,
          // --- Internationalized Content ---
          commonNameEn: muscle.commonNameEn || '',
          commonNameFr: muscle.commonNameFr || '',
          descriptionEn: muscle.descriptionEn || '',
          descriptionFr: muscle.descriptionFr || '',
          // --- Media ---
          imageUrl: muscle.imageUrl || ''
        });

        this.muscleForm.enable();
        this.membersCount.set(muscle.members?.length ?? 0);
        if (this.membersCount() > 0) {
          this.muscleForm.controls.parentId.disable();
        }
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
