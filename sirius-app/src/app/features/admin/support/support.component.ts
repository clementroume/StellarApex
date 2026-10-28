import {ChangeDetectionStrategy, Component, computed, DestroyRef, inject, OnInit, signal} from '@angular/core';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {FormBuilder, ReactiveFormsModule, Validators} from '@angular/forms';
import {HttpErrorResponse} from '@angular/common/http';
import {NgTemplateOutlet} from '@angular/common';
import {TranslatePipe, TranslateService} from '@ngx-translate/core';
import {NgIcon} from '@ng-icons/core';
import {BehaviorSubject, debounceTime, distinctUntilChanged, skip} from 'rxjs';
import {AdminService, UserSearchFilters} from '../../../api/antares/services/admin.service';
import {UserResponse} from '../../../api/antares/models/user.model';
import {GymRole, MembershipStatus} from '../../../api/antares/models/membership.model';
import {AdminGymResponse} from '../../../api/antares/models/gym.model';
import {SupportSessionService} from '../../../core/services/support-session.service';
import {NotificationService} from '../../../core/services/notification.service';
import {ProblemDetail} from '../../../core/models/problem-detail.model';
import {SearchInputComponent} from '../../../shared/component/search-input/search-input.component';

export const USER_SEARCH_DEBOUNCE_MS = 300;
export const USER_SEARCH_MIN_LENGTH = 2;
export const USER_PAGE_SIZE = 20;
/** Gyms offered in the gym filter, every status, by name (Antares caps a page at 50). */
const GYM_FILTER_SIZE = 50;

export const SUPPORT_ROLES: GymRole[] = ['OWNER', 'PROGRAMMER', 'COACH', 'ATHLETE'];
export const SUPPORT_STATUSES: MembershipStatus[] = ['ACTIVE', 'PENDING', 'INACTIVE', 'BANNED'];

import {HlmSpinner} from '@spartan-ng/helm/spinner';
import {NativeSelectDirective} from '../../../shared/component/native-select/native-select.directive';
import {HlmAlertImports} from '@spartan-ng/helm/alert';
import {HlmBadge} from '@spartan-ng/helm/badge';
import {HlmButton} from '@spartan-ng/helm/button';
import {HlmCardImports} from '@spartan-ng/helm/card';
import {HlmTextarea} from '@spartan-ng/helm/textarea';
@Component({
  selector: 'app-support',
  standalone: true,
  imports: [HlmTextarea, HlmCardImports, HlmButton, HlmBadge, HlmAlertImports, NativeSelectDirective, HlmSpinner, ReactiveFormsModule, NgTemplateOutlet, TranslatePipe, NgIcon, SearchInputComponent],
  templateUrl: './support.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {'(document:keydown.escape)': 'clearSelection()'}
})
export class SupportComponent implements OnInit {
  // --- INJECTIONS ---
  private readonly fb = inject(FormBuilder);
  private readonly adminService = inject(AdminService);
  private readonly supportSessionService = inject(SupportSessionService);
  private readonly notificationService = inject(NotificationService);
  private readonly translate = inject(TranslateService);
  private readonly destroyRef = inject(DestroyRef);

  // --- STATE (SIGNALS & VARIABLES) ---
  users = signal<UserResponse[]>([]);
  recentUsers = signal<UserResponse[]>([]);
  gyms = signal<AdminGymResponse[]>([]);
  hasMore = signal<boolean>(false);
  isSearching = signal<boolean>(false);
  isLoadingMore = signal<boolean>(false);
  isStarting = signal<boolean>(false);
  selectedUser = signal<UserResponse | null>(null);

  searchQuery = signal<string>('');
  gymFilter = signal<number | null>(null);
  roleFilter = signal<GymRole | null>(null);
  statusFilter = signal<MembershipStatus | null>(null);

  readonly roles = SUPPORT_ROLES;
  readonly statuses = SUPPORT_STATUSES;
  private currentPage = 0;
  // Starts with the initial (empty) search so an ignored 1-character input does not reload it
  private readonly searchTrigger$ = new BehaviorSubject<string>('');

  reasonForm = this.fb.nonNullable.group({
    reason: ['', [Validators.required, Validators.maxLength(255)]]
  });

  // --- COMPUTED STATE ---
  /** A 1-character search is ignored: it would match almost everyone. */
  isSearchTooShort = computed(() => {
    const length = this.searchQuery().trim().length;
    return length > 0 && length < USER_SEARCH_MIN_LENGTH;
  });

  hasFilters = computed(() =>
    this.searchQuery().trim().length >= USER_SEARCH_MIN_LENGTH
    || this.gymFilter() !== null || this.roleFilter() !== null || this.statusFilter() !== null
  );

  // --- LIFECYCLE ---
  ngOnInit(): void {
    this.searchTrigger$
      .pipe(debounceTime(USER_SEARCH_DEBOUNCE_MS), distinctUntilChanged(), skip(1), takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.loadUsers());

    this.adminService.searchGyms('', null, 0, GYM_FILTER_SIZE).subscribe(slice => this.gyms.set(slice.content));
    this.adminService.getRecentSupportTargets().subscribe(users => this.recentUsers.set(users));
    this.loadUsers();
  }

  // --- DATA FETCHING ---
  loadUsers(): void {
    this.currentPage = 0;
    this.isSearching.set(true);
    this.adminService.searchUsers(this.filters(), 0, USER_PAGE_SIZE).subscribe({
      next: slice => {
        this.users.set(slice.content);
        this.hasMore.set(!slice.last);
        this.isSearching.set(false);
      },
      error: () => {
        this.users.set([]);
        this.hasMore.set(false);
        this.isSearching.set(false);
      }
    });
  }

  loadMore(): void {
    if (!this.hasMore() || this.isLoadingMore()) return;
    const nextPage = this.currentPage + 1;
    this.isLoadingMore.set(true);
    this.adminService.searchUsers(this.filters(), nextPage, USER_PAGE_SIZE).subscribe({
      next: slice => {
        this.currentPage = nextPage;
        this.users.update(current => [...current, ...slice.content]);
        this.hasMore.set(!slice.last);
        this.isLoadingMore.set(false);
      },
      error: () => this.isLoadingMore.set(false)
    });
  }

  private filters(): UserSearchFilters {
    const search = this.searchQuery().trim();
    return {
      search: search.length >= USER_SEARCH_MIN_LENGTH ? search : undefined,
      gymId: this.gymFilter() ?? undefined,
      role: this.roleFilter() ?? undefined,
      status: this.statusFilter() ?? undefined
    };
  }

  // --- ACTIONS & EVENTS ---
  onSearchChange(query: string): void {
    this.searchQuery.set(query);
    const search = query.trim();
    this.searchTrigger$.next(search.length >= USER_SEARCH_MIN_LENGTH ? search : '');
  }

  onGymChange(value: string): void {
    this.gymFilter.set(value ? Number(value) : null);
    this.loadUsers();
  }

  onRoleChange(role: GymRole | null): void {
    if (this.roleFilter() === role) return;
    this.roleFilter.set(role);
    this.loadUsers();
  }

  onStatusChange(status: MembershipStatus | null): void {
    if (this.statusFilter() === status) return;
    this.statusFilter.set(status);
    this.loadUsers();
  }

  /** Clicking the selected user again closes the start panel. */
  selectUser(user: UserResponse): void {
    if (user.platformRole === 'ADMIN') return;
    if (this.selectedUser()?.id === user.id) {
      this.clearSelection();
      return;
    }
    this.selectedUser.set(user);
  }

  clearSelection(): void {
    this.selectedUser.set(null);
    this.reasonForm.reset();
  }

  startSession(): void {
    const user = this.selectedUser();
    if (!user || this.reasonForm.invalid) {
      this.reasonForm.markAllAsTouched();
      return;
    }

    this.isStarting.set(true);
    this.supportSessionService.start(user.id, this.reasonForm.getRawValue().reason.trim()).subscribe({
      next: () => {
        this.isStarting.set(false);
        this.notificationService.showSuccess(
          this.translate.instant('ADMIN.SUPPORT.STARTED', {name: `${user.firstName} ${user.lastName}`})
        );
      },
      error: (err: HttpErrorResponse) => {
        this.isStarting.set(false);
        const problem: ProblemDetail = err.error;
        this.notificationService.showError(problem?.detail || this.translate.instant('GLOBAL.ERROR_UNEXPECTED'));
      }
    });
  }
}
