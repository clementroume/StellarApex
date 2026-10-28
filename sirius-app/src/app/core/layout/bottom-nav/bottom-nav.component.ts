import {ChangeDetectionStrategy, Component, inject} from '@angular/core';
import {RouterLink} from '@angular/router';
import {TranslatePipe} from '@ngx-translate/core';
import {NgIcon} from '@ng-icons/core';
import {SpacesService} from '../spaces.service';

/** Mobile tab bar (D40): one thumb-sized tab per space, above the iOS home indicator. */
@Component({
  selector: 'app-bottom-nav',
  standalone: true,
  imports: [RouterLink, TranslatePipe, NgIcon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <nav class="md:hidden fixed inset-x-0 bottom-0 z-40 border-t bg-card/95 backdrop-blur pb-[env(safe-area-inset-bottom)]"
         [attr.aria-label]="'NAVBAR.SPACES.LABEL' | translate">
      <ul class="flex">
        @for (space of spacesService.spaces(); track space.key) {
          <li class="flex-1">
            <a [routerLink]="space.link"
               class="flex flex-col items-center justify-center gap-0.5 h-16 text-[11px] font-medium text-muted-foreground"
               [class.text-primary]="spacesService.current()?.key === space.key"
               [attr.aria-current]="spacesService.current()?.key === space.key ? 'page' : null">
              <ng-icon [name]="space.icon" size="1.5rem" aria-hidden="true"/>
              <span class="truncate max-w-full px-1">{{ space.label | translate }}</span>
            </a>
          </li>
        }
      </ul>
    </nav>
  `
})
export class BottomNavComponent {
  // --- INJECTIONS ---
  protected readonly spacesService = inject(SpacesService);
}
