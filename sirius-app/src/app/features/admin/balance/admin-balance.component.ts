import {ChangeDetectionStrategy, Component, computed, inject, OnInit, signal} from '@angular/core';
import {RouterLink} from '@angular/router';
import {TranslatePipe, TranslateService} from '@ngx-translate/core';
import {HlmBadge} from '@spartan-ng/helm/badge';
import {HlmButton} from '@spartan-ng/helm/button';
import {HlmSpinner} from '@spartan-ng/helm/spinner';
import {UncountedLine, UncountedReason} from '../../../api/aldebaran/models/balance.model';
import {BalanceService} from '../../../api/aldebaran/services/balance.service';
import {NotificationService} from '../../../core/services/notification.service';
import {PageComponent} from '../../../shared/component/page/page.component';

/**
 * Admin › Balance control (D121 rule 5, D126): every WOD line that counts nothing in the weekly
 * balance, an unreadable text or a unit without the value of one set in the catalog, to fix in the
 * WOD or in the catalog. A button marks every stored week to rebuild after a catalog change made
 * outside the forms (a migration).
 */
@Component({
  selector: 'app-admin-balance',
  standalone: true,
  imports: [RouterLink, TranslatePipe, HlmBadge, HlmButton, HlmSpinner, PageComponent],
  templateUrl: './admin-balance.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class AdminBalanceComponent implements OnInit {
  // --- INJECTIONS ---
  private readonly balanceService = inject(BalanceService);
  private readonly notificationService = inject(NotificationService);
  private readonly translate = inject(TranslateService);

  // --- STATE (SIGNALS & VARIABLES) ---
  readonly lines = signal<UncountedLine[]>([]);
  readonly isLoading = signal(true);
  readonly failed = signal(false);
  readonly isRecomputing = signal(false);
  readonly reason = signal<UncountedReason | null>(null);
  readonly reasons: UncountedReason[] = ['UNREADABLE', 'NO_SET_VALUE'];

  // --- COMPUTED STATE ---
  readonly counts = computed(() => {
    const counts: Record<UncountedReason, number> = {UNREADABLE: 0, NO_SET_VALUE: 0};
    for (const l of this.lines()) counts[l.reason]++;
    return counts;
  });
  readonly shown = computed(() => {
    const reason = this.reason();
    return reason ? this.lines().filter(l => l.reason === reason) : this.lines();
  });

  // --- LIFECYCLE ---
  ngOnInit(): void {
    this.balanceService.getUncountedLines().subscribe({
      next: lines => {
        this.lines.set(lines);
        this.isLoading.set(false);
      },
      error: () => {
        this.failed.set(true);
        this.isLoading.set(false);
      }
    });
  }

  // --- ACTIONS & EVENTS ---
  recompute(): void {
    this.isRecomputing.set(true);
    this.balanceService.recomputeAll().subscribe({
      next: () => {
        this.isRecomputing.set(false);
        this.notificationService.showSuccess(this.translate.instant('ADMIN.BALANCE.RECOMPUTED'));
      },
      error: () => {
        this.isRecomputing.set(false);
        this.notificationService.showError(this.translate.instant('ADMIN.BALANCE.RECOMPUTE_ERROR'));
      }
    });
  }

  pickReason(reason: UncountedReason | null): void {
    this.reason.set(this.reason() === reason ? null : reason);
  }
}
