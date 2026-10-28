import {ChangeDetectionStrategy, Component, input} from '@angular/core';
import {TranslatePipe} from '@ngx-translate/core';
import {PageComponent} from '../page/page.component';
import {ComingSoonComponent} from './coming-soon.component';

/** Space page whose feature comes in a later lot; title, description and icon come from the route data. */
@Component({
  selector: 'app-space-placeholder',
  standalone: true,
  imports: [TranslatePipe, PageComponent, ComingSoonComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <app-page [title]="titleKey() | translate">
      <app-coming-soon [icon]="icon()" [description]="descriptionKey() | translate"/>
    </app-page>
  `
})
export class SpacePlaceholderComponent {
  // Route data (withComponentInputBinding)
  readonly titleKey = input.required<string>();
  readonly descriptionKey = input.required<string>();
  readonly icon = input<string>('heroClock');
}
