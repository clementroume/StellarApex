import {ChangeDetectionStrategy, Component, DestroyRef, inject, OnInit, signal} from '@angular/core';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {debounceTime, distinctUntilChanged, Subject} from 'rxjs';
import {TranslatePipe, TranslateService} from '@ngx-translate/core';
import {NgIcon} from '@ng-icons/core';
import {HlmBadge} from '@spartan-ng/helm/badge';
import {HlmButton} from '@spartan-ng/helm/button';
import {HlmSpinner} from '@spartan-ng/helm/spinner';
import {AdminService} from '../../../api/antares/services/admin.service';
import {GymService} from '../../../api/antares/services/gym.service';
import {AdminGymResponse, GymStatus} from '../../../api/antares/models/gym.model';
import {NotificationService} from '../../../core/services/notification.service';
import {problemDetailOf} from '../../../core/models/problem-detail.model';
import {PageComponent} from '../../../shared/component/page/page.component';
import {SearchInputComponent} from '../../../shared/component/search-input/search-input.component';
import {ConfirmDialogComponent} from '../../../shared/component/confirm-dialog/confirm-dialog.component';

export const ADMIN_GYM_SEARCH_DEBOUNCE_MS = 300;
export const ADMIN_GYM_PAGE_SIZE = 20;
export const ADMIN_GYM_STATUSES: GymStatus[] = ['PENDING_APPROVAL', 'ACTIVE', 'SUSPENDED', 'REJECTED'];

/** Status change waiting for confirmation (rejecting or suspending). */
type GymAction = {gym: AdminGymResponse; status: 'REJECTED' | 'SUSPENDED'};

/**
 * Admin › Boxes & programs: approval queue (PENDING_APPROVAL first), search, approve, reject,
 * suspend and reactivate (Antares `PUT /gyms/{id}/status`, platform admin only).
 */
@Component({
  selector: 'app-admin-gyms',
  standalone: true,
  imports: [TranslatePipe, NgIcon, HlmBadge, HlmButton, HlmSpinner, PageComponent, SearchInputComponent, ConfirmDialogComponent],
  templateUrl: './admin-gyms.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AdminGymsComponent implements OnInit {
  // --- INJECTIONS ---
  private readonly adminService = inject(AdminService);
  private readonly gymService = inject(GymService);
  private readonly notificationService = inject(NotificationService);
  private readonly translate = inject(TranslateService);
  private readonly destroyRef = inject(DestroyRef);

  // --- STATE (SIGNALS & VARIABLES) ---
  readonly gyms = signal<AdminGymResponse[]>([]);
  readonly hasMore = signal<boolean>(false);
  readonly isLoading = signal<boolean>(false);
  readonly busyId = signal<number | null>(null);
  readonly searchQuery = signal<string>('');
  readonly statusFilter = signal<GymStatus | null>('PENDING_APPROVAL');
  readonly pendingAction = signal<GymAction | null>(null);
  readonly statuses = ADMIN_GYM_STATUSES;

  private currentPage = 0;
  private readonly searchTrigger$ = new Subject<string>();

  // --- LIFECYCLE ---
  ngOnInit(): void {
    this.searchTrigger$
      .pipe(debounceTime(ADMIN_GYM_SEARCH_DEBOUNCE_MS), distinctUntilChanged(), takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.load());
    this.load();
  }

  // --- DATA FETCHING ---
  load(): void {
    this.currentPage = 0;
    this.isLoading.set(true);
    this.adminService.searchGyms(this.searchQuery().trim(), this.statusFilter(), 0, ADMIN_GYM_PAGE_SIZE).subscribe({
      next: slice => {
        this.gyms.set(slice.content);
        this.hasMore.set(!slice.last);
        this.isLoading.set(false);
      },
      error: () => {
        this.gyms.set([]);
        this.hasMore.set(false);
        this.isLoading.set(false);
      }
    });
  }

  loadMore(): void {
    if (!this.hasMore() || this.isLoading()) return;
    const next = this.currentPage + 1;
    this.isLoading.set(true);
    this.adminService.searchGyms(this.searchQuery().trim(), this.statusFilter(), next, ADMIN_GYM_PAGE_SIZE).subscribe({
      next: slice => {
        this.currentPage = next;
        this.gyms.update(current => [...current, ...slice.content]);
        this.hasMore.set(!slice.last);
        this.isLoading.set(false);
      },
      error: () => this.isLoading.set(false)
    });
  }

  // --- ACTIONS & EVENTS ---
  onSearchChange(query: string): void {
    this.searchQuery.set(query);
    this.searchTrigger$.next(query.trim());
  }

  onStatusFilter(status: GymStatus | null): void {
    if (this.statusFilter() === status) return;
    this.statusFilter.set(status);
    this.load();
  }

  /** Approve or reactivate at once; rejecting and suspending ask first. */
  setStatus(gym: AdminGymResponse, status: GymStatus): void {
    if (status === 'REJECTED' || status === 'SUSPENDED') {
      this.pendingAction.set({gym, status});
      return;
    }
    this.apply(gym, status);
  }

  confirmAction(): void {
    const action = this.pendingAction();
    this.pendingAction.set(null);
    if (action) this.apply(action.gym, action.status);
  }

  cancelAction(): void {
    this.pendingAction.set(null);
  }

  // --- HELPERS ---
  formatDate(value: string): string {
    return new Date(value).toLocaleDateString(this.translate.currentLang() ?? undefined);
  }

  private apply(gym: AdminGymResponse, status: GymStatus): void {
    this.busyId.set(gym.id);
    this.gymService.updateStatus(gym.id, status).subscribe({
      next: () => {
        this.busyId.set(null);
        this.notificationService.showSuccess(this.translate.instant('ADMIN.GYMS.STATUS_CHANGED', {
          name: gym.name, status: this.translate.instant('GYM.GYM_STATUS.' + status)
        }));
        // Keep the row while it still matches the filter, drop it otherwise (e.g. approved from the queue)
        const filter = this.statusFilter();
        this.gyms.update(list => filter === null || filter === status
          ? list.map(g => (g.id === gym.id ? {...g, status} : g))
          : list.filter(g => g.id !== gym.id));
      },
      error: err => {
        this.busyId.set(null);
        this.notificationService.showError(problemDetailOf(err) ?? this.translate.instant('GLOBAL.ERROR_UNEXPECTED'));
      }
    });
  }
}
