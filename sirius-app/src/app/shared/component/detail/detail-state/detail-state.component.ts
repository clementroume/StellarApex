import {ChangeDetectionStrategy, Component, input} from '@angular/core';

@Component({
  selector: 'app-detail-state',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (isLoading()) {
      <ng-content select="[skeleton]"></ng-content>
    } @else {
      <ng-content></ng-content>
    }
  `
})
export class DetailStateComponent {
  isLoading = input.required<boolean>();
}
