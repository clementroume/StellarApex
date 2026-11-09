import {ChangeDetectionStrategy, Component, computed, effect, inject, input, signal} from '@angular/core';
import {TranslatePipe, TranslateService} from '@ngx-translate/core';
import {Router, RouterModule} from '@angular/router';
import {Location, NgOptimizedImage} from '@angular/common';
import {NgIconComponent} from '@ng-icons/core';
import {MovementReferenceData, MovementResponse, MovementVariant, UsualName, VariantMuscle} from '../../../api/aldebaran/models/movement.model';
import {toggleVariant, VariantPickerComponent} from '../../../shared/component/variant-picker/variant-picker.component';
import {AccessService} from '../../../core/services/access.service';
import {MovementService} from '../../../api/aldebaran/services/movement.service';
import {NotificationService} from '../../../core/services/notification.service';
import {HttpContext, HttpErrorResponse} from '@angular/common/http';
import {forkJoin} from 'rxjs';
import {RecordService} from '../../../api/aldebaran/services/record.service';
import {BYPASS_LOADER} from '../../../core/interceptors/loading.interceptor';
import {LiftRecordsComponent} from '../../records/lift-records.component';
import {
  DetailAdminActionsComponent
} from '../../../shared/component/detail/detail-admin-actions/detail-admin-actions.component';
import {DetailCardComponent} from '../../../shared/component/detail/detail-card/detail-card.component';
import {DetailHeaderComponent} from '../../../shared/component/detail/detail-header/detail-header.component';
import {DetailStateComponent} from '../../../shared/component/detail/detail-state/detail-state.component';

import {HlmBadge} from '@spartan-ng/helm/badge';
import {HlmButton} from '@spartan-ng/helm/button';
@Component({
  selector: 'app-movement-detail',
  standalone: true,
  imports: [VariantPickerComponent, HlmButton, HlmBadge, TranslatePipe, RouterModule, NgIconComponent, NgOptimizedImage, DetailAdminActionsComponent, DetailCardComponent, DetailHeaderComponent, DetailStateComponent, LiftRecordsComponent],
  templateUrl: './movement-detail.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class MovementDetailComponent {
  // --- INJECTIONS ---
  id = input.required<string>();
  /** Query parameters: what a usual name stands for (comma-separated). */
  techniques = input<string>();
  equipment = input<string>();
  private readonly translate = inject(TranslateService);
  private readonly accessService = inject(AccessService);
  private readonly movementService = inject(MovementService);
  private readonly notificationService = inject(NotificationService);
  private readonly router = inject(Router);
  private readonly location = inject(Location);
  private readonly recordService = inject(RecordService);

  // --- STATE (SIGNALS) ---
  activeLang = computed(() => this.translate.currentLang() ?? this.translate.fallbackLang() ?? 'en');
  isAdmin = this.accessService.isAdmin;
  private variantRequest = 0;

  movement = signal<MovementResponse | null>(null);
  isLoading = signal<boolean>(true); // Nouvel état explicite
  /** Shows the user's records block when they hold one on this movement or it has canonical tests (lot C). */
  readonly showRecords = signal<boolean>(false);
  readonly referenceData = signal<MovementReferenceData | null>(null);
  readonly chosenTechniques = signal<string[]>([]);
  readonly chosenEquipment = signal<string[]>([]);
  readonly variant = signal<MovementVariant | null>(null);
  readonly roles = ['AGONIST', 'SYNERGIST', 'STABILIZER'] as const;

  // --- COMPUTED STATE ---
  readonly displayName = computed(() => {
    const v = this.variant();
    return (this.activeLang() === 'fr' ? v?.usualNameFr : null) || v?.usualNameEn || this.movement()?.name || '';
  });
  /** Variants worth a shortcut: every usual name but the plain movement. */
  readonly usualNames = computed(() => (this.movement()?.usualNames ?? [])
    .filter(u => (u.techniques?.length ?? 0) + (u.equipment?.length ?? 0) > 0)
    .sort((a, b) => a.nameEn.localeCompare(b.nameEn)));

  constructor() {
    effect(() => {
      const currentId = Number(this.id());
      if (currentId && !Number.isNaN(currentId)) {
        this.loadMovement(currentId);
        this.loadRecords(currentId);
      }
    });
    // The catalog says what a variant is called and which roles it gives
    effect(() => {
      const movement = this.movement();
      const techniques = this.chosenTechniques();
      const equipment = this.chosenEquipment();
      if (!movement) return;
      const asked = ++this.variantRequest;
      this.movementService.getVariant(movement.id, techniques, equipment, new HttpContext().set(BYPASS_LOADER, true)).subscribe({
        next: variant => {
          if (asked === this.variantRequest) this.variant.set(variant);
        },
        error: () => {
          if (asked === this.variantRequest) this.variant.set(null);
        }
      });
    });
    this.movementService.getReferenceData().subscribe(reference => this.referenceData.set(reference));
  }

  // --- DATA FETCHING ---
  private loadMovement(id: number): void {
    this.isLoading.set(true);

    this.movementService.getMovement(id).subscribe({
      next: (m) => {
        this.chosenTechniques.set(this.fromQuery(this.techniques(), m.techniques));
        this.chosenEquipment.set(this.fromQuery(this.equipment(), m.equipment));
        this.movement.set(m);
        this.isLoading.set(false);
      },
      error: () => {
        this.notificationService.showError(this.translate.instant('MOVEMENT.MESSAGES.NOT_FOUND'));
        this.isLoading.set(false);
        this.location.back();
      }
    });
  }

  private loadRecords(id: number): void {
    // Both lists are cached by RecordService: the block itself loads the details
    const context = new HttpContext().set(BYPASS_LOADER, true);
    this.showRecords.set(false);
    forkJoin([this.recordService.getMyRecords(context), this.recordService.getCanonicalLifts(context)]).subscribe({
      next: ([records, lifts]) =>
        this.showRecords.set(records.some(r => r.movementId === id) || lifts.some(l => l.movementId === id)),
      error: () => this.showRecords.set(false)
    });
  }

  // --- ACTIONS ---
  toggleTechnique(value: string): void {
    this.chosenTechniques.update(selected => toggleVariant(selected, value, this.referenceData()?.exclusiveTechniqueSets));
  }

  /** One equipment at a time: each one is another record. */
  toggleEquipment(value: string): void {
    this.chosenEquipment.update(selected => selected.includes(value) ? [] : [value]);
  }

  /** A usual name is a shortcut for its techniques and equipment. */
  applyUsualName(name: UsualName): void {
    // Tapping the name shown again leaves it
    if (this.isUsualNameOn(name)) {
      this.chosenTechniques.update(selected => selected.filter(v => !name.techniques?.includes(v)));
      this.chosenEquipment.update(selected => selected.filter(v => !name.equipment?.includes(v)));
      return;
    }
    // A chain's component technique (1:POWER) has no chip but names the variant
    this.chosenTechniques.set([...(name.techniques ?? [])]);
    this.chosenEquipment.set([...(name.equipment ?? [])]);
  }

  goBack(): void {
    this.location.back();
  }

  onDelete(movement: MovementResponse): void {
    const confirmMsg = this.translate.instant('GLOBAL.CONFIRM_DELETE', {name: movement.name});

    if (confirm(confirmMsg)) {
      this.movementService.deleteMovement(movement.id).subscribe({
        next: () => {
          this.notificationService.showSuccess(this.translate.instant('GLOBAL.DELETE_SUCCESS'));
          this.movementService.notifyRefresh();
          void this.router.navigate(['/movements']);
        },
        error: (err: HttpErrorResponse) => {
          if (err.status === 409) {
            this.notificationService.showError(this.translate.instant('MOVEMENT.MESSAGES.DELETE_CONFLICT', {name: movement.name}));
          } else {
            this.notificationService.showError(this.translate.instant('GLOBAL.DELETE_ERROR'));
          }
        }
      });
    }
  }

  // --- HELPERS ---
  /** Whether a shortcut is the name of the variant shown. */
  isUsualNameOn(usual: {nameEn: string}): boolean {
    return this.variant()?.usualNameEn === usual.nameEn;
  }

  private fromQuery(value: string | undefined, allowed: string[]): string[] {
    return (value ?? '').split(',').filter(v => allowed.includes(v) || v.includes(':'));
  }

  musclesOf(role: VariantMuscle['role']): VariantMuscle[] {
    return (this.variant()?.muscles ?? []).filter(m => m.role === role);
  }

  variantMuscleName(muscle: VariantMuscle): string {
    return (this.activeLang() === 'fr' ? muscle.commonNameFr : muscle.commonNameEn) || muscle.medicalName;
  }

  getLocalizedDescription(movement: MovementResponse): string {
    const variant = this.variant();
    return this.activeLang() === 'fr'
      ? (variant?.descriptionFr || movement.descriptionFr || '')
      : (variant?.descriptionEn || movement.descriptionEn || '');
  }

  getLocalizedCues(movement: MovementResponse): string {
    return this.activeLang() === 'fr' ? (movement.coachingCuesFr || '') : (movement.coachingCuesEn || '');
  }
}
