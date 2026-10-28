import {ChangeDetectionStrategy, Component, input, output} from '@angular/core';
import {NgIcon} from '@ng-icons/core';

import {HlmButton} from '@spartan-ng/helm/button';
import {HlmBadge} from '@spartan-ng/helm/badge';
import {TranslatePipe} from '@ngx-translate/core';
@Component({
  selector: 'app-detail-header',
  standalone: true,
  imports: [NgIcon, HlmButton, HlmBadge, TranslatePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="mb-8 flex flex-col md:flex-row items-center justify-between gap-4">
      <div class="flex items-center gap-3">
        <button hlmBtn variant="ghost" size="icon-lg" class="rounded-full" (click)="back.emit()"
                [attr.aria-label]="'GLOBAL.BACK' | translate">
          <ng-icon name="hugeCircleArrowLeft02" size="1.75em" aria-hidden="true"/>
        </button>
        <h1 class="text-3xl font-bold">
          {{ title() }}
          @if (subtitle()) {
            <span class="text-xl text-muted-foreground font-normal ml-2">({{ subtitle() }})</span>
          }
        </h1>
      </div>
      <div class="flex items-center gap-2 empty:hidden">
        @if (badgeText()) {
          <span hlmBadge class="h-7 px-3 text-sm bg-accent/15 text-accent">{{ badgeText() }}</span>
        }
        <!-- Optional right-hand content (e.g. a link to related content) -->
        <ng-content></ng-content>
      </div>
    </div>
  `
})
export class DetailHeaderComponent {
  title = input.required<string>();
  subtitle = input<string>();
  badgeText = input<string>();
  back = output<void>();
}
