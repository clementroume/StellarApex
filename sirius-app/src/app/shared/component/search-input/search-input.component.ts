import {ChangeDetectionStrategy, Component, input, output} from '@angular/core';
import {NgIconComponent} from '@ng-icons/core';

@Component({
  selector: 'app-search-input',
  standalone: true,
  imports: [NgIconComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="flex justify-center mb-8 w-full">
      <div class="flex items-center w-full max-w-md">
        <div
          class="flex items-center w-full bg-base-100 h-10 rounded-lg px-4 gap-2 border border-neutral focus-within:ring-2 focus-within:ring-neutral/80">
          <ng-icon name="heroMagnifyingGlass" class="opacity-70"></ng-icon>
          <input type="text"
                 class="bg-transparent w-full outline-none"
                 [placeholder]="placeholder()"
                 [value]="value()"
                 (input)="onInput($event)"/>
        </div>
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
