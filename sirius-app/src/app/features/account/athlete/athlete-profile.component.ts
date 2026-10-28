import {ChangeDetectionStrategy, Component, computed, inject, OnInit, signal} from '@angular/core';
import {FormBuilder, ReactiveFormsModule, Validators} from '@angular/forms';
import {TranslatePipe, TranslateService} from '@ngx-translate/core';
import {NgIcon} from '@ng-icons/core';
import {HlmButton} from '@spartan-ng/helm/button';
import {HlmCardImports} from '@spartan-ng/helm/card';
import {HlmInputGroupImports} from '@spartan-ng/helm/input-group';
import {AuthService} from '../../../api/antares/services/auth.service';
import {UserService} from '../../../api/antares/services/user.service';
import {BodyWeightResponse, Division, DIVISIONS} from '../../../api/antares/models/user.model';
import {NotificationService} from '../../../core/services/notification.service';
import {UnitService} from '../../../core/services/unit.service';
import {problemDetailOf} from '../../../core/models/problem-detail.model';
import {FormFieldComponent} from '../../../shared/component/form-field/form-field.component';

/**
 * Athlete profile (D31), all optional: division (men's/women's loads highlighted, separate
 * leaderboards), birth year, height and body weight history. Only the division is shared (it is
 * copied on the scores logged afterwards); the rest stays private (Antares returns it to its owner
 * only). Heights and weights are typed and shown in the user's unit system, stored in cm / kg.
 */
@Component({
  selector: 'app-athlete-profile',
  standalone: true,
  imports: [ReactiveFormsModule, TranslatePipe, NgIcon, HlmButton, HlmCardImports, HlmInputGroupImports,
    FormFieldComponent],
  templateUrl: './athlete-profile.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AthleteProfileComponent implements OnInit {
  // --- INJECTIONS ---
  private readonly authService = inject(AuthService);
  private readonly userService = inject(UserService);
  private readonly notificationService = inject(NotificationService);
  private readonly translate = inject(TranslateService);
  protected readonly units = inject(UnitService);
  private readonly fb = inject(FormBuilder);

  // --- STATE (SIGNALS & VARIABLES) ---
  readonly divisions = DIVISIONS;
  readonly currentYear = new Date().getFullYear();
  readonly division = signal<Division | null>(null);
  readonly profileForm = this.fb.group({
    birthYear: [null as number | null, [Validators.min(1900), Validators.max(this.currentYear)]],
    height: [null as number | null, [Validators.min(1)]]
  });
  readonly weightForm = this.fb.group({
    weight: [null as number | null, [Validators.required, Validators.min(1)]],
    measuredOn: [this.today(), [Validators.required]]
  });
  readonly weights = signal<BodyWeightResponse[]>([]);
  readonly savingProfile = signal(false);
  readonly savingWeight = signal(false);

  // --- COMPUTED STATE ---
  readonly latest = computed(() => this.weights()[0] ?? null);

  // --- LIFECYCLE ---
  ngOnInit(): void {
    const user = this.authService.currentUser();
    if (user) {
      this.division.set(user.division ?? null);
      this.profileForm.patchValue({
        birthYear: user.birthYear ?? null,
        height: user.heightCm != null ? this.units.heightFromCm(user.heightCm) : null
      });
    }
    this.loadWeights();
  }

  // --- DATA FETCHING ---
  private loadWeights(): void {
    this.userService.getBodyWeights().subscribe({
      next: weights => this.weights.set(weights),
      error: () => this.weights.set([])
    });
  }

  // --- ACTIONS & EVENTS ---
  onDivision(division: Division | null): void {
    this.division.set(division);
    this.profileForm.markAsDirty();
  }

  saveProfile(): void {
    if (this.profileForm.invalid || this.savingProfile()) return;
    const {birthYear, height} = this.profileForm.getRawValue();
    this.savingProfile.set(true);
    this.userService.updateAthleteProfile({
      division: this.division(),
      birthYear: birthYear ?? null,
      heightCm: height != null ? this.units.heightToCm(height) : null
    }).subscribe({
      next: () => {
        this.savingProfile.set(false);
        this.profileForm.markAsPristine();
        this.notificationService.showSuccess(this.translate.instant('ATHLETE_PROFILE.SAVED'));
      },
      error: err => this.fail(err, this.savingProfile)
    });
  }

  addWeight(): void {
    const {weight, measuredOn} = this.weightForm.getRawValue();
    if (this.weightForm.invalid || weight == null || this.savingWeight()) return;
    this.savingWeight.set(true);
    this.userService.recordBodyWeight({weightKg: this.units.bodyWeightToKg(weight), measuredOn}).subscribe({
      next: () => {
        this.savingWeight.set(false);
        this.weightForm.reset({weight: null, measuredOn: this.today()});
        this.loadWeights();
      },
      error: err => this.fail(err, this.savingWeight)
    });
  }

  deleteWeight(measure: BodyWeightResponse): void {
    this.userService.deleteBodyWeight(measure.id).subscribe({
      next: () => this.weights.set(this.weights().filter(w => w.id !== measure.id)),
      error: err => this.notificationService.showError(
        problemDetailOf(err) ?? this.translate.instant('GLOBAL.ERROR_UNEXPECTED'))
    });
  }

  // --- HELPERS ---
  /** "28 oct. 2026" in the language shown. */
  date(isoDate: string): string {
    const lang = this.translate.currentLang() ?? 'fr';
    return new Date(isoDate + 'T00:00:00').toLocaleDateString(lang, {day: 'numeric', month: 'short', year: 'numeric'});
  }

  private today(): string {
    const now = new Date();
    return new Date(now.getTime() - now.getTimezoneOffset() * 60000).toISOString().slice(0, 10);
  }

  private fail(err: unknown, saving: {set(value: boolean): void}): void {
    saving.set(false);
    this.notificationService.showError(problemDetailOf(err) ?? this.translate.instant('GLOBAL.ERROR_UNEXPECTED'));
  }
}
