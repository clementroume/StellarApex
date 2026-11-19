import {ChangeDetectionStrategy, Component, input} from '@angular/core';
import {NgIcon, provideIcons} from '@ng-icons/core';
import {TranslatePipe} from '@ngx-translate/core';
import {HlmEmptyImports} from '@spartan-ng/helm/empty';

import {heroClock} from '@ng-icons/heroicons/outline';
/** Placeholder for a space whose feature comes in a later lot. */
@Component({
  viewProviders: [provideIcons({heroClock})],
  selector: 'app-coming-soon',
  standalone: true,
  imports: [NgIcon, TranslatePipe, HlmEmptyImports],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div hlmEmpty class="bg-card border">
      <div hlmEmptyHeader>
        <div hlmEmptyMedia variant="icon"><ng-icon [name]="icon()" aria-hidden="true"/></div>
        <p hlmEmptyTitle>{{ 'GLOBAL.COMING_SOON' | translate }}</p>
        <p hlmEmptyDescription>{{ description() }}</p>
      </div>
      <ng-content/>
    </div>
  `
})
export class ComingSoonComponent {
  readonly icon = input<string>('heroClock');
  readonly description = input.required<string>();
}
