import {ChangeDetectionStrategy, Component, input} from '@angular/core';
import {RouterLink} from '@angular/router';
import {NgIconComponent} from '@ng-icons/core';
import {NgOptimizedImage, UpperCasePipe} from '@angular/common';

import {HlmBadge} from '@spartan-ng/helm/badge';
@Component({
  selector: 'app-list-item',
  standalone: true,
  imports: [RouterLink, NgIconComponent, UpperCasePipe, NgOptimizedImage, HlmBadge],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <li>
      <a [routerLink]="link()"
         class="w-full flex items-center justify-between gap-4 p-4 hover:bg-muted/60 transition-colors group">
        <div class="flex items-center gap-4 min-w-0">
          <div class="size-12 shrink-0 rounded-lg overflow-hidden bg-muted text-muted-foreground flex items-center justify-center">
            @if (imageUrl()) {
              <img [ngSrc]="imageUrl()!" [alt]="title()" width="48" height="48" class="object-cover size-full"/>
            } @else {
              <span class="text-xl font-bold" aria-hidden="true">{{ title().charAt(0) | uppercase }}</span>
            }
          </div>
          <div class="flex flex-col min-w-0">
            <div class="font-semibold text-lg flex items-center gap-2">
              <span class="truncate">{{ title() }}</span>
              @if (badge()) {
                <span hlmBadge variant="secondary" class="font-normal">{{ badge() }}</span>
              }
            </div>
            @if (subtitle()) {
              <span data-slot="list-item-subtitle" class="text-sm text-muted-foreground">{{ subtitle() }}</span>
            }
          </div>
        </div>
        <ng-icon name="heroChevronRight" size="1rem" aria-hidden="true"
                 class="shrink-0 text-muted-foreground group-hover:text-primary transition-colors"/>
      </a>
    </li>
  `
})
export class ListItemComponent {
  title = input.required<string>();
  link = input.required<any[]>();
  imageUrl = input<string>();
  badge = input<string>();
  subtitle = input<string>();
}
