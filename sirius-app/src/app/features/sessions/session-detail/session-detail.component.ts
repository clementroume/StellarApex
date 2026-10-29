import {ChangeDetectionStrategy, Component, computed, effect, inject, input, signal, untracked} from '@angular/core';
import {HttpErrorResponse} from '@angular/common/http';
import {Location} from '@angular/common';
import {Router, RouterLink} from '@angular/router';
import {FormsModule} from '@angular/forms';
import {TranslatePipe, TranslateService} from '@ngx-translate/core';
import {NgIcon} from '@ng-icons/core';
import {HlmBadge} from '@spartan-ng/helm/badge';
import {HlmButton} from '@spartan-ng/helm/button';
import {HlmInput} from '@spartan-ng/helm/input';
import {HlmSpinner} from '@spartan-ng/helm/spinner';
import {SessionService} from '../../../api/aldebaran/services/session.service';
import {
  SessionLogResponse,
  SessionResponse,
  TIMED_POLICIES,
  VisibilityPolicy
} from '../../../api/aldebaran/models/session.model';
import {AccessService} from '../../../core/services/access.service';
import {NotificationService} from '../../../core/services/notification.service';
import {problemDetailOf} from '../../../core/models/problem-detail.model';
import {PageComponent} from '../../../shared/component/page/page.component';
import {ConfirmDialogComponent} from '../../../shared/component/confirm-dialog/confirm-dialog.component';
import {NativeSelectDirective} from '../../../shared/component/native-select/native-select.directive';
import {localizedText} from '../../wods/wod.utils';
import {SessionBlockComponent} from '../session-block/session-block.component';
import {dayLabel, localDate, sessionDateLabel, toLocalInput} from '../session.utils';

/**
 * A session: frame (day or week, discipline, block themes), then the content when the visibility
 * policy shows it to the user. "Séance faite" creates the user's journal (every block checked);
 * scores are logged per block. Staff edit, delete (until someone logged it) and change the
 * visibility (always). Every flag comes from `SessionResponse.capabilities`.
 */
@Component({
  selector: 'app-session-detail',
  standalone: true,
  imports: [RouterLink, FormsModule, TranslatePipe, NgIcon, HlmBadge, HlmButton, HlmInput, HlmSpinner, PageComponent,
    ConfirmDialogComponent, NativeSelectDirective, SessionBlockComponent],
  templateUrl: './session-detail.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class SessionDetailComponent {
  // --- INJECTIONS ---
  private readonly sessionService = inject(SessionService);
  private readonly accessService = inject(AccessService);
  private readonly notificationService = inject(NotificationService);
  private readonly translate = inject(TranslateService);
  private readonly router = inject(Router);
  private readonly location = inject(Location);

  // --- STATE (SIGNALS & VARIABLES) ---
  readonly id = input.required<string>();
  protected readonly session = signal<SessionResponse | null>(null);
  protected readonly isLoading = signal(true);
  protected readonly isLogging = signal(false);
  protected readonly confirmDelete = signal(false);
  protected readonly confirmUnlog = signal(false);
  /** Visibility dialog draft: policy and local opening instant, null while closed. */
  protected readonly visibilityDraft = signal<{policy: VisibilityPolicy; at: string} | null>(null);

  // --- COMPUTED STATE ---
  protected readonly lang = computed(() => this.translate.currentLang() ?? this.translate.fallbackLang() ?? 'en');
  protected readonly log = computed<SessionLogResponse | null>(() => this.session()?.myLog ?? null);
  protected readonly heading = computed(() => {
    const s = this.session();
    return s ? s.title || this.translate.instant('SESSION.TYPES.' + s.type) : '';
  });
  protected readonly dateLabel = computed(() => {
    const s = this.session();
    return s ? sessionDateLabel(s, this.lang(), (k, p) => this.translate.instant(k, p)) : '';
  });
  protected readonly loggedOn = computed(() => {
    const log = this.log();
    return log ? dayLabel(log.date, this.lang()) : '';
  });
  protected readonly description = computed(() =>
    localizedText(this.lang(), this.session()?.descriptionEn, this.session()?.descriptionFr));
  protected readonly gymName = computed(() =>
    this.accessService.memberships().find(m => m.gymId === this.session()?.gymId)?.gymName ?? null);
  /** A program only shows its sessions in advance or on the day (Aldebaran refuses the others). */
  protected readonly policies = computed<VisibilityPolicy[]>(() => {
    const gym = this.accessService.memberships().find(m => m.gymId === this.session()?.gymId);
    return gym?.gymProgramming ? TIMED_POLICIES
      : ['ADVANCE', 'DAY_OF', 'AFTER', 'AFTER_PARTICIPANTS', 'PARTICIPANTS_ONLY'];
  });
  /** "Visible le 4 nov. à 00:00", or why the content is still hidden. */
  protected readonly opensAt = computed(() => {
    const from = this.session()?.contentVisibleFrom;
    return from ? new Date(from).toLocaleString(this.lang(), {
      weekday: 'long', day: 'numeric', month: 'long', hour: '2-digit', minute: '2-digit'
    }) : null;
  });
  protected readonly editLink = computed(() => {
    const s = this.session();
    return s ? [s.gymId == null ? '/training/sessions' : '/box/sessions', String(s.id), 'edit'] : [];
  });

  // --- LIFECYCLE ---
  constructor() {
    effect(() => {
      const id = Number(this.id());
      untracked(() => this.load(id));
    });
  }

  // --- DATA FETCHING ---
  private load(id: number, quiet = false): void {
    if (!quiet) this.isLoading.set(true);
    this.sessionService.getSession(id).subscribe({
      next: s => {
        this.session.set(s);
        this.isLoading.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.isLoading.set(false);
        this.notificationService.showError(problemDetailOf(err) ?? this.translate.instant(
          err.status === 403 ? 'SESSION.MESSAGES.FORBIDDEN' : 'SESSION.MESSAGES.NOT_FOUND'));
        this.location.back();
      }
    });
  }

  protected reload(): void {
    const s = this.session();
    if (s) this.load(s.id, true);
  }

  // --- ACTIONS & EVENTS ---
  /** "Séance faite": today when the session covers it, else its last day (a past session). */
  protected logSession(): void {
    const s = this.session();
    if (!s || this.isLogging()) return;
    const today = localDate();
    const date = today > s.endDate ? s.endDate : today;
    this.isLogging.set(true);
    this.sessionService.logSession(s.id, date).subscribe({
      next: () => {
        this.isLogging.set(false);
        this.notificationService.showSuccess(this.translate.instant('SESSION.MESSAGES.LOGGED'));
        this.sessionService.notifyRefresh();
        this.reload();
      },
      error: (err: HttpErrorResponse) => {
        this.isLogging.set(false);
        this.notificationService.showError(problemDetailOf(err) ?? this.translate.instant('SESSION.MESSAGES.SAVE_ERROR'));
      }
    });
  }

  protected setBlockDone(blockId: number, done: boolean): void {
    const log = this.log();
    if (!log) return;
    this.sessionService.setBlockDone(log.id, blockId, done).subscribe({
      next: updated => this.session.update(s => s && {...s, myLog: updated}),
      error: (err: HttpErrorResponse) =>
        this.notificationService.showError(problemDetailOf(err) ?? this.translate.instant('SESSION.MESSAGES.SAVE_ERROR'))
    });
  }

  protected unlog(): void {
    const log = this.log();
    this.confirmUnlog.set(false);
    if (!log) return;
    this.sessionService.deleteLog(log.id).subscribe({
      next: () => {
        this.notificationService.showSuccess(this.translate.instant('SESSION.MESSAGES.UNLOGGED'));
        this.sessionService.notifyRefresh();
        this.reload();
      },
      error: (err: HttpErrorResponse) =>
        this.notificationService.showError(problemDetailOf(err) ?? this.translate.instant('SESSION.MESSAGES.SAVE_ERROR'))
    });
  }

  protected deleteSession(): void {
    const s = this.session();
    this.confirmDelete.set(false);
    if (!s) return;
    this.sessionService.deleteSession(s.id).subscribe({
      next: () => {
        this.notificationService.showSuccess(this.translate.instant('GLOBAL.DELETE_SUCCESS'));
        this.sessionService.notifyRefresh();
        void this.router.navigate([s.gymId == null ? '/training/journal' : '/box/sessions']);
      },
      error: (err: HttpErrorResponse) =>
        this.notificationService.showError(problemDetailOf(err) ?? this.translate.instant('GLOBAL.DELETE_ERROR'))
    });
  }

  protected openVisibility(): void {
    const s = this.session();
    if (!s?.visibilityPolicy) return;
    this.visibilityDraft.set({policy: s.visibilityPolicy, at: toLocalInput(s.contentVisibleFrom) ?? `${s.date}T00:00`});
  }

  protected setDraftPolicy(policy: VisibilityPolicy): void {
    this.visibilityDraft.update(d => d && {...d, policy});
  }

  protected setDraftAt(at: string): void {
    this.visibilityDraft.update(d => d && {...d, at});
  }

  protected saveVisibility(): void {
    const s = this.session();
    const draft = this.visibilityDraft();
    this.visibilityDraft.set(null);
    if (!s || !draft) return;
    this.sessionService.updateVisibility(s.id, {
      visibilityPolicy: draft.policy,
      contentVisibleAt: TIMED_POLICIES.includes(draft.policy) ? draft.at : null
    }).subscribe({
      next: updated => {
        this.session.set(updated);
        this.notificationService.showSuccess(this.translate.instant('SESSION.MESSAGES.VISIBILITY_SAVED'));
        this.sessionService.notifyRefresh();
      },
      error: (err: HttpErrorResponse) =>
        this.notificationService.showError(problemDetailOf(err) ?? this.translate.instant('SESSION.MESSAGES.SAVE_ERROR'))
    });
  }

  // --- HELPERS ---
  protected isTimed(policy: VisibilityPolicy): boolean {
    return TIMED_POLICIES.includes(policy);
  }
}
