import {ChangeDetectionStrategy, Component, input} from '@angular/core';
import {NgClass} from '@angular/common';

@Component({
  selector: 'app-detail-card',
  standalone: true,
  imports: [NgClass],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div data-slot="detail-card" class="bg-card text-card-foreground rounded-xl border flex flex-col w-full h-full"
         [ngClass]="[paddingClass(), gapClass()]">
      @if (title()) {
        <h3 class="font-semibold text-lg pb-2 border-b" [class.mb-4]="gapClass() === 'gap-0'">
          {{ title() }}
        </h3>
      }
      <ng-content></ng-content>
    </div>
  `
})
export class DetailCardComponent {
  title = input<string>();
  gapClass = input<string>('gap-6');
  paddingClass = input<string>('p-6');
}
