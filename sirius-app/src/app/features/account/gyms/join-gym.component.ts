import {ChangeDetectionStrategy, Component, computed, DestroyRef, inject, input, OnInit, signal} from '@angular/core';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {Router, RouterLink} from '@angular/router';
import {FormBuilder, ReactiveFormsModule, Validators} from '@angular/forms';
import {debounceTime, distinctUntilChanged, Subject} from 'rxjs';
import {TranslatePipe, TranslateService} from '@ngx-translate/core';
import {NgIcon} from '@ng-icons/core';
import {HlmAlertImports} from '@spartan-ng/helm/alert';
import {HlmBadge} from '@spartan-ng/helm/badge';
import {HlmButton} from '@spartan-ng/helm/button';
import {HlmCardImports} from '@spartan-ng/helm/card';
import {HlmInputGroupImports} from '@spartan-ng/helm/input-group';
import {HlmSpinner} from '@spartan-ng/helm/spinner';
import {AuthService} from '../../../api/antares/services/auth.service';
import {GymService} from '../../../api/antares/services/gym.service';
import {UserService} from '../../../api/antares/services/user.service';
import {GymPublicResponse, GymType} from '../../../api/antares/models/gym.model';
import {NotificationService} from '../../../core/services/notification.service';
import {problemDetailOf} from '../../../core/models/problem-detail.model';
import {SearchInputComponent} from '../../../shared/component/search-input/search-input.component';
import {FormFieldComponent} from '../../../shared/component/form-field/form-field.component';

export const GYM_SEARCH_DEBOUNCE_MS = 300;
export const GYM_PAGE_SIZE = 20;

/**
 * Join a box or program: search the ACTIVE ones by name and type, pick one, enter its enrollment
 * code. An invitation link (`/join?gym=12&code=ABC`, redirected here) pre-fills both.
 */
@Component({
  selector: 'app-join-gym',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink, TranslatePipe, NgIcon, HlmAlertImports, HlmBadge, HlmButton, HlmCardImports,
    HlmInputGroupImports, HlmSpinner, SearchInputComponent, FormFieldComponent],
  templateUrl: './join-gym.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class JoinGymComponent implements OnInit {
  // --- INJECTIONS ---
  private readonly gymService = inject(GymService);
  private readonly userService = inject(UserService);
  private readonly authService = inject(AuthService);
  private readonly notificationService = inject(NotificationService);
  private readonly translate = inject(TranslateService);
  private readonly router = inject(Router);
  private readonly fb = inject(FormBuilder);
  private readonly destroyRef = inject(DestroyRef);

  // --- STATE (SIGNALS & VARIABLES) ---
  /** Query parameters of an invitation link. */
  readonly gym = input<string>();
  readonly code = input<string>();

  readonly gyms = signal<GymPublicResponse[]>([]);
  readonly hasMore = signal<boolean>(false);
  readonly isSearching = signal<boolean>(false);
  readonly isJoining = signal<boolean>(false);
  readonly searchQuery = signal<string>('');
  readonly typeFilter = signal<GymType | null>(null);
  readonly selected = signal<GymPublicResponse | null>(null);
  readonly invitationNotFound = signal<boolean>(false);
  readonly fromInvitation = signal<boolean>(false);
  readonly types: GymType[] = ['BOX', 'PROGRAM'];

  readonly form = this.fb.nonNullable.group({
    enrollmentCode: ['', [Validators.required, Validators.maxLength(50)]]
  });

  private currentPage = 0;
  private readonly searchTrigger$ = new Subject<string>();

  // --- COMPUTED STATE ---
  /** Gyms the user already belongs to or asked to join (a hint only: Antares answers 409). */
  private readonly memberGymIds = computed(() => new Set(
    (this.authService.currentUser()?.memberships ?? [])
      .filter(m => m.status === 'ACTIVE' || m.status === 'PENDING')
      .map(m => m.gymId)
  ));

  // --- LIFECYCLE ---
  ngOnInit(): void {
    this.searchTrigger$
      .pipe(debounceTime(GYM_SEARCH_DEBOUNCE_MS), distinctUntilChanged(), takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.search());

    if (this.code()) {
      this.form.controls.enrollmentCode.setValue(this.code()!);
    }
    const invitedId = Number(this.gym());
    if (Number.isInteger(invitedId) && invitedId > 0) {
      this.gymService.getGym(invitedId).subscribe({
        next: gym => {
          this.fromInvitation.set(!!this.code());
          this.selected.set(gym);
        },
        error: () => this.invitationNotFound.set(true)
      });
    }
    this.search();
  }

  // --- DATA FETCHING ---
  search(): void {
    this.currentPage = 0;
    this.isSearching.set(true);
    this.gymService.searchGyms(this.searchQuery().trim(), this.typeFilter(), 0, GYM_PAGE_SIZE).subscribe({
      next: slice => {
        this.gyms.set(slice.content);
        this.hasMore.set(!slice.last);
        this.isSearching.set(false);
      },
      error: () => {
        this.gyms.set([]);
        this.hasMore.set(false);
        this.isSearching.set(false);
      }
    });
  }

  loadMore(): void {
    if (!this.hasMore() || this.isSearching()) return;
    const next = this.currentPage + 1;
    this.isSearching.set(true);
    this.gymService.searchGyms(this.searchQuery().trim(), this.typeFilter(), next, GYM_PAGE_SIZE).subscribe({
      next: slice => {
        this.currentPage = next;
        this.gyms.update(current => [...current, ...slice.content]);
        this.hasMore.set(!slice.last);
        this.isSearching.set(false);
      },
      error: () => this.isSearching.set(false)
    });
  }

  // --- ACTIONS & EVENTS ---
  onSearchChange(query: string): void {
    this.searchQuery.set(query);
    this.searchTrigger$.next(query.trim());
  }

  onTypeChange(type: GymType | null): void {
    if (this.typeFilter() === type) return;
    this.typeFilter.set(type);
    this.search();
  }

  select(gym: GymPublicResponse): void {
    this.selected.set(gym);
    this.invitationNotFound.set(false);
  }

  changeGym(): void {
    this.selected.set(null);
    this.fromInvitation.set(false);
  }

  join(): void {
    const gym = this.selected();
    if (!gym || this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.isJoining.set(true);
    this.gymService.joinGym({gymId: gym.id, enrollmentCode: this.form.getRawValue().enrollmentCode.trim()}).subscribe({
      next: membership => {
        this.isJoining.set(false);
        const key = membership.status === 'ACTIVE' ? 'JOIN_GYM.JOINED' : 'JOIN_GYM.REQUESTED';
        this.notificationService.showSuccess(this.translate.instant(key, {name: gym.name}));
        this.userService.refreshCurrentUser().subscribe();
        void this.router.navigate(['/my-account/gyms']);
      },
      error: err => {
        this.isJoining.set(false);
        this.notificationService.showError(problemDetailOf(err) ?? this.translate.instant('GLOBAL.ERROR_UNEXPECTED'));
      }
    });
  }

  // --- HELPERS ---
  isMember(gymId: number): boolean {
    return this.memberGymIds().has(gymId);
  }
}
