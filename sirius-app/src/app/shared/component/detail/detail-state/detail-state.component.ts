import {Component, Input, ChangeDetectionStrategy} from '@angular/core';

@Component({
  selector: 'app-detail-state',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.Eager,
  template: `
    @if (isLoading) {
      <ng-content select="[skeleton]"></ng-content>
    } @else {
      <ng-content></ng-content>
    }
  `
})
export class DetailStateComponent {
  @Input({required: true}) isLoading!: boolean;
}
