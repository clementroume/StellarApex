import {ChangeDetectionStrategy, Component, computed, effect, inject, input, signal} from '@angular/core';
import {NgIcon} from '@ng-icons/core';
import {TranslatePipe} from '@ngx-translate/core';
import {ThemeService} from '../../../core/services/theme.service';
import {MuscleCreditComponent} from '../muscle-credit/muscle-credit.component';
import {canShow3d, muscleImageUrl, muscleModelEntry, muscleRegionEntry, muscleRegionUrl} from '../muscle-model/muscle-model';
import {MuscleViewerComponent} from '../muscle-viewer/muscle-viewer.component';

/**
 * The square scene of a muscle (`code`) or of a region of the catalog (`region`): its image first, then the 3D model
 * takes over. The image stays as the fallback (no WebGL, data saver, model failure). A muscle missing from the model
 * shows its own image or an icon.
 */
@Component({
  selector: 'app-muscle-scene',
  standalone: true,
  imports: [NgIcon, TranslatePipe, MuscleCreditComponent, MuscleViewerComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {class: 'block'},
  template: `
    <div class="relative aspect-square w-full overflow-hidden rounded-xl" data-slot="muscle-scene">
      @if (posterUrl(); as url) {
        <img [src]="url" [alt]="alt()" width="480" height="480"
             class="absolute inset-0 size-full transition-opacity" style="object-fit: contain"
             [class.opacity-0]="viewerReady()" (error)="brokenPoster.set(url)"/>
      } @else if (!viewerReady()) {
        <div class="flex size-full flex-col items-center justify-center bg-card" data-slot="muscle-scene-empty">
          <ng-icon name="heroEye" class="mb-2 text-4xl text-secondary/50" aria-hidden="true"/>
          <span class="font-bold text-secondary">{{ 'GLOBAL.NO_IMAGE' | translate }}</span>
        </div>
      }
      @if (viewerEntry(); as entry) {
        @defer (on viewport) {
          <app-muscle-viewer class="absolute inset-0" [entry]="entry" [theme]="theme()"
                             (ready)="viewerReady.set(true)" (failed)="onViewerFailed()"/>
        } @placeholder {
          <div class="absolute inset-0"></div>
        }
      }
    </div>
    @if (hasModel()) {
      <app-muscle-credit class="mt-1 block text-center"/>
    }
  `
})
export class MuscleSceneComponent {
  // --- INJECTIONS ---
  code = input<string | null | undefined>();
  region = input<string | null | undefined>();
  imageUrl = input<string | null | undefined>();
  alt = input.required<string>();
  private readonly themeService = inject(ThemeService);

  // --- STATE (SIGNALS & VARIABLES) ---
  readonly theme = this.themeService.currentTheme;
  readonly viewerReady = signal(false);
  readonly brokenPoster = signal<string | null>(null);
  private readonly viewerFailed = signal(false);
  private readonly webGl = canShow3d();

  // --- COMPUTED STATE ---
  private readonly modelEntry = computed(() => muscleRegionEntry(this.region()) ?? muscleModelEntry(this.code()));
  readonly viewerEntry = computed(() => (this.webGl && !this.viewerFailed() ? this.modelEntry() : undefined));
  readonly hasModel = computed(() => !!this.modelEntry());
  readonly posterUrl = computed(() => {
    const url = muscleRegionUrl(this.region(), this.theme()) ?? muscleImageUrl(this.code(), this.theme()) ?? this.imageUrl() ?? null;
    return url === this.brokenPoster() ? null : url;
  });

  // --- LIFECYCLE ---
  constructor() {
    effect(() => {
      if (!this.viewerEntry()) this.viewerReady.set(false);
    });
  }

  // --- ACTIONS & EVENTS ---
  onViewerFailed(): void {
    this.viewerFailed.set(true);
    this.viewerReady.set(false);
  }
}
