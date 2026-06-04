import {Component, Input} from '@angular/core';

@Component({
  selector: 'app-detail-card',
  standalone: true,
  template: `
    <div class="bg-base-100 rounded-box p-6 border border-neutral flex flex-col w-full h-full" [class]="gapClass">
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
