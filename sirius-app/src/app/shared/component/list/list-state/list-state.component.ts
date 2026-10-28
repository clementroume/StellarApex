import {ChangeDetectionStrategy, Component, input} from '@angular/core';

import {HlmSpinner} from '@spartan-ng/helm/spinner';
import {HlmEmptyImports} from '@spartan-ng/helm/empty';
import {NgIcon} from '@ng-icons/core';
import {TranslatePipe} from '@ngx-translate/core';
@Component({
  selector: 'app-list-state',
  standalone: true,
  imports: [HlmSpinner, HlmEmptyImports, NgIcon, TranslatePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (isLoading() && isRawEmpty()) {
      <div class="flex justify-center p-12">
        <hlm-spinner class="text-3xl text-primary" [aria-label]="'GLOBAL.LOADING' | translate"/>
      </div>
    } @else if (isRawEmpty()) {
      <div hlmEmpty class="bg-card border">
        <p hlmEmptyDescription>{{ emptyMessage() }}</p>
      </div>
    } @else if (isFilteredEmpty()) {
      <div hlmEmpty class="bg-card border border-dashed">
        <ng-icon hlmEmptyMedia variant="icon" name="heroMagnifyingGlass" aria-hidden="true"/>
        <p hlmEmptyTitle>{{ emptySearchMessage() }}</p>
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
