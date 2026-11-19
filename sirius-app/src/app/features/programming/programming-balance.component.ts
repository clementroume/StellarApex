import {ChangeDetectionStrategy, Component, computed, effect, inject, linkedSignal, signal} from '@angular/core';
import {HttpContext} from '@angular/common/http';
import {TranslatePipe, TranslateService} from '@ngx-translate/core';
import {NgIcon, provideIcons} from '@ng-icons/core';
import {heroChevronLeft, heroChevronRight} from '@ng-icons/heroicons/outline';
import {HlmButton} from '@spartan-ng/helm/button';
import {HlmDialogImports} from '@spartan-ng/helm/dialog';
import {HlmSpinner} from '@spartan-ng/helm/spinner';
import {
  DayOfWeek, PatternAxis, ProfileRow, ProgrammingAlert, ProgrammingBalance, ProgrammingProfile
} from '../../api/aldebaran/models/programming.model';
import {ProgrammingService} from '../../api/aldebaran/services/programming.service';
import {BYPASS_LOADER} from '../../core/interceptors/loading.interceptor';
import {NativeSelectDirective} from '../../shared/component/native-select/native-select.directive';
import {PageComponent} from '../../shared/component/page/page.component';
import {StaffGymPickerComponent, staffGymSelection} from '../box/staff-gym';
import {ReviewMetconsComponent} from '../review/review-metcons.component';
import {ReviewModalitiesComponent} from '../review/review-modalities.component';
import {addDays, localDate, mondayOf, shortDay} from '../sessions/session.utils';
import {PatternRadarComponent} from './pattern-radar.component';
import {dayShort, daysLabel, decimal, percent, sortDays} from './programming.utils';

/** One line of text for an alert: its title and its explanation. */
interface AlertText {
  kind: ProgrammingAlert['kind'];
  title: string;
  why: string;
}

/**
 * The balance of the programming (`/box/programming`, lot D', D161-D181), for the staff of any role:
 * the cycle chosen by the coach, the full week (radar of movement patterns, regions, unusual
 * sessions), the attendance profiles in alert by size, and one profile composed or picked. Rules and
 * thresholds live in Aldebaran; this page shows what it sends.
 */
@Component({
  viewProviders: [provideIcons({heroChevronLeft, heroChevronRight})],
  selector: 'app-programming-balance',
  standalone: true,
  imports: [
    TranslatePipe, NgIcon, HlmButton, HlmDialogImports, HlmSpinner, NativeSelectDirective, PageComponent,
    StaffGymPickerComponent, PatternRadarComponent, ReviewMetconsComponent, ReviewModalitiesComponent
  ],
  templateUrl: './programming-balance.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ProgrammingBalanceComponent {
  // --- INJECTIONS ---
  private readonly programmingService = inject(ProgrammingService);
  private readonly translate = inject(TranslateService);

  // --- STATE (SIGNALS & VARIABLES) ---
  readonly gym = staffGymSelection('STAFF');
  /** Monday of the first week (D163: the current week by default, decided here). */
  readonly start = signal(mondayOf(localDate()));
  readonly weeks = signal(4);
  readonly scaled = signal(false);
  readonly optional = signal(false);
  /** The profile detailed; empty = none. Reset when the staff picks another gym (other training days). */
  readonly days = linkedSignal<number | null, DayOfWeek[]>({source: () => this.gym.gymId(), computation: () => []});
  readonly balance = signal<ProgrammingBalance | null>(null);
  readonly isLoading = signal(true);
  readonly failed = signal(false);
  readonly fullAxis = signal<PatternAxis | null>(null);
  readonly profileAxis = signal<PatternAxis | null>(null);
  readonly weekOptions = [4, 5, 6, 7, 8] as const;
  readonly helpParts = ['RADAR', 'PROFILES', 'ALERTS', 'INFO'] as const;
  private request = 0;

  // --- COMPUTED STATE ---
  readonly lang = computed(() => this.translate.currentLang() ?? this.translate.fallbackLang() ?? 'en');
  readonly startText = computed(() => shortDay(this.start(), this.lang()));
  readonly cycleText = computed(() => {
    const b = this.balance();
    if (!b) return '';
    return this.translate.instant('PROGRAMMING.CYCLE', {
      from: shortDay(b.start, this.lang()), to: shortDay(b.end, this.lang()), sessions: b.sessions, windows: b.windows
    });
  });
  /** The profile of the last answer, only while it is the one asked (not the previous one during a reload). */
  readonly profile = computed(() => {
    const p = this.balance()?.profile ?? null;
    const days = this.days();
    return p && days.length === p.days.length && days.every(d => p.days.includes(d)) ? p : null;
  });
  readonly profileWindow = computed(() => {
    const p = this.profile();
    return p ? this.translate.instant('PROGRAMMING.WINDOW', {
      from: shortDay(p.windowStart, this.lang()), to: shortDay(p.windowEnd, this.lang())
    }) : '';
  });
  /** No official Scaled version in the cycle: Scaled would read as RX. */
  readonly noScaled = computed(() => this.balance()?.scaledLines === 0);
  readonly profileDays = computed(() => daysLabel(this.days(), this.lang()));
  /** Why some profile sizes have no profile: a size as large as the training days is the full week. */
  readonly sizesNote = computed(() => {
    const b = this.balance();
    if (!b) return '';
    const missing = b.summaries.filter(s => s.total === 0).map(s => s.size);
    return missing.length
      ? this.translate.instant('PROGRAMMING.PROFILES.SIZES_NOTE', {days: b.trainingDays.length, sizes: missing.join(', ')})
      : '';
  });
  /** Region bars of what the left shows: the profile against the full week, else the full week. */
  readonly regionRows = computed(() => {
    const b = this.balance();
    if (!b) return [];
    const p = this.profile();
    const rows = (p ?? b.fullWeek).regions;
    const max = Math.max(1, ...rows.flatMap(r => [r.share, r.fullShare]));
    return rows.map(r => ({
      region: r.region,
      trend: p ? r.trend : 'IN_LINE',
      text: percent(r.share, this.lang()),
      fullText: p ? percent(r.fullShare, this.lang()) : '',
      width: r.share / max * 100,
      fullWidth: r.fullShare / max * 100
    }));
  });
  readonly fullAlerts = computed(() => this.alertTexts(this.balance()?.fullWeek ?? null));
  readonly profileAlerts = computed(() => this.alertTexts(this.profile()));
  readonly profileAlerted = computed(() =>
    (this.profile()?.alerts ?? []).filter(a => a.kind === 'PATTERN_GAP').map(a => a.subject));
  readonly unusual = computed(() => (this.balance()?.unusualSessions ?? []).map(u => ({
    key: u.date + u.region,
    text: this.translate.instant('PROGRAMMING.UNUSUAL.LINE', {
      day: shortDay(u.date, this.lang()), region: this.translate.instant('MUSCLE.REGIONS.' + u.region),
      sets: decimal(u.sets, this.lang()), base: decimal(u.base, this.lang())
    })
  })));
  readonly concentrated = computed(() => (this.balance()?.concentratedAxes ?? [])
    .map(a => this.translate.instant('MOVEMENT.PATTERNS.AXES.' + a)).join(', '));
  readonly summaries = computed(() => (this.balance()?.summaries ?? []).filter(s => s.total > 0).map(s => ({
    ...s,
    rows: s.inAlert.map(row => ({row, days: daysLabel(row.days, this.lang()), main: this.shortAlert(row)}))
  })));

  // --- LIFECYCLE ---
  constructor() {
    effect(() => {
      const gymId = this.gym.gymId();
      const query = {start: this.start(), weeks: this.weeks(), scaled: this.scaled(), optional: this.optional(), days: this.days()};
      if (gymId !== null) this.load(gymId, query);
    });
  }

  // --- DATA FETCHING ---
  private load(gymId: number, query: Parameters<ProgrammingService['getBalance']>[1]): void {
    const request = ++this.request;
    this.isLoading.set(true);
    this.failed.set(false);
    this.programmingService.getBalance(gymId, query, new HttpContext().set(BYPASS_LOADER, true)).subscribe({
      next: balance => {
        if (request !== this.request) return;
        this.balance.set(balance);
        this.isLoading.set(false);
      },
      error: () => {
        if (request !== this.request) return;
        this.balance.set(null);
        this.failed.set(true);
        this.isLoading.set(false);
      }
    });
  }

  // --- ACTIONS & EVENTS ---
  shiftStart(weeks: number): void {
    this.start.update(s => addDays(s, 7 * weeks));
  }

  setWeeks(event: Event): void {
    this.weeks.set(Number((event.target as HTMLSelectElement).value));
  }

  openProfile(row: ProfileRow): void {
    this.days.set(sortDays(row.days));
  }

  toggleDay(day: DayOfWeek): void {
    this.days.update(days => days.includes(day) ? days.filter(d => d !== day) : sortDays([...days, day]));
  }

  closeProfile(): void {
    this.days.set([]);
  }

  // --- HELPERS ---
  dayName(day: DayOfWeek): string {
    return dayShort(day, this.lang());
  }

  private alertTexts(profile: ProgrammingProfile | null): AlertText[] {
    if (!profile) return [];
    return profile.alerts.map(alert => {
      if (alert.kind === 'PATTERN_GAP') {
        const full = profile.axes.find(a => a.axis === alert.subject)?.fullShare ?? 0;
        const axis = this.translate.instant('MOVEMENT.PATTERNS.AXES.' + alert.subject);
        return {
          kind: alert.kind,
          title: this.translate.instant('PROGRAMMING.ALERT.GAP_TITLE', {axis, share: percent(alert.value, this.lang())}),
          why: this.translate.instant('PROGRAMMING.ALERT.GAP_WHY', {full: percent(full, this.lang())})
        };
      }
      return {
        kind: alert.kind,
        title: this.translate.instant('PROGRAMMING.ALERT.MINIMUM_TITLE', {
          group: this.translate.instant('PROGRAMMING.GROUPS.' + alert.subject), sets: decimal(alert.value, this.lang())
        }),
        why: this.translate.instant('PROGRAMMING.ALERT.MINIMUM_WHY', {limit: decimal(alert.limit, this.lang())})
      };
    });
  }

  private shortAlert(row: ProfileRow): string {
    const a = row.main;
    return a.kind === 'PATTERN_GAP'
      ? `${this.translate.instant('MOVEMENT.PATTERNS.AXES.' + a.subject)} ${percent(a.value, this.lang())}`
      : this.translate.instant('PROGRAMMING.ALERT.MINIMUM_SHORT', {
        group: this.translate.instant('PROGRAMMING.GROUPS.' + a.subject), sets: decimal(a.value, this.lang())
      });
  }
}
