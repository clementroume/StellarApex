import {ChangeDetectionStrategy, Component, input} from '@angular/core';
import {TranslatePipe} from '@ngx-translate/core';

/**
 * A card of the balance: its heading and its content. The explanations live in the page's single
 * help dialog.
 */
@Component({
  selector: 'app-review-section',
  standalone: true,
  imports: [TranslatePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="rounded-xl border bg-card p-4 flex flex-col gap-3" [attr.aria-labelledby]="headingId()">
      <h2 class="text-base font-semibold" [id]="headingId()">{{ heading() | translate }}</h2>
      <ng-content/>
    </section>
  `
})
export class ReviewSectionComponent {
  // --- STATE (SIGNALS & VARIABLES) ---
  /** Translation key of the heading (not `title`: the host would show it as a native tooltip). */
  readonly heading = input.required<string>();
  /** Id of the heading, for the section's label (not `id`: the host would carry the same id). */
  readonly headingId = input.required<string>();
}
