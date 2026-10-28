import {ChangeDetectionStrategy, Component, input} from '@angular/core';
import {NgIcon} from '@ng-icons/core';
import {HlmLabel} from '@spartan-ng/helm/label';
import {HlmInputGroupImports} from '@spartan-ng/helm/input-group';

/**
 * Labelled form field (spartan input group): visible label, optional leading icon, then an error or a
 * hint. Project the control with `hlmInputGroupInput` (or `hlmInputGroupTextarea`), give it the same
 * `id` as `for` and `[attr.aria-describedby]="id + '-message'"` so screen readers read the message.
 */
@Component({
  selector: 'app-form-field',
  standalone: true,
  imports: [NgIcon, HlmLabel, HlmInputGroupImports],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="flex flex-col gap-1.5">
      <label hlmLabel [for]="for()">{{ label() }}</label>
      <div hlmInputGroup class="h-11 md:h-10" [attr.data-invalid]="error() ? 'true' : null">
        @if (icon()) {
          <div hlmInputGroupAddon>
            <ng-icon [name]="icon()!" aria-hidden="true"/>
          </div>
        }
        <ng-content/>
      </div>
      @if (error()) {
        <p [id]="for() + '-message'" class="text-sm text-destructive" role="alert">{{ error() }}</p>
      } @else if (hint()) {
        <p [id]="for() + '-message'" class="text-xs text-muted-foreground">{{ hint() }}</p>
      }
    </div>
  `
})
export class FormFieldComponent {
  // --- STATE (SIGNALS & VARIABLES) ---
  /** Id of the projected control. */
  readonly for = input.required<string>();
  readonly label = input.required<string>();
  readonly icon = input<string>();
  /** Translated error, shown instead of the hint when set. */
  readonly error = input<string | null>(null);
  readonly hint = input<string>();
}
