import {ChangeDetectionStrategy, Component, computed, effect, inject, signal} from '@angular/core';
import {HttpContext} from '@angular/common/http';
import {RouterLink} from '@angular/router';
import {TranslatePipe, TranslateService} from '@ngx-translate/core';
import {NgIcon, provideIcons} from '@ng-icons/core';
import {HlmBadge} from '@spartan-ng/helm/badge';
import {HlmButton} from '@spartan-ng/helm/button';
import {HlmDialogImports} from '@spartan-ng/helm/dialog';
import {HlmEmptyImports} from '@spartan-ng/helm/empty';
import {HlmSpinner} from '@spartan-ng/helm/spinner';
import {WeeklyBalance} from '../../api/aldebaran/models/balance.model';
import {BalanceService} from '../../api/aldebaran/services/balance.service';
import {BYPASS_LOADER} from '../../core/interceptors/loading.interceptor';
import {PageComponent} from '../../shared/component/page/page.component';
import {addDays, localDate, mondayOf, shortDay} from '../sessions/session.utils';
import {ReviewForceComponent} from './review-force.component';
import {ReviewMetconsComponent} from './review-metcons.component';
import {ReviewModalitiesComponent} from './review-modalities.component';
import {ReviewMusclesComponent} from './review-muscles.component';
import {ReviewVolumeComponent} from './review-volume.component';
import {weekLabel} from './review.utils';

import {heroChevronLeft, heroChevronRight} from '@ng-icons/heroicons/outline';
/**
 * The athlete's weekly balance (`/training/review`, lot D): muscles, volume, metcons, strength and
 * modalities of the selected week and the 3 before (D125), facts only (D89), explained in one help
 * dialog opened from the "?" next to the title. The current week is the
 * one of the device's local day; the server decides nothing about it.
 */
@Component({
  viewProviders: [provideIcons({heroChevronLeft, heroChevronRight})],
  selector: 'app-weekly-review',
  standalone: true,
  imports: [
    RouterLink, TranslatePipe, NgIcon, HlmBadge, HlmButton, HlmDialogImports, HlmEmptyImports, HlmSpinner, PageComponent,
    ReviewMusclesComponent, ReviewVolumeComponent, ReviewMetconsComponent, ReviewForceComponent, ReviewModalitiesComponent
  ],
  templateUrl: './weekly-review.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {'(document:visibilitychange)': 'refreshToday()', '(window:focus)': 'refreshToday()'}
})
export class WeeklyReviewComponent {
  // --- INJECTIONS ---
  private readonly balanceService = inject(BalanceService);
  private readonly translate = inject(TranslateService);

  // --- STATE (SIGNALS & VARIABLES) ---
  /** The device's day, read again when the page comes back to the foreground (a new week may start). */
  private readonly today = signal(localDate());
  /** Monday of the current week, on the device's calendar. */
  readonly currentMonday = computed(() => mondayOf(this.today()));
  readonly monday = signal(mondayOf(localDate()));
  readonly balance = signal<WeeklyBalance | null>(null);
  readonly isLoading = signal(true);
  readonly failed = signal(false);
  private request = 0;
  /** Sections explained in the help dialog, in the order of the page. */
  readonly helpParts = ['MUSCLES', 'VOLUME', 'METCONS', 'FORCE', 'MODALITIES'] as const;

  // --- COMPUTED STATE ---
  readonly lang = computed(() => this.translate.currentLang() ?? this.translate.fallbackLang() ?? 'en');
  readonly isCurrent = computed(() => this.monday() >= this.currentMonday());
  readonly weekText = computed(() => weekLabel(this.monday(), this.lang()));
  /** "Du 19 oct. au 15 nov.": the window of the sections. */
  readonly windowText = computed(() => {
    const b = this.balance();
    return b ? this.translate.instant('REVIEW.WINDOW', {
      from: shortDay(b.windowStart, this.lang()), to: shortDay(addDays(b.weekStart, 6), this.lang())
    }) : '';
  });
  /** Nothing at all in the window: one sentence for the whole screen (D129). */
  readonly isEmpty = computed(() => {
    const b = this.balance();
    return !!b && b.volume.slice(-4).every(w => w.sets <= 0) && Object.values(b.minutes).every(m => m <= 0);
  });

  // --- LIFECYCLE ---
  constructor() {
    effect(() => this.load(this.monday()));
  }

  // --- DATA FETCHING ---
  private load(monday: string): void {
    const request = ++this.request;
    this.isLoading.set(true);
    this.failed.set(false);
    this.balanceService.getMyBalance(monday, new HttpContext().set(BYPASS_LOADER, true)).subscribe({
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
  previous(): void {
    this.monday.update(m => addDays(m, -7));
  }

  /** Reads the device's day again: a page left open past Sunday midnight gets its new current week. */
  refreshToday(): void {
    this.today.set(localDate());
  }

  next(): void {
    if (!this.isCurrent()) this.monday.update(m => addDays(m, 7));
  }
}
