import {Component, Input, ChangeDetectionStrategy} from '@angular/core';
import {NgClass} from '@angular/common';

@Component({
  selector: 'app-detail-card',
  standalone: true,
  imports: [NgClass],
  changeDetection: ChangeDetectionStrategy.Eager,
  template: `
    <div class="bg-base-100 rounded-box border border-neutral flex flex-col w-full h-full"
         [ngClass]="[paddingClass, gapClass]">

      @if (title) {
        <h3 class="font-semibold text-lg pb-2 border-b border-base-200" [class.mb-4]="gapClass === 'gap-0'">
          {{ title }}
        </h3>
      }
      <ng-content></ng-content>

    </div>
  `
})
export class DetailCardComponent {
  @Input() title?: string;
  @Input() gapClass: string = 'gap-6';
  @Input() paddingClass: string = 'p-6';
}
