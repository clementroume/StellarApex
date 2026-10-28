import {ChangeDetectionStrategy, Component, input, output} from '@angular/core';
import {NgIcon} from '@ng-icons/core';
import {HlmBadge} from '@spartan-ng/helm/badge';
import {HlmCollapsibleImports} from '@spartan-ng/helm/collapsible';

/** Collapsible list section (spartan collapsible): title, item count, projected rows. */
@Component({
  selector: 'app-list-group',
  standalone: true,
  imports: [NgIcon, HlmBadge, HlmCollapsibleImports],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div hlmCollapsible class="bg-card border rounded-xl overflow-hidden"
         [expanded]="expanded()" (expandedChange)="expandedChange.emit($event)">
      <button hlmCollapsibleTrigger type="button"
              class="group w-full flex items-center justify-between gap-3 px-4 min-h-14 text-left hover:bg-muted/60
                     focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-inset">
        <span class="flex items-center gap-3 text-xl font-medium">
          {{ title() }}
          <span hlmBadge class="bg-accent/15 text-accent">{{ count() }}</span>
        </span>
        <ng-icon name="heroChevronDown" size="1.25rem" aria-hidden="true"
                 class="shrink-0 transition-transform group-aria-expanded:rotate-180"/>
      </button>
      <div hlmCollapsibleContent>
        <ul class="divide-y border-t">
          <ng-content/>
        </ul>
      </div>
    </div>
  `
})
export class ListGroupComponent {
  // --- STATE (SIGNALS & VARIABLES) ---
  readonly title = input.required<string>();
  readonly count = input.required<number>();
  readonly expanded = input<boolean>(false);
  readonly expandedChange = output<boolean>();
}
