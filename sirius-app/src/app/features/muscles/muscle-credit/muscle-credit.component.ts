import {ChangeDetectionStrategy, Component} from '@angular/core';
import {TranslatePipe} from '@ngx-translate/core';
import {LICENSE_URL} from '../muscle-model/muscle-model';

/** Credit required by CC BY-SA 4.0 wherever the muscle model or its images are shown. Sources and changes are in the licence file. */
@Component({
  selector: 'app-muscle-credit',
  standalone: true,
  imports: [TranslatePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <p class="text-muted-foreground" style="font-size: 0.6875rem" data-slot="muscle-credit">
      {{ 'MUSCLE.CREDIT.INTRO' | translate }}
      <a class="underline" [href]="licenseUrl" target="_blank" rel="noopener">Z-Anatomy · BodyParts3D · CC BY-SA 4.0</a>
    </p>
  `
})
export class MuscleCreditComponent {
  readonly licenseUrl = LICENSE_URL;
}
