import {ChangeDetectionStrategy, Component, computed, effect, inject, input, signal} from '@angular/core';
import {TranslatePipe, TranslateService} from '@ngx-translate/core';
import {Router, RouterModule} from '@angular/router';
import {Location} from '@angular/common';
import {MuscleRefResponse, MuscleResponse, MuscleUsage} from '../../../api/aldebaran/models/muscle.model';
import {AccessService} from '../../../core/services/access.service';
import {MuscleService} from '../../../api/aldebaran/services/muscle.service';
import {NotificationService} from '../../../core/services/notification.service';
import {HttpContext, HttpErrorResponse} from '@angular/common/http';
import {BYPASS_LOADER} from '../../../core/interceptors/loading.interceptor';
import {
  DetailAdminActionsComponent
} from '../../../shared/component/detail/detail-admin-actions/detail-admin-actions.component';
import {DetailHeaderComponent} from '../../../shared/component/detail/detail-header/detail-header.component';
import {DetailStateComponent} from '../../../shared/component/detail/detail-state/detail-state.component';

import {ThemeService} from '../../../core/services/theme.service';
import {MuscleCreditComponent} from '../muscle-credit/muscle-credit.component';
import {muscleModelEntry, muscleThumbUrl} from '../muscle-model/muscle-model';
import {normalize} from '../../../shared/text.utils';
import {MuscleSceneComponent} from '../muscle-scene/muscle-scene.component';
import {HlmButton} from '@spartan-ng/helm/button';
@Component({
  selector: 'app-muscle-detail',
  standalone: true,
  imports: [HlmButton, TranslatePipe, RouterModule, MuscleSceneComponent, MuscleCreditComponent, DetailAdminActionsComponent, DetailHeaderComponent, DetailStateComponent],
  templateUrl: './muscle-detail.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  styles: `
    .tiles { display: flex; flex-wrap: wrap; justify-content: center; gap: 0.5rem; }
    .tile {
      display: flex; flex-direction: column; align-items: center; gap: 0.25rem; width: 4.5rem; padding: 0.25rem;
      border: 2px solid var(--sa-base-300); border-radius: 0.625rem; background: var(--sa-base-100);
      font-size: 0.6875rem; font-weight: 500; line-height: 1.2; text-align: center;
    }
    .tile[aria-current='page'] { border-color: var(--sa-secondary); }
    @media (min-width: 768px) { .tile { width: 5.25rem; } }
  `
})
export class MuscleDetailComponent {
  // --- INJECTIONS ---
  id = input.required<string>();
  private readonly translate = inject(TranslateService);
  private readonly accessService = inject(AccessService);
  private readonly muscleService = inject(MuscleService);
  private readonly notificationService = inject(NotificationService);
  private readonly router = inject(Router);
  private readonly location = inject(Location);
  private readonly themeService = inject(ThemeService);

  // --- STATE (SIGNALS) ---
  activeLang = computed(() => this.translate.currentLang() ?? this.translate.fallbackLang() ?? 'en');
  isAdmin = this.accessService.isAdmin;

  muscle = signal<MuscleResponse | null>(null);
  usage = signal<MuscleUsage[]>([]);
  readonly roles = ['AGONIST', 'SYNERGIST', 'STABILIZER'] as const;
  role = signal<MuscleUsage['role']>('AGONIST');
  readonly shownUsage = computed(() => this.usage().filter(u => u.role === this.role()));
  isLoading = signal<boolean>(true); // Nouvel état explicite
  /** The group shown as tiles: the muscle when it is one, else its parent. Kept while moving between its tiles. */
  private readonly family = signal<MuscleResponse | null>(null);
  private requestedId = 0;
  private readonly noLoader = new HttpContext().set(BYPASS_LOADER, true);

  readonly tiles = computed<MuscleRefResponse[]>(() => {
    const family = this.family();
    return family ? [family, ...(family.members ?? [])] : [];
  });

  constructor() {
    effect(() => {
      const currentId = Number(this.id());
      if (currentId && !Number.isNaN(currentId)) {
        this.requestedId = currentId;
        this.loadMuscle(currentId);
        this.muscleService.getUsage(currentId, this.noLoader).subscribe({
          next: usage => {
            if (this.requestedId === currentId) this.usage.set(usage);
          },
          error: () => {
            if (this.requestedId === currentId) this.usage.set([]);
          }
        });
      }
    });
  }

  // --- DATA FETCHING ---
  private loadMuscle(id: number): void {
    this.isLoading.set(true);

    this.muscleService.getMuscle(id, this.noLoader).subscribe({
      next: (m) => {
        if (this.requestedId !== id) return;
        this.muscle.set(m);
        this.isLoading.set(false);
        this.loadFamily(m);
      },
      error: () => {
        if (this.requestedId !== id) return;
        this.notificationService.showError(this.translate.instant('MUSCLE.MESSAGES.NOT_FOUND'));
        this.isLoading.set(false);
        this.location.back();
      }
    });
  }

  private loadFamily(muscle: MuscleResponse): void {
    if (muscle.members?.length) {
      this.family.set(muscle);
      return;
    }
    const parentId = muscle.parent?.id;
    if (parentId === undefined) {
      this.family.set(null);
      return;
    }
    if (this.family()?.id === parentId) return;
    this.family.set(null);
    this.muscleService.getMuscle(parentId, this.noLoader).subscribe({
      next: parent => {
        if (this.muscle()?.parent?.id === parentId) this.family.set(parent);
      },
      error: () => {
        if (this.muscle()?.parent?.id === parentId) this.family.set(null);
      }
    });
  }

  countOf(role: MuscleUsage['role']): number {
    return this.usage().filter(u => u.role === role).length;
  }

  /** The technique or equipment that brings a role, translated. */
  triggerLabel(trigger: string): string {
    const technique = 'MOVEMENT.TECHNIQUES.' + trigger;
    const label = this.translate.instant(technique);
    return label === technique ? this.translate.instant('MOVEMENT.EQUIPMENT.' + trigger) : label;
  }

  // --- ACTIONS ---
  goBack(): void {
    this.location.back();
  }

  onDelete(muscle: MuscleResponse): void {
    const name = this.getLocalizedName(muscle);
    const confirmMsg = this.translate.instant('GLOBAL.CONFIRM_DELETE', {name});

    if (confirm(confirmMsg)) {
      this.muscleService.deleteMuscle(muscle.id).subscribe({
        next: () => {
          this.notificationService.showSuccess(this.translate.instant('GLOBAL.DELETE_SUCCESS'));
          this.muscleService.notifyRefresh();
          void this.router.navigate(['/muscles']);
        },
        error: (err: HttpErrorResponse) => {
          if (err.status === 409) {
            this.notificationService.showError(this.translate.instant('MUSCLE.MESSAGES.DELETE_CONFLICT', {name}));
          } else {
            this.notificationService.showError(this.translate.instant('GLOBAL.DELETE_ERROR'));
          }
        }
      });
    }
  }

  // --- HELPERS ---
  /** The scene credits the model; without it the tiles still show its images. */
  hasModel(muscle: MuscleResponse): boolean {
    return !!muscleModelEntry(muscle.code);
  }

  thumbOf(muscle: MuscleRefResponse): string | undefined {
    return muscleThumbUrl(muscle.code, this.themeService.currentTheme());
  }

  /** The medical name beside the title, unless the title already says it. */
  medicalNameShown(muscle: MuscleResponse): string | undefined {
    return normalize(this.getLocalizedName(muscle)).includes(normalize(muscle.medicalName)) ? undefined : muscle.medicalName;
  }

  getLocalizedName(muscle: MuscleResponse | MuscleRefResponse): string {
    const isFr = this.activeLang() === 'fr';
    return (isFr ? muscle.commonNameFr : muscle.commonNameEn) || muscle.medicalName;
  }

  getLocalizedDescription(muscle: MuscleResponse): string {
    return this.activeLang() === 'fr' ? (muscle.descriptionFr || '') : (muscle.descriptionEn || '');
  }
}
