import {ChangeDetectionStrategy, Component, input} from '@angular/core';

@Component({
  selector: 'app-list-state',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (isLoading() && isRawEmpty()) {
      <div class="flex justify-center p-12">
        <span class="loading loading-spinner loading-lg text-primary"></span>
      </div>
    } @else if (isRawEmpty()) {
      <div class="text-center py-12 text-base-content/50 bg-base-100 rounded-box">
        {{ emptyMessage() }}
      </div>
    } @else if (isFilteredEmpty()) {
      <div class="text-center py-12 text-secondary font-bold bg-base-100 rounded-box border border-secondary">
        {{ emptySearchMessage() }}
      </div>
    } @else {
      <ng-content></ng-content>
    }
  `
})
export class ListStateComponent {
  isLoading = input.required<boolean>();
  isRawEmpty = input.required<boolean>();
  isFilteredEmpty = input.required<boolean>();
  emptyMessage = input.required<string>();
  emptySearchMessage = input.required<string>();
}
