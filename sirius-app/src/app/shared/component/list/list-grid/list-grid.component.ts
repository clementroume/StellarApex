import {Component, ContentChild, Input, TemplateRef} from '@angular/core';
import {NgTemplateOutlet} from '@angular/common';

@Component({
  selector: 'app-list-grid',
  standalone: true,
  imports: [NgTemplateOutlet],
  template: `
    <div class="w-full">

      <div class="flex flex-col gap-4 md:hidden">
        @for (item of items; track $index) {
          <ng-container *ngTemplateOutlet="template; context: { $implicit: item }"></ng-container>
        }
      </div>

      <div class="hidden md:flex flex-row gap-4 items-start">

        <div class="flex-1 flex flex-col gap-4 w-full">
          @for (item of items; track $index; let i = $index) {
            @if (i % 2 === 0) {
              <ng-container *ngTemplateOutlet="template; context: { $implicit: item }"></ng-container>
            }
          }
        </div>

        <div class="flex-1 flex flex-col gap-4 w-full">
          @for (item of items; track $index; let i = $index) {
            @if (i % 2 !== 0) {
              <ng-container *ngTemplateOutlet="template; context: { $implicit: item }"></ng-container>
            }
          }
        </div>

      </div>

    </div>
  `
})
export class ListGridComponent<T> {
  @Input({required: true}) items!: T[];
  @ContentChild(TemplateRef) template!: TemplateRef<{ $implicit: T }>;
}
