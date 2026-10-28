import {ChangeDetectionStrategy, Component, inject} from '@angular/core';
import {RouterLink, RouterLinkActive} from '@angular/router';
import {TranslatePipe} from '@ngx-translate/core';
import {NgIcon} from '@ng-icons/core';
import {SpacesService} from '../spaces.service';

/** Sub-navigation of the current space (e.g. Catalogue: WODs · Movements · Muscles), scrollable on mobile. */
@Component({
  selector: 'app-space-nav',
  standalone: true,
  imports: [RouterLink, RouterLinkActive, TranslatePipe, NgIcon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (spacesService.current(); as space) {
      @if (space.children.length > 1) {
        <nav class="border-b bg-background" [attr.aria-label]="space.label | translate">
          <ul class="mx-auto flex max-w-7xl gap-1 overflow-x-auto no-scrollbar px-3 md:px-6">
            @for (link of space.children; track link.link) {
              <li class="shrink-0">
                <a [routerLink]="link.link" routerLinkActive="border-primary text-foreground" ariaCurrentWhenActive="page"
                   class="flex items-center gap-2 h-11 px-3 border-b-2 border-transparent text-sm font-medium text-muted-foreground hover:text-foreground">
                  <ng-icon [name]="link.icon" size="1.1rem" aria-hidden="true"/>
                  {{ link.label | translate }}
                </a>
              </li>
            }
          </ul>
        </nav>
      }
    }
  `
})
export class SpaceNavComponent {
  // --- INJECTIONS ---
  protected readonly spacesService = inject(SpacesService);
}
