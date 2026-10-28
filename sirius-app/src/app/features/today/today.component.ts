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
import {ComingSoonComponent} from '../../shared/component/coming-soon/coming-soon.component';

/** Home (D38): today's session (planning comes later), latest scores and records of the displayed box/prog. */
@Component({
  selector: 'app-today',
  standalone: true,
  imports: [RouterLink, TranslatePipe, NgIcon, HlmBadge, HlmButton, HlmCardImports, HlmSkeleton, PageComponent,
    ComingSoonComponent, ScoreRowComponent],
  templateUrl: './today.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TodayComponent {
  // --- INJECTIONS ---
  private readonly authService = inject(AuthService);
  private readonly scoreService = inject(ScoreService);
  private readonly translate = inject(TranslateService);
  protected readonly displayGym = inject(DisplayGymService);

  // --- STATE (SIGNALS & VARIABLES) ---
  protected readonly isLoading = signal(true);
  protected readonly hasError = signal(false);
  private readonly scores = signal<ScoreResponse[]>([]);

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
  protected readonly latestScores = computed(() => this.visibleScores().slice(0, 5));
  protected readonly latestRecords = computed(() => this.visibleScores().filter(s => s.personalRecord).slice(0, 5));

  // --- LIFECYCLE ---
  constructor() {
    this.loadScores();
  }

  // --- DATA FETCHING ---
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
