import {ChangeDetectionStrategy, Component, input} from '@angular/core';

/**
 * Page template: title, optional subtitle, actions (`[pageActions]`, stacked full width on mobile)
 * and content in a centred column.
 */
@Component({
  selector: 'app-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="mx-auto w-full" [class]="width()">
      <header class="mb-6 flex flex-col gap-4 md:flex-row md:items-end md:justify-between">
        <div class="min-w-0">
          <h1 class="text-2xl md:text-3xl font-bold">{{ title() }}</h1>
          @if (subtitle()) {
            <p class="mt-1 text-muted-foreground">{{ subtitle() }}</p>
          }
        </div>
        <div class="flex flex-col sm:flex-row gap-2 empty:hidden">
          <ng-content select="[pageActions]"/>
        </div>
      </header>
      <ng-content/>
    </div>
  `
})
export class PageComponent {
  readonly title = input.required<string>();
  readonly subtitle = input<string>();
  /** Max width of the column (Tailwind class). */
  readonly width = input<string>('max-w-5xl');
}
