import {ChangeDetectionStrategy, Component, computed, inject, signal} from '@angular/core';
import {HttpContext} from '@angular/common/http';
import {RouterLink} from '@angular/router';
import {TranslatePipe, TranslateService} from '@ngx-translate/core';
import {NgIcon} from '@ng-icons/core';
import {HlmBadge} from '@spartan-ng/helm/badge';
import {HlmButton} from '@spartan-ng/helm/button';
import {HlmCardImports} from '@spartan-ng/helm/card';
import {HlmSkeleton} from '@spartan-ng/helm/skeleton';
import {AuthService} from '../../api/antares/services/auth.service';
import {ScoreService} from '../../api/aldebaran/services/score.service';
import {ScoreResponse} from '../../api/aldebaran/models/score.model';
import {BYPASS_LOADER} from '../../core/interceptors/loading.interceptor';
import {DisplayGymService} from '../../core/services/display-gym.service';
import {ScoreRowComponent} from '../wods/scores/score-row/score-row.component';
import {PageComponent} from '../../shared/component/page/page.component';
import {SessionService} from '../../api/aldebaran/services/session.service';
import {SessionSummaryResponse} from '../../api/aldebaran/models/session.model';
import {NotificationService} from '../../core/services/notification.service';
import {problemDetailOf} from '../../core/models/problem-detail.model';
import {SessionCardComponent} from '../sessions/session-card/session-card.component';
import {localDate, sessionLink, sortSessions} from '../sessions/session.utils';

/**
 * Home (D38): today's sessions of the displayed box/prog (WOD of the day, specialties of the week,
 * personal sessions) with "Séance faite", then the latest scores and records.
 */
@Component({
  selector: 'app-today',
  standalone: true,
  imports: [RouterLink, TranslatePipe, NgIcon, HlmBadge, HlmButton, HlmCardImports, HlmSkeleton, PageComponent,
    ScoreRowComponent, SessionCardComponent],
  templateUrl: './today.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TodayComponent {
  // --- INJECTIONS ---
  private readonly authService = inject(AuthService);
  private readonly scoreService = inject(ScoreService);
  private readonly sessionService = inject(SessionService);
  private readonly notificationService = inject(NotificationService);
  private readonly translate = inject(TranslateService);
  protected readonly displayGym = inject(DisplayGymService);

  // --- STATE (SIGNALS & VARIABLES) ---
  protected readonly isLoading = signal(true);
  protected readonly hasError = signal(false);
  private readonly scores = signal<ScoreResponse[]>([]);
  private readonly sessions = signal<SessionSummaryResponse[]>([]);
  protected readonly sessionsLoading = signal(true);
  protected readonly loggingId = signal<number | null>(null);
  protected readonly sessionLink = sessionLink;

  // --- COMPUTED STATE ---
  protected readonly firstName = computed(() => this.authService.currentUser()?.firstName ?? '');
  /** "Wednesday, October 28" / "mercredi 28 octobre", in the language shown. */
  protected readonly todayLabel = computed(() => {
    const lang = this.translate.currentLang() ?? this.translate.fallbackLang() ?? 'en';
    return new Date().toLocaleDateString(lang, {weekday: 'long', day: 'numeric', month: 'long'});
  });
  /** Scores of the displayed box/prog (all of them when none is chosen); personal scores have no gym. */
  private readonly visibleScores = computed(() =>
    this.scores().filter(s => s.gymId == null || this.displayGym.matches(s.gymId)));
  /** Sessions of today in the displayed box/prog, and personal ones. */
  protected readonly todaySessions = computed(() =>
    sortSessions(this.sessions().filter(s => s.gymId == null || this.displayGym.matches(s.gymId))));
  protected readonly latestScores = computed(() => this.visibleScores().slice(0, 5));
  protected readonly latestRecords = computed(() => this.visibleScores().filter(s => s.personalRecord).slice(0, 5));

  // --- LIFECYCLE ---
  constructor() {
    this.loadScores();
    this.loadSessions();
  }

  // --- DATA FETCHING ---
  private loadSessions(): void {
    const today = localDate();
    this.sessionService.getSessions({from: today, to: today}, 0, 20, new HttpContext().set(BYPASS_LOADER, true)).subscribe({
      next: slice => {
        this.sessions.set(slice.content);
        this.sessionsLoading.set(false);
      },
      error: () => {
        this.sessions.set([]);
        this.sessionsLoading.set(false);
      }
    });
  }

  // --- ACTIONS & EVENTS ---
  /** "Séance faite" from the home page; Aldebaran checks the day and the right to read the session. */
  protected logSession(session: SessionSummaryResponse): void {
    if (this.loggingId() !== null) return;
    this.loggingId.set(session.id);
    this.sessionService.logSession(session.id, localDate()).subscribe({
      next: () => {
        this.loggingId.set(null);
        this.notificationService.showSuccess(this.translate.instant('SESSION.MESSAGES.LOGGED'));
        this.loadSessions();
      },
      error: err => {
        this.loggingId.set(null);
        this.notificationService.showError(problemDetailOf(err) ?? this.translate.instant('SESSION.MESSAGES.SAVE_ERROR'));
      }
    });
  }

  private loadScores(): void {
    this.scoreService.getMyScores(undefined, 0, 20, new HttpContext().set(BYPASS_LOADER, true)).subscribe({
      next: slice => {
        this.scores.set(slice.content);
        this.isLoading.set(false);
      },
      error: () => {
        this.hasError.set(true);
        this.isLoading.set(false);
      }
    });
  }

}
