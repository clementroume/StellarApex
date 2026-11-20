import {ChangeDetectionStrategy, Component, computed, effect, inject, signal} from '@angular/core';
import {HttpContext, HttpErrorResponse} from '@angular/common/http';
import {ActivatedRoute, Router, RouterLink} from '@angular/router';
import {TranslatePipe, TranslateService} from '@ngx-translate/core';
import {NgIcon, provideIcons} from '@ng-icons/core';
import {heroLockClosed, heroMapPin} from '@ng-icons/heroicons/outline';
import {HlmButton} from '@spartan-ng/helm/button';
import {HlmAlertDialogImports} from '@spartan-ng/helm/alert-dialog';
import {HlmSpinner} from '@spartan-ng/helm/spinner';
import {
  DayOfWeek, HeavyDays, OrderApplyRequest, OrderMode, OrderPart, OrderProposal
} from '../../api/aldebaran/models/programming.model';
import {ProgrammingService} from '../../api/aldebaran/services/programming.service';
import {BYPASS_LOADER} from '../../core/interceptors/loading.interceptor';
import {NotificationService} from '../../core/services/notification.service';
import {PageComponent} from '../../shared/component/page/page.component';
import {StaffGymPickerComponent, staffGymSelection} from '../box/staff-gym';
import {localDate, mondayOf, shortDay} from '../sessions/session.utils';
import {dayShort} from './programming.utils';

/** A part shown in the proposed grid. */
interface PartCell {
  part: OrderPart;
  moved: boolean;
  from: string;
}

/**
 * The session-order proposal (`/box/programming/order`, lot D', D168-D171), for the writers of the
 * box or program: the cycle of the balance, the mode (WOD only, whole sessions, WOD and Skills),
 * the strength-day option and pins; then each week before and after, the weeks to keep and their
 * application after confirmation. Aldebaran searches the order and checks every rule; this page
 * shows what it sends.
 */
@Component({
  viewProviders: [provideIcons({heroLockClosed, heroMapPin})],
  selector: 'app-session-order',
  standalone: true,
  imports: [
    TranslatePipe, NgIcon, HlmButton, HlmAlertDialogImports, HlmSpinner, PageComponent, RouterLink,
    StaffGymPickerComponent
  ],
  templateUrl: './session-order.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class SessionOrderComponent {
  // --- INJECTIONS ---
  private readonly programmingService = inject(ProgrammingService);
  private readonly translate = inject(TranslateService);
  private readonly router = inject(Router);
  private readonly notifications = inject(NotificationService);
  private readonly params = inject(ActivatedRoute).snapshot.queryParamMap;

  // --- STATE (SIGNALS & VARIABLES) ---
  readonly gym = staffGymSelection('WOD_WRITE');
  /** The balance's cycle and switches (query parameters), else the current 4 weeks in RX. */
  readonly start = this.params.get('start') ?? mondayOf(localDate());
  readonly weeks = Number(this.params.get('weeks') ?? 4);
  readonly scaled = this.params.get('scaled') === 'true';
  readonly optional = this.params.get('optional') === 'true';
  readonly mode = signal<OrderMode>('WOD_ONLY');
  readonly heavy = signal<HeavyDays>('STRICT');
  /** Blocks pinned to their day (never stored). */
  readonly pins = signal<number[]>([]);
  readonly proposal = signal<OrderProposal | null>(null);
  readonly isLoading = signal(true);
  readonly failed = signal(false);
  /** Mondays of the weeks to apply. */
  readonly kept = signal<string[]>([]);
  readonly confirmState = signal<'open' | 'closed'>('closed');
  readonly applying = signal(false);
  readonly modes: readonly OrderMode[] = ['WOD_ONLY', 'WHOLE_SESSIONS', 'WOD_AND_SKILLS'];
  readonly heavyOptions: readonly HeavyDays[] = ['STRICT', 'SOFT'];
  private request = 0;

  // --- COMPUTED STATE ---
  readonly lang = computed(() => this.translate.currentLang() ?? this.translate.fallbackLang() ?? 'en');
  /** The Skills move: the strength-day option applies. */
  readonly skillsMove = computed(() => this.mode() !== 'WOD_ONLY');
  /** Back to the balance on the same gym, cycle and switches. */
  readonly backParams = computed(() => ({
    gym: this.gym.gymId(), start: this.start, weeks: this.weeks, scaled: this.scaled, optional: this.optional
  }));
  readonly improved = computed(() => {
    const p = this.proposal();
    return !!p && p.weeks.some(w => w.changed);
  });
  /** Each week: its days in the proposed order, the moves in words. */
  readonly weekViews = computed(() => (this.proposal()?.weeks ?? []).map(w => ({
    monday: w.monday,
    changed: w.changed,
    title: this.translate.instant('PROGRAMMING.ORDER.WEEK', {date: shortDay(w.monday, this.lang())}),
    days: w.days.map(d => ({
      key: d.date,
      name: dayShort(d.day, this.lang()),
      date: shortDay(d.date, this.lang()),
      locked: d.locked,
      skill: this.cell(d.skillAfter, d.day),
      wod: this.cell(d.wodAfter, d.day)
    })),
    moves: w.days.flatMap(d => [
      this.moveText('PROGRAMMING.ORDER.SKILL', d.skillAfter, d.day),
      this.moveText('PROGRAMMING.ORDER.WOD', d.wodAfter, d.day)
    ].filter((t): t is string => t !== null))
  })));

  // --- LIFECYCLE ---
  constructor() {
    const gymParam = this.params.get('gym');
    if (gymParam) this.gym.select(Number(gymParam));
    effect(() => {
      const gymId = this.gym.gymId();
      const query = {mode: this.mode(), heavy: this.heavy(), pinnedBlockIds: this.pins()};
      if (gymId !== null) this.load(gymId, query);
    });
  }

  // --- DATA FETCHING ---
  private load(gymId: number, query: {mode: OrderMode; heavy: HeavyDays; pinnedBlockIds: number[]}): void {
    const request = ++this.request;
    this.isLoading.set(true);
    this.failed.set(false);
    this.programmingService.proposeOrder(gymId, {
      start: this.start, weeks: this.weeks, scaled: this.scaled, optional: this.optional, ...query
    }, new HttpContext().set(BYPASS_LOADER, true)).subscribe({
      next: proposal => {
        if (request !== this.request) return;
        this.proposal.set(proposal);
        this.kept.set(proposal.weeks.filter(w => w.changed).map(w => w.monday));
        this.isLoading.set(false);
      },
      error: () => {
        if (request !== this.request) return;
        this.proposal.set(null);
        this.failed.set(true);
        this.isLoading.set(false);
      }
    });
  }

  // --- ACTIONS & EVENTS ---
  /** Pins a part to its own day (or unpins it), then asks again. */
  togglePin(part: OrderPart): void {
    this.pins.update(pins => part.pinned
      ? pins.filter(id => !part.blockIds.includes(id))
      : [...pins, ...part.blockIds.filter(id => !pins.includes(id))]);
  }

  toggleKeep(monday: string): void {
    this.kept.update(kept => kept.includes(monday) ? kept.filter(m => m !== monday) : [...kept, monday]);
  }

  openConfirm(): void {
    this.confirmState.set('open');
  }

  closeConfirm(): void {
    this.confirmState.set('closed');
  }

  /** Applies the weeks kept: their unlocked days in the proposed order. */
  apply(): void {
    const proposal = this.proposal();
    const gymId = this.gym.gymId();
    if (!proposal || gymId === null) return;
    const request: OrderApplyRequest = {
      mode: proposal.mode,
      weeks: proposal.weeks.filter(w => this.kept().includes(w.monday)).map(w => ({
        monday: w.monday,
        days: w.days.filter(d => !d.locked).map(d => ({
          sessionId: d.sessionId,
          skillBlockIds: d.skillAfter?.blockIds ?? [],
          wodBlockIds: d.wodAfter?.blockIds ?? []
        }))
      }))
    };
    this.applying.set(true);
    this.closeConfirm();
    this.programmingService.applyOrder(gymId, request).subscribe({
      next: () => {
        this.applying.set(false);
        this.notifications.showSuccess(this.translate.instant('PROGRAMMING.ORDER.APPLIED'));
        void this.router.navigate(['/box/programming'], {queryParams: this.backParams()});
      },
      error: (error: HttpErrorResponse) => {
        this.applying.set(false);
        this.notifications.showError(error.error?.detail ?? this.translate.instant('PROGRAMMING.ORDER.APPLY_ERROR'));
        this.pins.set([...this.pins()]); // ask again: the programming changed
      }
    });
  }

  // --- HELPERS ---
  private cell(part: OrderPart | null, day: DayOfWeek): PartCell | null {
    return part ? {part, moved: part.fromDay !== day, from: dayShort(part.fromDay, this.lang())} : null;
  }

  private moveText(kind: string, part: OrderPart | null, day: DayOfWeek): string | null {
    if (!part || part.fromDay === day) return null;
    return this.translate.instant('PROGRAMMING.ORDER.MOVE', {
      part: this.translate.instant(kind), title: part.title,
      from: dayShort(part.fromDay, this.lang()), to: dayShort(day, this.lang())
    });
  }
}
