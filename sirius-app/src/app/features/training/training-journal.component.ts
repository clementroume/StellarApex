import {ChangeDetectionStrategy, Component, computed, inject, signal} from '@angular/core';
import {HttpContext} from '@angular/common/http';
import {TranslatePipe} from '@ngx-translate/core';
import {HlmButton} from '@spartan-ng/helm/button';
import {HlmCardImports} from '@spartan-ng/helm/card';
import {HlmSpinner} from '@spartan-ng/helm/spinner';
import {ScoreService} from '../../api/aldebaran/services/score.service';
import {ScoreResponse} from '../../api/aldebaran/models/score.model';
import {BYPASS_LOADER} from '../../core/interceptors/loading.interceptor';
import {DisplayGymService} from '../../core/services/display-gym.service';
import {PageComponent} from '../../shared/component/page/page.component';
import {ScoreRowComponent} from '../wods/scores/score-row/score-row.component';

const PAGE_SIZE = 20;

/** Training journal: the user's scores, newest first, filtered by the displayed box/prog. */
@Component({
  selector: 'app-training-journal',
  standalone: true,
  imports: [TranslatePipe, HlmButton, HlmCardImports, HlmSpinner, PageComponent, ScoreRowComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <app-page [title]="'TRAINING.JOURNAL.TITLE' | translate" [subtitle]="'TRAINING.JOURNAL.SUBTITLE' | translate">
      <section hlmCard>
        <div hlmCardContent>
          <ul class="divide-y">
            @for (score of visibleScores(); track score.id) {
              <li><app-score-row [score]="score"/></li>
            } @empty {
              @if (!isLoading()) {
                <li class="py-8 text-center text-sm text-muted-foreground">{{ 'TODAY.SCORES.EMPTY' | translate }}</li>
              }
            }
          </ul>
          @if (isLoading()) {
            <div class="flex justify-center py-6"><hlm-spinner class="text-2xl text-primary" [aria-label]="'GLOBAL.LOADING' | translate"/></div>
          } @else if (hasMore()) {
            <div class="flex justify-center pt-4">
              <button hlmBtn variant="outline" size="lg" (click)="loadMore()">{{ 'TRAINING.JOURNAL.MORE' | translate }}</button>
            </div>
          }
        </div>
      </section>
    </app-page>
  `
})
export class TrainingJournalComponent {
  // --- INJECTIONS ---
  private readonly scoreService = inject(ScoreService);
  private readonly displayGym = inject(DisplayGymService);

  // --- STATE (SIGNALS & VARIABLES) ---
  private readonly scores = signal<ScoreResponse[]>([]);
  protected readonly isLoading = signal(false);
  protected readonly hasMore = signal(false);
  private page = 0;

  // --- COMPUTED STATE ---
  protected readonly visibleScores = computed(() =>
    this.scores().filter(s => s.gymId == null || this.displayGym.matches(s.gymId)));

  // --- LIFECYCLE ---
  constructor() {
    this.loadMore();
  }

  // --- DATA FETCHING ---
  protected loadMore(): void {
    this.isLoading.set(true);
    this.scoreService.getMyScores(undefined, this.page, PAGE_SIZE, new HttpContext().set(BYPASS_LOADER, true)).subscribe({
      next: slice => {
        this.scores.update(list => [...list, ...slice.content]);
        this.hasMore.set(!slice.last);
        this.page++;
        this.isLoading.set(false);
      },
      error: () => this.isLoading.set(false)
    });
  }
}
