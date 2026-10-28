import {ChangeDetectionStrategy, Component} from '@angular/core';

/** Section title between two rules, for long forms. */
@Component({
  selector: 'app-section-divider',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {class: 'flex items-center gap-3 my-2 text-sm font-medium text-muted-foreground'},
  template: `
    <span class="h-px flex-1 bg-border" aria-hidden="true"></span>
    <span><ng-content/></span>
    <span class="h-px flex-1 bg-border" aria-hidden="true"></span>
  `
})
export class SectionDividerComponent {
}
