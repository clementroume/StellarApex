import {ChangeDetectionStrategy, Component, computed, inject, signal} from '@angular/core';
import {HttpContext} from '@angular/common/http';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {RouterLink} from '@angular/router';
import {TranslatePipe, TranslateService} from '@ngx-translate/core';
import {NgIcon} from '@ng-icons/core';
import {HlmBadge} from '@spartan-ng/helm/badge';
import {HlmButton} from '@spartan-ng/helm/button';
import {HlmCardImports} from '@spartan-ng/helm/card';
import {SessionService} from '../../../api/aldebaran/services/session.service';
import {SessionLogResponse, SessionSummaryResponse} from '../../../api/aldebaran/models/session.model';
import {BYPASS_LOADER} from '../../../core/interceptors/loading.interceptor';
import {DisplayGymService} from '../../../core/services/display-gym.service';
import {SessionCardComponent} from '../session-card/session-card.component';
import {addDays, dayLabel, localDate, sessionLink} from '../session.utils';

/** Personal sessions planned from two weeks ago on. */
const PERSONAL_PAST_DAYS = 14;

/**
 * Sessions part of My training › Journal: the sessions the user did (journal: day, blocks done,
 * scores), newest first, and their personal sessions not done yet (D15) with the create button.
 */
@Component({
  selector: 'app-session-journal',
  standalone: true,
  imports: [RouterLink, TranslatePipe, NgIcon, HlmBadge, HlmButton, HlmCardImports, SessionCardComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section hlmCard class="mb-6">
      <div hlmCardHeader class="flex flex-row flex-wrap items-center justify-between gap-2">
        <h2 hlmCardTitle class="flex items-center gap-2">
          <ng-icon name="heroCalendarDays" class="text-primary" aria-hidden="true"/>{{ 'SESSION.JOURNAL.TITLE' | translate }}
        </h2>
        <a hlmBtn size="sm" id="new-personal-session" routerLink="/training/sessions/new">
          <ng-icon name="heroPlus" aria-hidden="true"/>{{ 'SESSION.ACTIONS.NEW_PERSONAL' | translate }}
        </a>
      </div>
      <div hlmCardContent class="flex flex-col gap-4">
        @if (personal().length > 0) {
          <div class="grid gap-3 md:grid-cols-2">
            @for (session of personal(); track session.id) {
              <app-session-card [session]="session"/>
            }
          </div>
        }
        <ul class="divide-y">
          @for (log of logs(); track log.id) {
            <li>
              <a [routerLink]="link(log)" class="flex items-center justify-between gap-3 py-3 px-2 -mx-2 rounded-md hover:bg-muted/50">
                <span class="min-w-0">
                  <span class="block font-medium truncate">{{ log.sessionTitle || ('SESSION.TYPES.' + log.sessionType | translate) }}</span>
                  <span class="block text-xs text-muted-foreground first-letter:uppercase">
                    {{ day(log.date) }} · {{ 'SESSION.DISCIPLINES.' + log.discipline | translate }}
                  </span>
                </span>
                <span class="flex items-center gap-2 shrink-0">
                  <span hlmBadge variant="secondary">{{ 'SESSION.JOURNAL.BLOCKS' | translate: {done: doneCount(log), total: log.blocks.length} }}</span>
                  @if (log.scores.length > 0) {
                    <span hlmBadge class="bg-primary/15 text-primary">{{ 'SESSION.JOURNAL.SCORES' | translate: {count: log.scores.length} }}</span>
                  }
                </span>
              </a>
            </li>
          } @empty {
            <li class="py-6 text-center text-sm text-muted-foreground">{{ 'SESSION.JOURNAL.EMPTY' | translate }}</li>
          }
        </ul>
      </div>
    </section>
  `
})
export class SessionJournalComponent {
  // --- INJECTIONS ---
  private readonly sessionService = inject(SessionService);
  private readonly displayGym = inject(DisplayGymService);
  private readonly translate = inject(TranslateService);

  // --- STATE (SIGNALS & VARIABLES) ---
  private readonly allLogs = signal<SessionLogResponse[]>([]);
  private readonly mine = signal<SessionSummaryResponse[]>([]);

  // --- COMPUTED STATE ---
  private readonly lang = computed(() => this.translate.currentLang() ?? this.translate.fallbackLang() ?? 'en');
  protected readonly logs = computed(() =>
    this.allLogs().filter(l => l.gymId == null || this.displayGym.matches(l.gymId)));
  /** Personal sessions not done yet. */
  protected readonly personal = computed(() => this.mine().filter(s => !s.loggedByMe));

  // --- LIFECYCLE ---
  constructor() {
    this.load();
    this.sessionService.refreshNeeded$.pipe(takeUntilDestroyed()).subscribe(() => this.load());
  }

  // --- DATA FETCHING ---
  private load(): void {
    const context = new HttpContext().set(BYPASS_LOADER, true);
    this.sessionService.getMyLogs(null, null, 0, 20, context).subscribe({
      next: slice => this.allLogs.set(slice.content),
      error: () => this.allLogs.set([])
    });
    this.sessionService.getSessions({scope: 'MINE', from: addDays(localDate(), -PERSONAL_PAST_DAYS)}, 0, 20, context).subscribe({
      next: slice => this.mine.set(slice.content),
      error: () => this.mine.set([])
    });
  }

  // --- HELPERS ---
  /** Both session routes show the same page; a journal's gym is the session's (or the one chosen). */
  protected link(log: SessionLogResponse): string[] {
    return sessionLink({id: log.sessionId, gymId: log.gymId});
  }

  protected doneCount(log: SessionLogResponse): number {
    return log.blocks.filter(b => b.done).length;
  }

  protected day(date: string): string {
    return dayLabel(date, this.lang());
  }
}
