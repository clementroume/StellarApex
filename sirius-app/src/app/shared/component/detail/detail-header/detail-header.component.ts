import {Component, EventEmitter, Input, Output, ChangeDetectionStrategy} from '@angular/core';
import {NgIcon} from '@ng-icons/core';

@Component({
  selector: 'app-detail-header',
  standalone: true,
  imports: [NgIcon],
  changeDetection: ChangeDetectionStrategy.Eager,
  template: `
    <div class="mb-8 flex flex-col md:flex-row items-center justify-between gap-4">
      <div class="flex items-center gap-4">
        <button (click)="back.emit()" class="btn btn-ghost btn-circle">
          <ng-icon name="hugeCircleArrowLeft02" size="1.75em"></ng-icon>
        </button>
        <h1 class="text-3xl font-bold">
          {{ title }}
          @if (subtitle) {
            <span class="text-xl opacity-50 font-normal ml-2">({{ subtitle }})</span>
          }
        </h1>
      </div>
      @if (badgeText) {
        <div class="badge badge-soft badge-accent badge-lg">
          {{ badgeText }}
        </div>
      }
    </div>
  `
})
export class DetailHeaderComponent {
  @Input({required: true}) title!: string;
  @Input() subtitle?: string;
  @Input() badgeText?: string;
  @Output() back = new EventEmitter<void>();
}
