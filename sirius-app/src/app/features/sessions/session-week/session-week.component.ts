import {ChangeDetectionStrategy, Component, computed, effect, inject, signal} from '@angular/core';
import {HttpContext} from '@angular/common/http';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {RouterLink} from '@angular/router';
import {TranslatePipe, TranslateService} from '@ngx-translate/core';
import {NgIcon} from '@ng-icons/core';
import {HlmButton} from '@spartan-ng/helm/button';
import {HlmSpinner} from '@spartan-ng/helm/spinner';
import {SessionService} from '../../../api/aldebaran/services/session.service';
import {SessionSummaryResponse} from '../../../api/aldebaran/models/session.model';
import {AccessService} from '../../../core/services/access.service';
import {DisplayGymService} from '../../../core/services/display-gym.service';
import {BYPASS_LOADER} from '../../../core/interceptors/loading.interceptor';
import {PageComponent} from '../../../shared/component/page/page.component';
import {SessionCardComponent} from '../session-card/session-card.component';
import {addDays, dayLabel, localDate, mondayOf, shortDay, sortSessions} from '../session.utils';

/**
 * My box › Sessions: the week of the displayed box/prog (D7), one column per day with its WOD
 * sessions, the specialties of the week apart. Members see each session's frame (themes); its content
 * opens per the session's visibility policy. Staff with WOD_WRITE create sessions from here.
 */
@Component({
  selector: 'app-session-week',
  standalone: true,
  imports: [RouterLink, TranslatePipe, NgIcon, HlmButton, HlmSpinner, PageComponent, SessionCardComponent],
  templateUrl: './session-week.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class SessionWeekComponent {
  // --- INJECTIONS ---
  private readonly sessionService = inject(SessionService);
  private readonly accessService = inject(AccessService);
  private readonly translate = inject(TranslateService);
  protected readonly displayGym = inject(DisplayGymService);

  // --- STATE (SIGNALS & VARIABLES) ---
  protected readonly today = localDate();
  protected readonly monday = signal(mondayOf(this.today));
  private readonly sessions = signal<SessionSummaryResponse[]>([]);
  protected readonly isLoading = signal(false);
  protected readonly hasError = signal(false);

  // --- COMPUTED STATE ---
  private readonly lang = computed(() => this.translate.currentLang() ?? this.translate.fallbackLang() ?? 'en');
  /** Sessions of the displayed box/prog (all gyms when none is chosen); personal ones live in My training. */
  private readonly shown = computed(() =>
    sortSessions(this.sessions().filter(s => s.gymId != null && this.displayGym.matches(s.gymId))));
  protected readonly specialties = computed(() => this.shown().filter(s => s.type === 'SPECIALTY'));
  protected readonly days = computed(() => {
    const wods = this.shown().filter(s => s.type === 'WOD');
    return Array.from({length: 7}, (_, i) => {
      const day = addDays(this.monday(), i);
      return {day, label: dayLabel(day, this.lang()), isToday: day === this.today, sessions: wods.filter(s => s.date === day)};
    });
  });
  protected readonly weekLabel = computed(() => this.translate.instant('SESSION.WEEK_OF', {
    from: shortDay(this.monday(), this.lang()), to: shortDay(addDays(this.monday(), 6), this.lang())
  }));
  protected readonly isCurrentWeek = computed(() => this.monday() === mondayOf(this.today));
  /** Staff programming sessions (WOD_WRITE) get the create button; Aldebaran checks the gym again. */
  protected readonly canCreate = computed(() => this.accessService.gymsWithPermission('WOD_WRITE').length > 0);

  // --- LIFECYCLE ---
  constructor() {
    effect(() => this.load(this.monday()));
    this.sessionService.refreshNeeded$.pipe(takeUntilDestroyed()).subscribe(() => this.load(this.monday()));
  }

  // --- DATA FETCHING ---
  private load(monday: string): void {
    this.isLoading.set(true);
    this.hasError.set(false);
    this.sessionService.getSessions({scope: 'GYM', from: monday, to: addDays(monday, 6)}, 0, 100,
      new HttpContext().set(BYPASS_LOADER, true)).subscribe({
      next: slice => {
        this.sessions.set(slice.content);
        this.isLoading.set(false);
      },
      error: () => {
        this.sessions.set([]);
        this.hasError.set(true);
        this.isLoading.set(false);
      }
    });
  }

  // --- ACTIONS & EVENTS ---
  protected shiftWeek(weeks: number): void {
    this.monday.update(m => addDays(m, weeks * 7));
  }

  protected thisWeek(): void {
    this.monday.set(mondayOf(this.today));
  }
}
