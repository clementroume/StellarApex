import {ChangeDetectionStrategy, Component, input, output} from '@angular/core';
import {NgIconComponent} from '@ng-icons/core';

import {HlmInputGroupImports} from '@spartan-ng/helm/input-group';
@Component({
  selector: 'app-search-input',
  standalone: true,
  imports: [NgIconComponent, HlmInputGroupImports],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="flex justify-center mb-8 w-full">
      <div hlmInputGroup class="w-full max-w-md h-11 md:h-10 bg-card">
        <div hlmInputGroupAddon>
          <ng-icon name="heroMagnifyingGlass" aria-hidden="true"/>
        </div>
        <input hlmInputGroupInput type="search"
               [placeholder]="placeholder()"
               [attr.aria-label]="placeholder()"
               [value]="value()"
               (input)="onInput($event)"/>
      </div>
    </div>
  `
})
export class SearchInputComponent {
  placeholder = input<string>('');
  value = input<string>('');
  valueChange = output<string>();

  onInput(event: Event): void {
    const inputElement = event.target as HTMLInputElement;
    this.valueChange.emit(inputElement.value);
  }
}
